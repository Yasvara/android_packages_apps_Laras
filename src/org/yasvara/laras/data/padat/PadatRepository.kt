/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.padat

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.ServiceManager
import com.android.server.yasvara.padat.IYasvaraPadat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class PadatPreset(val mode: String, val algo: Int, val level: Int, val clusterKb: Int) {
    OFF("off", 0, 0, 16),
    FAST("fast", 0, 0, 16),
    BALANCED("balanced", 2, 1, 32),
    MAX("max", 2, 3, 64),
    CUSTOM("custom", 2, 3, 64);

    companion object {
        fun fromMode(mode: String?): PadatPreset =
            entries.firstOrNull { it.mode.equals(mode, ignoreCase = true) } ?: OFF
    }
}

enum class PadatAlgo(
    val id: Int,
    val wire: String,
    val label: String,
    val hasLevel: Boolean,
    val minLevel: Int,
    val maxLevel: Int,
    val defaultLevel: Int,
) {
    LZ4(0, "lz4", "LZ4", false, 0, 0, 0),
    LZ4HC(1, "lz4hc", "LZ4HC", true, 3, 12, 6),
    ZSTD(2, "zstd", "ZSTD", true, 1, 9, 3);

    fun clampLevel(level: Int): Int = if (!hasLevel) 0 else level.coerceIn(minLevel, maxLevel)

    companion object {
        fun fromId(id: Int): PadatAlgo = entries.firstOrNull { it.id == id } ?: LZ4
        fun fromWire(name: String?): PadatAlgo =
            entries.firstOrNull { it.wire.equals(name, ignoreCase = true) } ?: LZ4
    }
}

data class DetectedProfile(
    val algo: PadatAlgo,
    val level: Int,
    val clusterKb: Int,
) {
    fun toMatchingPreset(): PadatPreset = when {
        algo == PadatAlgo.LZ4 && clusterKb == 16 -> PadatPreset.FAST
        algo == PadatAlgo.ZSTD && level == 1 && clusterKb == 32 -> PadatPreset.BALANCED
        algo == PadatAlgo.ZSTD && level == 3 && clusterKb == 64 -> PadatPreset.MAX
        else -> PadatPreset.CUSTOM
    }

    fun shortLabel(): String =
        if (algo.hasLevel) "${algo.label} L$level · ${clusterKb}K"
        else "${algo.label} · ${clusterKb}K"
}

data class PadatScopes(
    val apkLib: Boolean = true,
    val artOat: Boolean = true,
    val externalData: Boolean = false,
    val obb: Boolean = false,
    val internalFiles: Boolean = false,
) {
    fun toMask(): Int {
        var m = 0
        if (apkLib) m = m or 0x01
        if (artOat) m = m or 0x02
        if (externalData) m = m or 0x04
        if (obb) m = m or 0x08
        if (internalFiles) m = m or 0x10
        return m
    }

    companion object {
        val TIER1 = PadatScopes(apkLib = true, artOat = true)
        val FULL = PadatScopes(apkLib = true, artOat = true, externalData = true, obb = true)
    }
}

data class ScopeBucket(
    val logicalBytes: Long = 0L,
    val physicalBytes: Long = 0L,
    val savedBytes: Long = 0L,
    val compressedFiles: Long = 0L,
    val totalFiles: Long = 0L,
)

data class PackageBreakdown(
    val apkLib: ScopeBucket = ScopeBucket(),
    val artOat: ScopeBucket = ScopeBucket(),
    val externalData: ScopeBucket = ScopeBucket(),
    val obb: ScopeBucket = ScopeBucket(),
    val internalFiles: ScopeBucket = ScopeBucket(),
    val cacheOther: ScopeBucket = ScopeBucket(),
)

data class AppPolicyEntry(
    val preset: PadatPreset = PadatPreset.OFF,
    val algo: PadatAlgo = PadatAlgo.LZ4,
    val level: Int = 0,
    val clusterKb: Int = 16,
    val scopes: PadatScopes = PadatScopes.TIER1,
) {
    fun effectiveAlgo(): PadatAlgo = when (preset) {
        PadatPreset.FAST -> PadatAlgo.LZ4
        PadatPreset.BALANCED, PadatPreset.MAX -> PadatAlgo.ZSTD
        PadatPreset.CUSTOM, PadatPreset.OFF -> algo
    }

    fun effectiveLevel(): Int = when (preset) {
        PadatPreset.FAST -> 0
        PadatPreset.BALANCED -> 1
        PadatPreset.MAX -> 3
        PadatPreset.CUSTOM -> algo.clampLevel(level)
        PadatPreset.OFF -> 0
    }

    fun effectiveClusterKb(): Int = when (preset) {
        PadatPreset.FAST -> 16
        PadatPreset.BALANCED -> 32
        PadatPreset.MAX -> 64
        PadatPreset.CUSTOM -> clusterKb
        PadatPreset.OFF -> 16
    }

    fun normalized(): AppPolicyEntry = copy(
        algo = effectiveAlgo(),
        level = effectiveLevel(),
        clusterKb = effectiveClusterKb(),
    )

    fun profileLabel(): String {
        val a = effectiveAlgo()
        val l = effectiveLevel()
        val c = effectiveClusterKb()
        return if (a.hasLevel) "${a.label} L$l · ${c}K" else "${a.label} · ${c}K"
    }
}

data class PadatPolicy(
    val autoTier1: Boolean = false,
    val minFileBytes: Long = 16384L,
    val minSavingPct: Int = 5,
    val thermalPauseMc: Int = 42000,
    val pauseOnGame: Boolean = true,
    val apps: Map<String, AppPolicyEntry> = emptyMap(),
) {
    /** Emits canonical Rust `policy.rs` JSON schema (`packages` + `preset`). */
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("auto_tier1", autoTier1)
        val def = JSONObject()
        def.put("algo", "lz4")
        def.put("level", 0)
        def.put("cluster_kb", 16)
        root.put("default_tier1", def)
        root.put("min_file_bytes", minFileBytes)
        root.put("min_saving_pct", minSavingPct)
        root.put("thermal_pause_mc", thermalPauseMc)
        root.put("pause_on_game", pauseOnGame)
        val pkgsObj = JSONObject()
        for ((pkg, e) in apps) {
            val o = JSONObject()
            o.put("preset", e.preset.mode)
            val norm = e.normalized()
            o.put("algo", norm.algo.wire)
            o.put("level", norm.algo.clampLevel(norm.level))
            o.put("cluster_kb", norm.clusterKb)
            o.put("apk_lib", e.scopes.apkLib)
            o.put("art_oat", e.scopes.artOat)
            o.put("external_data", e.scopes.externalData)
            o.put("obb", e.scopes.obb)
            o.put("internal_files", e.scopes.internalFiles)
            pkgsObj.put(pkg, o)
        }
        root.put("packages", pkgsObj)
        return root.toString()
    }
}

data class PadatStatus(
    val kernelCompression: Boolean,
    val userdataCompression: Boolean,
    val compressAutoReserve: Boolean,
    val comprWrittenBlocks: Long,
    val comprSavedBlocks: Long,
    val comprAutoReserved: Long,
    val comprNewInodes: Long,
    val compressPercent: Int,
    val batteryTempMc: Int,
    val busy: Boolean,
    val paused: Boolean,
    val pauseReason: String,
    val activePackage: String,
    val activeAction: String,
    val totalFiles: Long,
    val doneFiles: Long,
    val compressedFiles: Long,
    val skippedFiles: Long,
    val errorFiles: Long,
    val totalBytes: Long,
    val doneBytes: Long,
    val savedBytes: Long,
    val elapsedMs: Long,
    val speedBps: Long,
    val currentFile: String,
) {
    val progressFraction: Float
        get() = when {
            totalBytes > 0L -> (doneBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
            totalFiles > 0L -> (doneFiles.toDouble() / totalFiles.toDouble()).toFloat().coerceIn(0f, 1f)
            else -> 0f
        }

    val etaSeconds: Long
        get() = if (speedBps > 0L && totalBytes > doneBytes) {
            (totalBytes - doneBytes) / speedBps
        } else {
            -1L
        }
}

data class PadatAppItem(
    val packageName: String,
    val label: String,
    val isRunning: Boolean,
    val logicalBytes: Long,
    val physicalBytes: Long,
    val savedBytes: Long,
    val compressedFiles: Long,
    val totalFiles: Long,
    val detectedProfile: DetectedProfile?,
    val breakdown: PackageBreakdown,
    val policy: AppPolicyEntry?,
) {
    /**
     * Resolves the last-used compression config for this app:
     * prefers verified on-disk F2FS inode metadata (matching saved policy when identical,
     * and reflecting the true on-disk algorithm even after CLI runs), falling back to saved policy.
     */
    fun resolvedAppliedEntry(): AppPolicyEntry? {
        val pol = policy?.takeIf { it.preset != PadatPreset.OFF }?.normalized()
        val det = detectedProfile
        if (det != null) {
            if (pol != null &&
                pol.algo == det.algo &&
                pol.level == det.level &&
                pol.clusterKb == det.clusterKb
            ) {
                return pol
            }
            val inferredScopes = pol?.scopes ?: PadatScopes(
                apkLib = breakdown.apkLib.compressedFiles > 0L ||
                    (breakdown.externalData.compressedFiles == 0L &&
                        breakdown.obb.compressedFiles == 0L &&
                        breakdown.internalFiles.compressedFiles == 0L),
                artOat = breakdown.artOat.compressedFiles > 0L || breakdown.apkLib.compressedFiles > 0L,
                externalData = breakdown.externalData.compressedFiles > 0L,
                obb = breakdown.obb.compressedFiles > 0L,
                internalFiles = breakdown.internalFiles.compressedFiles > 0L,
            )
            return AppPolicyEntry(
                preset = det.toMatchingPreset(),
                algo = det.algo,
                level = det.level,
                clusterKb = det.clusterKb,
                scopes = inferredScopes,
            )
        }
        return pol
    }

    /** Human-readable active algorithm string verified on disk (or saved policy). */
    fun activeProfileLabel(): String? =
        detectedProfile?.shortLabel() ?: policy?.takeIf { it.preset != PadatPreset.OFF }?.profileLabel()
}

data class PadatScanResult(
    val packageName: String,
    val isRunning: Boolean,
    val logicalBytes: Long,
    val physicalBytes: Long,
    val savedBytes: Long,
    val compressedFiles: Long,
    val totalFiles: Long,
    val candidateFiles: Long,
    val estExtraSavingsBytes: Long,
    val skippedIncompressible: Long,
    val skippedVerity: Long,
    val skippedExcluded: Long,
    val detectedProfile: DetectedProfile?,
    val breakdown: PackageBreakdown,
)

data class PadatRunReport(
    val packageName: String,
    val action: String,
    val processedFiles: Long,
    val compressedFiles: Long,
    val decompressedFiles: Long,
    val releasedFiles: Long,
    val skippedProbe: Long,
    val skippedPolicy: Long,
    val skippedVerity: Long,
    val errors: Long,
    val bytesBefore: Long,
    val bytesAfter: Long,
    val savedDeltaBytes: Long,
    val cancelled: Boolean,
    val refusedRunning: Boolean,
    val error: String? = null,
)

data class PadatCachedSnapshot(
    val status: PadatStatus?,
    val policy: PadatPolicy,
    val apps: List<PadatAppItem>,
)

/** Talks to "yasvara_padat" in system_server. Keep calls off the main thread. */
class PadatRepository(private val context: Context) {
    private val cacheFile: File get() = File(context.cacheDir, CACHE_FILE_NAME)

    /** Instant 0ms process-memory snapshot for returning to Padat without re-scanning. */
    fun peekMemorySnapshot(): PadatCachedSnapshot? = memSnapshot

    fun saveMemorySnapshot(status: PadatStatus?, policy: PadatPolicy, apps: List<PadatAppItem>) {
        if (apps.isNotEmpty()) {
            memSnapshot = PadatCachedSnapshot(status, policy, apps)
        }
    }

    /** Loads last saved stats JSON from disk cache in ~10ms for zero-wait cold start. */
    fun loadDiskCachedApps(policy: PadatPolicy): List<PadatAppItem> {
        val raw = try {
            val f = cacheFile
            if (f.isFile && f.length() > 2L) f.readText() else null
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return parseAllAppsJson(raw, policy)
    }

    /**
     * Ultra-fast (~20ms) fallback when no disk cache exists yet (brand-new install):
     * lists installed user/updated apps with real labels & policy badges immediately.
     */
    fun loadFastInstalledShell(policy: PadatPolicy): List<PadatAppItem> {
        val pm = context.packageManager
        val labels = ensureLabelCache(pm)
        return try {
            pm.getInstalledApplications(0)
                .filter { ai ->
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                }
                .map { ai ->
                    val pkg = ai.packageName
                    PadatAppItem(
                        packageName = pkg,
                        label = labels[pkg] ?: pkg,
                        isRunning = false,
                        logicalBytes = 0L,
                        physicalBytes = 0L,
                        savedBytes = 0L,
                        compressedFiles = 0L,
                        totalFiles = 0L,
                        detectedProfile = null,
                        breakdown = PackageBreakdown(),
                        policy = policy.apps[pkg],
                    )
                }
                .sortedBy { it.label.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveDiskCache(rawJson: String) {
        try {
            val tmp = File(context.cacheDir, "$CACHE_FILE_NAME.tmp")
            tmp.writeText(rawJson)
            tmp.renameTo(cacheFile)
        } catch (_: Exception) {
        }
    }

    private fun ensureLabelCache(pm: PackageManager): Map<String, String> {
        if (labelCache.isEmpty()) {
            try {
                for (ai in pm.getInstalledApplications(0)) {
                    val lbl = pm.getApplicationLabel(ai)?.toString()?.takeIf { it.isNotBlank() }
                        ?: ai.packageName
                    labelCache[ai.packageName] = lbl
                }
            } catch (_: Exception) {
            }
        }
        return labelCache
    }

    private fun parseAllAppsJson(raw: String, policy: PadatPolicy): List<PadatAppItem> {
        val pm = context.packageManager
        val labels = ensureLabelCache(pm)
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<PadatAppItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pkg = o.optString("package", "")
                if (pkg.isEmpty()) continue
                val label = labels[pkg] ?: resolveLabel(pm, pkg).also { labelCache[pkg] = it }
                out.add(
                    PadatAppItem(
                        packageName = pkg,
                        label = label,
                        isRunning = o.optBoolean("is_running", false),
                        logicalBytes = o.optLong("logical_bytes", 0L),
                        physicalBytes = o.optLong("physical_bytes", 0L),
                        savedBytes = o.optLong("saved_bytes", 0L),
                        compressedFiles = o.optLong("compressed_files", 0L),
                        totalFiles = o.optLong("total_files", 0L),
                        detectedProfile = parseDetectedProfile(o.optJSONObject("detected_profile")),
                        breakdown = parseBreakdown(o.optJSONObject("buckets")),
                        policy = policy.apps[pkg],
                    ),
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }
    private fun service(): IYasvaraPadat? = try {
        ServiceManager.getService(SERVICE_NAME)?.let(IYasvaraPadat.Stub::asInterface)
    } catch (_: RuntimeException) {
        null
    }

    val available: Boolean get() = service() != null

    fun loadStatus(): PadatStatus? {
        val raw = try {
            service()?.getStatusJson()
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            val o = JSONObject(raw)
            if (o.has("error")) return null
            PadatStatus(
                kernelCompression = o.optBoolean("kernel_compression", false),
                userdataCompression = o.optBoolean("userdata_compression", false),
                compressAutoReserve = o.optBoolean("compress_auto_reserve", false),
                comprWrittenBlocks = o.optLong("compr_written_blocks", 0L),
                comprSavedBlocks = o.optLong("compr_saved_blocks", 0L),
                comprAutoReserved = o.optLong("compr_auto_reserved", 0L),
                comprNewInodes = o.optLong("compr_new_inodes", 0L),
                compressPercent = o.optInt("compress_percent", 0),
                batteryTempMc = o.optInt("battery_temp_mc", 0),
                busy = o.optBoolean("busy", false),
                paused = o.optBoolean("paused", false),
                pauseReason = o.optString("pause_reason", ""),
                activePackage = o.optString("active_package", ""),
                activeAction = o.optString("active_action", ""),
                totalFiles = o.optLong("total_files", 0L),
                doneFiles = o.optLong("done_files", 0L),
                compressedFiles = o.optLong("compressed_files", 0L),
                skippedFiles = o.optLong("skipped_files", 0L),
                errorFiles = o.optLong("error_files", 0L),
                totalBytes = o.optLong("total_bytes", 0L),
                doneBytes = o.optLong("done_bytes", 0L),
                savedBytes = o.optLong("saved_bytes", 0L),
                elapsedMs = o.optLong("elapsed_ms", 0L),
                speedBps = o.optLong("speed_bps", 0L),
                currentFile = o.optString("current_file", ""),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun loadPolicy(): PadatPolicy {
        val raw = try {
            service()?.getPolicyJson()
        } catch (_: Exception) {
            null
        } ?: return PadatPolicy()
        return try {
            val o = JSONObject(raw)
            val appsMap = LinkedHashMap<String, AppPolicyEntry>()
            val pkgsObj = o.optJSONObject("packages") ?: o.optJSONObject("apps")
            if (pkgsObj != null) {
                val keys = pkgsObj.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    val a = pkgsObj.optJSONObject(pkg) ?: continue
                    val rawMode = if (a.has("preset")) a.optString("preset", "off") else a.optString("mode", "off")
                    val preset = PadatPreset.fromMode(rawMode)
                    val algo = PadatAlgo.fromWire(a.optString("algo", "lz4"))
                    val level = algo.clampLevel(a.optInt("level", preset.level))
                    val clusterKb = a.optInt("cluster_kb", preset.clusterKb)
                    val scopes = PadatScopes(
                        apkLib = a.optBoolean("apk_lib", true),
                        artOat = a.optBoolean("art_oat", true),
                        externalData = a.optBoolean("external_data", false),
                        obb = a.optBoolean("obb", false),
                        internalFiles = a.optBoolean("internal_files", false),
                    )
                    appsMap[pkg] = AppPolicyEntry(preset, algo, level, clusterKb, scopes)
                }
            }
            PadatPolicy(
                autoTier1 = o.optBoolean("auto_tier1", false),
                minFileBytes = o.optLong("min_file_bytes", 16384L),
                minSavingPct = o.optInt("min_saving_pct", 5),
                thermalPauseMc = o.optInt("thermal_pause_mc", 42000),
                pauseOnGame = o.optBoolean("pause_on_game", true),
                apps = appsMap,
            )
        } catch (_: Exception) {
            PadatPolicy()
        }
    }

    fun savePolicy(policy: PadatPolicy): Boolean = try {
        service()?.setPolicyJson(policy.toJsonString()) == 0
    } catch (_: Exception) {
        false
    }

    fun loadAllApps(policy: PadatPolicy): List<PadatAppItem> {
        val raw = try {
            service()?.getAllStatsJson()
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val parsed = parseAllAppsJson(raw, policy)
        if (parsed.isNotEmpty()) {
            saveDiskCache(raw)
        }
        return parsed
    }

    fun scanPackage(pkg: String, entry: AppPolicyEntry): PadatScanResult? {
        val norm = entry.normalized()
        val raw = try {
            service()?.scanPackageJson(
                pkg,
                norm.algo.id,
                norm.level,
                norm.clusterKb,
                norm.scopes.toMask(),
            )
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            val o = JSONObject(raw)
            if (o.has("error")) return null
            PadatScanResult(
                packageName = o.optString("package", pkg),
                isRunning = o.optBoolean("is_running", false),
                logicalBytes = o.optLong("logical_bytes", 0L),
                physicalBytes = o.optLong("physical_bytes", 0L),
                savedBytes = o.optLong("saved_bytes", 0L),
                compressedFiles = o.optLong("compressed_files", 0L),
                totalFiles = o.optLong("total_files", 0L),
                candidateFiles = o.optLong("candidate_files", 0L),
                estExtraSavingsBytes = o.optLong("est_extra_savings_bytes", 0L),
                skippedIncompressible = o.optLong("skipped_incompressible", 0L),
                skippedVerity = o.optLong("skipped_verity", 0L),
                skippedExcluded = o.optLong("skipped_excluded", 0L),
                detectedProfile = parseDetectedProfile(o.optJSONObject("detected_profile")),
                breakdown = parseBreakdown(o.optJSONObject("buckets")),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun compressPackage(pkg: String, entry: AppPolicyEntry, allowRunning: Boolean): PadatRunReport {
        val norm = entry.normalized()
        val raw = try {
            service()?.compressPackageJson(
                pkg,
                norm.algo.id,
                norm.level,
                norm.clusterKb,
                norm.scopes.toMask(),
                allowRunning,
            )
        } catch (e: Exception) {
            return errorReport(pkg, "compress", e.message ?: "binder_error")
        } ?: return errorReport(pkg, "compress", "service_unavailable")
        return parseReport(pkg, "compress", raw)
    }

    fun decompressPackage(pkg: String, scopes: PadatScopes, allowRunning: Boolean): PadatRunReport {
        val raw = try {
            service()?.decompressPackageJson(pkg, scopes.toMask(), allowRunning)
        } catch (e: Exception) {
            return errorReport(pkg, "decompress", e.message ?: "binder_error")
        } ?: return errorReport(pkg, "decompress", "service_unavailable")
        return parseReport(pkg, "decompress", raw)
    }

    fun forceStopPackage(pkg: String) {
        try {
            service()?.forceStopPackage(pkg)
        } catch (_: Exception) {
        }
    }

    fun setPaused(paused: Boolean) {
        try {
            service()?.setPaused(paused)
        } catch (_: Exception) {
        }
    }

    fun cancelJob() {
        try {
            service()?.cancelJob()
        } catch (_: Exception) {
        }
    }

    private fun parseDetectedProfile(o: JSONObject?): DetectedProfile? {
        if (o == null) return null
        val algoStr = o.optString("algo", "")
        if (algoStr.isEmpty()) return null
        val algo = PadatAlgo.fromWire(algoStr)
        val level = algo.clampLevel(o.optInt("level", algo.defaultLevel))
        val clusterKb = o.optInt("cluster_kb", 16)
        return DetectedProfile(algo = algo, level = level, clusterKb = clusterKb)
    }

    private fun parseBucket(o: JSONObject?): ScopeBucket {
        if (o == null) return ScopeBucket()
        return ScopeBucket(
            logicalBytes = o.optLong("logical_bytes", 0L),
            physicalBytes = o.optLong("physical_bytes", 0L),
            savedBytes = o.optLong("saved_bytes", 0L),
            compressedFiles = o.optLong("compressed_files", 0L),
            totalFiles = o.optLong("total_files", 0L),
        )
    }

    private fun parseBreakdown(o: JSONObject?): PackageBreakdown {
        if (o == null) return PackageBreakdown()
        return PackageBreakdown(
            apkLib = parseBucket(o.optJSONObject("apk_lib")),
            artOat = parseBucket(o.optJSONObject("art_oat")),
            externalData = parseBucket(o.optJSONObject("external_data")),
            obb = parseBucket(o.optJSONObject("obb")),
            internalFiles = parseBucket(o.optJSONObject("internal_files")),
            cacheOther = parseBucket(o.optJSONObject("cache_other")),
        )
    }

    private fun resolveLabel(pm: PackageManager, pkg: String): String = try {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
    } catch (_: Exception) {
        pkg
    }

    private fun parseReport(pkg: String, action: String, raw: String): PadatRunReport = try {
        val o = JSONObject(raw)
        if (o.has("error")) {
            errorReport(pkg, action, o.optString("error", "error"))
        } else {
            PadatRunReport(
                packageName = o.optString("package", pkg),
                action = o.optString("action", action),
                processedFiles = o.optLong("processed_files", 0L),
                compressedFiles = o.optLong("compressed_files", 0L),
                decompressedFiles = o.optLong("decompressed_files", 0L),
                releasedFiles = o.optLong("released_files", 0L),
                skippedProbe = o.optLong("skipped_probe", 0L),
                skippedPolicy = o.optLong("skipped_policy", 0L),
                skippedVerity = o.optLong("skipped_verity", 0L),
                errors = o.optLong("errors", 0L),
                bytesBefore = o.optLong("bytes_before", 0L),
                bytesAfter = o.optLong("bytes_after", 0L),
                savedDeltaBytes = o.optLong("saved_delta_bytes", 0L),
                cancelled = o.optBoolean("cancelled", false),
                refusedRunning = o.optBoolean("refused_running", false),
            )
        }
    } catch (e: Exception) {
        errorReport(pkg, action, e.message ?: "parse_error")
    }

    private fun errorReport(pkg: String, action: String, err: String) = PadatRunReport(
        packageName = pkg,
        action = action,
        processedFiles = 0L,
        compressedFiles = 0L,
        decompressedFiles = 0L,
        releasedFiles = 0L,
        skippedProbe = 0L,
        skippedPolicy = 0L,
        skippedVerity = 0L,
        errors = 1L,
        bytesBefore = 0L,
        bytesAfter = 0L,
        savedDeltaBytes = 0L,
        cancelled = false,
        refusedRunning = false,
        error = err,
    )

    companion object {
        const val SERVICE_NAME = "yasvara_padat"
        private const val CACHE_FILE_NAME = "padat_stats_v1.json"

        @Volatile
        private var memSnapshot: PadatCachedSnapshot? = null
        private val labelCache = ConcurrentHashMap<String, String>()
    }
}
