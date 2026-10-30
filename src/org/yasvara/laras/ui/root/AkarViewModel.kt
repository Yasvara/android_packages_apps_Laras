/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yasvara.laras.data.root.AuditEvent
import org.yasvara.laras.data.root.DeniedAttempt
import org.yasvara.laras.data.root.RootGrant
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.data.root.RootScope
import org.yasvara.laras.data.root.RootState
import org.yasvara.laras.data.root.RootTier

data class AkarUi(
    val loading: Boolean = true,
    /** False when this build has no root service at all. */
    val available: Boolean = true,
    val state: RootState = RootState(false, false, false, false),
    val grants: List<RootGrant> = emptyList(),
    val denied: List<DeniedAttempt> = emptyList(),
    val audit: List<AuditEvent> = emptyList(),
    val idleDays: Int = 30,
    val notifyDenied: Boolean = true,
    val logFull: Boolean = false,
    val termuxInstalled: Boolean = false,
    val termuxEnrolled: Boolean = false,
    val selfTest: String? = null,
    /** Set when a change was refused (locked device, daemon missing…). */
    val error: Boolean = false,
) {
    /** adb shell is enrolled as the pseudo package "shell". */
    val adbShell: RootGrant? get() = grants.firstOrNull { it.uid == 2000 }
    val apps: List<RootGrant> get() = grants.filter { it.uid != 2000 }
}

/**
 * Akar (SPEC-0006): which apps may use root and how. Each change is written to
 * YasvaraRootService at once; the daemon sees it on its next request. Nothing here polls: the
 * state is read when the screen opens and after every change.
 */
class AkarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RootRepository(app)
    private val _state = MutableStateFlow(AkarUi())
    val state: StateFlow<AkarUi> = _state.asStateFlow()

    fun repository() = repo

    fun load() {
        viewModelScope.launch {
            val s = repo.state()
            if (s == null) {
                _state.update { it.copy(loading = false, available = false) }
                return@launch
            }
            val grants = repo.grants()
            val termux = repo.isInstalled(RootRepository.TERMUX)
            _state.update {
                it.copy(
                    loading = false,
                    available = true,
                    state = s,
                    grants = grants,
                    denied = repo.denied(),
                    audit = repo.audit(),
                    idleDays = repo.option("idle_days").takeIf { v -> v >= 0 } ?: it.idleDays,
                    notifyDenied = repo.option("notify_denied") != 0,
                    logFull = repo.option("log_full") == 1,
                    termuxInstalled = termux,
                    termuxEnrolled = grants.any { g -> RootRepository.TERMUX in g.packages },
                )
            }
        }
    }

    private fun change(block: suspend () -> Boolean) {
        viewModelScope.launch {
            val ok = block()
            _state.update { it.copy(error = !ok) }
            load()
        }
    }

    fun setEnabled(on: Boolean) = change { repo.setEnabled(on) }

    fun setFrozen(on: Boolean) = change { repo.setFrozen(on) }

    fun enroll(
        pkg: String,
        tier: RootTier,
        scope: RootScope,
        durationMs: Long = 0,
        globalNamespace: Boolean = true,
        flags: Int = 0,
    ) = change { repo.enroll(pkg, tier, scope, durationMs, globalNamespace, flags) }

    fun revoke(uid: Int) = change { repo.revoke(uid) }

    fun revokeAll() = change { repo.revokeAll(); true }

    fun setAdbShell(on: Boolean) = change {
        if (on) {
            // An hour of root over adb; a longer one is a deliberate trip to the sheet.
            repo.enroll("shell", RootTier.ROOT, RootScope.TIMED, durationMs = 3_600_000L)
        } else {
            repo.revoke(2000)
            true
        }
    }

    fun setOption(key: String, value: Int) = change { repo.setOption(key, value) }

    fun clearDenied() {
        viewModelScope.launch {
            repo.clearDenied()
            load()
        }
    }

    fun runSelfTest() {
        viewModelScope.launch {
            _state.update { it.copy(selfTest = "…") }
            val r = repo.selfTest()
            _state.update { it.copy(selfTest = r) }
        }
    }

    fun dismissError() = _state.update { it.copy(error = false) }
}
