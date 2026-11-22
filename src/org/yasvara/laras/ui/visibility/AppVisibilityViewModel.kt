/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.visibility

import android.app.Application
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
    val viewAsDevHidden: Boolean = false,
    /** Target package to whether it is hidden from [viewAs]. */
    val viewAsResult: Map<String, Boolean> = emptyMap(),
) {
    val callersList
        get() = if (scope == Rules.SCOPE_ALL_USER_APPS) Rules.LIST_EXCEPTIONS else Rules.LIST_OBSERVERS

    val hasSuggestion
        get() = suggestedDetectors.isNotEmpty() &&
            (suggestedTargets.isNotEmpty() || detectedRom.isNotEmpty() || detectedRoot.isNotEmpty() || modules.isNotEmpty())

    fun label(pkg: String) = labels[pkg] ?: pkg
}

/** Every change goes to system_server at once and applies without a restart. */
class AppVisibilityViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AppVisibilityRepository(app)
    private val _state = MutableStateFlow(AppVisibilityUi())
    val state: StateFlow<AppVisibilityUi> = _state.asStateFlow()

    init {
        reload()
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

    fun saveTemplate(name: String, mode: Int, packages: Set<String>) =
        edit { repository.putTemplate(name, mode, packages) }

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

    fun clearObserverConfig(observer: String) =
        edit { repository.clearObserverConfig(observer) }

    fun viewAs(pkg: String?) {
        _state.value = _state.value.copy(viewAs = pkg)
        reload()
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
            load()
            withContext(Dispatchers.Main) { onDone(count) }
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
            load()
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    private fun edit(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            load()
        }
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) { load() }
    }

    private fun load() {
        val scope = repository.scope
        val hideXposed = repository.hideXposed
        val hideRom = repository.hideRomPackages
        val hideRoot = repository.hideRootTooling
        val devMode = repository.devOptionsHideMode

        val hidden = repository.list(Rules.LIST_HIDDEN)
        val observers = repository.list(Rules.LIST_OBSERVERS)
        val callers = repository.list(
            if (scope == Rules.SCOPE_ALL_USER_APPS) Rules.LIST_EXCEPTIONS else Rules.LIST_OBSERVERS,
        )
        val modules = repository.modules()
        val detectedRom = repository.detectedRomPackages()
        val detectedRoot = repository.detectedRootPackages()
        val templates = repository.templates()
        val observerConfigs = repository.observerConfigs()

        val apps = repository.userApps()
        val romApps = repository.resolveApps(detectedRom)
        val extraKnown = hidden + callers + observerConfigs.keys +
            observerConfigs.values.flatMap { it.packages } +
            templates.flatMap { it.packages }
        val extraResolved = repository.resolveApps(extraKnown - apps.map { it.packageName }.toSet() - detectedRom)

        val labels = HashMap<String, String>(apps.size + romApps.size + extraResolved.size)
        for (a in apps) labels[a.packageName] = a.label
        for (a in romApps) labels[a.packageName] = a.label
        for (a in extraResolved) labels[a.packageName] = a.label

        val installedUser = apps.map { it.packageName }
        val configuredObservers = observers + observerConfigs.keys

        val viewAs = _state.value.viewAs
        val candidateTargets = LinkedHashSet<String>().apply {
            addAll(hidden)
            addAll(modules)
            addAll(detectedRoot)
            addAll(detectedRom)
            if (viewAs != null) {
                val obsRule = observerConfigs[viewAs]
                if (obsRule != null) {
                    addAll(obsRule.packages)
                    if (obsRule.mode == Rules.MODE_WHITELIST) {
                        addAll(installedUser)
                    }
                }
            }
        }

        val viewAsResult = viewAs?.let { observer ->
            candidateTargets
                .filter { it != observer && it != "android" }
                .sortedBy { (labels[it] ?: it).lowercase() }
                .associateWith { target -> repository.isHiddenFrom(target, observer) }
        }.orEmpty()

        val viewAsDevHidden = viewAs?.let { repository.isDevOptionsHiddenFrom(it) } ?: false

        _state.value = _state.value.copy(
            loading = false,
            available = repository.available,
            scope = scope,
            hideXposed = hideXposed,
            hideRomPackages = hideRom,
            hideRootTooling = hideRoot,
            devOptionsHideMode = devMode,
            hidden = hidden,
            callers = callers,
            modules = modules,
            detectedRom = detectedRom,
            detectedRoot = detectedRoot,
            templates = templates,
            observerConfigs = observerConfigs,
            apps = apps,
            romApps = romApps,
            labels = labels,
            suggestedTargets = installedUser.filter {
                VisibilitySuggestions.isTarget(it) && it !in hidden && !hideRoot
            },
            suggestedDetectors = installedUser.filter {
                VisibilitySuggestions.isDetector(it) && it !in configuredObservers
            },
            viewAsDevHidden = viewAsDevHidden,
            viewAsResult = viewAsResult,
        )
    }
}
