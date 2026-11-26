/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.pasang

import android.app.Application
import android.net.Uri
import android.os.Build
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
import kotlinx.coroutines.withContext
import org.yasvara.laras.data.pasang.KernelPackageInfo
import org.yasvara.laras.data.pasang.PasangDevOptions
import org.yasvara.laras.data.pasang.PasangInstallService
import org.yasvara.laras.data.pasang.PasangOverlayCleanResult
import org.yasvara.laras.data.pasang.PasangPlan
import org.yasvara.laras.data.pasang.PasangRepository
import org.yasvara.laras.data.pasang.PasangSlotInfo
import org.yasvara.laras.data.pasang.PasangStatus

data class PasangUiState(
    val loading: Boolean = true,
    val daemonAvailable: Boolean = true,
    val buildDisplay: String = Build.DISPLAY,
    val securityPatch: String = Build.VERSION.SECURITY_PATCH,
    val isDevVisible: Boolean = false,
    val isDebuggableBuild: Boolean = Build.IS_DEBUGGABLE,
    val slotInfo: PasangSlotInfo = PasangSlotInfo(),
    val status: PasangStatus = PasangStatus(),
    val selectedPath: String? = null,
    val selectedLabel: String? = null,
    val planning: Boolean = false,
    val plan: PasangPlan? = null,
    val devOptions: PasangDevOptions = PasangDevOptions(),
    val cleaningOverlay: Boolean = false,
    val lastOverlayClean: PasangOverlayCleanResult? = null,
    val message: String? = null,
    val runningKernel: String = System.getProperty("os.version").orEmpty(),
    val kernelPath: String? = null,
    val kernelPackage: KernelPackageInfo? = null,
    val kernelPlanning: Boolean = false,
    val kernelPlan: PasangPlan? = null,
    val kernelError: String? = null,
)

class PasangViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = PasangRepository(app)
    private val _state = MutableStateFlow(
        PasangUiState(
            isDevVisible = repo.isDevSectionVisible(),
            devOptions = repo.loadDevOptions(),
        )
    )
    val state: StateFlow<PasangUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val (slot, status) = withContext(Dispatchers.IO) {
                repo.fetchSlotInfo() to repo.fetchStatus()
            }
            if (slot == null || status == null) {
                _state.update { it.copy(loading = false, daemonAvailable = false) }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    daemonAvailable = true,
                    isDevVisible = repo.isDevSectionVisible(),
                    slotInfo = slot,
                    status = status,
                )
            }
            if (status.busy) {
                PasangInstallService.startMonitoring(getApplication())
                startPolling()
            }
        }
    }

    fun onPickUri(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(planning = true, plan = null, message = null) }
            val resolved = withContext(Dispatchers.IO) { repo.resolveOtaUri(uri) }
            if (resolved == null) {
                _state.update { it.copy(planning = false, message = "Cannot open selected file") }
                return@launch
            }
            val (path, label) = resolved
            val kernel = withContext(Dispatchers.IO) { repo.readKernelPackage(uri) }
            if (kernel != null) {
                _state.update { it.copy(planning = false) }
                planKernel(path, kernel)
                return@launch
            }
            val opts = _state.value.devOptions
            val plan = withContext(Dispatchers.IO) { repo.planOta(path, opts) }
            _state.update {
                it.copy(
                    selectedPath = path,
                    selectedLabel = label,
                    planning = false,
                    plan = plan,
                )
            }
        }
    }

    fun onPickKernelUri(uri: Uri) {
        viewModelScope.launch {
            _state.update {
                it.copy(kernelPlanning = true, kernelPlan = null, kernelPackage = null, kernelError = null)
            }
            val (resolved, kernel) = withContext(Dispatchers.IO) {
                repo.resolveOtaUri(uri) to repo.readKernelPackage(uri)
            }
            when {
                resolved == null -> fail("Cannot open selected file")
                kernel == null -> fail("Not a kernel package (no ${KernelPackageInfo.ENTRY})")
                !kernel.deviceMatches -> fail("Built for ${kernel.device}, this is ${android.os.Build.DEVICE}")
                else -> planKernel(resolved.first, kernel)
            }
        }
    }

    private fun fail(msg: String) {
        _state.update { it.copy(kernelPlanning = false, kernelPath = null, kernelError = msg) }
    }

    private suspend fun planKernel(path: String, kernel: KernelPackageInfo) {
        _state.update {
            it.copy(
                kernelPlanning = true,
                kernelPath = path,
                kernelPackage = kernel,
                kernelPlan = null,
                kernelError = null,
            )
        }
        val plan = withContext(Dispatchers.IO) { repo.planOta(path, _state.value.devOptions) }
        _state.update { it.copy(kernelPlanning = false, kernelPlan = plan) }
    }

    fun flashKernel() {
        val path = _state.value.kernelPath ?: return
        val opts = _state.value.devOptions
        viewModelScope.launch {
            val rc = withContext(Dispatchers.IO) { repo.startOta(path, opts) }
            if (rc != 0) {
                _state.update { it.copy(kernelError = "Failed to start flash (code $rc)") }
                return@launch
            }
            PasangInstallService.startMonitoring(getApplication())
            startPolling()
        }
    }

    fun reanalyzePlan() {
        val path = _state.value.selectedPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(planning = true, message = null) }
            val plan = withContext(Dispatchers.IO) { repo.planOta(path, _state.value.devOptions) }
            _state.update { it.copy(planning = false, plan = plan) }
        }
    }

    fun startInstall() {
        val path = _state.value.selectedPath ?: return
        val opts = _state.value.devOptions
        viewModelScope.launch {
            val rc = withContext(Dispatchers.IO) { repo.startOta(path, opts) }
            if (rc != 0) {
                _state.update { it.copy(message = "Failed to start OTA (code $rc)") }
                return@launch
            }
            PasangInstallService.startMonitoring(getApplication())
            startPolling()
        }
    }

    fun cancelInstall() {
        viewModelScope.launch(Dispatchers.IO) { repo.cancelOta() }
    }

    fun rebootNow() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { repo.rebootToNewSlot() }
            if (!ok) {
                _state.update { it.copy(message = "Reboot request rejected") }
            }
        }
    }

    fun cleanOverlaysNow() {
        viewModelScope.launch {
            _state.update { it.copy(cleaningOverlay = true, lastOverlayClean = null) }
            val res = withContext(Dispatchers.IO) { repo.cleanOverlaysNow() }
            val slot = withContext(Dispatchers.IO) { repo.fetchSlotInfo() }
            _state.update {
                it.copy(
                    cleaningOverlay = false,
                    lastOverlayClean = res,
                    slotInfo = slot ?: it.slotInfo,
                )
            }
        }
    }

    fun setAutoCleanOverlay(enabled: Boolean) = updateDev { it.copy(autoCleanOverlay = enabled) }
    fun setExtentAliasing(enabled: Boolean) = updateDev { it.copy(enableExtentAliasing = enabled) }
    fun setFastVerify(enabled: Boolean) = updateDev { it.copy(fastVerify = enabled) }
    fun setAllowDevKey(enabled: Boolean) = updateDev { it.copy(allowDevKey = enabled) }

    private fun updateDev(transform: (PasangDevOptions) -> PasangDevOptions) {
        val updated = transform(_state.value.devOptions)
        repo.saveDevOptions(updated)
        _state.update { it.copy(devOptions = updated) }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val st = withContext(Dispatchers.IO) { repo.fetchStatus() }
                if (st != null) {
                    _state.update { it.copy(status = st, daemonAvailable = true) }
                    if (!st.busy) {
                        val slot = withContext(Dispatchers.IO) { repo.fetchSlotInfo() }
                        if (slot != null) _state.update { it.copy(slotInfo = slot) }
                        break
                    }
                }
                delay(250L)
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
