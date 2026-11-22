/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.LocalDate
import java.time.YearMonth

/** Local estimates, not a server verdict or proof of genuine hardware trust. */
enum class Tier { NONE, BASIC, DEVICE, STRONG }

enum class Check {
    /** A hardware key attestation could be produced and its chain is well formed. */
    CHAIN,
    /** The chain ends at one of Google's attestation roots. */
    GOOGLE_ROOT,
    /** The attestation claims the key lives in a TEE or StrongBox. */
    HARDWARE,
    /** Bootloader locked and verified boot green, as reported by the TEE. */
    BOOT,
    /** The OS patch level is recent enough for Strong integrity. */
    PATCH,
    /** No certificate in the chain is on Google's revocation list. */
    REVOCATION,
    /** The chain is bound to the key that was just generated. */
    KEY_BINDING,
}

enum class CheckState { PASS, FAIL, UNKNOWN }

class CheckResult(val check: Check, val state: CheckState, val detail: String? = null)

enum class ChainOrigin { DEVICE, KEYBOX }

class IntegrityReport(
    val tier: Tier,
    val checks: List<CheckResult>,
    val origin: ChainOrigin,
    val strongBox: Boolean,
    val at: Long,
    val revocation: Revocation = Revocation.Unknown(),
) {
    operator fun get(check: Check) = checks.first { it.check == check }
}

/**
 * Estimates an integrity tier locally from a fresh key attestation. It cannot prove that
 * attestation claims are genuine hardware state. It runs in Laras' own process, treated as a
 * target app, so it sees exactly the chain target apps see (device chain or keybox chain).
 *
 * This is an estimate: Google's verdict also weighs signals a device cannot see.
 */
class IntegrityChecker(
    private val context: Context,
    private val keybox: KeyboxRepository,
) {
    private val roots = AttestationRoots(context)
    private val feed = RevocationFeed(context)

    suspend fun run(forceRevocation: Boolean = true): IntegrityReport = withContext(Dispatchers.IO) {
        roots.refreshIfStale()
        val challenge = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val probe = try {
            attest(challenge)
        } catch (e: Exception) {
            return@withContext failed(CheckResult(Check.CHAIN, CheckState.FAIL, e.javaClass.simpleName))
        }
        val (chain, probePublicKey) = probe
        if (chain.isEmpty()) return@withContext failed(CheckResult(Check.CHAIN, CheckState.FAIL, "short"))
        val record = AttestationRecord.of(chain.first())
        val origin = if (isKeyboxChain(chain)) ChainOrigin.KEYBOX else ChainOrigin.DEVICE
        val certsToCheck = if (origin == ChainOrigin.KEYBOX) {
            (chain + keybox.chains().flatten()).distinctBy { it.serialNumber }
        } else {
            chain
        }
        val revStatus = feed.check(certsToCheck, force = forceRevocation)
        if (origin == ChainOrigin.KEYBOX && revStatus !is Revocation.Unknown) {
            keybox.recordRevocation(revStatus)
        }

        val checks = listOf(
            chainCheck(chain, record),
            rootCheck(chain),
            hardwareCheck(record),
            bootCheck(record),
            patchCheck(record),
            revocationResult(revStatus),
            bindingCheck(record, challenge, chain.first(), probePublicKey),
        )
        IntegrityReport(
            tier = tierOf(checks),
            checks = checks,
            origin = origin,
            strongBox = record?.strongBox == true,
            at = System.currentTimeMillis(),
            revocation = revStatus,
        )
    }

    private fun failed(chain: CheckResult) = IntegrityReport(
        Tier.NONE, listOf(chain), ChainOrigin.DEVICE, false, System.currentTimeMillis())

    /**
     * True when the probe chain was served from the keybox (PATCH rung). The engine
     * re-signs the app's real leaf with the keybox private key but preserves the leaf's
     * own serial, then appends the keybox chain behind it, so the served chain looks
     * like `[patched(realSerial), keyboxLeaf, ...]`. Comparing `chain[0]` against the
     * keybox leaf therefore never matches; the keybox leaf is at `chain[1]`, and the
     * patched leaf verifies against the keybox leaf's public key.
     */
    private fun isKeyboxChain(chain: List<X509Certificate>): Boolean {
        if (chain.size < 2) return false
        val leafs = keybox.chains().mapNotNull { it.firstOrNull() }
        if (leafs.isEmpty()) return false
        if (leafs.any { it.serialNumber == chain[1].serialNumber }) return true
        return leafs.any { kb ->
            try {
                chain.first().verify(kb.publicKey)
                true
            } catch (_: Exception) { false }
        }
    }

    private fun attest(challenge: ByteArray): Pair<List<X509Certificate>, ByteArray> {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .build()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        try {
            val pair = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                .apply { initialize(spec) }.generateKeyPair()
            val chain = store.getCertificateChain(ALIAS).map { it as X509Certificate }
            return chain to pair.public.encoded
        } finally {
            try { store.deleteEntry(ALIAS) } catch (_: Exception) { }
        }
    }

    private fun chainCheck(chain: List<X509Certificate>, record: AttestationRecord?): CheckResult {
        if (chain.size < 2) return CheckResult(Check.CHAIN, CheckState.FAIL, "short")
        if (record == null) return CheckResult(Check.CHAIN, CheckState.FAIL, "no-attestation")
        return try {
            chain.forEachIndexed { i, cert ->
                cert.checkValidity()
                if (i + 1 < chain.size) cert.verify(chain[i + 1].publicKey)
            }
            CheckResult(Check.CHAIN, CheckState.PASS)
        } catch (e: Exception) {
            CheckResult(Check.CHAIN, CheckState.FAIL, e.javaClass.simpleName)
        }
    }

    private fun rootCheck(chain: List<X509Certificate>): CheckResult {
        val known = roots.publicKeys()
        val trusted = known.any { it.encoded.contentEquals(chain.last().publicKey.encoded) }
        return CheckResult(Check.GOOGLE_ROOT, if (trusted) CheckState.PASS else CheckState.FAIL)
    }

    private fun hardwareCheck(record: AttestationRecord?) = when {
        record == null -> CheckResult(Check.HARDWARE, CheckState.UNKNOWN)
        record.strongBox -> CheckResult(Check.HARDWARE, CheckState.PASS, "strongbox")
        record.hardwareBacked -> CheckResult(Check.HARDWARE, CheckState.PASS, "tee")
        else -> CheckResult(Check.HARDWARE, CheckState.FAIL, "software")
    }

    private fun bootCheck(record: AttestationRecord?): CheckResult {
        val locked = record?.deviceLocked
        val state = record?.verifiedBootState
        if (locked == null || state == null) return CheckResult(Check.BOOT, CheckState.UNKNOWN)
        val verified = state == AttestationRecord.BOOT_VERIFIED
        val detail = when {
            !locked -> "unlocked"
            !verified -> "state-$state"
            else -> null
        }
        return CheckResult(Check.BOOT, if (locked && verified) CheckState.PASS else CheckState.FAIL, detail)
    }


    private fun revocationResult(r: Revocation) = when (r) {
        is Revocation.Valid -> CheckResult(Check.REVOCATION, CheckState.PASS)
        is Revocation.Revoked -> CheckResult(Check.REVOCATION, CheckState.FAIL, r.reason)
        is Revocation.Unknown -> CheckResult(Check.REVOCATION, CheckState.UNKNOWN, r.failure?.kind?.name)
    }

    private fun bindingCheck(
        record: AttestationRecord?, challenge: ByteArray, leaf: X509Certificate, publicKey: ByteArray,
    ): CheckResult {
        val bound = record != null && record.challenge.contentEquals(challenge) &&
            leaf.publicKey.encoded.contentEquals(publicKey)
        return CheckResult(Check.KEY_BINDING, if (bound) CheckState.PASS else CheckState.FAIL)
    }

    internal companion object {
        private const val ALIAS = "laras_integrity_probe"
        private const val MAX_PATCH_AGE_MONTHS = 12L

        fun tierOf(checks: List<CheckResult>): Tier {
            fun state(c: Check) = checks.firstOrNull { it.check == c }?.state
            fun ok(c: Check) = state(c) == CheckState.PASS
            if (!ok(Check.CHAIN) || !ok(Check.KEY_BINDING)) return Tier.NONE
            if (!ok(Check.GOOGLE_ROOT) || !ok(Check.HARDWARE) || state(Check.REVOCATION) == CheckState.FAIL) {
                return Tier.BASIC
            }
            if (!ok(Check.BOOT) || !ok(Check.PATCH) || !ok(Check.REVOCATION)) return Tier.DEVICE
            return Tier.STRONG
        }

        fun patchCheck(record: AttestationRecord?, today: LocalDate = LocalDate.now()): CheckResult {
            val levels = listOf(record?.osPatchLevel, record?.vendorPatchLevel, record?.bootPatchLevel)
            // Vendor/boot fields were added in Keymaster 4; all KeyMint (100+) versions
            // support them. Legacy hardware is assessed using the fields it can supply.
            val partitionsRequired = (record?.keyMintVersion ?: 0) >= 4
            val missing = levels[0] == null || (partitionsRequired && levels.any { it == null })
            var stale = false
            val dates = mutableListOf<LocalDate>()
            for ((index, level) in levels.withIndex()) {
                if (level == null) continue
                val date = try {
                    when {
                        index == 0 && level in 200001..999912 -> YearMonth.of(level / 100, level % 100).atDay(1)
                        level in 20000101..99991231 -> LocalDate.of(level / 10000, level / 100 % 100, level % 100)
                        else -> null
                    }
                } catch (_: Exception) { null }
                if (date == null) return CheckResult(Check.PATCH, CheckState.FAIL, "invalid-patch")
                if (date.isAfter(today)) return CheckResult(Check.PATCH, CheckState.FAIL, "future-patch")
                dates += date
                val age = java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(date), YearMonth.from(today))
                if (age > MAX_PATCH_AGE_MONTHS) stale = true
            }
            if (stale) return CheckResult(Check.PATCH, CheckState.FAIL, dates.minOrNull()?.toString())
            if (missing) return CheckResult(Check.PATCH, CheckState.UNKNOWN, "missing-patch")
            return CheckResult(Check.PATCH, CheckState.PASS, dates.minOrNull()?.toString())
        }
    }
}
