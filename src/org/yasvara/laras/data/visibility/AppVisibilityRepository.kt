/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.visibility

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.RemoteException
import android.os.ServiceManager
import com.android.server.yasvara.visibility.IYasvaraAppVisibility

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
    } catch (e: RuntimeException) {
        null
    }

    val available get() = service() != null

    val scope get() = call(IYasvaraAppVisibility.SCOPE_SELECTED) { it.scope }

    fun setScope(scope: Int) = call(Unit) { it.setScope(scope) }

    val hideXposed get() = call(true) { it.hideXposed }

    fun setHideXposed(hide: Boolean) = call(Unit) { it.setHideXposed(hide) }

    val hideRomPackages get() = call(true) { it.hideRomPackages }

    fun setHideRomPackages(hide: Boolean) = call(Unit) { it.setHideRomPackages(hide) }

    val hideRootTooling get() = call(true) { it.hideRootTooling }

    fun setHideRootTooling(hide: Boolean) = call(Unit) { it.setHideRootTooling(hide) }

    val devOptionsHideMode
        get() = call(IYasvaraAppVisibility.DEV_HIDE_ALL_NON_SYSTEM) { it.devOptionsHideMode }

    fun setDevOptionsHideMode(mode: Int) = call(Unit) { it.setDevOptionsHideMode(mode) }

    fun list(list: Int): Set<String> = call(emptySet()) { it.getList(list).toSet() }

    fun setListed(list: Int, packages: Collection<String>, listed: Boolean) =
        call(Unit) { it.setListed(list, packages.toTypedArray(), listed) }

    fun modules(): Set<String> = call(emptySet()) { it.xposedModules.toSet() }

    fun detectedRomPackages(): Set<String> = call(emptySet()) { it.detectedRomPackages.toSet() }

    fun detectedRootPackages(): Set<String> = call(emptySet()) { it.detectedRootPackages.toSet() }

    fun templates(): List<TabirTemplate> = call(emptyList()) { svc ->
        svc.templateNames.map { name ->
            TabirTemplate(
                name = name,
                mode = svc.getTemplateMode(name),
                packages = svc.getTemplatePackages(name).toSet(),
            )
        }
    }

    fun putTemplate(name: String, mode: Int, packages: Collection<String>) =
        call(Unit) { it.putTemplate(name, mode, packages.toTypedArray()) }

    fun deleteTemplate(name: String) = call(Unit) { it.deleteTemplate(name) }

    fun observerConfigs(): Map<String, ObserverConfig> = call(emptyMap()) { svc ->
        svc.configuredObservers.associateWith { obs ->
            ObserverConfig(
                mode = svc.getObserverMode(obs),
                packages = svc.getObserverPackages(obs).toSet(),
                templates = svc.getObserverTemplates(obs).toSet(),
            )
        }
    }

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

    fun clearObserverConfig(observer: String) = call(Unit) { it.clearObserverConfig(observer) }

    fun isHiddenFrom(target: String, observer: String) =
        call(false) { it.isHiddenFrom(target, observer) }

    fun isDevOptionsHiddenFrom(observer: String) =
        call(false) { it.isDevOptionsHiddenFrom(observer) }

    fun forceStopHiddenTargets(): Int = call(0) { it.forceStopHiddenTargets() }

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

    private inline fun <T> call(fallback: T, block: (IYasvaraAppVisibility) -> T): T = try {
        service()?.let(block) ?: fallback
    } catch (e: RemoteException) {
        fallback
    }

    private companion object {
        const val SERVICE = "yasvara_app_visibility"
    }
}
