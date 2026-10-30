/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import android.security.yasvara.YasvaraKeyboxProvider
import android.security.yasvara.YasvaraKeyboxProvider.ParseResult
import android.util.Log
import java.security.cert.X509Certificate

enum class KeyboxProblem { EMPTY, NOT_XML, NO_CERTIFICATE, BAD_CERTIFICATE, CHAIN_TOO_SHORT, TOO_LARGE, NO_PRIVATE_KEY }

/** What the user needs to know about the installed keybox. */
class KeyboxInfo(
    val algorithm: String,
    val serialHex: String,
    val issuer: String,
    val notAfter: Long,
    val chainLength: Int,
    val hasPrivateKey: Boolean,
) {
    val expired get() = notAfter < System.currentTimeMillis()
}

sealed interface KeyboxImport {
    class Imported(val info: KeyboxInfo) : KeyboxImport

    class Rejected(val problem: KeyboxProblem) : KeyboxImport
}

/**
 * The user's own keybox.xml.
 *
 * <p>Phase 2 custody: the full file (private key included) is installed into
 * YasvaraAttestationService, which keeps it in {@code /data/system/yasvara/} (0600,
 * system). Settings.Secure holds only the public chain for the info/revocation UI.
 * When the service is unreachable (old builds), the full file stays in Secure as the
 * Phase-1 fallback and the UI flags interim custody.
 */
class KeyboxRepository(private val context: Context, private val store: SecureStore) {
    private val feed = RevocationFeed(context)

    private fun installed(): ParseResult = YasvaraKeyboxProvider.parse(store.getString(IntegrityKeys.TS_KEYBOX))

    /** True when the private key is held by the service (preferred) or in Secure (fallback). */
    fun custody(): Custody {
        val remote = AttestationService.info()
        if (remote != null) return Custody.SERVICE
        val stored = installed().keyboxes.firstOrNull()
        if (stored != null && stored.hasPrivateKey()) return Custody.INTERIM
        return Custody.NONE
    }

    enum class Custody { SERVICE, INTERIM, NONE }

    /** The chain the engine will use, leaf first, or empty when no keybox is installed. */
    fun chain(): List<X509Certificate> = installed().keyboxes.firstOrNull()?.chain.orEmpty()

    /**
     * Every installed keybox chain (one per algorithm: ecdsa, rsa, ...). The engine picks
     * the chain matching the app key's algorithm, so callers that compare chains must
     * consider all of them, not just [chain].
     */
    fun chains(): List<List<X509Certificate>> = installed().keyboxes.map { it.chain }

    fun info(): KeyboxInfo? {
        AttestationService.info()?.let { remote ->
            return KeyboxInfo(
                algorithm = remote.algorithm,
                serialHex = remote.serialHex,
                issuer = remote.issuer,
                notAfter = remote.notAfter,
                chainLength = remote.chainLength,
                hasPrivateKey = remote.hasPrivateKey,
            )
        }
        val keybox = installed().keyboxes.firstOrNull() ?: return null
        val leaf = keybox.leaf()
        return KeyboxInfo(
            algorithm = keybox.algorithm,
            serialHex = leaf.serialNumber.toString(16),
            issuer = leaf.issuerX500Principal.name,
            notAfter = leaf.notAfter.time,
            chainLength = keybox.chain.size,
            hasPrivateKey = keybox.hasPrivateKey(),
        )
    }

    fun import(raw: String): KeyboxImport {
        if (raw.length > MAX_CHARS) return KeyboxImport.Rejected(KeyboxProblem.TOO_LARGE)
        val result = YasvaraKeyboxProvider.parse(raw)
        if (!result.isValid) return KeyboxImport.Rejected(problemOf(result.error))
        if (result.keyboxes.none { it.hasPrivateKey() }) {
            return KeyboxImport.Rejected(KeyboxProblem.NO_PRIVATE_KEY)
        }
        val clean = raw.trim()
        if (AttestationService.canWrite(context)) {
            // Preferred: private key into the service, public chain into Secure.
            try {
                AttestationService.get()?.setKeybox(clean)
                store.putString(IntegrityKeys.TS_KEYBOX, YasvaraKeyboxProvider.stripPrivateKeys(clean))
                store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
                return KeyboxImport.Imported(info()!!)
            } catch (e: Exception) {
                // Fall through to the Secure fallback below.
                Log.w(TAG, "Attestation service refused the keybox; keeping it in Secure", e)
            }
        }
        store.putString(IntegrityKeys.TS_KEYBOX, clean)
        store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
        return KeyboxImport.Imported(info()!!)
    }

    fun delete() {
        try {
            if (AttestationService.canWrite(context)) AttestationService.get()?.clearKeybox()
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the keybox in the attestation service", e)
        }
        store.putString(IntegrityKeys.TS_KEYBOX, "")
        store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
    }

    /**
     * Moves a Phase-1 full keybox (private key in Secure) into the service when it is
     * available, leaving only the public chain behind. Drops values that parse to
     * nothing. Returns true when the user must re-import (a privkey-less value with no
     * service to complete it). Idempotent.
     */
    fun migrate(): Boolean {
        val raw = store.getString(IntegrityKeys.TS_KEYBOX)
        if (raw.isEmpty()) return false
        val xml = if (raw.trimStart().startsWith("<")) raw
        else try {
            String(android.util.Base64.decode(raw.trim(), android.util.Base64.DEFAULT))
        } catch (_: Exception) { return false }
        val result = YasvaraKeyboxProvider.parse(xml)
        if (!result.isValid) {
            store.putString(IntegrityKeys.TS_KEYBOX, "")
            store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
            return true
        }
        val full = result.keyboxes.any { it.hasPrivateKey() }
        if (full && AttestationService.canWrite(context)) {
            try {
                AttestationService.get()?.setKeybox(xml.trim())
                store.putString(
                    IntegrityKeys.TS_KEYBOX, YasvaraKeyboxProvider.stripPrivateKeys(xml.trim()))
                return false
            } catch (e: Exception) {
                Log.w(TAG, "Could not move the keybox into the attestation service", e)
                return false
            }
        }
        if (!full && AttestationService.info() == null) return true
        if (xml.trim() != raw) store.putString(IntegrityKeys.TS_KEYBOX, xml.trim())
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

    /** Checks the whole chain against Google's list and remembers a definitive answer. */
    suspend fun checkRevocation(): Revocation {
        val result = feed.check(chain())
        when (result) {
            is Revocation.Valid -> store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "1|${result.checkedAt}")
            is Revocation.Revoked -> store.putString(
                IntegrityKeys.TS_LAST_REVOKE_STATUS, "2|${result.checkedAt}|${result.reason}")
            is Revocation.Unknown -> Unit
        }
        return result
    }

    private fun problemOf(error: Int) = when (error) {
        YasvaraKeyboxProvider.ERROR_EMPTY -> KeyboxProblem.EMPTY
        YasvaraKeyboxProvider.ERROR_NOT_XML -> KeyboxProblem.NOT_XML
        YasvaraKeyboxProvider.ERROR_BAD_CERTIFICATE -> KeyboxProblem.BAD_CERTIFICATE
        YasvaraKeyboxProvider.ERROR_CHAIN_TOO_SHORT -> KeyboxProblem.CHAIN_TOO_SHORT
        else -> KeyboxProblem.NO_CERTIFICATE
    }

    private companion object {
        const val MAX_CHARS = 200_000
    }
}

private const val TAG = "KeyboxRepository"
