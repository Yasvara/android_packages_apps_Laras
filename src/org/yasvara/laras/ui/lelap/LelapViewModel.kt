/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.lelap

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yasvara.laras.data.lelap.Cut
import org.yasvara.laras.data.lelap.LelapCatalog
import org.yasvara.laras.data.lelap.LelapRepository
import org.yasvara.laras.data.lelap.Rule
import org.yasvara.laras.data.lelap.SleepApp
import org.yasvara.laras.data.lelap.WakeEvent

data class LelapUi(
    val available: Boolean = true,
    val loading: Boolean = true,
    val apps: List<SleepApp> = emptyList(),
    val events: List<WakeEvent> = emptyList(),
    /** Ids of the cuts whose rules are all stored. */
    val cutsOn: Set<String> = emptySet(),
    /** The framework's reason for refusing the last change. */
    val error: String? = null,
)

class LelapViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = LelapRepository(app)
    private val _state = MutableStateFlow(LelapUi())
    val state: StateFlow<LelapUi> = _state.asStateFlow()

    init {
        reload()
    }

    fun setStrict(packageName: String, on: Boolean) =
        edit { repository.change(listOf(Rule(LelapRepository.STRICT, packageName)), on) }

    fun setCut(cut: Cut, on: Boolean) = edit { repository.change(cut.rules, on) }

    fun clearEvents() = edit {
        repository.clearEvents()
        null
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) { publish(null) }
    }

    private fun edit(block: () -> String?) {
        viewModelScope.launch(Dispatchers.IO) { publish(block()) }
    }

    private fun publish(error: String?) {
        val stored = repository.allRules()
        val events = repository.events()
        val apps = repository.apps(events)
        _state.update {
            LelapUi(
                available = repository.available,
                loading = false,
                apps = apps,
                events = events,
                cutsOn = LelapCatalog.cuts.filter { stored.containsAll(it.rules) }.mapTo(HashSet()) { it.id },
                error = error,
            )
        }
    }
}
