/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.updates

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.data.updates.UpdateApp
import org.yasvara.laras.data.updates.UpdateEvent
import org.yasvara.laras.data.updates.UpdatePolicy
import org.yasvara.laras.data.updates.UpdatePolicyRepository
import org.yasvara.laras.data.updates.VaultEntry

enum class UpdateFilter { ALL, PAUSED, DELAYED, ASK }

data class AppUpdatesUi(
    val loading: Boolean = true,
    val vaultBytes: Long = 0,
    val available: Boolean = true,
    val apps: List<UpdateApp> = emptyList(),
    val query: String = "",
    val filter: UpdateFilter = UpdateFilter.ALL,
) {
    val visible: List<UpdateApp>
        get() = apps.filter { app ->
            (query.isBlank() || app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)) &&
                when (filter) {
                    UpdateFilter.ALL -> true
                    UpdateFilter.PAUSED -> app.policy == UpdatePolicy.NEVER || app.policy == UpdatePolicy.SKIP
                    UpdateFilter.DELAYED -> app.policy == UpdatePolicy.DELAY
                    UpdateFilter.ASK -> app.policy == UpdatePolicy.ASK
                }
        }

    fun count(policy: UpdatePolicy) = apps.count { it.policy == policy }
}

/** What the per-app sheet shows besides the policy itself. */
data class AppDetail(
    val history: List<UpdateEvent> = emptyList(),
    /** When the Allow once grant runs out (wall clock), 0 for none. */
    val allowOnceUntil: Long = 0,
    /** A paused app the store still lists updates for. */
    val shownInStore: Boolean = false,
    /** The version a Skip rule refuses, 0 otherwise. */
    val skippedVersion: Long = 0,
    val vault: List<VaultEntry> = emptyList(),
    val vaultEnabled: Boolean = false,
    /** When the current policy was picked (wall clock), 0 on Auto. */
    val since: Long = 0,
)

/** Progress of the last rollback started from this screen. */
sealed interface RollbackState {
    data object Idle : RollbackState
    data class Running(val pkg: String) : RollbackState
    data class Done(val pkg: String, val ok: Boolean, val message: String?) : RollbackState
}

/** The App updates screen (SPEC-0005 §4.7). Every change is written to system_server at once. */
class AppUpdatesViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = UpdatePolicyRepository(app)
    private val _state = MutableStateFlow(AppUpdatesUi())
    val state: StateFlow<AppUpdatesUi> = _state.asStateFlow()
    private val _detail = MutableStateFlow(AppDetail())
    val detail: StateFlow<AppDetail> = _detail.asStateFlow()

    private val _rollback = MutableStateFlow<RollbackState>(RollbackState.Idle)
    val rollback: StateFlow<RollbackState> = _rollback.asStateFlow()

    private var includeCore = false

    // PackageInstaller reports the rollback here; the PendingIntent is explicit to this app.
    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.getStringExtra(EXTRA_ROLLBACK_PKG) ?: return
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) return
            _rollback.value = RollbackState.Done(pkg, status == PackageInstaller.STATUS_SUCCESS,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
            load(includeCore)
            open(pkg)
        }
    }

    init {
        app.registerReceiver(resultReceiver, IntentFilter(ACTION_ROLLBACK_RESULT),
            Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(resultReceiver)
    }

    fun load(includeCore: Boolean) {
        this.includeCore = includeCore
        viewModelScope.launch {
            val apps = repository.apps(includeCore)
            val bytes = withContext(Dispatchers.IO) { repository.vaultBytes() }
            _state.update {
                it.copy(loading = false, available = repository.available, apps = apps, vaultBytes = bytes)
            }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setFilter(filter: UpdateFilter) = _state.update { it.copy(filter = filter) }

    fun setPolicy(pkg: String, policy: UpdatePolicy, delayDays: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.set(pkg, policy, delayDays)) return@launch
            _state.update { ui ->
                ui.copy(apps = ui.apps.map {
                    if (it.packageName != pkg) it
                    else it.copy(policy = policy, delayDays = if (policy == UpdatePolicy.DELAY) delayDays else 0)
                })
            }
            open(pkg)
        }
    }

    /** Loads history and the allow-once state for the sheet of [pkg]. */
    fun open(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _detail.value = AppDetail(repository.history(pkg), repository.allowOnceUntil(pkg),
                repository.shownInStore(pkg), repository.skippedVersion(pkg),
                repository.vault(pkg), repository.vaultEnabled(pkg), repository.policySince(pkg))
        }
    }

    /** Lets the next store update of a paused app through ([allow] false takes it back). */
    fun allowOnce(pkg: String, allow: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.allowOnce(pkg, allow)
            open(pkg)
        }
    }

    fun setShownInStore(pkg: String, shown: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setShownInStore(pkg, shown)
            open(pkg)
        }
    }

    fun skipVersion(pkg: String, versionCode: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.skipVersion(pkg, versionCode)) return@launch
            _state.update { ui ->
                ui.copy(apps = ui.apps.map {
                    if (it.packageName != pkg) it else it.copy(policy = UpdatePolicy.SKIP, delayDays = 0)
                })
            }
            open(pkg)
        }
    }

    /** Reinstalls a saved version (SPEC-0005 §4.4). */
    fun rollback(pkg: String, versionCode: Long, clearData: Boolean, pin: Boolean) {
        val app = getApplication<Application>()
        val result = PendingIntent.getBroadcast(app, pkg.hashCode(),
            Intent(ACTION_ROLLBACK_RESULT).setPackage(app.packageName).putExtra(EXTRA_ROLLBACK_PKG, pkg),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        _rollback.value = RollbackState.Running(pkg)
        viewModelScope.launch(Dispatchers.IO) {
            if (!repository.rollback(pkg, versionCode, clearData, pin, result.intentSender)) {
                _rollback.value = RollbackState.Done(pkg, false, null)
            }
        }
    }

    fun setVaultEnabled(pkg: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setVaultEnabled(pkg, enabled)
            open(pkg)
        }
    }

    fun deleteVault(pkg: String, versionCode: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteVault(pkg, versionCode)
            open(pkg)
            _state.update { it.copy(vaultBytes = repository.vaultBytes()) }
        }
    }

    fun clearRollback() {
        _rollback.value = RollbackState.Idle
    }

    fun installerLabel(pkg: String?): String? = repository.labelOf(pkg)

    private companion object {
        const val ACTION_ROLLBACK_RESULT = "org.yasvara.laras.action.ROLLBACK_RESULT"
        const val EXTRA_ROLLBACK_PKG = "rollback_package"
    }
}
