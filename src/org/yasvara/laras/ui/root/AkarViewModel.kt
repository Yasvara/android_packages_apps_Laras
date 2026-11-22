/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.app.Application
import android.content.Context
import android.os.Build
import android.system.Os
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.root.AkarShortcuts
import org.yasvara.laras.data.root.AuditEvent
import org.yasvara.laras.data.root.DeniedAttempt
import org.yasvara.laras.data.root.GrantPreset
import org.yasvara.laras.data.root.InstalledApp
import org.yasvara.laras.data.root.RootGrant
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.data.root.RootScope
import org.yasvara.laras.data.root.RootState
import org.yasvara.laras.data.root.RootTier

data class DeviceInfo(
    val model: String = Build.MODEL,
    val androidVersion: String = Build.VERSION.RELEASE,
    val kernelRelease: String = Os.uname().release,
    val buildType: String = Build.TYPE,
)

data class AkarUi(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** False when this build has no root service at all. */
    val available: Boolean = true,
    val state: RootState = RootState(false, false, false, false),
    val grants: List<RootGrant> = emptyList(),
    val denied: List<DeniedAttempt> = emptyList(),
    val audit: List<AuditEvent> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val idleDays: Int = 30,
    val notifyDenied: Boolean = true,
    val logFull: Boolean = false,
    val termuxInstalled: Boolean = false,
    val termuxEnrolled: Boolean = false,
    val adbPreset: GrantPreset = GrantPreset(RootTier.ROOT, RootScope.TIMED, RootRepository.DEFAULT_ADB_DURATION_MS),
    val pinSupported: Boolean = false,
    val shortcutPinned: Boolean = false,
    val drawerEnabled: Boolean = false,
    val selfTest: String? = null,
    val selfTestOk: Boolean? = null,
    val device: DeviceInfo = DeviceInfo(),
    /** Set when a change was refused (locked device, daemon missing…). */
    val error: Boolean = false,
) {
    /** adb shell is enrolled as the pseudo package "shell". */
    val adbShell: RootGrant? get() = grants.firstOrNull { it.uid == RootRepository.UID_SHELL }
    val apps: List<RootGrant> get() = grants.filter { it.uid != RootRepository.UID_SHELL }
}

/**
 * Akar: manages enrolled root grants, daemon state, adb shell timing,
 * behaviour options, and merged audit logs through [RootRepository].
 */
class AkarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RootRepository(app)
    private val _state = MutableStateFlow(AkarUi())
    val state: StateFlow<AkarUi> = _state.asStateFlow()

    fun repository() = repo

    fun load(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) _state.update { it.copy(refreshing = true) }
            val s = repo.state()
            if (s == null) {
                _state.update { it.copy(loading = false, refreshing = false, available = false) }
                return@launch
            }
            val appCtx: Context = getApplication()
            val (grants, denied, audit, allApps) = coroutineScope {
                val g = async { repo.grants() }
                val d = async { repo.denied() }
                val a = async { repo.audit(limit = 200) }
                val apps = async { repo.allApps() }
                Quad(g.await(), d.await(), a.await(), apps.await())
            }
            val termux = repo.isInstalled(RootRepository.TERMUX)
            val idle = repo.option("idle_days")
            val notify = repo.option("notify_denied")
            val logFull = repo.option("log_full")
            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    available = true,
                    state = s,
                    grants = grants,
                    denied = denied,
                    audit = audit,
                    installedApps = allApps,
                    idleDays = idle.takeIf { v -> v >= 0 } ?: it.idleDays,
                    notifyDenied = notify != 0,
                    logFull = logFull == 1,
                    termuxInstalled = termux,
                    termuxEnrolled = grants.any { g -> RootRepository.TERMUX in g.packages },
                    adbPreset = repo.adbPreset(),
                    pinSupported = AkarShortcuts.isPinSupported(appCtx),
                    shortcutPinned = AkarShortcuts.isPinned(appCtx),
                    drawerEnabled = AkarShortcuts.isDrawerEnabled(appCtx),
                )
            }
        }
    }

    private fun change(onDone: ((Boolean) -> Unit)? = null, block: suspend () -> Boolean) {
        viewModelScope.launch {
            val ok = block()
            _state.update { it.copy(error = !ok) }
            load()
            onDone?.invoke(ok)
        }
    }

    fun setEnabled(on: Boolean, onDone: ((Boolean) -> Unit)? = null) = change(onDone) { repo.setEnabled(on) }

    fun setFrozen(on: Boolean, onDone: ((Boolean) -> Unit)? = null) = change(onDone) { repo.setFrozen(on) }

    fun enroll(
        pkg: String,
        tier: RootTier,
        scope: RootScope,
        durationMs: Long = 0,
        globalNamespace: Boolean = true,
        flags: Int = 0,
        onDone: ((Boolean) -> Unit)? = null,
    ) = change(onDone) {
        val ok = repo.enroll(pkg, tier, scope, durationMs, globalNamespace, flags)
        if (ok) {
            if (pkg == RootRepository.SHELL_PACKAGE) {
                repo.saveAdbPreset(tier, scope, durationMs)
            } else {
                repo.saveProfile(pkg, tier, scope, durationMs, flags)
            }
        }
        ok
    }

    fun revoke(uid: Int, onDone: ((Boolean) -> Unit)? = null) = change(onDone) { repo.revoke(uid) }

    fun revokeAll(onDone: ((Boolean) -> Unit)? = null) = change(onDone) { repo.revokeAll(); true }

    fun setAdbShell(on: Boolean, onDone: ((Boolean) -> Unit)? = null) = change(onDone) {
        if (on) {
            val preset = repo.adbPreset()
            repo.enroll(
                RootRepository.SHELL_PACKAGE,
                preset.tier,
                preset.scope,
                durationMs = if (preset.scope == RootScope.TIMED) preset.durationMs else 0L,
            )
        } else {
            repo.revoke(RootRepository.UID_SHELL)
            true
        }
    }

    fun setAdbConfig(tier: RootTier, scope: RootScope, durationMs: Long, onDone: ((Boolean) -> Unit)? = null) =
        change(onDone) {
            repo.saveAdbPreset(tier, scope, durationMs)
            repo.enroll(
                RootRepository.SHELL_PACKAGE,
                tier,
                scope,
                durationMs = if (scope == RootScope.TIMED) durationMs else 0L,
            )
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
            val testing = getApplication<Application>().getString(R.string.akar_selftest_running)
            _state.update { it.copy(selfTest = testing, selfTestOk = null) }
            val r = repo.selfTest()
            _state.update { it.copy(selfTest = r, selfTestOk = r.startsWith("ok")) }
        }
    }

    fun setDrawerEnabled(enabled: Boolean) {
        val ctx = getApplication<Application>()
        AkarShortcuts.setDrawerEnabled(ctx, enabled)
        _state.update { it.copy(drawerEnabled = AkarShortcuts.isDrawerEnabled(ctx)) }
    }

    fun refreshShortcutState() {
        val ctx = getApplication<Application>()
        _state.update {
            it.copy(
                pinSupported = AkarShortcuts.isPinSupported(ctx),
                shortcutPinned = AkarShortcuts.isPinned(ctx),
                drawerEnabled = AkarShortcuts.isDrawerEnabled(ctx),
            )
        }
    }

    fun dismissError() = _state.update { it.copy(error = false) }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
