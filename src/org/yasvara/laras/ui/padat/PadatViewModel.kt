/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.padat

import android.app.Application
import android.text.format.Formatter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.yasvara.laras.data.padat.AppPolicyEntry
import org.yasvara.laras.data.padat.PadatAppItem
import org.yasvara.laras.data.padat.PadatPolicy
import org.yasvara.laras.data.padat.PadatPreset
import org.yasvara.laras.data.padat.PadatRepository
import org.yasvara.laras.data.padat.PadatScanResult
import org.yasvara.laras.data.padat.PadatScopes
import org.yasvara.laras.data.padat.PadatStatus

data class RunningAppPrompt(
    val packageName: String,
    val label: String,
    val decompress: Boolean,
    val entry: AppPolicyEntry,
)

data class PadatAppResult(
    val message: String,
    val isError: Boolean = false,
    val isCancelled: Boolean = false,
)

data class PadatUiState(
    val available: Boolean = true,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val status: PadatStatus? = null,
    val policy: PadatPolicy = PadatPolicy(),
    val apps: List<PadatAppItem> = emptyList(),
    val scanningPkg: String? = null,
    val scanResults: Map<String, PadatScanResult> = emptyMap(),
    val activePackage: String? = null,
    val activeLabel: String? = null,
    val activeAction: String? = null,
    val paused: Boolean = false,
    val recentResults: Map<String, PadatAppResult> = emptyMap(),
    val runningPrompt: RunningAppPrompt? = null,
) {
    val totalLogicalBytes: Long get() = apps.sumOf { it.logicalBytes }
    val totalPhysicalBytes: Long get() = apps.sumOf { it.physicalBytes }
    val totalSavedBytes: Long get() = apps.sumOf { it.savedBytes }
    val compressedAppsCount: Int get() = apps.count { it.savedBytes > 0L || it.compressedFiles > 0L }
    val compressedAppsLogicalBytes: Long
        get() = apps.filter { it.savedBytes > 0L || it.compressedFiles > 0L }.sumOf { it.logicalBytes }

    /** Returns live job status ONLY if it belongs to [pkg], avoiding stale progress from a previous job. */
    fun liveStatusFor(pkg: String): PadatStatus? {
        if (activePackage != pkg) return null
        val st = status ?: return null
        return if (st.activePackage == pkg) st else null
    }
}

class PadatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PadatRepository(app)
    private val _state = MutableStateFlow(
        repo.peekMemorySnapshot()?.let { snap ->
            PadatUiState(
                available = true,
                loading = false,
                refreshing = true,
                status = snap.status,
                policy = snap.policy,
                apps = snap.apps,
            )
        } ?: PadatUiState(),
    )
    val state: StateFlow<PadatUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val hadApps = _state.value.apps.isNotEmpty()
            _state.update { it.copy(loading = !hadApps, refreshing = true) }
            val avail = repo.available
            if (!avail) {
                _state.update { it.copy(available = false, loading = false, refreshing = false) }
                return@launch
            }

            // Phase 1 (~5-15ms): paint status + policy + cached snapshot (or fast PM shell on first launch)
            // immediately so the user NEVER waits on a blank loading spinner!
            val status = repo.loadStatus()
            val policy = repo.loadPolicy()
            val livePkg = status?.takeIf { it.busy && it.activePackage.isNotEmpty() }?.activePackage
            val liveAction = livePkg?.let { status.activeAction.ifEmpty { "compress" } }

            if (!hadApps) {
                val quickApps = repo.loadDiskCachedApps(policy).ifEmpty {
                    repo.loadFastInstalledShell(policy)
                }
                val liveLabel = livePkg?.let { p ->
                    quickApps.firstOrNull { it.packageName == p }?.label ?: p
                }
                _state.update {
                    it.copy(
                        available = true,
                        loading = quickApps.isEmpty(),
                        refreshing = true,
                        status = status,
                        policy = policy,
                        apps = quickApps,
                        paused = status?.paused ?: it.paused,
                        activePackage = it.activePackage ?: livePkg,
                        activeLabel = it.activeLabel ?: liveLabel,
                        activeAction = it.activeAction ?: liveAction,
                    )
                }
                if (livePkg != null) {
                    startStatusPolling()
                }
            } else {
                _state.update {
                    it.copy(
                        status = status,
                        policy = policy,
                        paused = status?.paused ?: it.paused,
                        activePackage = it.activePackage ?: livePkg,
                        activeAction = it.activeAction ?: liveAction,
                    )
                }
                if (livePkg != null) {
                    startStatusPolling()
                }
            }

            // Phase 2: live 6-bucket F2FS stat scan in background, smoothly swapping fresh numbers in place.
            val freshApps = repo.loadAllApps(policy)
            val freshStatus = repo.loadStatus() ?: status
            val finalApps = if (freshApps.isNotEmpty()) freshApps else _state.value.apps
            repo.saveMemorySnapshot(freshStatus, policy, finalApps)
            val finalLivePkg = freshStatus?.takeIf { it.busy && it.activePackage.isNotEmpty() }?.activePackage
            val finalLiveLabel = finalLivePkg?.let { p ->
                finalApps.firstOrNull { it.packageName == p }?.label ?: p
            }
            val finalLiveAction = finalLivePkg?.let { freshStatus.activeAction.ifEmpty { "compress" } }
            _state.update {
                it.copy(
                    available = true,
                    loading = false,
                    refreshing = false,
                    status = freshStatus,
                    policy = policy,
                    apps = finalApps,
                    paused = freshStatus?.paused ?: it.paused,
                    activePackage = it.activePackage ?: finalLivePkg,
                    activeLabel = it.activeLabel ?: finalLiveLabel,
                    activeAction = it.activeAction ?: finalLiveAction,
                )
            }
            if (finalLivePkg != null && pollJob == null) {
                startStatusPolling()
            }
        }
    }

    private fun startStatusPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            var firstTick = true
            while (isActive) {
                delay(if (firstTick) 100L else 300L)
                firstTick = false
                val st = repo.loadStatus() ?: continue
                _state.update {
                    it.copy(
                        status = st,
                        paused = st.paused,
                    )
                }
                if (!st.busy && _state.value.activePackage == null) {
                    break
                }
            }
        }
    }

    private fun stopStatusPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun setAutoTier1(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = _state.value.policy.copy(autoTier1 = enabled)
            if (repo.savePolicy(updated)) {
                repo.saveMemorySnapshot(_state.value.status, updated, _state.value.apps)
                _state.update { it.copy(policy = updated) }
            }
        }
    }

    fun scanApp(pkg: String, entry: AppPolicyEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(scanningPkg = pkg) }
            val res = repo.scanPackage(pkg, entry)
            _state.update { cur ->
                val map = cur.scanResults.toMutableMap()
                if (res != null) map[pkg] = res
                cur.copy(scanningPkg = null, scanResults = map)
            }
        }
    }

    fun startCompress(app: PadatAppItem, entry: AppPolicyEntry) {
        if (entry.preset == PadatPreset.OFF) {
            startDecompress(app, entry)
            return
        }
        if (app.isRunning) {
            _state.update {
                it.copy(
                    runningPrompt = RunningAppPrompt(
                        packageName = app.packageName,
                        label = app.label,
                        decompress = false,
                        entry = entry,
                    ),
                )
            }
            return
        }
        executeCompress(app.packageName, app.label, entry, forceStop = false, allowRunning = false)
    }

    fun startDecompress(app: PadatAppItem, entry: AppPolicyEntry) {
        if (app.isRunning) {
            _state.update {
                it.copy(
                    runningPrompt = RunningAppPrompt(
                        packageName = app.packageName,
                        label = app.label,
                        decompress = true,
                        entry = entry.copy(preset = PadatPreset.OFF),
                    ),
                )
            }
            return
        }
        executeDecompress(
            app.packageName,
            app.label,
            entry.scopes,
            forceStop = false,
            allowRunning = false,
        )
    }

    fun resolveRunningPrompt(forceStop: Boolean) {
        val prompt = _state.value.runningPrompt ?: return
        _state.update { it.copy(runningPrompt = null) }
        if (prompt.decompress) {
            executeDecompress(
                prompt.packageName,
                prompt.label,
                prompt.entry.scopes,
                forceStop = forceStop,
                allowRunning = !forceStop,
            )
        } else {
            executeCompress(
                prompt.packageName,
                prompt.label,
                prompt.entry,
                forceStop = forceStop,
                allowRunning = !forceStop,
            )
        }
    }

    fun dismissRunningPrompt() {
        _state.update { it.copy(runningPrompt = null) }
    }

    private fun clearJobFields(st: PadatStatus?): PadatStatus? = st?.copy(
        busy = false,
        paused = false,
        pauseReason = "",
        activePackage = "",
        activeAction = "",
        totalFiles = 0L,
        doneFiles = 0L,
        compressedFiles = 0L,
        skippedFiles = 0L,
        errorFiles = 0L,
        totalBytes = 0L,
        doneBytes = 0L,
        savedBytes = 0L,
        elapsedMs = 0L,
        speedBps = 0L,
        currentFile = "",
    )

    private fun executeCompress(
        pkg: String,
        label: String,
        entry: AppPolicyEntry,
        forceStop: Boolean,
        allowRunning: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { cur ->
                val results = cur.recentResults.toMutableMap().apply { remove(pkg) }
                cur.copy(
                    activePackage = pkg,
                    activeLabel = label,
                    activeAction = "compress",
                    paused = false,
                    status = clearJobFields(cur.status),
                    recentResults = results,
                )
            }
            val updatedApps = _state.value.policy.apps.toMutableMap()
            updatedApps[pkg] = entry
            val updatedPolicy = _state.value.policy.copy(apps = updatedApps)
            repo.savePolicy(updatedPolicy)
            _state.update { it.copy(policy = updatedPolicy) }

            if (forceStop) {
                repo.forceStopPackage(pkg)
                delay(300)
            }

            startStatusPolling()
            val rep = repo.compressPackage(pkg, entry, allowRunning || forceStop)
            stopStatusPolling()

            val appCtx = getApplication<Application>()
            val result = when {
                rep.error != null -> PadatAppResult(rep.error, isError = true)
                rep.cancelled -> PadatAppResult("Cancelled", isCancelled = true)
                rep.refusedRunning -> PadatAppResult(
                    "App is active — force-stop first or choose Continue Anyway",
                    isError = true,
                )
                rep.savedDeltaBytes > 0 -> {
                    val saved = Formatter.formatShortFileSize(appCtx, rep.savedDeltaBytes)
                    PadatAppResult("Saved $saved · ${rep.compressedFiles} compressed · ${rep.skippedProbe} skipped")
                }
                else -> PadatAppResult("${rep.compressedFiles} updated · ${rep.skippedProbe} incompressible skipped")
            }

            val status = repo.loadStatus()
            val apps = repo.loadAllApps(updatedPolicy)
            repo.saveMemorySnapshot(status, updatedPolicy, apps)
            _state.update { cur ->
                val scans = cur.scanResults.toMutableMap().apply { remove(pkg) }
                val results = cur.recentResults.toMutableMap().apply { put(pkg, result) }
                cur.copy(
                    activePackage = null,
                    activeLabel = null,
                    activeAction = null,
                    paused = false,
                    status = status,
                    apps = apps,
                    scanResults = scans,
                    recentResults = results,
                )
            }
        }
    }

    private fun executeDecompress(
        pkg: String,
        label: String,
        scopes: PadatScopes,
        forceStop: Boolean,
        allowRunning: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { cur ->
                val results = cur.recentResults.toMutableMap().apply { remove(pkg) }
                cur.copy(
                    activePackage = pkg,
                    activeLabel = label,
                    activeAction = "decompress",
                    paused = false,
                    status = clearJobFields(cur.status),
                    recentResults = results,
                )
            }
            val updatedApps = _state.value.policy.apps.toMutableMap()
            updatedApps.remove(pkg)
            val updatedPolicy = _state.value.policy.copy(apps = updatedApps)
            repo.savePolicy(updatedPolicy)
            _state.update { it.copy(policy = updatedPolicy) }

            if (forceStop) {
                repo.forceStopPackage(pkg)
                delay(300)
            }

            val fullScopes = PadatScopes(
                apkLib = true,
                artOat = true,
                externalData = true,
                obb = true,
                internalFiles = true,
            )
            startStatusPolling()
            val rep = repo.decompressPackage(pkg, fullScopes, allowRunning || forceStop)
            stopStatusPolling()

            val result = when {
                rep.error != null -> PadatAppResult(rep.error, isError = true)
                rep.cancelled -> PadatAppResult("Cancelled", isCancelled = true)
                else -> PadatAppResult("Decompressed ${rep.decompressedFiles} files")
            }
            val status = repo.loadStatus()
            val apps = repo.loadAllApps(updatedPolicy)
            repo.saveMemorySnapshot(status, updatedPolicy, apps)
            _state.update { cur ->
                val scans = cur.scanResults.toMutableMap().apply { remove(pkg) }
                val results = cur.recentResults.toMutableMap().apply { put(pkg, result) }
                cur.copy(
                    activePackage = null,
                    activeLabel = null,
                    activeAction = null,
                    paused = false,
                    status = status,
                    apps = apps,
                    scanResults = scans,
                    recentResults = results,
                )
            }
        }
    }

    fun togglePause() {
        val next = !_state.value.paused
        viewModelScope.launch(Dispatchers.IO) {
            repo.setPaused(next)
            _state.update { it.copy(paused = next) }
        }
    }

    fun cancelJob() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.cancelJob()
        }
    }

    fun dismissResult(pkg: String) {
        _state.update { cur ->
            val results = cur.recentResults.toMutableMap().apply { remove(pkg) }
            cur.copy(recentResults = results)
        }
    }
}
