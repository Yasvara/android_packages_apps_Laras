/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.xposed

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.os.UserHandle
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yasvara.xposed.IYasvaraXposed

/** An installed Xposed module as the runtime in system_server sees it. */
data class XposedModule(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    /** Why the runtime cannot load it, or null when it can. */
    val problem: String?,
    val scope: List<String>,
    val recommended: List<String>,
)

/** An app a module can be scoped to. */
data class ScopeApp(val packageName: String, val label: String, val isSystem: Boolean)

/**
 * The ROM's rootless Xposed runtime (vendor/yasvara/xposed), reached through its management
 * binder. That binder is not in servicemanager: it is handed out by a reserved transaction on the
 * "activity" service to holders of yasvara.permission.MANAGE_XPOSED, so every call here is null
 * or empty when the runtime is off or the permission is missing.
 */
class XposedRepository(private val context: Context) {
    private val pm = context.packageManager
    private val userId = UserHandle.myUserId()

    @Volatile private var cached: IYasvaraXposed? = null

    /** The management binder, or null when the runtime is not running or refuses us. */
    fun service(): IYasvaraXposed? {
        cached?.takeIf { it.asBinder().isBinderAlive }?.let { return it }
        return connect()?.also { cached = it }
    }

    private fun connect(): IYasvaraXposed? {
        val activity: IBinder = ServiceManager.getService("activity") ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            if (!activity.transact(MANAGE_CODE, data, reply, 0)) return null
            reply.readException()
            reply.readStrongBinder()?.let(IYasvaraXposed.Stub::asInterface)
        } catch (e: Exception) {
            Log.w(TAG, "Xposed runtime unreachable", e)
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    suspend fun modules(): List<XposedModule>? = io { s ->
        s.listModules(userId).map { pkg ->
            XposedModule(
                packageName = pkg,
                label = label(pkg),
                enabled = s.isModuleEnabled(pkg),
                problem = s.getModuleProblem(pkg, userId),
                scope = s.getScope(pkg, userId),
                recommended = s.getRecommendedScope(pkg, userId),
            )
        }
    }

    suspend fun module(pkg: String): XposedModule? = modules()?.find { it.packageName == pkg }

    /** Packages with a process running the runtime right now. */
    suspend fun injected(): Set<String> =
        io { s -> s.injectedProcesses.map { it.substringBefore(':') }.toSet() }.orEmpty()

    suspend fun setEnabled(module: String, enabled: Boolean): Boolean =
        io { s -> s.setModuleEnabled(module, enabled) } != null

    suspend fun setScope(module: String, packages: Collection<String>): Boolean =
        io { s -> s.setScope(module, userId, packages.toList()) } != null

    suspend fun restart(pkg: String): Boolean = io { s -> s.restartApp(pkg, userId) } != null

    /**
     * Apps a module can be loaded into: launchable apps, plus system ones on request, plus what is
     * already in scope or recommended so a choice never disappears from the list.
     */
    suspend fun apps(includeSystem: Boolean, pinned: Set<String>): List<ScopeApp> =
        withContext(Dispatchers.IO) {
            val launchable = pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
            ).map { it.activityInfo.packageName }.toSet()
            pm.getInstalledApplications(0)
                // The runtime only loads into ordinary app processes, and restarting Laras
                // from inside Laras would close the screen the user is on.
                .filter { it.packageName != SELF && UserHandle.getAppId(it.uid) >= FIRST_APP_ID }
                .mapNotNull { info ->
                    val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val listed = info.packageName in pinned ||
                        (if (system) includeSystem else info.packageName in launchable)
                    if (listed) ScopeApp(info.packageName, info.loadLabel(pm).toString(), system) else null
                }
                .sortedWith(compareBy({ it.packageName !in pinned }, { it.label.lowercase() }))
        }

    private fun label(pkg: String): String = try {
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }

    /** Runs [block] on the binder off the main thread; null when the runtime is unreachable. */
    private suspend fun <T> io(block: (IYasvaraXposed) -> T): T? = withContext(Dispatchers.IO) {
        val s = service() ?: return@withContext null
        try {
            block(s)
        } catch (e: Exception) {
            Log.w(TAG, "Xposed call failed", e)
            cached = null
            null
        }
    }

    companion object {
        private const val TAG = "LarasXposed"
        private const val SELF = "org.yasvara.laras"
        private const val FIRST_APP_ID = android.os.Process.FIRST_APPLICATION_UID

        /** YasvaraXposed.MANAGE_CODE in system_server: '_YXM'. */
        private const val MANAGE_CODE =
            ('_'.code shl 24) or ('Y'.code shl 16) or ('X'.code shl 8) or 'M'.code
    }
}
