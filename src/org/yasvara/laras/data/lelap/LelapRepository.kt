/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.lelap

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.ServiceManager
import com.android.server.yasvara.lelap.IYasvaraLelap

/** One rule of the framework's Lelap store: a kind and its value. */
data class Rule(val kind: String, val value: String)

data class SleepApp(
    val packageName: String,
    val label: String,
    val strict: Boolean,
    val asleep: Boolean,
    val notifications: Boolean,
    val system: Boolean,
    /** Kept out of the stopped state so its home screen widget keeps drawing. */
    val widget: Boolean,
    /** Wake attempts in the log. */
    val blocked: Int,
)

data class WakeEvent(val time: Long, val kind: String, val packageName: String, val detail: String)

/** Talks to "yasvara_lelap" in system_server. Every call is a binder call; keep it off the main thread. */
class LelapRepository(private val context: Context) {
    private fun service(): IYasvaraLelap? = try {
        ServiceManager.getService(SERVICE)?.let(IYasvaraLelap.Stub::asInterface)
    } catch (e: RuntimeException) {
        null
    }

    val available: Boolean get() = service() != null

    fun rules(kind: String): Set<String> = try {
        service()?.getRules(kind)?.toSet().orEmpty()
    } catch (e: Exception) {
        emptySet()
    }

    /** Every stored rule, for deciding which cuts are on. */
    fun allRules(): Set<Rule> = KINDS.flatMapTo(HashSet()) { kind -> rules(kind).map { Rule(kind, it) } }

    /** Adds or removes rules, one write per kind. Returns the service's refusal, or null. */
    fun change(rules: List<Rule>, on: Boolean): String? {
        val service = service() ?: return "unavailable"
        return try {
            for ((kind, values) in rules.groupBy({ it.kind }, { it.value })) {
                val now = service.getRules(kind).toMutableSet()
                if (on) now += values else now -= values.toSet()
                service.setRules(kind, now.toTypedArray())
            }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    fun apps(events: List<WakeEvent>): List<SleepApp> {
        val pm = context.packageManager
        val strict = rules(STRICT)
        val asleep = names { it.asleep }
        val widgets = names { it.widgetKept }
        val blocked = events.groupingBy { it.packageName }.eachCount()
        return pm.getInstalledApplications(0)
            .filter { it.packageName != context.packageName && sleepable(pm, it) }
            .map { info ->
                SleepApp(
                    info.packageName,
                    info.loadLabel(pm).toString(),
                    info.packageName in strict,
                    info.packageName in asleep,
                    pm.checkPermission(Manifest.permission.POST_NOTIFICATIONS, info.packageName) ==
                        PackageManager.PERMISSION_GRANTED,
                    info.isSystemApp,
                    info.packageName in widgets,
                    blocked[info.packageName] ?: 0,
                )
            }
            .sortedWith(compareByDescending<SleepApp> { it.strict }.thenBy { it.label.lowercase() })
    }

    /** The framework refuses privileged and persistent apps; of the other system apps only those with a launcher icon are worth listing. */
    private fun sleepable(pm: PackageManager, info: ApplicationInfo): Boolean {
        if (!info.isSystemApp) return true
        if (info.isPrivilegedApp || info.flags and ApplicationInfo.FLAG_PERSISTENT != 0) return false
        return pm.getLaunchIntentForPackage(info.packageName) != null
    }

    private fun names(read: (IYasvaraLelap) -> Array<String>?): Set<String> = try {
        service()?.let(read)?.toSet().orEmpty()
    } catch (e: Exception) {
        emptySet()
    }

    fun events(): List<WakeEvent> = try {
        service()?.events.orEmpty().mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 4) null else WakeEvent(p[0].toLongOrNull() ?: 0, p[1], p[2], p[3])
        }.asReversed()
    } catch (e: Exception) {
        emptyList()
    }

    fun clearEvents() {
        try {
            service()?.clearEvents()
        } catch (e: Exception) {
            // Nothing to clear when the service is gone.
        }
    }

    companion object {
        private const val SERVICE = "yasvara_lelap"
        const val STRICT = "strict"
        const val PUSH = "push"
        const val JOB = "job"
        const val SENSOR = "sensor"
        const val COMPONENT = "component"
        const val PACKAGE = "package"
        const val SYNC = "sync"
        const val SETTING = "setting"
        val KINDS = listOf(STRICT, PUSH, JOB, SENSOR, COMPONENT, PACKAGE, SYNC, SETTING)
    }
}
