/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.updates

import android.content.Context
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.os.UserHandle
import android.security.yasvara.YasvaraUpdatePolicy
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Mirrors YasvaraUpdatePolicy.POLICY_*. SKIP is set per version, not picked from the ladder. */
enum class UpdatePolicy(val value: Int) {
    AUTO(YasvaraUpdatePolicy.POLICY_AUTO),
    DELAY(YasvaraUpdatePolicy.POLICY_DELAY),
    ASK(YasvaraUpdatePolicy.POLICY_ASK),
    NEVER(YasvaraUpdatePolicy.POLICY_NEVER),
    SKIP(YasvaraUpdatePolicy.POLICY_SKIP);

    companion object {
        val LADDER = listOf(AUTO, DELAY, ASK, NEVER)
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

data class UpdateApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val installer: String?,
    /** Play Store, Play services, GSF or a system app: only listed behind the Lab gate. */
    val core: Boolean,
    val policy: UpdatePolicy,
    val delayDays: Int,
)

data class UpdateEvent(
    val time: Long,
    val fromVersionCode: Long,
    val toVersionCode: Long,
    val installer: String,
    /** "allowed", "approved", "deferred", "blocked", "skipped", "ask" or "rolledback". */
    val decision: String,
)

/** One saved APK set in the version vault. */
data class VaultEntry(
    val versionCode: Long,
    val versionName: String,
    val bytes: Long,
    val savedAt: Long,
)

/**
 * Reads and writes the per-app update policy kept by YasvaraUpdatePolicyService in
 * system_server. Every call is blocking binder IO; call from Dispatchers.IO.
 */
class UpdatePolicyRepository(context: Context) {
    private val pm = context.packageManager
    private val self = context.packageName
    private val user = UserHandle.myUserId()

    /** False when the running build has no policy service (older OTA). */
    val available: Boolean get() = YasvaraUpdatePolicy.service() != null

    suspend fun apps(includeCore: Boolean): List<UpdateApp> = withContext(Dispatchers.IO) {
        val service = YasvaraUpdatePolicy.service()
        val managed = runCatching { service?.getManagedPackages(user)?.toSet() }.getOrNull().orEmpty()
        pm.getInstalledPackages(0).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            if (info.packageName == self) return@mapNotNull null
            val core = isCore(info.packageName, app)
            val installer = installerOf(info.packageName)
            // Only apps a store can update: those installed by one, plus anything with a rule.
            val listed = info.packageName in managed ||
                (installer != null && (!core || includeCore))
            if (!listed) return@mapNotNull null
            val policy = if (info.packageName in managed) {
                UpdatePolicy.of(runCatching { service!!.getPolicy(info.packageName, user) }
                    .getOrDefault(YasvaraUpdatePolicy.POLICY_AUTO))
            } else UpdatePolicy.AUTO
            UpdateApp(
                packageName = info.packageName,
                label = app.loadLabel(pm).toString(),
                versionName = info.versionName.orEmpty(),
                versionCode = info.longVersionCode,
                installer = installer,
                core = core,
                policy = policy,
                delayDays = if (policy == UpdatePolicy.DELAY)
                    runCatching { service!!.getDelayDays(info.packageName, user) }.getOrDefault(0)
                else 0,
            )
        }.sortedBy { it.label.lowercase() }
    }

    fun set(pkg: String, policy: UpdatePolicy, delayDays: Int): Boolean = try {
        YasvaraUpdatePolicy.service()?.setPolicy(pkg, policy.value, delayDays, user) != null
    } catch (t: Throwable) {
        Log.w(TAG, "setPolicy $pkg", t)
        false
    }

    /** Lets the next store update of a paused app through for a day; [allow] false revokes. */
    fun allowOnce(pkg: String, allow: Boolean = true) = try {
        YasvaraUpdatePolicy.service()?.allowOnce(pkg, if (allow) 1L else 0L, user)
    } catch (t: Throwable) {
        Log.w(TAG, "allowOnce $pkg", t)
    }

    /** When the Allow once grant of [pkg] runs out, or 0 for none. */
    fun allowOnceUntil(pkg: String): Long =
        runCatching { YasvaraUpdatePolicy.service()?.getAllowOnceUntil(pkg, user) ?: 0L }.getOrDefault(0L)

    /** Whether a paused app stays visible to its store (true) or is hidden from it (false). */
    fun shownInStore(pkg: String): Boolean =
        runCatching { YasvaraUpdatePolicy.service()?.isShownInStore(pkg, user) ?: false }.getOrDefault(false)

    fun setShownInStore(pkg: String, shown: Boolean) = try {
        YasvaraUpdatePolicy.service()?.setShownInStore(pkg, shown, user)
    } catch (t: Throwable) {
        Log.w(TAG, "setShownInStore $pkg", t)
    }

    /** Refuses exactly [versionCode]; the next version installs and the rule ends. */
    fun skipVersion(pkg: String, versionCode: Long): Boolean = try {
        YasvaraUpdatePolicy.service()?.skipVersion(pkg, versionCode, user) != null
    } catch (t: Throwable) {
        Log.w(TAG, "skipVersion $pkg", t)
        false
    }

    fun skippedVersion(pkg: String): Long =
        runCatching { YasvaraUpdatePolicy.service()?.getSkippedVersion(pkg, user) ?: 0L }.getOrDefault(0L)

    fun history(pkg: String): List<UpdateEvent> =
        runCatching { YasvaraUpdatePolicy.service()?.getHistory(pkg, user)?.toList() }.getOrNull().orEmpty()
            .mapNotNull { line ->
                val p = line.split('|')
                if (p.size < 5) null
                else UpdateEvent(p[0].toLongOrNull() ?: 0, p[1].toLongOrNull() ?: 0,
                    p[2].toLongOrNull() ?: 0, p[3], p[4])
            }

    fun firstSeen(pkg: String, versionCode: Long): Long =
        runCatching { YasvaraUpdatePolicy.service()?.getFirstSeen(pkg, versionCode, user) ?: 0L }.getOrDefault(0L)

    fun vault(pkg: String): List<VaultEntry> =
        runCatching { YasvaraUpdatePolicy.service()?.listVault(pkg, user)?.toList() }.getOrNull().orEmpty()
            .mapNotNull { line ->
                val p = line.split('|')
                val vc = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                VaultEntry(vc, p.getOrElse(1) { "" }, p.getOrNull(2)?.toLongOrNull() ?: 0,
                    p.getOrNull(3)?.toLongOrNull() ?: 0)
            }

    fun vaultBytes(): Long =
        runCatching { YasvaraUpdatePolicy.service()?.getVaultBytes(user) ?: 0L }.getOrDefault(0L)

    fun deleteVault(pkg: String, versionCode: Long) = try {
        YasvaraUpdatePolicy.service()?.deleteVault(pkg, versionCode, user)
    } catch (t: Throwable) {
        Log.w(TAG, "deleteVault $pkg", t)
    }

    /** Starts a rollback; the result arrives on [result]. False when it could not start. */
    fun rollback(pkg: String, versionCode: Long, clearData: Boolean, pin: Boolean,
            result: IntentSender): Boolean = try {
        YasvaraUpdatePolicy.service()?.rollback(pkg, versionCode, clearData, pin, user, result) != null
    } catch (t: Throwable) {
        Log.w(TAG, "rollback $pkg", t)
        false
    }

    /** Whether the outgoing version is saved before store updates (always, unless on Auto). */
    fun vaultEnabled(pkg: String): Boolean =
        runCatching { YasvaraUpdatePolicy.service()?.isVaultEnabled(pkg, user) ?: false }.getOrDefault(false)

    /** Opts an app on Auto in or out of the version vault (§4.4). */
    fun setVaultEnabled(pkg: String, enabled: Boolean) = try {
        YasvaraUpdatePolicy.service()?.setVaultEnabled(pkg, enabled, user)
    } catch (t: Throwable) {
        Log.w(TAG, "setVaultEnabled $pkg", t)
    }

    /** When the current policy was picked, or 0 on Auto. */
    fun policySince(pkg: String): Long =
        runCatching { YasvaraUpdatePolicy.service()?.getPolicySince(pkg, user) ?: 0L }.getOrDefault(0L)

    fun clearData(pkg: String) = try {
        YasvaraUpdatePolicy.service()?.clearAppData(pkg, user)
    } catch (t: Throwable) {
        Log.w(TAG, "clearAppData $pkg", t)
    }

    fun labelOf(pkg: String?): String? = pkg?.let {
        runCatching { pm.getApplicationInfo(it, 0).loadLabel(pm).toString() }.getOrNull()
    }

    private fun installerOf(pkg: String): String? = runCatching {
        pm.getInstallSourceInfo(pkg).installingPackageName
    }.getOrNull()

    private fun isCore(pkg: String, app: ApplicationInfo) =
        pkg in CORE || app.flags and ApplicationInfo.FLAG_SYSTEM != 0

    companion object {
        private const val TAG = "LarasUpdatePolicy"
        const val PLAY_STORE = "com.android.vending"
        val CORE = setOf(PLAY_STORE, "com.google.android.gms", "com.google.android.gsf")
    }
}
