/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.apps

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.security.yasvara.YasvaraSpoofProfiles.Profile
import org.yasvara.laras.data.integrity.AppProfilesRepository
import org.yasvara.laras.data.integrity.IntegrityKeys
import org.yasvara.laras.data.integrity.SecureStore

class Assignment(val packageName: String, val label: String, val profile: Profile?)

data class AppsUi(
    val photos: Boolean = true,
    val snapchat: Boolean = true,
    val spoofMode: Int = IntegrityKeys.MODE_AUTO,
    val assignments: List<Assignment> = emptyList(),
    val profiles: List<Profile> = emptyList(),
)

class AppsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repository = AppProfilesRepository(app, SecureStore(app.contentResolver))
    private val _state = MutableStateFlow(AppsUi())
    val state: StateFlow<AppsUi> = _state.asStateFlow()

    init {
        reload()
    }

    fun setPhotos(on: Boolean) = edit { repository.photos = on }

    fun setSnapchat(on: Boolean) = edit { repository.snapchat = on }

    fun setSpoofMode(mode: Int) = edit { repository.spoofMode = mode }

    fun assign(packageName: String, profileId: String) = edit { repository.assign(packageName.trim(), profileId) }

    fun unassign(packageName: String) = edit { repository.unassign(packageName) }

    /** The label of an installed package, or null when it is not installed. */
    fun labelOf(packageName: String): String? = try {
        app.packageManager.getApplicationInfo(packageName, 0).loadLabel(app.packageManager).toString()
    } catch (_: PackageManager.NameNotFoundException) { null }

    private fun edit(block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val assignments = repository.assignments().map { (pkg, id) ->
                Assignment(pkg, labelOf(pkg) ?: pkg, repository.profile(id))
            }.sortedBy { it.label.lowercase() }
            _state.update {
                AppsUi(repository.photos, repository.snapchat, repository.spoofMode, assignments, repository.profiles())
            }
        }
    }
}
