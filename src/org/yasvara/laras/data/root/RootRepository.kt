/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.root

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.Process
import android.os.ServiceManager
import android.os.UserHandle
import android.util.Log
import com.android.server.yasvara.root.IYasvaraRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.yasvara.laras.R
import java.io.File

/** What an enrolled app may ask for. Mirrors the tiers of yasvara_rootd. */
enum class RootTier(val value: Int) {
    /** uid 2000 in the enforcing `shell` domain: adb-shell power, no root. */
    SHELL(1),

    /** uid 0 in AOSP's permissive `su` domain: full root. */
    ROOT(2);

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: SHELL
    }
}

/** How long a grant lasts. */
enum class RootScope(val value: Int) { ALWAYS(0), UNTIL_REBOOT(1), TIMED(2) }

object GrantFlag {
    const val ALLOW_BEFORE_UNLOCK = 1
    const val NOTIFY_ON_USE = 2
    const val LOG_FULL = 4
    const val ALLOW_GLOBAL_NS = 8
}

data class RootGrant(
    val uid: Int,
    val tier: RootTier,
    val scope: RootScope,
    val globalNamespace: Boolean,
    val flags: Int,
    val expiresMs: Long,
    val grantedMs: Long,
    val lastUsedMs: Long,
    val packages: List<String>,
) {
    val primaryPackage: String get() = packages.firstOrNull().orEmpty()
}

data class AuditEvent(
    val time: Long,
    val event: String,
    val uid: Int,
    val tier: String,
    val packages: String,
    val detail: String,
)

data class DeniedAttempt(val uid: Int, val packageName: String, val lastMs: Long, val count: Int, val reason: String)

data class InstalledApp(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val sharedPackages: List<String>,
)

data class GrantPreset(
    val tier: RootTier,
    val scope: RootScope,
    val durationMs: Long,
    val flags: Int = 0,
)

data class RootState(
    val enabled: Boolean,
    val frozen: Boolean,
    val daemonRunning: Boolean,
    val daemonInstalled: Boolean,
)

data class AkarModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: String,
    val author: String,
    val description: String,
    val updateJson: String,
    val metamodule: Boolean,
    val enabled: Boolean,
    val remove: Boolean,
    val update: Boolean,
    val hasWebroot: Boolean,
    val hasAction: Boolean,
    val hasZygisk: Boolean,
    val zygiskAbis: List<String>,
    val managedFeatures: List<String>,
    val mountStrategy: String,
    val sizeBytes: Long,
)

data class BootguardStatus(
    val bootcount: Int = 0,
    val safeMode: Boolean = false,
    val reason: String = "none",
    val keyTrigger: String = "",
    val disabledSuspects: List<String> = emptyList(),
    val zygiskBypassed: Boolean = false,
)

data class ZygiskConfig(
    val modulesEnabled: Boolean = false,
    val enabled: Boolean = false,
    val denylist: Set<Int> = emptySet(),
    val scopes: Map<String, Set<Int>> = emptyMap(),
    val defaultUnmount: Boolean = true,
    val forceUnmount: Set<Int> = emptySet(),
    val exemptUnmount: Set<Int> = emptySet(),
)

data class ExecOutput(
    val exitCode: Int,
    val output: String,
)

/**
 * The control plane of the native root: talks to YasvaraRootService in
 * system_server, which owns the grants the daemon obeys. Every call is blocking binder IO; call
 * from Dispatchers.IO. Changes only succeed with the device unlocked and are gated by the device
 * credential in the UI before they get here.
 */
class RootRepository(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    private fun service(): IYasvaraRoot? {
        val binder: IBinder = ServiceManager.getService(SERVICE) ?: return null
        return IYasvaraRoot.Stub.asInterface(binder)
    }

    /** False when the running build has no root service (older OTA, or `user` build). */
    val available: Boolean get() = service() != null

    private inline fun <T> call(default: T, what: String, block: (IYasvaraRoot) -> T): T = try {
        service()?.let(block) ?: default
    } catch (t: Throwable) {
        Log.w(TAG, what, t)
        default
    }

    suspend fun state(): RootState? = withContext(Dispatchers.IO) {
        val s = service() ?: return@withContext null
        try {
            val bits = s.state
            RootState(
                enabled = bits and 1 != 0,
                frozen = bits and 2 != 0,
                daemonRunning = bits and 4 != 0,
                daemonInstalled = bits and 8 != 0,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "state", t)
            null
        }
    }

    suspend fun setEnabled(on: Boolean) = withContext(Dispatchers.IO) { call(false, "setEnabled") { it.setEnabled(on) } }

    suspend fun setFrozen(on: Boolean) = withContext(Dispatchers.IO) { call(false, "setFrozen") { it.setFrozen(on) } }

    suspend fun grants(): List<RootGrant> = withContext(Dispatchers.IO) {
        call(emptyList(), "listGrants") { s ->
            s.listGrants().mapNotNull { line ->
                val p = line.split('|')
                if (p.size < 9) return@mapNotNull null
                runCatching {
                    RootGrant(
                        uid = p[0].toInt(),
                        tier = RootTier.of(p[1].toInt()),
                        scope = RootScope.entries.firstOrNull { it.value == p[2].toInt() } ?: RootScope.ALWAYS,
                        globalNamespace = p[3] == "1",
                        flags = p[4].toInt(),
                        expiresMs = p[5].toLong(),
                        grantedMs = p[6].toLong(),
                        lastUsedMs = p[7].toLong(),
                        packages = p[8].split(',').filter { it.isNotEmpty() },
                    )
                }.getOrNull()
            }
        }
    }

    suspend fun enroll(
        pkg: String,
        tier: RootTier,
        scope: RootScope,
        durationMs: Long = 0,
        globalNamespace: Boolean = true,
        flags: Int = 0,
    ) = withContext(Dispatchers.IO) {
        call(false, "setGrant $pkg") {
            it.setGrant(pkg, android.os.UserHandle.myUserId(), tier.value, scope.value, durationMs,
                if (globalNamespace) 1 else 0, flags)
        }
    }

    suspend fun revoke(uid: Int) = withContext(Dispatchers.IO) { call(false, "revoke") { it.revoke(uid) } }

    suspend fun revokeAll() = withContext(Dispatchers.IO) { call(Unit, "revokeAll") { it.revokeAll() } }

    suspend fun audit(limit: Int = 60): List<AuditEvent> = withContext(Dispatchers.IO) {
        call(emptyList(), "getAudit") { s ->
            s.getAudit(limit).mapNotNull { line ->
                val p = line.split('|', limit = 7)
                if (p.size < 6) return@mapNotNull null
                AuditEvent(
                    time = p[0].toLongOrNull() ?: return@mapNotNull null,
                    event = p[1],
                    uid = p[2].toIntOrNull() ?: 0,
                    tier = p[4],
                    packages = p[5],
                    detail = p.getOrElse(6) { "" },
                )
            }
        }
    }

    suspend fun denied(): List<DeniedAttempt> = withContext(Dispatchers.IO) {
        call(emptyList(), "getDenied") { s ->
            s.denied.mapNotNull { line ->
                val p = line.split('|')
                if (p.size < 5) return@mapNotNull null
                DeniedAttempt(p[0].toIntOrNull() ?: return@mapNotNull null, p[1],
                    p[2].toLongOrNull() ?: 0, p[3].toIntOrNull() ?: 1, p[4])
            }
        }
    }

    suspend fun clearDenied() = withContext(Dispatchers.IO) { call(Unit, "clearDenied") { it.clearDenied() } }

    suspend fun selfTest(): String = withContext(Dispatchers.IO) {
        call("the root service is not available", "selfTest") { it.selfTest() }
    }

    suspend fun option(key: String): Int = withContext(Dispatchers.IO) { call(-1, "getOption") { it.getOption(key) } }

    suspend fun setOption(key: String, value: Int) = withContext(Dispatchers.IO) {
        call(false, "setOption") { it.setOption(key, value) }
    }

    // ---- Akar Module Engine, Bootguard & Native Zygisk (SPEC-0049) ----

    suspend fun listModules(): List<AkarModule> = withContext(Dispatchers.IO) {
        val raw = call("[]", "listModulesJson") { it.listModulesJson() ?: "[]" }
        runCatching {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val abisArr = o.optJSONArray("zygiskAbis")
                    val abis = buildList {
                        if (abisArr != null) for (j in 0 until abisArr.length()) add(abisArr.optString(j))
                    }
                    val featsArr = o.optJSONArray("managedFeatures")
                    val feats = buildList {
                        if (featsArr != null) for (j in 0 until featsArr.length()) add(featsArr.optString(j))
                    }
                    add(
                        AkarModule(
                            id = o.optString("id"),
                            name = o.optString("name", o.optString("id")),
                            version = o.optString("version"),
                            versionCode = o.optString("versionCode"),
                            author = o.optString("author"),
                            description = o.optString("description"),
                            updateJson = o.optString("updateJson"),
                            metamodule = o.optBoolean("metamodule"),
                            enabled = o.optBoolean("enabled", true),
                            remove = o.optBoolean("remove"),
                            update = o.optBoolean("update"),
                            hasWebroot = o.optBoolean("hasWebroot"),
                            hasAction = o.optBoolean("hasAction"),
                            hasZygisk = o.optBoolean("hasZygisk"),
                            zygiskAbis = abis,
                            managedFeatures = feats,
                            mountStrategy = o.optString("mountStrategy", "none"),
                            sizeBytes = o.optLong("sizeBytes"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseExecResult(raw: String): ExecOutput {
        val nl = raw.indexOf('\n')
        return if (nl > 0) {
            ExecOutput(raw.substring(0, nl).toIntOrNull() ?: 1, raw.substring(nl + 1))
        } else {
            ExecOutput(1, raw)
        }
    }

    suspend fun installModuleFromUri(uri: Uri): ExecOutput = withContext(Dispatchers.IO) {
        val stageDir = File(context.cacheDir, "akar_install").apply { mkdirs() }
        val fileName = "mod_${System.currentTimeMillis()}.zip"
        val dest = File(stageDir, fileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext ExecOutput(1, "Cannot open selected ZIP")
            val raw = call("1\nroot service unavailable", "installModuleFromCache") {
                it.installModuleFromCache(fileName) ?: "1\nempty response"
            }
            parseExecResult(raw)
        } catch (t: Throwable) {
            ExecOutput(1, t.message ?: "install error")
        } finally {
            dest.delete()
        }
    }

    suspend fun setModuleEnabled(id: String, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        call(false, "setModuleEnabled") { it.setModuleEnabled(id, enabled) }
    }

    suspend fun setModuleRemove(id: String, remove: Boolean): Boolean = withContext(Dispatchers.IO) {
        call(false, "setModuleRemove") { it.setModuleRemove(id, remove) }
    }

    suspend fun runModuleAction(id: String): ExecOutput = withContext(Dispatchers.IO) {
        val raw = call("1\nroot service unavailable", "runModuleAction") {
            it.runModuleAction(id) ?: "1\nempty response"
        }
        parseExecResult(raw)
    }

    suspend fun bootguardStatus(): BootguardStatus = withContext(Dispatchers.IO) {
        val raw = call("{}", "getBootguardStatusJson") { it.bootguardStatusJson ?: "{}" }
        runCatching {
            val o = JSONObject(raw)
            val sArr = o.optJSONArray("disabledSuspects")
            val suspects = buildList {
                if (sArr != null) for (i in 0 until sArr.length()) {
                    val s = sArr.optString(i)
                    if (s.isNotEmpty()) add(s)
                }
            }
            BootguardStatus(
                bootcount = o.optInt("bootcount"),
                safeMode = o.optBoolean("safeMode"),
                reason = o.optString("reason", "none"),
                keyTrigger = o.optString("keyTrigger"),
                disabledSuspects = suspects,
                zygiskBypassed = o.optBoolean("zygiskBypassed"),
            )
        }.getOrDefault(BootguardStatus())
    }

    suspend fun clearSafeMode(): Boolean = withContext(Dispatchers.IO) {
        call(false, "clearSafeMode") { it.clearSafeMode() }
    }

    suspend fun zygiskConfig(): ZygiskConfig = withContext(Dispatchers.IO) {
        val raw = call("{}", "getZygiskConfigJson") { it.zygiskConfigJson ?: "{}" }
        runCatching {
            val o = JSONObject(raw)
            fun intSet(arr: JSONArray?): Set<Int> = buildSet {
                if (arr != null) for (i in 0 until arr.length()) add(arr.optInt(i))
            }
            val scopesObj = o.optJSONObject("scopes")
            val scopes = buildMap<String, Set<Int>> {
                if (scopesObj != null) {
                    val keys = scopesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        put(k, intSet(scopesObj.optJSONArray(k)))
                    }
                }
            }
            ZygiskConfig(
                modulesEnabled = o.optBoolean("modulesEnabled", false),
                enabled = o.optBoolean("enabled", false),
                denylist = intSet(o.optJSONArray("denylist")),
                scopes = scopes,
                defaultUnmount = o.optBoolean("defaultUnmount", true),
                forceUnmount = intSet(o.optJSONArray("forceUnmount")),
                exemptUnmount = intSet(o.optJSONArray("exemptUnmount")),
            )
        }.getOrDefault(ZygiskConfig())
    }

    suspend fun saveZygiskConfig(cfg: ZygiskConfig): Boolean = withContext(Dispatchers.IO) {
        val jo = JSONObject().apply {
            put("modulesEnabled", cfg.modulesEnabled)
            put("enabled", cfg.enabled)
            put("denylist", JSONArray(cfg.denylist.sorted()))
            put("defaultUnmount", cfg.defaultUnmount)
            put("forceUnmount", JSONArray(cfg.forceUnmount.sorted()))
            put("exemptUnmount", JSONArray(cfg.exemptUnmount.sorted()))
            val sc = JSONObject()
            for ((id, uids) in cfg.scopes) {
                sc.put(id, JSONArray(uids.sorted()))
            }
            put("scopes", sc)
        }
        call(false, "setZygiskConfigJson") { it.setZygiskConfigJson(jo.toString()) }
    }

    suspend fun execModuleShell(moduleId: String, command: String): ExecOutput = withContext(Dispatchers.IO) {
        val raw = call("1\nroot service unavailable", "execModuleShell") {
            it.execModuleShell(moduleId, command) ?: "1\nempty response"
        }
        parseExecResult(raw)
    }

    fun labelOf(pkg: String): String = try {
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    fun isInstalled(pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** Apps worth listing in the picker: installed, launchable, not Laras itself. */
    suspend fun installedApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val launch = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launch, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { it to labelOf(it) }
            .sortedBy { it.second.lowercase() }
    }

    /**
     * All enrollable application UIDs on this user, grouped by UID so shared-UID families
     * (such as Termux and its plugins) appear once with every package listed.
     */
    suspend fun allApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val launchable = pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
            0,
        ).mapTo(HashSet()) { it.activityInfo.packageName }

        val sysMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { UserHandle.getAppId(it.uid) >= Process.FIRST_APPLICATION_UID }
            .groupBy { it.uid }
            .values
            .map { group ->
                val sorted = group.sortedWith(
                    compareByDescending<ApplicationInfo> { it.packageName in launchable }
                        .thenBy { it.packageName },
                )
                val primary = sorted.first()
                InstalledApp(
                    packageName = primary.packageName,
                    label = primary.loadLabel(pm).toString(),
                    uid = primary.uid,
                    isSystem = group.all { (it.flags and sysMask) != 0 },
                    sharedPackages = group.map { it.packageName }.distinct().sorted(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun resolveApp(packageName: String): InstalledApp? = withContext(Dispatchers.IO) {
        if (packageName == SHELL_PACKAGE) {
            return@withContext InstalledApp(
                packageName = SHELL_PACKAGE,
                label = context.getString(R.string.akar_adb_title),
                uid = UID_SHELL,
                isSystem = true,
                sharedPackages = listOf(SHELL_PACKAGE),
            )
        }
        try {
            val ai = pm.getApplicationInfo(packageName, 0)
            val sharing = pm.getPackagesForUid(ai.uid)?.distinct()?.sorted().orEmpty()
                .ifEmpty { listOf(packageName) }
            val sysMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            InstalledApp(
                packageName = packageName,
                label = ai.loadLabel(pm).toString(),
                uid = ai.uid,
                isSystem = (ai.flags and sysMask) != 0,
                sharedPackages = sharing,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun adbPreset(): GrantPreset {
        val tier = RootTier.of(prefs.getInt(KEY_ADB_TIER, RootTier.ROOT.value))
        val scope = RootScope.entries.firstOrNull {
            it.value == prefs.getInt(KEY_ADB_SCOPE, RootScope.TIMED.value)
        } ?: RootScope.TIMED
        val duration = prefs.getLong(KEY_ADB_DURATION, DEFAULT_ADB_DURATION_MS)
            .takeIf { it > 0 } ?: DEFAULT_ADB_DURATION_MS
        return GrantPreset(tier, scope, duration)
    }

    fun saveAdbPreset(tier: RootTier, scope: RootScope, durationMs: Long) {
        prefs.edit()
            .putInt(KEY_ADB_TIER, tier.value)
            .putInt(KEY_ADB_SCOPE, scope.value)
            .putLong(KEY_ADB_DURATION, if (scope == RootScope.TIMED && durationMs > 0) durationMs else DEFAULT_ADB_DURATION_MS)
            .apply()
    }

    fun savedProfile(pkg: String): GrantPreset? {
        val raw = prefs.getString(KEY_PROFILE_PREFIX + pkg, null) ?: return null
        val p = raw.split('|')
        if (p.size < 4) return null
        val tier = p[0].toIntOrNull()?.let(RootTier::of) ?: return null
        val scope = p[1].toIntOrNull()?.let { v -> RootScope.entries.firstOrNull { it.value == v } } ?: return null
        val duration = p[2].toLongOrNull() ?: 0L
        val flags = p[3].toIntOrNull() ?: 0
        return GrantPreset(tier, scope, duration, flags)
    }

    fun saveProfile(pkg: String, tier: RootTier, scope: RootScope, durationMs: Long, flags: Int) {
        prefs.edit()
            .putString(KEY_PROFILE_PREFIX + pkg, "${tier.value}|${scope.value}|$durationMs|$flags")
            .apply()
    }

    companion object {
        const val SERVICE = "yasvara_session"
        const val TERMUX = "com.termux"
        const val SHELL_PACKAGE = "shell"
        const val UID_SHELL = 2000
        const val DEFAULT_ADB_DURATION_MS = 3_600_000L
        private const val PREFS_NAME = "akar_ui"
        private const val KEY_ADB_TIER = "adb_tier"
        private const val KEY_ADB_SCOPE = "adb_scope"
        private const val KEY_ADB_DURATION = "adb_duration_ms"
        private const val KEY_PROFILE_PREFIX = "profile:"
        private const val TAG = "LarasRoot"
    }
}
