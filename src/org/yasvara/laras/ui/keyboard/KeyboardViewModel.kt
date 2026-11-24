/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.keyboard

import android.app.Application
import android.os.Bundle
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
import org.yasvara.laras.data.kait.KaitRepository
import org.yasvara.laras.data.ketik.FlagState
import org.yasvara.laras.data.ketik.GBOARD_PACKAGE
import org.yasvara.laras.data.ketik.KETIK_HOOK_PACKAGE
import org.yasvara.laras.data.ketik.KetikFeatureDef
import org.yasvara.laras.data.ketik.KetikRepository
import org.yasvara.laras.data.ketik.KetikRuntimeHealth

/** What Gboard says about an enabled feature, verified against both flags and runtime tags. */
enum class FeatureState { ACTIVE, BLOCKED, WAITING, MISSING, NOT_APPLIED }

/** Live state of the dynamic Kait companion module (`org.yasvara.ketik.hook`) inside Gboard. */
enum class HookBridgeState {
    UNAVAILABLE,
    NOT_INSTALLED,
    DISABLED,
    UNSCOPED,
    PENDING_RESTART,
    ACTIVE,
}

data class KeyboardUi(
    val loading: Boolean = true,
    val available: Boolean = true,
    val installed: Boolean = true,
    val features: List<KetikFeatureDef> = emptyList(),
    val on: Set<String> = emptySet(),
    val states: Map<String, FeatureState> = emptyMap(),
    val health: KetikRuntimeHealth = KetikRuntimeHealth(),
    val hookState: HookBridgeState = HookBridgeState.UNAVAILABLE,
    val hookBusy: Boolean = false,
    /** Flags the crash guard removed; empty when it has not stepped in. */
    val reverted: List<String> = emptyList(),
)

enum class FlagFilter { ALL, MODIFIED, AI_LAB, OFF }

data class FlagEditorUi(
    val loading: Boolean = true,
    val query: String = "",
    val filter: FlagFilter = FlagFilter.ALL,
    val flags: List<FlagState> = emptyList(),
    val overrides: Map<String, Any> = emptyMap(),
    val totalCount: Int = 0,
    val message: String? = null,
)

/** The stored overrides drive the switches; Gboard's live dump & Kait drive the state badges. */
class KeyboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = KetikRepository(app)
    private val kait = KaitRepository(app)
    private val writes = Mutex()

    private val _state = MutableStateFlow(KeyboardUi())
    val state: StateFlow<KeyboardUi> = _state.asStateFlow()

    private val _editor = MutableStateFlow(FlagEditorUi())
    val editor: StateFlow<FlagEditorUi> = _editor.asStateFlow()

    private var cachedLiveKeys: Set<String> = emptySet()

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Toggles queue up: each starts from what the service stored, never from a stale screen. */
    fun setEnabled(feature: KetikFeatureDef, enabled: Boolean) {
        _state.update { cur ->
            val nextOn = cur.on.toMutableSet()
            if (enabled) {
                if (feature.exclusiveGroup != null) {
                    cur.features
                        .filter { it.exclusiveGroup == feature.exclusiveGroup }
                        .forEach { nextOn.remove(it.id) }
                }
                nextOn.add(feature.id)
            } else {
                nextOn.remove(feature.id)
            }
            cur.copy(on = nextOn, states = cur.states - feature.id)
        }
        viewModelScope.launch {
            writes.withLock {
                val catalog = _state.value.features.ifEmpty { repository.catalog() }
                repository.setFeatureEnabled(feature, enabled, catalog, cachedLiveKeys)
            }
            delay(SETTLE_MS)
            load()
        }
    }

    /** One-tap enable + scope `org.yasvara.ketik.hook` to Gboard and restart Gboard cleanly. */
    fun activateHookBridge() {
        viewModelScope.launch {
            _state.update { it.copy(hookBusy = true) }
            try {
                val mod = kait.module(KETIK_HOOK_PACKAGE)
                if (mod != null) {
                    if (!mod.enabled) kait.setEnabled(KETIK_HOOK_PACKAGE, true)
                    if (GBOARD_PACKAGE !in mod.scope) {
                        kait.setScope(KETIK_HOOK_PACKAGE, mod.scope + GBOARD_PACKAGE)
                    }
                    kait.restart(GBOARD_PACKAGE)
                }
            } finally {
                delay(SETTLE_MS)
                _state.update { it.copy(hookBusy = false) }
                load()
            }
        }
    }

    fun restartGboard() {
        viewModelScope.launch {
            _state.update { it.copy(hookBusy = true) }
            kait.restart(GBOARD_PACKAGE)
            delay(SETTLE_MS)
            _state.update { it.copy(hookBusy = false) }
            load()
        }
    }

    fun dismissRevert() {
        _state.update { it.copy(reverted = emptyList()) }
        viewModelScope.launch { repository.clearLastRevert() }
    }

    // ---- Live Flag Editor (SPEC-0022 M2) -------------------------------------------------

    fun loadAllFlags() {
        viewModelScope.launch {
            _editor.update { it.copy(loading = true) }
            val all = repository.allFlagStates()
            cachedLiveKeys = all.mapTo(HashSet()) { it.name }
            val ov = bundleToMap(repository.overrides())
            _editor.update {
                it.copy(
                    loading = false,
                    flags = all,
                    overrides = ov,
                    totalCount = all.size,
                )
            }
        }
    }

    fun setEditorQuery(query: String) {
        _editor.update { it.copy(query = query) }
    }

    fun setEditorFilter(filter: FlagFilter) {
        _editor.update { it.copy(filter = filter) }
    }

    fun setRawFlag(name: String, value: Any) {
        viewModelScope.launch {
            writes.withLock { repository.setSingleOverride(name, value) }
            delay(SETTLE_MS)
            loadAllFlags()
            load()
        }
    }

    fun resetRawFlag(name: String) {
        viewModelScope.launch {
            writes.withLock { repository.removeSingleOverride(name) }
            delay(SETTLE_MS)
            loadAllFlags()
            load()
        }
    }

    fun clearAllOverrides() {
        viewModelScope.launch {
            writes.withLock { repository.clearAllOverrides() }
            delay(SETTLE_MS)
            loadAllFlags()
            load()
        }
    }

    suspend fun exportJson(): String? = repository.exportOverridesJson()

    fun importJson(json: String) {
        viewModelScope.launch {
            val res = writes.withLock { repository.importOverridesJson(json) }
            _editor.update {
                it.copy(
                    message = res.fold(
                        onSuccess = { n -> "Imported $n flags" },
                        onFailure = { e -> "Import failed: ${e.message ?: "invalid JSON"}" },
                    ),
                )
            }
            delay(SETTLE_MS)
            loadAllFlags()
            load()
        }
    }

    fun clearEditorMessage() {
        _editor.update { it.copy(message = null) }
    }

    // ---- Internal state resolution -------------------------------------------------------

    private suspend fun load() {
        writes.withLock {
            val catalog = repository.catalog()
            val overrides = repository.overrides()
            val onFeatures = overrides?.let { b -> catalog.filter { it.isOn(b) } }.orEmpty()
            val names = onFeatures.flatMapTo(LinkedHashSet()) { it.allKnownFlagKeys() }
            val flags = if (names.isEmpty()) emptyMap() else repository.flagStates(names)
            val health = repository.runtimeHealth()
            val hookState = resolveHookState()

            _state.value = KeyboardUi(
                loading = false,
                available = overrides != null,
                installed = repository.gboardInstalled(),
                features = catalog,
                on = onFeatures.mapTo(HashSet()) { it.id },
                states = onFeatures.associate { it.id to stateOf(it, flags, health) },
                health = health,
                hookState = hookState,
                hookBusy = _state.value.hookBusy,
                reverted = repository.lastRevert(),
            )
        }
    }

    private suspend fun resolveHookState(): HookBridgeState {
        val mods = kait.modules() ?: return HookBridgeState.UNAVAILABLE
        val mod = mods.find { it.packageName == KETIK_HOOK_PACKAGE }
            ?: return HookBridgeState.NOT_INSTALLED
        if (!mod.enabled) return HookBridgeState.DISABLED
        if (GBOARD_PACKAGE !in mod.scope) return HookBridgeState.UNSCOPED
        val injected = kait.injected()
        return if (GBOARD_PACKAGE in injected) HookBridgeState.ACTIVE else HookBridgeState.PENDING_RESTART
    }

    private fun stateOf(
        feature: KetikFeatureDef,
        flags: Map<String, FlagState>,
        health: KetikRuntimeHealth,
    ): FeatureState {
        if (flags.isEmpty()) return FeatureState.WAITING
        val resolved = feature.flags.map { spec ->
            val matched = spec.allKeys().firstNotNullOfOrNull { flags[it] }
            matched to spec.value.toString()
        }
        return when {
            resolved.any { (state, _) -> state == null } -> FeatureState.MISSING
            !resolved.all { (state, want) -> state!!.fromUs && state.value == want } ->
                FeatureState.NOT_APPLIED
            feature.requiredTags.isNotEmpty()
                && health.running
                && !feature.requiredTags.all { it in health.tags } -> FeatureState.BLOCKED
            else -> FeatureState.ACTIVE
        }
    }

    private fun bundleToMap(b: Bundle?): Map<String, Any> {
        if (b == null) return emptyMap()
        val out = LinkedHashMap<String, Any>()
        for (k in b.keySet()) {
            b.get(k)?.let { out[k] = it }
        }
        return out
    }

    private companion object {
        const val SETTLE_MS = 1500L
    }
}
