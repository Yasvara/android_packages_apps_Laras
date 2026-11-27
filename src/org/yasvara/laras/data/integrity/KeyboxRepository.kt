/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import android.security.yasvara.YasvaraIntegrityContract
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

enum class KeyboxProblem { EMPTY, NOT_XML, NO_CERTIFICATE, BAD_CERTIFICATE, CHAIN_TOO_SHORT, INVALID_KEYBOX, TOO_LARGE, NO_PRIVATE_KEY, SERVICE_UNAVAILABLE }

/** What the user needs to know about the installed keybox. */
class KeyboxInfo(
    val algorithm: String,
    val serialHex: String,
    val issuer: String,
    val notAfter: Long,
    val chainLength: Int,
    val hasPrivateKey: Boolean,
    val rkpManufacturer: String = "",
) {
    val expired get() = notAfter < System.currentTimeMillis()
}

sealed interface KeyboxImport {
    class Imported(val info: KeyboxInfo) : KeyboxImport

    class Rejected(val problem: KeyboxProblem) : KeyboxImport
}

/**
 * Thin UI repository for the user's keybox.xml.
 *
 * <p>All cryptographic XML/PEM/chain/SPKI/CBOR validation runs in Rust inside
 * `YasvaraAttestationService` (`libyasvara_integrity_jni`), guaranteeing 100% parity with
 * `keystore2`. Settings.Secure stores only the Rust-generated public chain XML for local
 * revocation checks and display.
 */
class KeyboxRepository(private val context: Context, private val store: SecureStore) {
    private val feed = RevocationFeed(context)

    /** True when the private key is held by the service. */
    fun custody(): Custody {
        if (AttestationService.info() != null) return Custody.SERVICE
        val raw = store.getString(IntegrityKeys.TS_KEYBOX)
        if (raw.contains("<PrivateKey", ignoreCase = true)) return Custody.INTERIM
        return Custody.NONE
    }

    enum class Custody { SERVICE, INTERIM, NONE }

    /** The first installed public certificate chain, leaf first, or empty. */
    fun chain(): List<X509Certificate> = chains().firstOrNull().orEmpty()

    /** Every installed public certificate chain stored in Settings.Secure (no private keys). */
    fun chains(): List<List<X509Certificate>> =
        decodePublicChains(store.getString(IntegrityKeys.TS_KEYBOX))

    fun info(): KeyboxInfo? {
        AttestationService.info()?.let { remote ->
            return KeyboxInfo(
                algorithm = remote.algorithm,
                serialHex = remote.serialHex,
                issuer = remote.issuer,
                notAfter = remote.notAfter,
                chainLength = remote.chainLength,
                hasPrivateKey = remote.hasPrivateKey,
                rkpManufacturer = remote.rkpManufacturer,
            )
        }
        val chain = chain()
        val leaf = chain.firstOrNull() ?: return null
        return KeyboxInfo(
            algorithm = if (leaf.publicKey.algorithm.equals("RSA", ignoreCase = true)) "rsa" else "ecdsa",
            serialHex = leaf.serialNumber.toString(16),
            issuer = leaf.issuerX500Principal.name,
            notAfter = leaf.notAfter.time,
            chainLength = chain.size,
            hasPrivateKey = false,
            rkpManufacturer = store.getString(YasvaraIntegrityContract.SECURE_RKP_MANUFACTURER),
        )
    }

    @Synchronized
    fun import(raw: String): KeyboxImport {
        if (raw.length > KeyboxPayload.MAX_BYTES
            || raw.toByteArray(Charsets.UTF_8).size > KeyboxPayload.MAX_BYTES) {
            return KeyboxImport.Rejected(KeyboxProblem.TOO_LARGE)
        }
        if (raw.isBlank()) return KeyboxImport.Rejected(KeyboxProblem.EMPTY)
        val serviceXml = KeyboxPayload.xml(raw) ?: return KeyboxImport.Rejected(KeyboxProblem.NOT_XML)
        val service = AttestationService.get()
        if (service == null || !AttestationService.canWrite(context)) {
            return KeyboxImport.Rejected(KeyboxProblem.SERVICE_UNAVAILABLE)
        }
        val inspected = AttestationService.inspectKeybox(serviceXml, requirePrivateKey = true)
            ?: return KeyboxImport.Rejected(KeyboxProblem.SERVICE_UNAVAILABLE)
        if (!inspected.valid) {
            return KeyboxImport.Rejected(problemOf(inspected.error))
        }
        val remote = inspected.info ?: return KeyboxImport.Rejected(KeyboxProblem.INVALID_KEYBOX)
        try {
            service.setKeybox(serviceXml)
        } catch (e: Exception) {
            Log.w(TAG, "Attestation service refused the keybox")
            return KeyboxImport.Rejected(KeyboxProblem.SERVICE_UNAVAILABLE)
        }
        // Persist only the Rust-generated public chain XML (zero private keys in Settings.Secure).
        store.putString(IntegrityKeys.TS_KEYBOX, inspected.publicChainXml)
        store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
        return KeyboxImport.Imported(
            KeyboxInfo(
                algorithm = remote.algorithm,
                serialHex = remote.serialHex,
                issuer = remote.issuer,
                notAfter = remote.notAfter,
                chainLength = remote.chainLength,
                hasPrivateKey = remote.hasPrivateKey,
                rkpManufacturer = inspected.rkpManufacturer,
            )
        )
    }

    /** Removes the keybox from the service first; the local view only follows a confirmed clear. */
    @Synchronized
    fun delete(): Boolean {
        try {
            if (!AttestationService.canWrite(context)) return false
            AttestationService.get()?.clearKeybox() ?: return false
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the keybox in the attestation service", e)
            return false
        }
        store.putString(IntegrityKeys.TS_KEYBOX, "")
        store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
        return true
    }

    /**
     * Moves a legacy Phase-1 full keybox (private key in Secure) into the Rust-backed service,
     * leaving only the public chain XML behind. Idempotent.
     */
    @Synchronized
    fun migrate(): Boolean {
        val raw = store.getString(IntegrityKeys.TS_KEYBOX)
        if (raw.isEmpty()) return false
        val xml = KeyboxPayload.xml(raw) ?: return true
        val service = AttestationService.get()
        if (service == null || !AttestationService.canWrite(context)) {
            return !raw.contains("<PrivateKey", ignoreCase = true)
        }
        val inspected = AttestationService.inspectKeybox(xml, requirePrivateKey = false)
            ?: return false
        if (!inspected.valid) {
            store.putString(IntegrityKeys.TS_KEYBOX, "")
            store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
            return true
        }
        val hasPriv = inspected.info?.hasPrivateKey == true
        if (hasPriv) {
            try {
                service.setKeybox(xml)
                store.putString(IntegrityKeys.TS_KEYBOX, inspected.publicChainXml)
                return false
            } catch (e: Exception) {
                Log.w(TAG, "Could not move keybox into attestation service")
                return false
            }
        }
        if (AttestationService.info() == null) return true
        if (raw != inspected.publicChainXml) {
            store.putString(IntegrityKeys.TS_KEYBOX, inspected.publicChainXml)
        }
        return false
    }

    /** The last stored revocation result, or [Revocation.Unknown] when never checked. */
    fun lastRevocation(): Revocation {
        val parts = store.getString(IntegrityKeys.TS_LAST_REVOKE_STATUS).split("|")
        val at = parts.getOrNull(1)?.toLongOrNull() ?: return Revocation.Unknown()
        return when (parts[0]) {
            "1" -> Revocation.Valid(at)
            "2" -> Revocation.Revoked(parts.getOrNull(2).orEmpty(), at)
            else -> Revocation.Unknown()
        }
    }

    /** Checks every installed chain (ECDSA + RSA) against Google's revocation list. */
    suspend fun checkRevocation(force: Boolean = false): Revocation {
        val result = feed.check(chains().flatten(), force = force)
        recordRevocation(result)
        return result
    }

    /** Persists a completed revocation result so the Hero, list row, and sheet stay in sync. */
    fun recordRevocation(result: Revocation) {
        when (result) {
            is Revocation.Valid -> store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "1|${result.checkedAt}")
            is Revocation.Revoked -> store.putString(
                IntegrityKeys.TS_LAST_REVOKE_STATUS, "2|${result.checkedAt}|${result.reason}")
            is Revocation.Unknown -> Unit
        }
    }

    private fun problemOf(error: Int) = when (error) {
        YasvaraIntegrityContract.KEYBOX_ERR_EMPTY -> KeyboxProblem.EMPTY
        YasvaraIntegrityContract.KEYBOX_ERR_NOT_XML -> KeyboxProblem.NOT_XML
        YasvaraIntegrityContract.KEYBOX_ERR_BAD_CERTIFICATE -> KeyboxProblem.BAD_CERTIFICATE
        YasvaraIntegrityContract.KEYBOX_ERR_CHAIN_TOO_SHORT -> KeyboxProblem.CHAIN_TOO_SHORT
        YasvaraIntegrityContract.KEYBOX_ERR_INVALID_KEYBOX -> KeyboxProblem.INVALID_KEYBOX
        YasvaraIntegrityContract.KEYBOX_ERR_NO_PRIVATE_KEY -> KeyboxProblem.NO_PRIVATE_KEY
        YasvaraIntegrityContract.KEYBOX_ERR_TOO_LARGE -> KeyboxProblem.TOO_LARGE
        else -> KeyboxProblem.NO_CERTIFICATE
    }

    companion object {
        private val KEY_BLOCK_RE = Regex("(?is)<Key\\b[^>]*>(.*?)</Key>")
        private val PEM_CERT_RE = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")

        /** Lightweight public-only PEM decoder for UI display & revocation checks. */
        private fun decodePublicChains(xml: String): List<List<X509Certificate>> {
            if (xml.isBlank()) return emptyList()
            val cf = CertificateFactory.getInstance("X.509")
            val keyBlocks = KEY_BLOCK_RE.findAll(xml).map { it.groupValues[1] }.toList()
                .ifEmpty { listOf(xml) }
            val result = mutableListOf<List<X509Certificate>>()
            for (block in keyBlocks) {
                val chain = mutableListOf<X509Certificate>()
                for (match in PEM_CERT_RE.findAll(block)) {
                    try {
                        val cert = cf.generateCertificate(
                            ByteArrayInputStream(match.value.toByteArray(Charsets.UTF_8))
                        ) as? X509Certificate
                        if (cert != null) chain += cert
                    } catch (_: Exception) {
                    }
                }
                if (chain.isNotEmpty()) result += chain
            }
            return result
        }
    }
}

private const val TAG = "KeyboxRepository"
