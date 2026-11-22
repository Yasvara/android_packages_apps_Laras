/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.yasvara.laras.data.integrity.IntegrityKeys
import org.yasvara.laras.data.integrity.PlayServices
import org.yasvara.laras.data.integrity.RungReport
import org.yasvara.laras.data.integrity.RungStore
import org.yasvara.laras.data.integrity.SecureStore
import org.yasvara.laras.data.integrity.TargetApp
import org.yasvara.laras.data.integrity.TargetsRepository
import org.json.JSONObject

data class TargetAppsUi(
    val loading: Boolean = true,
    val apps: List<TargetApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val showSystem: Boolean = false,
    /** Last engine outcome per package; empty when the engine never served it. */
    val rungs: Map<String, RungReport> = emptyMap(),
    /** Debug override for the ladder; "" means auto. */
    val force: String = "",
    /** Per-package patch-date override ({package: YYYY-MM-DD}). */
    val patchOverrides: Map<String, String> = emptyMap(),
) {
    val visible: List<TargetApp>
        get() = apps.filter {
            query.isBlank() || it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
}

/** Picks the apps whose attestation is answered from the keybox; every change saves at once. */
class TargetAppsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app.contentResolver)
    private val repository = TargetsRepository(app, store)
    private val rungs = RungStore(store)
    private val _state = MutableStateFlow(TargetAppsUi(selected = repository.selected()))
    val state: StateFlow<TargetAppsUi> = _state.asStateFlow()

    init {
        load()
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setShowSystem(show: Boolean) {
        _state.update { it.copy(showSystem = show) }
        load()
    }

    fun toggle(packageName: String, on: Boolean) {
        if (packageName in TargetsRepository.REQUIRED) return
        commit(_state.value.selected.let { if (on) it + packageName else it - packageName })
    }

    /** Selects or clears everything currently shown, keeping the required apps on. */
    fun setAllVisible(on: Boolean) {
        val shown = _state.value.visible.map { it.packageName }.toSet()
        commit(_state.value.selected.let { if (on) it + shown else it - shown } + TargetsRepository.REQUIRED)
    }

    /** Selects the installed apps known to check attestation, without clearing anything. */
    fun autoSelect() = commit(_state.value.selected + repository.suggestions(_state.value.apps))

    /** Re-reads engine outcomes; call when returning to the screen. */
    fun refreshRungs() {
        _state.update {
            it.copy(
                rungs = rungs.all(),
                force = store.getString(IntegrityKeys.TS_FORCE),
                patchOverrides = readOverrides(),
            )
        }
    }

    /** Debug override for the ladder; empty means auto. */
    fun setForce(value: String) {
        _state.update { it.copy(force = value) }
        PlayServices.markRestartNeeded()
        viewModelScope.launch(Dispatchers.IO) { store.putString(IntegrityKeys.TS_FORCE, value) }
    }

    fun setPatchOverride(pkg: String, date: String) {
        val next = _state.value.patchOverrides.toMutableMap()
        if (date.isBlank()) next.remove(pkg) else next[pkg] = date.trim()
        _state.update { it.copy(patchOverrides = next) }
        viewModelScope.launch(Dispatchers.IO) {
            val o = JSONObject()
            for ((k, v) in next) o.put(k, v)
            store.putString(IntegrityKeys.TS_PATCH_OVERRIDE, o.toString())
            PlayServices.markRestartNeeded(autoHandledByService = true)
        }
    }

    fun reprobeTee() {
        PlayServices.markRestartNeeded()
        viewModelScope.launch(Dispatchers.IO) {
            store.putLong(IntegrityKeys.TS_TEE_REPROBE, System.currentTimeMillis())
        }
    }

    private fun readOverrides(): Map<String, String> {
        val raw = store.getString(IntegrityKeys.TS_PATCH_OVERRIDE)
        if (raw.isEmpty()) return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap {
                for (key in o.keys()) {
                    val v = o.optString(key)
                    if (v.isNotEmpty() && key != "*") put(key, v)
                }
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun commit(selected: Set<String>) {
        _state.update { it.copy(selected = selected) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.save(selected)
            PlayServices.markRestartNeeded(autoHandledByService = true)
        }
    }

    private fun load() {
        viewModelScope.launch {
            val ui = _state.value
            val apps = repository.installed(ui.showSystem, ui.selected)
            _state.update { it.copy(loading = false, apps = apps) }
        }
    }
}
