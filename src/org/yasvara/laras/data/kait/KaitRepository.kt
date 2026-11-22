/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.kait

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import java.io.File
import java.util.Properties
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yasvara.kait.IYasvaraKait

/** An installed Kait/Xposed module with full manifest and `module.prop` metadata. */
data class KaitModule(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val author: String,
    val description: String,
    val apiLabel: String,
    val isModernApi: Boolean,
    val hasNativeHook: Boolean,
    val canOpenUi: Boolean,
    val enabled: Boolean,
    /** Why the runtime cannot load it, or null when it can. */
    val problem: String?,
    val scope: List<String>,
    val recommended: List<String>,
)

/** A live process currently running with the Kait runtime attached. */
data class InjectedProcess(
    val processName: String,
    val pid: Int,
    val packageName: String,
)

/** An app a module can be scoped to. */
data class ScopeApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/**
 * The ROM's native Kait hooking runtime (`vendor/yasvara/kait`), reached through its
 * stealth management binder (`'_YKM'` on `activity`) for holders of `yasvara.permission.MANAGE_KAIT`.
 */
class KaitRepository(private val context: Context) {
    private val pm = context.packageManager
    private val userId = UserHandle.myUserId()

    @Volatile private var cached: IYasvaraKait? = null

    fun service(): IYasvaraKait? {
        cached?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return connect()?.also { cached = it }
    }

    private fun connect(): IYasvaraKait? {
        val activity: IBinder = ServiceManager.getService("activity") ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            if (!activity.transact(MANAGE_CODE, data, reply, 0)) return null
            reply.readException()
            reply.readStrongBinder()?.let(IYasvaraKait.Stub::asInterface)
        } catch (e: Exception) {
            Log.w(TAG, "Kait runtime unreachable", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    suspend fun frameworkVersion(): String =
        io { s -> s.frameworkVersion } ?: "Kait 2.0"

    suspend fun modules(): List<KaitModule>? = io { s ->
        s.listModules(userId).map { pkg ->
            val meta = inspectModulePackage(pkg)
            KaitModule(
                packageName = pkg,
                label = meta.label,
                versionName = meta.versionName,
                versionCode = meta.versionCode,
                author = meta.author,
                description = meta.description,
                apiLabel = meta.apiLabel,
                isModernApi = meta.isModernApi,
                hasNativeHook = meta.hasNativeHook,
                canOpenUi = findModuleLaunchIntent(pkg) != null,
                enabled = s.isModuleEnabled(pkg),
                problem = s.getModuleProblem(pkg, userId),
                scope = s.getScope(pkg, userId),
                recommended = s.getRecommendedScope(pkg, userId),
            )
        }.sortedWith(compareByDescending<KaitModule> { it.enabled }.thenBy { it.label.lowercase() })
    }

    suspend fun module(pkg: String): KaitModule? = modules()?.find { it.packageName == pkg }

    /** Packages with at least one process running with Kait attached right now. */
    suspend fun injected(): Set<String> =
        io { s -> s.injectedProcesses.map { it.substringBefore(':').substringBefore(':') }.toSet() }.orEmpty()

    /** Detailed list of currently attached processes ("processName:pid"). */
    suspend fun injectedProcesses(): List<InjectedProcess> =
        io { s ->
            s.injectedProcesses.mapNotNull { raw ->
                val idx = raw.lastIndexOf(':')
                if (idx <= 0) return@mapNotNull null
                val proc = raw.substring(0, idx)
                val pid = raw.substring(idx + 1).toIntOrNull() ?: return@mapNotNull null
                val pkg = proc.substringBefore(':')
                InjectedProcess(processName = proc, pid = pid, packageName = pkg)
            }
        }.orEmpty()

    /** Packages auto-bypassed by Kait's crash circuit breaker for the current user. */
    suspend fun crashBypassed(): List<String> =
        io { s -> s.getCrashBypassedPackages(userId) }.orEmpty()

    suspend fun clearCrashBypass(pkg: String): Boolean =
        io { s -> s.clearCrashBypass(pkg, userId) } != null

    suspend fun setEnabled(module: String, enabled: Boolean): Boolean =
        io { s -> s.setModuleEnabled(module, enabled) } != null

    suspend fun setScope(module: String, packages: Collection<String>): Boolean =
        io { s -> s.setScope(module, userId, packages.toList()) } != null

    suspend fun restart(pkg: String): Boolean = io { s -> s.restartApp(pkg, userId) } != null

    suspend fun restartAll(packages: Collection<String>): Int {
        var count = 0
        for (pkg in packages.distinct()) {
            if (restart(pkg)) count++
        }
        return count
    }

    /** Installed package versions on the device (`packageName -> RepoVersion`) for Repository comparison. */
    suspend fun installedPackageVersions(): Map<String, RepoVersion> =
        withContext(Dispatchers.IO) {
            val out = HashMap<String, RepoVersion>()
            runCatching {
                val pkgs = pm.getInstalledPackages(0)
                for (pi in pkgs) {
                    out[pi.packageName] = RepoVersion(pi.longVersionCode, pi.versionName.orEmpty())
                }
            }
            out
        }

    suspend fun apps(includeSystem: Boolean, pinned: Set<String>): List<ScopeApp> =
        withContext(Dispatchers.IO) {
            val launchable = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
            ).map { it.activityInfo.packageName }.toSet()
            pm.getInstalledApplications(0)
                .filter { it.packageName != SELF && UserHandle.getAppId(it.uid) >= FIRST_APP_ID }
                .mapNotNull { info ->
                    val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val listed = info.packageName in pinned ||
                        (if (system) includeSystem else info.packageName in launchable)
                    if (listed) ScopeApp(info.packageName, info.loadLabel(pm).toString(), system) else null
                }
                .sortedWith(compareBy({ it.packageName !in pinned }, { it.label.lowercase() }))
        }

    fun label(pkg: String): String = try {
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    /**
     * Resolves the launchable settings UI of an Xposed/Kait module:
     * 1. `ACTION_MAIN` + `de.robv.android.xposed.category.MODULE_SETTINGS`
     * 2. `ACTION_MAIN` + `CATEGORY_LAUNCHER`
     * 3. `ACTION_MAIN` + `CATEGORY_INFO`
     */
    fun findModuleLaunchIntent(packageName: String): Intent? {
        val categories = listOf(
            "de.robv.android.xposed.category.MODULE_SETTINGS",
            Intent.CATEGORY_LAUNCHER,
            Intent.CATEGORY_INFO,
        )
        for (cat in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(cat).setPackage(packageName)
            val resolved = pm.queryIntentActivities(query, 0).firstOrNull()?.activityInfo
            if (resolved != null) {
                return Intent(Intent.ACTION_MAIN)
                    .addCategory(cat)
                    .setComponent(ComponentName(resolved.packageName, resolved.name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openModuleUi(packageName: String): Boolean {
        val intent = findModuleLaunchIntent(packageName) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openAppInfo(packageName: String) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun requestUninstall(packageName: String) {
        runCatching {
            @Suppress("DEPRECATION")
            context.startActivity(
                Intent(Intent.ACTION_UNINSTALL_PACKAGE)
                    .setData(Uri.fromParts("package", packageName, null))
                    .putExtra(Intent.EXTRA_RETURN_RESULT, false)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private data class ParsedModuleMeta(
        val label: String,
        val versionName: String,
        val versionCode: Long,
        val author: String,
        val description: String,
        val apiLabel: String,
        val isModernApi: Boolean,
        val hasNativeHook: Boolean,
    )

    private fun inspectModulePackage(pkg: String): ParsedModuleMeta {
        return try {
            val pi = pm.getPackageInfo(pkg, PackageManager.GET_META_DATA)
            val ai = pi.applicationInfo
            val label = ai?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
            val versionName = pi.versionName?.takeIf { it.isNotBlank() } ?: "1.0"
            val versionCode = pi.longVersionCode

            var description = ""
            var author = ""
            var targetApi = 0
            var minApi = 0
            var hasNative = false

            // 1. Inspect META-INF/xposed/module.prop & native_init inside APK.
            val sourceDir = ai?.sourceDir
            if (sourceDir != null && File(sourceDir).isFile) {
                runCatching {
                    ZipFile(sourceDir).use { zip ->
                        val propEntry = zip.getEntry("META-INF/xposed/module.prop")
                        if (propEntry != null) {
                            val props = Properties()
                            zip.getInputStream(propEntry).use { props.load(it) }
                            description = props.getProperty("description", "").trim()
                            author = props.getProperty("author", "").trim()
                            targetApi = props.getProperty("targetApiVersion", "")
                                .trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                            minApi = props.getProperty("minApiVersion", "")
                                .trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                        }
                        hasNative = zip.getEntry("META-INF/xposed/native_init.list") != null ||
                            zip.getEntry("assets/native_init") != null
                    }
                }
            }

            // 2. Fallback to AndroidManifest meta-data (`xposeddescription`, `xposedminversion`).
            val meta = ai?.metaData
            if (description.isBlank() && meta != null) {
                val rawDesc = meta.get("xposeddescription")
                description = when (rawDesc) {
                    is String -> rawDesc.trim()
                    is Int -> if (rawDesc != 0) {
                        runCatching {
                            pm.getResourcesForApplication(ai).getString(rawDesc).trim()
                        }.getOrDefault("")
                    } else ""
                    else -> ""
                }
            }
            if (minApi == 0 && meta != null) {
                val rawMin = meta.get("xposedminversion")
                minApi = when (rawMin) {
                    is Int -> rawMin
                    is String -> rawMin.trim().takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                    else -> 0
                }
            }

            val isModern = targetApi >= 100
            val apiLabel = when {
                targetApi >= 100 -> "API $targetApi"
                minApi > 0 -> "API $minApi"
                else -> "Xposed"
            }

            ParsedModuleMeta(
                label = label,
                versionName = versionName,
                versionCode = versionCode,
                author = author,
                description = description,
                apiLabel = apiLabel,
                isModernApi = isModern,
                hasNativeHook = hasNative,
            )
        } catch (_: Exception) {
            ParsedModuleMeta(
                label = pkg,
                versionName = "",
                versionCode = 0L,
                author = "",
                description = "",
                apiLabel = "Xposed",
                isModernApi = false,
                hasNativeHook = false,
            )
        }
    }

    private suspend fun <T> io(block: (IYasvaraKait) -> T): T? = withContext(Dispatchers.IO) {
        val s = service() ?: return@withContext null
        try {
            block(s)
        } catch (e: Exception) {
            Log.w(TAG, "Kait call failed", e)
            cached = null
            null
        }
    }

    companion object {
        private const val TAG = "LarasKait"
        private const val SELF = "org.yasvara.laras"
        private const val FIRST_APP_ID = android.os.Process.FIRST_APPLICATION_UID

        /** YasvaraKait.MANAGE_CODE in system_server: '_YKM'. */
        private const val MANAGE_CODE =
            ('_'.code shl 24) or ('Y'.code shl 16) or ('K'.code shl 8) or 'M'.code
    }
}
