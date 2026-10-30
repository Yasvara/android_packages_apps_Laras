/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import org.json.JSONObject
import java.math.BigInteger
import java.security.cert.X509Certificate

sealed interface Revocation {
    /** Never checked, or the check could not complete ([failure] says why, when known). */
    class Unknown(val failure: FetchFailure? = null) : Revocation

    class Valid(val checkedAt: Long) : Revocation

    class Revoked(val reason: String, val checkedAt: Long) : Revocation
}

/** Google's attestation status list: which certificate serials have been revoked. */
class RevocationFeed(private val context: Context) {
    @Volatile private var cached: Pair<Long, JSONObject>? = null

    /** Checks every certificate of [chain]; one revoked certificate revokes the chain. */
    suspend fun check(chain: List<X509Certificate>): Revocation {
        if (chain.isEmpty()) return Revocation.Unknown()
        val entries = try {
            entries()
        } catch (e: FetchException) {
            return Revocation.Unknown(e.failure)
        }
        val now = System.currentTimeMillis()
        for (cert in chain) {
            val hit = lookup(entries, cert.serialNumber)
            if (hit != null) return Revocation.Revoked(hit, now)
        }
        return Revocation.Valid(now)
    }

    private suspend fun entries(): JSONObject {
        cached?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        val body = Http.get(context, STATUS_URL, MAX_BYTES)
        val entries = try {
            JSONObject(body).getJSONObject("entries")
        } catch (_: Exception) {
            throw FetchException(FetchFailure(FailureKind.OTHER, "unexpected response"))
        }
        cached = System.currentTimeMillis() to entries
        return entries
    }

    /** Google keys serials as lowercase hex without leading zeros, older ones as decimal. */
    private fun lookup(entries: JSONObject, serial: BigInteger): String? {
        val entry = entries.optJSONObject(serial.toString(16)) ?: entries.optJSONObject(serial.toString())
        return entry?.optString("reason")?.ifEmpty { entry.optString("status") }
            ?.ifEmpty { "REVOKED" }
    }

    companion object {
        const val STATUS_URL = "https://android.googleapis.com/attestation/status"
        private const val MAX_BYTES = 1 shl 20
        private const val CACHE_MS = 10 * 60 * 1000L
    }
}
