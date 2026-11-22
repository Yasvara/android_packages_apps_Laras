/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.kait

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yasvara.laras.data.kait.InjectedProcess
import org.yasvara.laras.data.kait.KaitModule
import org.yasvara.laras.data.kait.KaitOnlineRepository
import org.yasvara.laras.data.kait.KaitRepository
import org.yasvara.laras.data.kait.KaitShortcuts
import org.yasvara.laras.data.kait.OnlineCatalog
import org.yasvara.laras.data.kait.OnlineModule
import org.yasvara.laras.data.kait.RepoVersion
import org.yasvara.laras.data.kait.ScopeApp

enum class ModuleListFilter { ALL, ENABLED, DISABLED, UPDATES }

enum class RepoListFilter { ALL, INSTALLED, UPDATES }

enum class RepoSortOrder { UPDATED, STARS, NAME }

enum class ScopeListFilter { ALL, SELECTED, RECOMMENDED, RUNNING }

data class KaitUi(
    val loading: Boolean = true,
    val available: Boolean = true,
    val frameworkVersion: String = "Kait 2.0",
    val modules: List<KaitModule> = emptyList(),
    val injected: Set<String> = emptySet(),
    val injectedProcesses: List<InjectedProcess> = emptyList(),
    val crashBypassed: List<String> = emptyList(),
    val pinSupported: Boolean = false,
    val shortcutPinned: Boolean = false,
    val drawerEnabled: Boolean = false,
    val onlineCatalog: OnlineCatalog = OnlineCatalog(),
    val repoRefreshing: Boolean = false,
    val installedVersions: Map<String, RepoVersion> = emptyMap(),
    val selectedOnlineModule: OnlineModule? = null,
    val detailLoading: Boolean = false,
) {
    /** Map of `packageName -> OnlineModule` for quick update badges on the installed modules tab. */
    val onlineByPackage: Map<String, OnlineModule>
        get() = onlineCatalog.modules.associateBy { it.packageName }

    fun hasUpdate(module: KaitModule): Boolean {
        val online = onlineByPackage[module.packageName]?.latestVersion ?: return false
        return online.upgradableOver(module.versionCode, module.versionName)
    }

    fun hasRepoUpdate(online: OnlineModule): Boolean {
        val inst = installedVersions[online.packageName] ?: return false
        val latest = online.latestVersion ?: return false
        return latest.upgradableOver(inst.versionCode, inst.versionName)
    }

    val upgradableModulesCount: Int
        get() = modules.count { hasUpdate(it) }
}

class ModulesViewModel(private val app: Application) : AndroidViewModel(app) {
    val repository = KaitRepository(app)
    private val onlineRepository = KaitOnlineRepository(app)
    private val _state = MutableStateFlow(
        KaitUi(
            pinSupported = KaitShortcuts.isPinSupported(app),
            shortcutPinned = KaitShortcuts.isPinned(app),
            drawerEnabled = KaitShortcuts.isDrawerEnabled(app),
        )
    )
    val state: StateFlow<KaitUi> = _state.asStateFlow()

    init {
        refresh(loadRepo = true)
    }

    fun refresh(loadRepo: Boolean = false, forceRepoNetwork: Boolean = false) {
        viewModelScope.launch {
            val fwVer = repository.frameworkVersion()
            val modules = repository.modules()
            val procs = repository.injectedProcesses()
            val injected = procs.map { it.packageName }.toSet()
            val bypassed = repository.crashBypassed()
            val installedVers = repository.installedPackageVersions()
            _state.update {
                it.copy(
                    loading = false,
                    available = modules != null,
                    frameworkVersion = fwVer,
                    modules = modules.orEmpty(),
                    injected = injected,
                    injectedProcesses = procs,
                    crashBypassed = bypassed,
                    installedVersions = installedVers,
                    pinSupported = KaitShortcuts.isPinSupported(app),
                    shortcutPinned = KaitShortcuts.isPinned(app),
                    drawerEnabled = KaitShortcuts.isDrawerEnabled(app),
                )
            }
            if (loadRepo || forceRepoNetwork || !_state.value.onlineCatalog.loaded) {
                refreshOnlineRepo(forceNetwork = forceRepoNetwork)
            }
        }
    }

    fun refreshOnlineRepo(forceNetwork: Boolean = true) {
        viewModelScope.launch {
            _state.update { it.copy(repoRefreshing = true) }
            val catalog = onlineRepository.fetchCatalog(forceNetwork = forceNetwork)
            val installedVers = repository.installedPackageVersions()
            _state.update {
                it.copy(
                    onlineCatalog = catalog,
                    repoRefreshing = false,
                    installedVersions = installedVers,
                )
            }
        }
    }

    fun openOnlineModuleDetail(module: OnlineModule) {
        _state.update { it.copy(selectedOnlineModule = module, detailLoading = true) }
        viewModelScope.launch {
            val full = onlineRepository.fetchModuleDetail(module.packageName, module)
            _state.update {
                if (it.selectedOnlineModule?.packageName == module.packageName) {
                    it.copy(selectedOnlineModule = full ?: module, detailLoading = false)
                } else {
                    it
                }
            }
        }
    }

    fun dismissOnlineModuleDetail() {
        _state.update { it.copy(selectedOnlineModule = null, detailLoading = false) }
    }

    fun setEnabled(module: String, enabled: Boolean) {
        _state.update { ui ->
            ui.copy(modules = ui.modules.map { if (it.packageName == module) it.copy(enabled = enabled) else it })
        }
        viewModelScope.launch {
            if (!repository.setEnabled(module, enabled)) refresh()
            else refresh()
        }
    }

    fun clearCrashBypass(pkg: String) {
        viewModelScope.launch {
            repository.clearCrashBypass(pkg)
            refresh()
        }
    }

    fun restartPackage(pkg: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.restart(pkg)
            refresh()
            onDone(ok)
        }
    }

    fun restartTargetsOf(module: KaitModule, onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val targets = (module.scope + module.packageName).distinct()
            val count = repository.restartAll(targets)
            refresh()
            onDone(count)
        }
    }

    fun refreshShortcutState() {
        _state.update {
            it.copy(
                pinSupported = KaitShortcuts.isPinSupported(app),
                shortcutPinned = KaitShortcuts.isPinned(app),
                drawerEnabled = KaitShortcuts.isDrawerEnabled(app),
            )
        }
    }

    fun setDrawerEnabled(enabled: Boolean) {
        KaitShortcuts.setDrawerEnabled(app, enabled)
        refreshShortcutState()
    }
}

data class ScopeUi(
    val loading: Boolean = true,
    val available: Boolean = true,
    val module: KaitModule? = null,
    val apps: List<ScopeApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val recommended: Set<String> = emptySet(),
    val runningPackages: Set<String> = emptySet(),
    val query: String = "",
    val filter: ScopeListFilter = ScopeListFilter.ALL,
    val showSystem: Boolean = false,
) {
    val visible: List<ScopeApp>
        get() = apps.filter { app ->
            val matchesQuery =
                query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            val matchesFilter = when (filter) {
                ScopeListFilter.ALL -> true
                ScopeListFilter.SELECTED -> app.packageName in selected
                ScopeListFilter.RECOMMENDED -> app.packageName in recommended
                ScopeListFilter.RUNNING -> app.packageName in runningPackages
            }
            matchesQuery && matchesFilter
        }
}

class ScopeViewModel(private val app: Application, private val moduleName: String) : AndroidViewModel(app) {
    val repository = KaitRepository(app)
    private val onlineRepository = KaitOnlineRepository(app)
    private val _state = MutableStateFlow(ScopeUi())
    val state: StateFlow<ScopeUi> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val module = repository.module(moduleName)
            val running = repository.injected()
            // Merge APK's recommended scope with any cached online repository scope for this module.
            val onlineRec = runCatching {
                onlineRepository.fetchCatalog(forceNetwork = false)
                    .modules.find { it.packageName == moduleName }?.scope.orEmpty()
            }.getOrDefault(emptyList())
            val recSet = (module?.recommended.orEmpty() + onlineRec).filter { it.isNotBlank() }.toSet()

            _state.update {
                it.copy(
                    available = module != null,
                    module = module,
                    selected = module?.scope.orEmpty().toSet(),
                    recommended = recSet,
                    runningPackages = running,
                )
            }
            loadApps()
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setFilter(filter: ScopeListFilter) = _state.update { it.copy(filter = filter) }

    fun setShowSystem(show: Boolean) {
        _state.update { it.copy(showSystem = show) }
        viewModelScope.launch { loadApps() }
    }

    fun toggle(pkg: String, on: Boolean) =
        commit(_state.value.selected.let { if (on) it + pkg else it - pkg })

    fun addRecommended() = commit(_state.value.selected + recommendedInstalled())

    fun clear() = commit(emptySet())

    fun recommendedInstalled(): Set<String> {
        val installed = _state.value.apps.map { it.packageName }.toSet()
        return _state.value.recommended.filter { it in installed }.toSet()
    }

    fun restartScopedApps(onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val pkgs = (_state.value.selected + moduleName).toList()
            val count = repository.restartAll(pkgs)
            val running = repository.injected()
            _state.update { it.copy(runningPackages = running) }
            onDone(count)
        }
    }

    private fun commit(selected: Set<String>) {
        _state.update { it.copy(selected = selected) }
        viewModelScope.launch {
            if (!repository.setScope(moduleName, selected)) {
                _state.update { it.copy(available = false) }
            } else {
                val running = repository.injected()
                _state.update { it.copy(runningPackages = running) }
            }
        }
    }

    private suspend fun loadApps() {
        val ui = _state.value
        val pinned = ui.selected + ui.recommended
        val apps = repository.apps(ui.showSystem, pinned)
        _state.update { it.copy(loading = false, apps = apps) }
    }
}
