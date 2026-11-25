/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.pasang

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.ServiceManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import yasvara.pasang.IPasang
import java.io.File
import java.util.Locale

data class PasangSlotInfo(
    val version: String = "",
    val currentSlot: Int = 0,
    val currentSuffix: String = "_a",
    val activeBootSlot: Int = 0,
    val slotABootable: Boolean = false,
    val slotASuccessful: Boolean = false,
    val slotBBootable: Boolean = false,
    val slotBSuccessful: Boolean = false,
    val mergeStatus: String = "none",
    val superTotalBytes: Long = 0L,
    val superUsedBytes: Long = 0L,
    val superFreeBytes: Long = 0L,
    val activePartitionCount: Int = 0,
    val overlayActive: Boolean = false,
    val overlayFiles: Long = 0L,
    val overlayBytes: Long = 0L,
) {
    val activeSlotLetter: String get() = if (currentSlot == 0) "A" else "B"
    val targetSlotLetter: String get() = if (currentSlot == 0) "B" else "A"
    val pendingSlotSwitch: Boolean get() = activeBootSlot != currentSlot
}

data class PasangStatus(
    val phase: String = "idle",
    val busy: Boolean = false,
    val activeSlot: Int = 0,
    val targetSlot: Int = 1,
    val currentPartition: String = "",
    val partitionsDone: Int = 0,
    val partitionsTotal: Int = 0,
    val progressPct: Float = 0f,
    val aliasPct: Float = 0f,
    val bytesTotalLogical: Long = 0L,
    val bytesAliased: Long = 0L,
    val bytesZeroed: Long = 0L,
    val bytesSkippedWhole: Long = 0L,
    val bytesToWrite: Long = 0L,
    val bytesWritten: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long = -1L,
    val elapsedMs: Long = 0L,
    val totalExtents: Int = 0,
    val overlayCleanedFiles: Long = 0L,
    val overlayFreedBytes: Long = 0L,
    val rebootRequired: Boolean = false,
    val error: String = "",
)

data class PasangPlan(
    val activeSlot: Int = 0,
    val targetSlot: Int = 1,
    val signatureVerified: Boolean = false,
    val dynamicPartitions: Int = 0,
    val rawPartitions: Int = 0,
    val wholeSkippedPartitions: Int = 0,
    val bytesTotalLogical: Long = 0L,
    val bytesAliased: Long = 0L,
    val bytesZeroed: Long = 0L,
    val bytesSkippedWhole: Long = 0L,
    val bytesToWrite: Long = 0L,
    val savedPct: Float = 0f,
    val totalExtents: Int = 0,
    val freeSuperBefore: Long = 0L,
    val freeSuperAfter: Long = 0L,
    val overlayFilesPresent: Long = 0L,
    val error: String = "",
)

data class PasangOverlayCleanResult(
    val removedEntries: Long = 0L,
    val freedBytes: Long = 0L,
    val cleanedPartitions: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

data class PasangDevOptions(
    val autoCleanOverlay: Boolean = Build.IS_DEBUGGABLE,
    val enableExtentAliasing: Boolean = true,
    val fastVerify: Boolean = false,
    val allowDevKey: Boolean = Build.IS_DEBUGGABLE,
) {
    fun toOptionsJson(isDevBuild: Boolean): String = JSONObject().apply {
        put("clean_overlay", autoCleanOverlay)
        put("enable_extent_aliasing", enableExtentAliasing)
        put("fast_verify", isDevBuild && fastVerify)
        put("allow_dev_key", isDevBuild && allowDevKey)
        put("backup_last_good", true)
    }.toString()
}

class PasangRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDevSectionVisible(): Boolean {
        if (Build.IS_DEBUGGABLE) return true
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0,
        ) == 1
    }

    fun loadDevOptions(): PasangDevOptions = PasangDevOptions(
        autoCleanOverlay = prefs.getBoolean(KEY_AUTO_CLEAN_OVERLAY, Build.IS_DEBUGGABLE),
        enableExtentAliasing = prefs.getBoolean(KEY_EXTENT_ALIASING, true),
        fastVerify = Build.IS_DEBUGGABLE && prefs.getBoolean(KEY_FAST_VERIFY, false),
        allowDevKey = Build.IS_DEBUGGABLE && prefs.getBoolean(KEY_ALLOW_DEV_KEY, Build.IS_DEBUGGABLE),
    )

    fun saveDevOptions(opts: PasangDevOptions) {
        prefs.edit()
            .putBoolean(KEY_AUTO_CLEAN_OVERLAY, opts.autoCleanOverlay)
            .putBoolean(KEY_EXTENT_ALIASING, opts.enableExtentAliasing)
            .putBoolean(KEY_FAST_VERIFY, opts.fastVerify)
            .putBoolean(KEY_ALLOW_DEV_KEY, opts.allowDevKey)
            .apply()
    }

    private fun service(): IPasang? = runCatching {
        val binder = ServiceManager.waitForService(SERVICE_NAME) ?: return null
        IPasang.Stub.asInterface(binder)
    }.onFailure {
        Log.w(TAG, "Failed to connect to $SERVICE_NAME", it)
    }.getOrNull()

    fun fetchSlotInfo(): PasangSlotInfo? {
        val raw = runCatching { service()?.slotInfoJson() }.getOrNull() ?: return null
        return runCatching {
            val j = JSONObject(raw)
            PasangSlotInfo(
                version = j.optString("version"),
                currentSlot = j.optInt("current_slot"),
                currentSuffix = j.optString("current_suffix", "_a"),
                activeBootSlot = j.optInt("active_boot_slot"),
                slotABootable = j.optBoolean("slot_a_bootable"),
                slotASuccessful = j.optBoolean("slot_a_successful"),
                slotBBootable = j.optBoolean("slot_b_bootable"),
                slotBSuccessful = j.optBoolean("slot_b_successful"),
                mergeStatus = j.optString("merge_status", "none"),
                superTotalBytes = j.optLong("super_total_bytes"),
                superUsedBytes = j.optLong("super_used_bytes"),
                superFreeBytes = j.optLong("super_free_bytes"),
                activePartitionCount = j.optInt("active_partition_count"),
                overlayActive = j.optBoolean("overlay_active"),
                overlayFiles = j.optLong("overlay_files"),
                overlayBytes = j.optLong("overlay_bytes"),
            )
        }.getOrNull()
    }

    fun fetchStatus(): PasangStatus? {
        val raw = runCatching { service()?.statusJson() }.getOrNull() ?: return null
        return runCatching {
            val j = JSONObject(raw)
            PasangStatus(
                phase = j.optString("phase", "idle"),
                busy = j.optBoolean("busy"),
                activeSlot = j.optInt("active_slot"),
                targetSlot = j.optInt("target_slot", 1),
                currentPartition = j.optString("current_partition"),
                partitionsDone = j.optInt("partitions_done"),
                partitionsTotal = j.optInt("partitions_total"),
                progressPct = j.optDouble("progress_pct", 0.0).toFloat(),
                aliasPct = j.optDouble("alias_pct", 0.0).toFloat(),
                bytesTotalLogical = j.optLong("bytes_total_logical"),
                bytesAliased = j.optLong("bytes_aliased"),
                bytesZeroed = j.optLong("bytes_zeroed"),
                bytesSkippedWhole = j.optLong("bytes_skipped_whole"),
                bytesToWrite = j.optLong("bytes_to_write"),
                bytesWritten = j.optLong("bytes_written"),
                speedBps = j.optLong("speed_bps"),
                etaSeconds = j.optLong("eta_seconds", -1L),
                elapsedMs = j.optLong("elapsed_ms"),
                totalExtents = j.optInt("total_extents"),
                overlayCleanedFiles = j.optLong("overlay_cleaned_files"),
                overlayFreedBytes = j.optLong("overlay_freed_bytes"),
                rebootRequired = j.optBoolean("reboot_required"),
                error = j.optString("error"),
            )
        }.getOrNull()
    }

    fun planOta(path: String, opts: PasangDevOptions): PasangPlan {
        val raw = runCatching {
            service()?.planOtaJson(path, opts.toOptionsJson(Build.IS_DEBUGGABLE))
        }.getOrElse {
            return PasangPlan(error = it.message ?: "Binder call failed")
        } ?: return PasangPlan(error = "pasangd unavailable")

        return runCatching {
            val j = JSONObject(raw)
            if (j.has("error") && j.optString("error").isNotEmpty()) {
                return PasangPlan(error = j.optString("error"))
            }
            PasangPlan(
                activeSlot = j.optInt("active_slot"),
                targetSlot = j.optInt("target_slot", 1),
                signatureVerified = j.optBoolean("signature_verified"),
                dynamicPartitions = j.optInt("dynamic_partitions"),
                rawPartitions = j.optInt("raw_partitions"),
                wholeSkippedPartitions = j.optInt("whole_skipped_partitions"),
                bytesTotalLogical = j.optLong("bytes_total_logical"),
                bytesAliased = j.optLong("bytes_aliased"),
                bytesZeroed = j.optLong("bytes_zeroed"),
                bytesSkippedWhole = j.optLong("bytes_skipped_whole"),
                bytesToWrite = j.optLong("bytes_to_write"),
                savedPct = j.optDouble("saved_pct", 0.0).toFloat(),
                totalExtents = j.optInt("total_extents"),
                freeSuperBefore = j.optLong("free_super_before"),
                freeSuperAfter = j.optLong("free_super_after"),
                overlayFilesPresent = j.optLong("overlay_files_present"),
            )
        }.getOrElse {
            PasangPlan(error = it.message ?: "Malformed plan response")
        }
    }

    fun startOta(path: String, opts: PasangDevOptions): Int = runCatching {
        service()?.startOta(path, opts.toOptionsJson(Build.IS_DEBUGGABLE)) ?: -19
    }.getOrElse { -5 }

    fun cancelOta() {
        runCatching { service()?.cancelOta() }
    }

    fun cleanOverlaysNow(): PasangOverlayCleanResult? {
        val raw = runCatching { service()?.cleanOverlaysNowJson() }.getOrNull() ?: return null
        return runCatching {
            val j = JSONObject(raw)
            val parts = mutableListOf<String>()
            j.optJSONArray("cleaned_partitions")?.let { arr ->
                for (i in 0 until arr.length()) parts.add(arr.optString(i))
            }
            val errs = mutableListOf<String>()
            j.optJSONArray("errors")?.let { arr ->
                for (i in 0 until arr.length()) errs.add(arr.optString(i))
            }
            PasangOverlayCleanResult(
                removedEntries = j.optLong("removed_entries"),
                freedBytes = j.optLong("freed_bytes"),
                cleanedPartitions = parts,
                errors = errs,
            )
        }.getOrNull()
    }

    fun rebootToNewSlot(): Boolean = runCatching {
        (service()?.rebootToNewSlot() ?: -1) == 0
    }.getOrDefault(false)

    /**
     * Resolves a SAF document URI picked from anywhere in Internal Storage, Downloads,
     * Recents, or USB OTG directly to the underlying path readable by `pasangd`
     * (`/data/media/0/...` for primary internal storage — zero 4+ GB temp copy!).
     */
    fun resolveOtaUri(uri: Uri): Pair<String, String>? {
        val displayName = queryDisplayName(uri)
        if (uri.scheme == "file") {
            val p = uri.path ?: return null
            val normalized = toDaemonPath(p)
            return normalized to (displayName ?: File(p).name)
        }
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val split = docId.split(":", limit = 2)
                    if (split.size == 2) {
                        val rel = split[1].trimStart('/')
                        if (split[0].equals("primary", ignoreCase = true)) {
                            val daemonPath = "/data/media/0/$rel"
                            return daemonPath to (displayName ?: File(rel).name)
                        } else {
                            // USB OTG / external volume UUID
                            val daemonPath = "/mnt/media_rw/${split[0]}/$rel"
                            return daemonPath to (displayName ?: File(rel).name)
                        }
                    }
                }
                "com.android.providers.downloads.documents" -> {
                    if (docId.startsWith("raw:")) {
                        val raw = docId.removePrefix("raw:")
                        return toDaemonPath(raw) to (displayName ?: File(raw).name)
                    }
                    // Handle `msf:<id>` or numeric Download IDs via MediaStore DATA column
                    val numId = docId.substringAfter("msf:", docId).toLongOrNull()
                    if (numId != null) {
                        val msPath = queryMediaStorePath(numId)
                        if (msPath != null) {
                            return toDaemonPath(msPath) to (displayName ?: File(msPath).name)
                        }
                    }
                    // Fallback: if displayName is known, Downloads lives at /data/media/0/Download/<name>
                    if (!displayName.isNullOrBlank()) {
                        return "/data/media/0/Download/$displayName" to displayName
                    }
                }
                "com.android.providers.media.documents" -> {
                    val split = docId.split(":", limit = 2)
                    val numId = split.getOrNull(1)?.toLongOrNull()
                    if (numId != null) {
                        val msPath = queryMediaStorePath(numId)
                        if (msPath != null) {
                            return toDaemonPath(msPath) to (displayName ?: File(msPath).name)
                        }
                    }
                }
            }
        }
        // Generic MediaStore DATA column query fallback
        queryUriDataColumn(uri)?.let { raw ->
            return toDaemonPath(raw) to (displayName ?: File(raw).name)
        }
        return null
    }

    private fun toDaemonPath(path: String): String = when {
        path.startsWith("/storage/emulated/0/") ->
            "/data/media/0/" + path.removePrefix("/storage/emulated/0/")
        path.startsWith("/sdcard/") ->
            "/data/media/0/" + path.removePrefix("/sdcard/")
        else -> path
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun queryMediaStorePath(id: Long): String? = runCatching {
        val contentUri = MediaStore.Files.getContentUri("external")
        context.contentResolver.query(
            contentUri,
            arrayOf(MediaStore.MediaColumns.DATA),
            "${MediaStore.MediaColumns._ID}=?",
            arrayOf(id.toString()),
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun queryUriDataColumn(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    companion object {
        private const val TAG = "LarasPasang"
        private const val SERVICE_NAME = "yasvara.pasang"
        private const val PREFS_NAME = "pasang_dev_prefs"
        private const val KEY_AUTO_CLEAN_OVERLAY = "auto_clean_overlay"
        private const val KEY_EXTENT_ALIASING = "extent_aliasing"
        private const val KEY_FAST_VERIFY = "fast_verify"
        private const val KEY_ALLOW_DEV_KEY = "allow_dev_key"

        /**
         * Best-effort fire-and-forget bridge writing the 32-byte Material You seed POD to
         * `/metadata/yasvara/recovery_theme.bin` so Pulih Recovery inherits the exact OS palette.
         */
        fun syncRecoveryTheme(seedArgb: Int, oledBlack: Boolean) {
            runCatching {
                val binder = ServiceManager.waitForService(SERVICE_NAME) ?: return
                val svc = IPasang.Stub.asInterface(binder) ?: return
                val loc = Locale.getDefault()
                val tag = "${loc.language}_${loc.country}".take(7)
                svc.writeRecoveryTheme(seedArgb, oledBlack, tag)
            }.onFailure {
                Log.w(TAG, "syncRecoveryTheme skipped: ${it.message}")
            }
        }
    }
}
