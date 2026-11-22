/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.keyboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.yasvara.laras.data.ketik.FlagState
import org.yasvara.laras.data.ketik.KetikFeature
import org.yasvara.laras.data.ketik.KetikRepository
import org.yasvara.laras.data.ketik.isOn

/** What Gboard says about an enabled feature. */
enum class FeatureState { ACTIVE, WAITING, MISSING, NOT_APPLIED }

data class KeyboardUi(
    val loading: Boolean = true,
    val available: Boolean = true,
    val installed: Boolean = true,
    val on: Set<KetikFeature> = emptySet(),
    val states: Map<KetikFeature, FeatureState> = emptyMap(),
    /** Flags the crash guard removed; empty when it has not stepped in. */
    val reverted: List<String> = emptyList(),
)

/** The stored overrides drive the switches; Gboard's own report drives the state lines. */
class KeyboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = KetikRepository(app)
    private val writes = Mutex()
    private val _state = MutableStateFlow(KeyboardUi())
    val state: StateFlow<KeyboardUi> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Toggles queue up: each starts from what the service stored, never from a stale screen. */
    fun setEnabled(feature: KetikFeature, enabled: Boolean) {
        _state.update {
            it.copy(on = if (enabled) it.on + feature else it.on - feature, states = it.states - feature)
        }
        viewModelScope.launch {
            writes.withLock {
                val stored = repository.overrides()
                    ?.let { b -> KetikFeature.entries.filter { it.isOn(b) }.toSet() }.orEmpty()
                repository.setFeatures(if (enabled) stored + feature else stored - feature)
            }
            // Gboard applies the change asynchronously and redraws, so look a moment later.
            delay(SETTLE_MS)
            load()
        }
    }

    fun dismissRevert() {
        _state.update { it.copy(reverted = emptyList()) }
        viewModelScope.launch { repository.clearLastRevert() }
    }

    private suspend fun load() {
        writes.withLock {
            val overrides = repository.overrides()
            val on = overrides?.let { b -> KetikFeature.entries.filter { it.isOn(b) }.toSet() }.orEmpty()
            val names = on.flatMap { it.flags.keys }
            val flags = if (names.isEmpty()) emptyMap() else repository.flagStates(names)
            _state.value = KeyboardUi(
                loading = false,
                available = overrides != null,
                installed = repository.gboardInstalled(),
                on = on,
                states = on.associateWith { stateOf(it, flags) },
                reverted = repository.lastRevert(),
            )
        }
    }

    private fun stateOf(feature: KetikFeature, flags: Map<String, FlagState>): FeatureState {
        if (flags.isEmpty()) return FeatureState.WAITING
        val rows = feature.flags.map { (name, want) -> flags[name] to want.toString() }
        return when {
            rows.any { (state, _) -> state == null } -> FeatureState.MISSING
            rows.all { (state, want) -> state!!.fromUs && state.value == want } -> FeatureState.ACTIVE
            else -> FeatureState.NOT_APPLIED
        }
    }

    private companion object {
        const val SETTLE_MS = 1500L
    }
}
