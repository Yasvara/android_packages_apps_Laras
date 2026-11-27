/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.visibility

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.RemoteException
import android.os.ServiceManager
import com.android.server.yasvara.visibility.IYasvaraAppVisibility
import org.json.JSONArray
import org.json.JSONObject

data class UserApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
)

data class TabirTemplate(
    val name: String,
    val mode: Int,
    val packages: Set<String>,
)

data class ObserverConfig(
    val mode: Int,
    val packages: Set<String>,
    val templates: Set<String>,
)

data class TabirSnapshot(
    val available: Boolean,
    val scope: Int = IYasvaraAppVisibility.SCOPE_SELECTED,
    val hideXposed: Boolean = true,
    val hideRomPackages: Boolean = true,
    val hideRootTooling: Boolean = true,
    val devOptionsHideMode: Int = IYasvaraAppVisibility.DEV_HIDE_ALL_NON_SYSTEM,
    val hidden: Set<String> = emptySet(),
    val observers: Set<String> = emptySet(),
    val exceptions: Set<String> = emptySet(),
    val modules: Set<String> = emptySet(),
    val detectedRom: Set<String> = emptySet(),
    val detectedRoot: Set<String> = emptySet(),
    val templates: List<TabirTemplate> = emptyList(),
    val observerConfigs: Map<String, ObserverConfig> = emptyMap(),
)

data class ObserverEvalResult(
    val observer: String,
    val mode: Int = IYasvaraAppVisibility.MODE_GLOBAL,
    val devHidden: Boolean = false,
    val results: Map<String, Boolean> = emptyMap(),
)

/** Offered to the owner as one-tap rules, never applied on their own. */
object VisibilitySuggestions {
    private val TARGETS = setOf(
        "bin.mt.plus",
        "bin.mt.plus.canary",
        "com.omarea.vtools",
        "com.rifsxd.ksunext",
        "com.sukisu.ultra",
        "com.termux",
        "com.topjohnwu.magisk",
        "com.tsng.hidemyapplist",
        "io.github.a13e300.ksuwebui",
        "io.github.huskydg.magisk",
        "io.github.muntashirakon.AppManager",
        "io.github.vvb2060.magisk",
        "me.bmax.apatch",
        "me.weishu.kernelsu",
        "moe.shizuku.privileged.api",
        "org.frknkrc44.hma_oss",
        "org.lsposed.manager",
        "rikka.appops",
        "io.github.vvb2060.keyattestation",
        "gr.nikolasspyr.integritycheck",
        "com.eltavine.duckdetector",
        "io.github.vvb2060.mahoshojo",
        "io.github.vvb2060.momo",
        "com.reveny.nativecheck",
        "icu.nullptr.nativetest",
        "com.zhenxi.hunter",
    )

    fun isTarget(pkg: String) = pkg in TARGETS

    fun isDetector(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.endsWith(".duckdetector") ||
            lower.endsWith(".keyattestation") ||
            "applistdetector" in lower ||
            "nativecheck" in lower ||
            "nativetest" in lower ||
            "hunter" in lower ||
            "rootbeer" in lower ||
            "playintegrity" in lower ||
            "yasnac" in lower ||
            "mahoshojo" in lower ||
            lower == "io.github.vvb2060.momo"
    }
}

/** Unified Framework Tabir repository backed by "yasvara_app_visibility" in system_server. */
class AppVisibilityRepository(private val context: Context) {
    private fun service(): IYasvaraAppVisibility? = try {
        ServiceManager.getService(SERVICE)?.let(IYasvaraAppVisibility.Stub::asInterface)
    } catch (_: RuntimeException) {
        null
    }

    val available get() = service() != null

    /**
     * Fetches the entire Tabir control-plane state in a single Binder IPC (eliminating the
     * 20+ sequential IPCs and redundant system-wide PM cache invalidations).
     */
    fun loadSnapshot(): TabirSnapshot {
        val svc = service() ?: return TabirSnapshot(available = false)
        return try {
            val raw = svc.uiSnapshotJson
            if (raw.isNullOrBlank() || raw == "{}") {
                loadSnapshotLegacy(svc)
            } else {
                parseSnapshot(raw)
            }
        } catch (_: Throwable) {
            runCatching { loadSnapshotLegacy(svc) }.getOrElse { TabirSnapshot(available = false) }
        }
    }

    private fun parseSnapshot(raw: String): TabirSnapshot {
        val root = JSONObject(raw)
        val templates = ArrayList<TabirTemplate>()
        root.optJSONObject("templates")?.let { tplMap ->
            val keys = tplMap.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                val obj = tplMap.optJSONObject(name) ?: continue
                templates += TabirTemplate(
                    name = name,
                    mode = obj.optInt("mode", IYasvaraAppVisibility.MODE_BLACKLIST),
                    packages = jsonToSet(obj.optJSONArray("packages")),
                )
            }
        }
        templates.sortBy { it.name.lowercase() }

        val observerConfigs = LinkedHashMap<String, ObserverConfig>()
        root.optJSONObject("observerRules")?.let { ruleMap ->
            val names = ArrayList<String>()
            val keys = ruleMap.keys()
            while (keys.hasNext()) names += keys.next()
            names.sort()
            for (obs in names) {
                val obj = ruleMap.optJSONObject(obs) ?: continue
                observerConfigs[obs] = ObserverConfig(
                    mode = obj.optInt("mode", IYasvaraAppVisibility.MODE_BLACKLIST),
                    packages = jsonToSet(obj.optJSONArray("packages")),
                    templates = jsonToSet(obj.optJSONArray("templates")),
                )
            }
        }

        return TabirSnapshot(
            available = true,
            scope = root.optInt("scope", IYasvaraAppVisibility.SCOPE_SELECTED),
            hideXposed = root.optBoolean("hideXposed", true),
            hideRomPackages = root.optBoolean("hideRomPackages", true),
            hideRootTooling = root.optBoolean("hideRootTooling", true),
            devOptionsHideMode = root.optInt(
                "devOptionsHideMode",
                IYasvaraAppVisibility.DEV_HIDE_ALL_NON_SYSTEM,
            ),
            hidden = jsonToSet(root.optJSONArray("hidden")),
            observers = jsonToSet(root.optJSONArray("observers")),
            exceptions = jsonToSet(root.optJSONArray("exceptions")),
            modules = jsonToSet(root.optJSONArray("modules")),
            detectedRom = jsonToSet(root.optJSONArray("detectedRom")),
            detectedRoot = jsonToSet(root.optJSONArray("detectedRoot")),
            templates = templates,
            observerConfigs = observerConfigs,
        )
    }

    private fun loadSnapshotLegacy(svc: IYasvaraAppVisibility): TabirSnapshot {
        val templates = svc.templateNames.map { name ->
            TabirTemplate(
                name = name,
                mode = svc.getTemplateMode(name),
                packages = svc.getTemplatePackages(name).toSet(),
            )
        }
        val observerConfigs = svc.configuredObservers.associateWith { obs ->
            ObserverConfig(
                mode = svc.getObserverMode(obs),
                packages = svc.getObserverPackages(obs).toSet(),
                templates = svc.getObserverTemplates(obs).toSet(),
            )
        }
        return TabirSnapshot(
            available = true,
            scope = svc.scope,
            hideXposed = svc.hideXposed,
            hideRomPackages = svc.hideRomPackages,
            hideRootTooling = svc.hideRootTooling,
            devOptionsHideMode = svc.devOptionsHideMode,
            hidden = svc.getList(IYasvaraAppVisibility.LIST_HIDDEN).toSet(),
            observers = svc.getList(IYasvaraAppVisibility.LIST_OBSERVERS).toSet(),
            exceptions = svc.getList(IYasvaraAppVisibility.LIST_EXCEPTIONS).toSet(),
            modules = svc.xposedModules.toSet(),
            detectedRom = svc.detectedRomPackages.toSet(),
            detectedRoot = svc.detectedRootPackages.toSet(),
            templates = templates,
            observerConfigs = observerConfigs,
        )
    }

    /** Single-IPC batch View-As evaluation for [observer]. */
    fun evaluateObserver(observer: String): ObserverEvalResult {
        val svc = service() ?: return ObserverEvalResult(observer)
        return try {
            val raw = svc.evaluateObserverJson(observer)
            if (raw.isNullOrBlank() || raw == "{}") {
                return ObserverEvalResult(observer)
            }
            val obj = JSONObject(raw)
            val resObj = obj.optJSONObject("results")
            val results = LinkedHashMap<String, Boolean>()
            if (resObj != null) {
                val keys = resObj.keys()
                while (keys.hasNext()) {
                    val pkg = keys.next()
                    results[pkg] = resObj.optBoolean(pkg, false)
                }
            }
            ObserverEvalResult(
                observer = observer,
                mode = obj.optInt("mode", IYasvaraAppVisibility.MODE_GLOBAL),
                devHidden = obj.optBoolean("devHidden", false),
                results = results,
            )
        } catch (_: Throwable) {
            ObserverEvalResult(observer)
        }
    }

    fun setScope(scope: Int) = call(Unit) { it.setScope(scope) }

    fun setHideXposed(hide: Boolean) = call(Unit) { it.setHideXposed(hide) }

    fun setHideRomPackages(hide: Boolean) = call(Unit) { it.setHideRomPackages(hide) }

    fun setHideRootTooling(hide: Boolean) = call(Unit) { it.setHideRootTooling(hide) }

    fun setDevOptionsHideMode(mode: Int) = call(Unit) { it.setDevOptionsHideMode(mode) }

    fun setListed(list: Int, packages: Collection<String>, listed: Boolean) =
        call(Unit) { it.setListed(list, packages.toTypedArray(), listed) }

    fun putTemplate(name: String, mode: Int, packages: Collection<String>) =
        call(Unit) { it.putTemplate(name, mode, packages.toTypedArray()) }

    fun setTemplateWithAppliedApps(
        oldName: String?,
        newName: String,
        mode: Int,
        targetPackages: Collection<String>,
        appliedObservers: Collection<String>,
    ) = call(Unit) {
        it.setTemplateWithAppliedApps(
            oldName,
            newName,
            mode,
            targetPackages.toTypedArray(),
            appliedObservers.toTypedArray(),
        )
    }

    fun deleteTemplate(name: String) = call(Unit) { it.deleteTemplate(name) }

    fun setObserverConfig(
        observer: String,
        mode: Int,
        packages: Collection<String>,
        templates: Collection<String>,
    ) = call(Unit) {
        it.setObserverConfig(
            observer,
            mode,
            packages.toTypedArray(),
            templates.toTypedArray(),
        )
    }

    fun setBulkObserverConfig(
        observers: Collection<String>,
        mode: Int,
        packages: Collection<String>,
        templates: Collection<String>,
    ) = call(Unit) {
        it.setBulkObserverConfig(
            observers.toTypedArray(),
            mode,
            packages.toTypedArray(),
            templates.toTypedArray(),
        )
    }

    fun clearObserverConfig(observer: String) = call(Unit) { it.clearObserverConfig(observer) }

    fun forceStopHiddenTargets(): Int = call(0) { it.forceStopHiddenTargets() }

    fun forceStopPackage(packageName: String): Boolean = call(false) { it.forceStopPackage(packageName) }

    fun resetObserverRiskCache(packageName: String): Boolean =
        call(false) { it.resetObserverRiskCache(packageName) }

    fun exportConfigJson(): String = call("{}") { it.exportConfigJson() }

    fun importConfigJson(json: String): Boolean = call(false) { it.importConfigJson(json) }

    fun userApps(): List<UserApp> {
        val pm = context.packageManager
        return pm.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { UserApp(it.packageName, it.loadLabel(pm).toString(), isSystem = false) }
            .sortedBy { it.label.lowercase() }
    }

    fun resolveApps(packages: Collection<String>): List<UserApp> {
        if (packages.isEmpty()) return emptyList()
        val pm = context.packageManager
        return packages.map { pkg ->
            runCatching {
                val ai = pm.getApplicationInfo(pkg, 0)
                UserApp(
                    packageName = pkg,
                    label = ai.loadLabel(pm).toString(),
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }.getOrElse {
                UserApp(packageName = pkg, label = pkg, isSystem = false)
            }
        }.sortedBy { it.label.lowercase() }
    }

    private fun jsonToSet(arr: JSONArray?): Set<String> {
        if (arr == null || arr.length() == 0) return emptySet()
        val out = LinkedHashSet<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotEmpty()) out += s
        }
        return out
    }

    private inline fun <T> call(fallback: T, block: (IYasvaraAppVisibility) -> T): T = try {
        service()?.let(block) ?: fallback
    } catch (_: RemoteException) {
        fallback
    }

    private companion object {
        const val SERVICE = "yasvara_app_visibility"
    }
}
