/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yasvara.laras.catalog.Catalog
import org.yasvara.laras.data.Pref
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.SettingsRepository
import org.yasvara.laras.data.setAny
import org.yasvara.laras.data.theme.SettingsThemeStore
import org.yasvara.laras.data.theme.ThemeApplier
import org.yasvara.laras.data.theme.ThemeCommitter
import org.yasvara.laras.data.theme.ThemeTransitionClient

class LarasViewModel(app: Application) : AndroidViewModel(app), PrefEditor {
    private val repository = SettingsRepository(app.contentResolver)
    private val themeCommitter = ThemeCommitter(SettingsThemeStore(repository))

    /** Applies theme changes atomically, behind SystemUI's reveal transition. */
    val themes = ThemeApplier(themeCommitter, ThemeTransitionClient(app))

    fun canUndoTheme(): Boolean = themeCommitter.canUndo()
    private val _prefs = MutableStateFlow(repository.snapshot(Catalog.observed))

    /** Current values of every catalogued setting, including changes made outside Laras. */
    val prefs: StateFlow<PrefSnapshot> = _prefs.asStateFlow()

    init {
        viewModelScope.launch { repository.observe(Catalog.observed).collect { _prefs.value = it } }
    }

    /** Applies [value] to the UI at once; the provider write follows off the main thread. */
    override fun <T> set(pref: Pref<T>, value: T) {
        _prefs.update { it.with(pref, value) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.write(pref, value)
            } catch (e: IllegalArgumentException) {
                // The provider's validator refused the value (e.g. an older platform build):
                // show what is really stored instead of taking Laras down.
                Log.w(TAG, "${pref.key} refused $value", e)
                _prefs.value = repository.snapshot(Catalog.observed)
            }
        }
    }

    override fun reset(pref: Pref<*>) = setAny(pref, pref.default)

    fun resetAll() = Catalog.prefs.filter(_prefs.value::isChanged).forEach(::reset)

    private companion object {
        const val TAG = "LarasViewModel"
    }
}
