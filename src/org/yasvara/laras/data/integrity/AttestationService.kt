/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.os.IBinder
import android.os.ServiceManager
import android.security.yasvara.IYasvaraAttestation
import org.json.JSONObject

/**
 * Binder handle to YasvaraAttestationService in system_server. Null when the service
 * is not up (old builds, early boot) — callers fall back to the Secure keybox.
 */
object AttestationService {
    private const val NAME = "yasvara_attestation"

    fun get(): IYasvaraAttestation? = try {
        val binder: IBinder = ServiceManager.getService(NAME) ?: return null
        IYasvaraAttestation.Stub.asInterface(binder)
    } catch (_: Exception) { null }

    data class RemoteInfo(
        val algorithm: String,
        val serialHex: String,
        val issuer: String,
        val notAfter: Long,
        val chainLength: Int,
        val hasPrivateKey: Boolean,
    )

    fun info(): RemoteInfo? {
        val raw = try {
            get()?.keyboxInfo.orEmpty()
        } catch (_: Exception) { return null }
        if (raw.isEmpty()) return null
        return try {
            val o = JSONObject(raw)
            RemoteInfo(
                algorithm = o.optString("algorithm"),
                serialHex = o.optString("serialHex"),
                issuer = o.optString("issuer"),
                notAfter = o.optLong("notAfter"),
                chainLength = o.optInt("chainLength"),
                hasPrivateKey = o.optBoolean("hasPrivateKey"),
            )
        } catch (_: Exception) { null }
    }

    /** Last rung report for pkg as (rung, atMs, reason), or null. */
    fun rung(pkg: String): Triple<String, Long, String>? {
        val raw = try {
            get()?.getRung(pkg).orEmpty()
        } catch (_: Exception) { return null }
        if (raw.isEmpty()) return null
        return try {
            val o = JSONObject(raw)
            Triple(o.optString("rung"), o.optLong("at"), o.optString("reason"))
        } catch (_: Exception) { null }
    }

    /** True when the write path (install/remove) is available to us. */
    fun canWrite(context: android.content.Context): Boolean = try {
        get() != null && context.checkSelfPermission(
            "yasvara.permission.WRITE_ATTESTATION") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }
}
