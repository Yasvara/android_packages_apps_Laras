/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.visibility

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.server.yasvara.visibility.IYasvaraAppVisibility as Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.data.visibility.AppVisibilityRepository
import org.yasvara.laras.data.visibility.ObserverConfig
import org.yasvara.laras.data.visibility.TabirTemplate
import org.yasvara.laras.data.visibility.UserApp
import org.yasvara.laras.data.visibility.VisibilitySuggestions

data class AppVisibilityUi(
    val loading: Boolean = true,
    val available: Boolean = true,
    val scope: Int = Rules.SCOPE_SELECTED,
    val hideXposed: Boolean = true,
    val hideRomPackages: Boolean = true,
    val hideRootTooling: Boolean = true,
    val devOptionsHideMode: Int = Rules.DEV_HIDE_ALL_NON_SYSTEM,
    val hidden: Set<String> = emptySet(),
    val observers: Set<String> = emptySet(),
    val exceptions: Set<String> = emptySet(),
    val callers: Set<String> = emptySet(),
    val modules: Set<String> = emptySet(),
    val detectedRom: Set<String> = emptySet(),
    val detectedRoot: Set<String> = emptySet(),
    val templates: List<TabirTemplate> = emptyList(),
    val observerConfigs: Map<String, ObserverConfig> = emptyMap(),
    val apps: List<UserApp> = emptyList(),
    val romApps: List<UserApp> = emptyList(),
    val labels: Map<String, String> = emptyMap(),
    val suggestedTargets: List<String> = emptyList(),
    val suggestedDetectors: List<String> = emptyList(),
    val viewAs: String? = null,
    val viewAsMode: Int = Rules.MODE_GLOBAL,
    val viewAsDevHidden: Boolean = false,
    /** Target package to whether it is hidden from [viewAs]. */
    val viewAsResult: Map<String, Boolean> = emptyMap(),
) {
    val callersList
        get() = if (scope == Rules.SCOPE_ALL_USER_APPS) Rules.LIST_EXCEPTIONS else Rules.LIST_OBSERVERS

    val hasSuggestion
        get() = suggestedDetectors.isNotEmpty() &&
            (suggestedTargets.isNotEmpty() || detectedRom.isNotEmpty() || detectedRoot.isNotEmpty() || modules.isNotEmpty())

    /** Count of apps actively shielded by Tabir (per-app overrides + global observers). */
    val protectedAppCount: Int
        get() = if (scope == Rules.SCOPE_ALL_USER_APPS) {
            val nonExemptUser = apps.count { it.packageName !in exceptions }
            maxOf(nonExemptUser, observerConfigs.size)
        } else {
            (observers + observerConfigs.keys).size
        }

    /** Total distinct targets hidden globally (manual + Xposed + root tooling + ROM packages). */
    val totalHiddenTargetCount: Int
        get() = buildSet {
            addAll(hidden)
            if (hideXposed) addAll(modules)
            if (hideRootTooling) addAll(detectedRoot)
            if (hideRomPackages) addAll(detectedRom)
        }.size

    val isShieldActive: Boolean
        get() = available && (
            observerConfigs.isNotEmpty() ||
                totalHiddenTargetCount > 0 && (scope == Rules.SCOPE_ALL_USER_APPS || observers.isNotEmpty()) ||
                devOptionsHideMode != Rules.DEV_HIDE_OFF
            )

    fun isProtectedApp(pkg: String): Boolean {
        if (pkg in observerConfigs) return true
        return if (scope == Rules.SCOPE_ALL_USER_APPS) {
            pkg !in exceptions && totalHiddenTargetCount > 0
        } else {
            pkg in observers
        }
    }

    fun appliedObserversForTemplate(templateName: String): Set<String> =
        observerConfigs.filterValues { templateName in it.templates }.keys

    fun label(pkg: String) = labels[pkg] ?: pkg
}

/**
 * Unified Tabir v2 ViewModel.
 *
 * Uses single-IPC snapshot + cached PM app metadata so toggling any setting or editing rules
 * requires 1 Binder round-trip and zero full PackageManager re-scans.
 */
class AppVisibilityViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repository = AppVisibilityRepository(app)
    private val _state = MutableStateFlow(AppVisibilityUi())
    val state: StateFlow<AppVisibilityUi> = _state.asStateFlow()

    @Volatile
    private var cachedUserApps: List<UserApp>? = null
    @Volatile
    private var cachedRomAppsKey: Set<String> = emptySet()
    @Volatile
    private var cachedRomApps: List<UserApp> = emptyList()

    init {
        refresh(reloadApps = true)
    }

    fun refresh(reloadApps: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) { load(reloadApps = reloadApps) }
    }

    fun setScope(scope: Int) = edit { repository.setScope(scope) }

    fun setHideXposed(hide: Boolean) = edit { repository.setHideXposed(hide) }

    fun setHideRomPackages(hide: Boolean) = edit { repository.setHideRomPackages(hide) }

    fun setHideRootTooling(hide: Boolean) = edit { repository.setHideRootTooling(hide) }

    fun setDevOptionsHideMode(mode: Int) = edit { repository.setDevOptionsHideMode(mode) }

    fun setHidden(pkg: String, hidden: Boolean) =
        edit { repository.setListed(Rules.LIST_HIDDEN, listOf(pkg), hidden) }

    fun setCaller(pkg: String, listed: Boolean) =
        edit { repository.setListed(_state.value.callersList, listOf(pkg), listed) }

    fun saveTemplateWithApplied(
        oldName: String?,
        newName: String,
        mode: Int,
        targetPackages: Set<String>,
        appliedObservers: Set<String>,
    ) = edit {
        repository.setTemplateWithAppliedApps(
            oldName = oldName,
            newName = newName,
            mode = mode,
            targetPackages = targetPackages,
            appliedObservers = appliedObservers,
        )
    }

    fun deleteTemplate(name: String) =
        edit { repository.deleteTemplate(name) }

    fun saveObserverConfig(
        observer: String,
        mode: Int,
        packages: Set<String>,
        templates: Set<String>,
    ) = edit {
        if (mode == Rules.MODE_GLOBAL) {
            repository.clearObserverConfig(observer)
        } else {
            repository.setObserverConfig(observer, mode, packages, templates)
        }
    }

    fun applyBulkObserverConfig(
        observers: Set<String>,
        mode: Int,
        packages: Set<String>,
        templates: Set<String>,
    ) = edit {
        repository.setBulkObserverConfig(observers, mode, packages, templates)
    }

    fun clearObserverConfig(observer: String) =
        edit { repository.clearObserverConfig(observer) }

    fun viewAs(pkg: String?) {
        _state.value = _state.value.copy(viewAs = pkg)
        if (pkg == null) {
            _state.value = _state.value.copy(
                viewAsMode = Rules.MODE_GLOBAL,
                viewAsDevHidden = false,
                viewAsResult = emptyMap(),
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            evaluateViewAs(pkg)
        }
    }

    fun applySuggestion() {
        val ui = _state.value
        edit {
            if (ui.suggestedTargets.isNotEmpty()) {
                repository.setListed(Rules.LIST_HIDDEN, ui.suggestedTargets, true)
            }
            if (ui.suggestedDetectors.isNotEmpty()) {
                repository.setListed(Rules.LIST_OBSERVERS, ui.suggestedDetectors, true)
            }
        }
    }

    fun forceStopHidden(onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.forceStopHiddenTargets()
            withContext(Dispatchers.Main) { onDone(count) }
        }
    }

    fun forceStopAndLaunch(pkg: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.forceStopPackage(pkg)
            val launch = app.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = if (launch != null) {
                runCatching {
                    app.startActivity(launch)
                    true
                }.getOrDefault(false)
            } else {
                false
            }
            withContext(Dispatchers.Main) { onDone(started) }
        }
    }

    fun resetObserverRiskCache(pkg: String, andLaunch: Boolean = false, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.resetObserverRiskCache(pkg)
            if (ok && andLaunch) {
                app.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }?.let { launch ->
                    runCatching { app.startActivity(launch) }
                }
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    fun exportJson(onReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val json = repository.exportConfigJson()
            withContext(Dispatchers.Main) { onReady(json) }
        }
    }

    fun importJson(json: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.importConfigJson(json)
            if (ok) load(reloadApps = false)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    private fun edit(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            load(reloadApps = false)
        }
    }

    private fun evaluateViewAs(observer: String) {
        val eval = repository.evaluateObserver(observer)
        val labels = _state.value.labels
        val sorted = eval.results.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Boolean>> { it.value }
                    .thenBy { (labels[it.key] ?: it.key).lowercase() },
            )
            .associate { it.key to it.value }
        _state.value = _state.value.copy(
            viewAs = observer,
            viewAsMode = eval.mode,
            viewAsDevHidden = eval.devHidden,
            viewAsResult = sorted,
        )
    }

    private fun load(reloadApps: Boolean) {
        val snap = repository.loadSnapshot()
        if (!snap.available) {
            _state.value = _state.value.copy(loading = false, available = false)
            return
        }

        val apps = if (reloadApps || cachedUserApps == null) {
            repository.userApps().also { cachedUserApps = it }
        } else {
            cachedUserApps!!
        }

        val romApps = if (reloadApps || snap.detectedRom != cachedRomAppsKey) {
            repository.resolveApps(snap.detectedRom).also {
                cachedRomAppsKey = snap.detectedRom
                cachedRomApps = it
            }
        } else {
            cachedRomApps
        }

        val callers = if (snap.scope == Rules.SCOPE_ALL_USER_APPS) snap.exceptions else snap.observers
        val userPkgSet = apps.mapTo(HashSet(apps.size)) { it.packageName }
        val extraKnown = snap.hidden + callers + snap.observerConfigs.keys +
            snap.observerConfigs.values.flatMap { it.packages } +
            snap.templates.flatMap { it.packages }
        val unresolved = extraKnown - userPkgSet - snap.detectedRom
        val extraResolved = if (unresolved.isNotEmpty()) repository.resolveApps(unresolved) else emptyList()

        val labels = HashMap<String, String>(apps.size + romApps.size + extraResolved.size)
        for (a in apps) labels[a.packageName] = a.label
        for (a in romApps) labels[a.packageName] = a.label
        for (a in extraResolved) labels[a.packageName] = a.label

        val configuredObservers = snap.observers + snap.observerConfigs.keys
        val currentViewAs = _state.value.viewAs
        val eval = currentViewAs?.let { repository.evaluateObserver(it) }
        val viewAsSorted = eval?.results?.entries
            ?.sortedWith(
                compareByDescending<Map.Entry<String, Boolean>> { it.value }
                    .thenBy { (labels[it.key] ?: it.key).lowercase() },
            )
            ?.associate { it.key to it.value }
            .orEmpty()

        _state.value = _state.value.copy(
            loading = false,
            available = true,
            scope = snap.scope,
            hideXposed = snap.hideXposed,
            hideRomPackages = snap.hideRomPackages,
            hideRootTooling = snap.hideRootTooling,
            devOptionsHideMode = snap.devOptionsHideMode,
            hidden = snap.hidden,
            observers = snap.observers,
            exceptions = snap.exceptions,
            callers = callers,
            modules = snap.modules,
            detectedRom = snap.detectedRom,
            detectedRoot = snap.detectedRoot,
            templates = snap.templates,
            observerConfigs = snap.observerConfigs,
            apps = apps,
            romApps = romApps,
            labels = labels,
            suggestedTargets = apps.map { it.packageName }.filter {
                VisibilitySuggestions.isTarget(it) && it !in snap.hidden && !snap.hideRootTooling
            },
            suggestedDetectors = apps.map { it.packageName }.filter {
                VisibilitySuggestions.isDetector(it) && it !in configuredObservers
            },
            viewAsMode = eval?.mode ?: Rules.MODE_GLOBAL,
            viewAsDevHidden = eval?.devHidden ?: false,
            viewAsResult = viewAsSorted,
        )
    }
}
