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
import java.util.Base64

enum class KeyboxProblem { EMPTY, NOT_XML, NO_CERTIFICATE, BAD_CERTIFICATE, CHAIN_TOO_SHORT, INVALID_KEYBOX, TOO_LARGE, NO_PRIVATE_KEY, SERVICE_UNAVAILABLE }

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
 * <p>The full file (private key included) is installed into YasvaraAttestationService, the
 * single owner of the credential. Settings.Secure holds only the public chain for the
 * info/revocation UI; import is refused when the service cannot take custody. A value left
 * in Secure by older builds is moved into the service by [migrate].
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

    @Synchronized
    fun import(raw: String): KeyboxImport {
        if (raw.length > KeyboxPayload.MAX_BYTES || raw.toByteArray(Charsets.UTF_8).size > KeyboxPayload.MAX_BYTES) {
            return KeyboxImport.Rejected(KeyboxProblem.TOO_LARGE)
        }
        if (raw.isBlank()) return KeyboxImport.Rejected(KeyboxProblem.EMPTY)
        val serviceXml = KeyboxPayload.xml(raw) ?: return KeyboxImport.Rejected(KeyboxProblem.NOT_XML)
        val result = YasvaraKeyboxProvider.parse(serviceXml)
        if (!result.isValid) return KeyboxImport.Rejected(problemOf(result.error))
        if (result.keyboxes.none { it.hasPrivateKey() }) {
            return KeyboxImport.Rejected(KeyboxProblem.NO_PRIVATE_KEY)
        }
        // Construct the public view from certificates only: arbitrary XML comments,
        // attributes or extra elements from a remote source must never reach Settings.
        val xml = publicChainXml(result)
        val first = result.keyboxes.first()
        val leaf = first.leaf()
        val info = KeyboxInfo(first.algorithm, leaf.serialNumber.toString(16),
            leaf.issuerX500Principal.name, leaf.notAfter.time, first.chain.size, first.hasPrivateKey())
        val service = AttestationService.get()
        if (service == null || !AttestationService.canWrite(context)) {
            return KeyboxImport.Rejected(KeyboxProblem.SERVICE_UNAVAILABLE)
        }
        try {
            service.setKeybox(serviceXml)
        } catch (e: Exception) {
            Log.w(TAG, "Attestation service refused the keybox")
            return KeyboxImport.Rejected(KeyboxProblem.SERVICE_UNAVAILABLE)
        }
        store.putString(IntegrityKeys.TS_KEYBOX, xml)
        store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
        return KeyboxImport.Imported(info)
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
     * Moves a Phase-1 full keybox (private key in Secure) into the service when it is
     * available, leaving only the public chain behind. Drops values that parse to
     * nothing. Returns true when the user must re-import (a privkey-less value with no
     * service to complete it). Idempotent.
     */
    @Synchronized
    fun migrate(): Boolean {
        val raw = store.getString(IntegrityKeys.TS_KEYBOX)
        if (raw.isEmpty()) return false
        val xml = KeyboxPayload.xml(raw) ?: return true
        val result = YasvaraKeyboxProvider.parse(xml)
        if (!result.isValid) {
            store.putString(IntegrityKeys.TS_KEYBOX, "")
            store.putString(IntegrityKeys.TS_LAST_REVOKE_STATUS, "")
            return true
        }
        val full = result.keyboxes.any { it.hasPrivateKey() }
        if (full && AttestationService.canWrite(context)) {
            try {
                val service = AttestationService.get() ?: return false
                val publicXml = publicChainXml(result)
                service.setKeybox(xml)
                store.putString(IntegrityKeys.TS_KEYBOX, publicXml)
                return false
            } catch (e: Exception) {
                Log.w(TAG, "Could not move the keybox into the attestation service")
                return false
            }
        }
        if (!full && AttestationService.info() == null) return true
        if (!full) {
            val publicXml = publicChainXml(result)
            if (raw != publicXml) store.putString(IntegrityKeys.TS_KEYBOX, publicXml)
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

    private fun publicChainXml(result: ParseResult): String = buildString {
        append("<AndroidAttestation>")
        for (key in result.keyboxes) {
            append("<Key algorithm=\"").append(key.algorithm).append("\"><CertificateChain>")
            for (cert in key.chain) {
                append("<Certificate format=\"pem\">-----BEGIN CERTIFICATE-----\n")
                append(Base64.getEncoder().encodeToString(cert.encoded))
                append("\n-----END CERTIFICATE-----</Certificate>")
            }
            append("</CertificateChain></Key>")
        }
        append("</AndroidAttestation>")
    }

    private fun problemOf(error: Int) = when (error) {
        YasvaraKeyboxProvider.ERROR_EMPTY -> KeyboxProblem.EMPTY
        YasvaraKeyboxProvider.ERROR_NOT_XML -> KeyboxProblem.NOT_XML
        YasvaraKeyboxProvider.ERROR_BAD_CERTIFICATE -> KeyboxProblem.BAD_CERTIFICATE
        YasvaraKeyboxProvider.ERROR_CHAIN_TOO_SHORT -> KeyboxProblem.CHAIN_TOO_SHORT
        YasvaraKeyboxProvider.ERROR_INVALID_KEYBOX -> KeyboxProblem.INVALID_KEYBOX
        else -> KeyboxProblem.NO_CERTIFICATE
    }

}

private const val TAG = "KeyboxRepository"
