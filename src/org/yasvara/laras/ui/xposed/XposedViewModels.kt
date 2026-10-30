/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.xposed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yasvara.laras.data.xposed.ScopeApp
import org.yasvara.laras.data.xposed.XposedModule
import org.yasvara.laras.data.xposed.XposedRepository

data class ModulesUi(
    val loading: Boolean = true,
    /** False when the runtime is off or refuses Laras. */
    val available: Boolean = true,
    val modules: List<XposedModule> = emptyList(),
    /** Packages running with the runtime loaded right now. */
    val injected: Set<String> = emptySet(),
)

/** The installed modules and their on/off state; re-read whenever the screen comes back. */
class ModulesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = XposedRepository(app)
    private val _state = MutableStateFlow(ModulesUi())
    val state: StateFlow<ModulesUi> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val modules = repository.modules()
            val injected = repository.injected()
            _state.update {
                it.copy(loading = false, available = modules != null, modules = modules.orEmpty(), injected = injected)
            }
        }
    }

    fun setEnabled(module: String, enabled: Boolean) {
        _state.update { ui ->
            ui.copy(modules = ui.modules.map { if (it.packageName == module) it.copy(enabled = enabled) else it })
        }
        viewModelScope.launch {
            if (!repository.setEnabled(module, enabled)) refresh()
        }
    }
}

data class ScopeUi(
    val loading: Boolean = true,
    val available: Boolean = true,
    val module: XposedModule? = null,
    val apps: List<ScopeApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val showSystem: Boolean = false,
) {
    val visible: List<ScopeApp>
        get() = apps.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
}

/**
 * One module's scope. Every change is saved at once; system_server force-stops the apps it adds
 * or removes, so they pick the change up on their next start.
 */
class ScopeViewModel(app: Application, private val moduleName: String) : AndroidViewModel(app) {
    private val repository = XposedRepository(app)
    private val _state = MutableStateFlow(ScopeUi())
    val state: StateFlow<ScopeUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val module = repository.module(moduleName)
            _state.update {
                it.copy(available = module != null, module = module, selected = module?.scope.orEmpty().toSet())
            }
            loadApps()
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setShowSystem(show: Boolean) {
        _state.update { it.copy(showSystem = show) }
        viewModelScope.launch { loadApps() }
    }

    fun toggle(pkg: String, on: Boolean) =
        commit(_state.value.selected.let { if (on) it + pkg else it - pkg })

    /** Adds what the module's author recommends, keeping everything already chosen. */
    fun addRecommended() = commit(_state.value.selected + recommendedInstalled())

    fun clear() = commit(emptySet())

    fun recommendedInstalled(): Set<String> {
        val installed = _state.value.apps.map { it.packageName }.toSet()
        return _state.value.module?.recommended.orEmpty().filter { it in installed }.toSet()
    }

    private fun commit(selected: Set<String>) {
        _state.update { it.copy(selected = selected) }
        viewModelScope.launch {
            if (!repository.setScope(moduleName, selected)) {
                _state.update { it.copy(available = false) }
            }
        }
    }

    private suspend fun loadApps() {
        val ui = _state.value
        val pinned = ui.selected + ui.module?.recommended.orEmpty()
        val apps = repository.apps(ui.showSystem, pinned)
        _state.update { it.copy(loading = false, apps = apps) }
    }
}
