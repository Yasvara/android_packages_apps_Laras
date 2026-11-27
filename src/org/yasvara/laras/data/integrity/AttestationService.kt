/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.os.IBinder
import android.os.ServiceManager
import android.security.yasvara.IYasvaraAttestation
import android.security.yasvara.YasvaraIntegrityContract
import org.json.JSONObject

/**
 * Binder handle to YasvaraAttestationService in system_server (backed by Rust).
 */
object AttestationService {
    private const val NAME = YasvaraIntegrityContract.SERVICE_NAME

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
        val rkpManufacturer: String = "",
    )

    data class InspectResult(
        val valid: Boolean,
        val error: Int,
        val publicChainXml: String,
        val rkpManufacturer: String,
        val info: RemoteInfo?,
    )

    fun parseRemoteInfo(raw: String): RemoteInfo? {
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
                rkpManufacturer = o.optString("rkpManufacturer"),
            )
        } catch (_: Exception) { null }
    }

    fun info(): RemoteInfo? {
        val raw = try {
            get()?.keyboxInfo.orEmpty()
        } catch (_: Exception) { return null }
        return parseRemoteInfo(raw)
    }

    /** Validates a keybox via Rust (`libyasvara_integrity_jni`) in system_server. */
    fun inspectKeybox(rawXml: String, requirePrivateKey: Boolean): InspectResult? {
        val raw = try {
            get()?.inspectKeybox(rawXml, requirePrivateKey).orEmpty()
        } catch (_: Exception) { return null }
        if (raw.isEmpty()) return null
        return try {
            val o = JSONObject(raw)
            InspectResult(
                valid = o.optBoolean("valid", false),
                error = o.optInt("error", YasvaraIntegrityContract.KEYBOX_ERR_INVALID_KEYBOX),
                publicChainXml = o.optString("publicChainXml"),
                rkpManufacturer = o.optString("rkpManufacturer"),
                info = parseRemoteInfo(o.optString("infoJson")),
            )
        } catch (_: Exception) { null }
    }

    data class RuntimeStatus(
        val bootDigestReady: Boolean,
        val bootSource: String,
        val keyboxLoaded: Boolean,
        val rkpManufacturer: String,
        val pifEnabled: Boolean,
        val trickystoreEnabled: Boolean,
        val purgeGen: Int,
    )

    /** Live runtime status of the Rust-backed integrity engine (zero disk I/O). */
    fun status(): RuntimeStatus? {
        val raw = try {
            get()?.status.orEmpty()
        } catch (_: Exception) { return null }
        if (raw.isEmpty()) return null
        return try {
            val o = JSONObject(raw)
            RuntimeStatus(
                bootDigestReady = o.optBoolean("bootDigestReady", false),
                bootSource = o.optString("bootSource"),
                keyboxLoaded = o.optBoolean("keyboxLoaded", false),
                rkpManufacturer = o.optString("rkpManufacturer"),
                pifEnabled = o.optBoolean("pifEnabled", false),
                trickystoreEnabled = o.optBoolean("trickystoreEnabled", false),
                purgeGen = o.optInt("purgeGen", 0),
            )
        } catch (_: Exception) { null }
    }

    /** Bumps purge generation and restarts GMS / Play Store / GSF to clear stale integrity caches. */
    fun purgeIntegrityCache(): Int = try {
        get()?.purgeIntegrityCache() ?: -1
    } catch (_: Exception) { -1 }

    /** True when the write path (install/remove) is available to us. */
    fun canWrite(context: android.content.Context): Boolean = try {
        get() != null && context.checkSelfPermission(
            YasvaraIntegrityContract.PERMISSION_WRITE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }
}
