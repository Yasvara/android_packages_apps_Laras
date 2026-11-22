/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.data.integrity.ChainOrigin
import org.yasvara.laras.data.integrity.EvokerProvider
import org.yasvara.laras.data.integrity.KeyboxPayload
import org.yasvara.laras.data.integrity.FailureKind
import org.yasvara.laras.data.integrity.FetchException
import org.yasvara.laras.data.integrity.FetchFailure
import org.yasvara.laras.data.integrity.IntegrityChecker
import org.yasvara.laras.data.integrity.IntegrityKeys
import org.yasvara.laras.data.integrity.KeyboxImport
import org.yasvara.laras.data.integrity.KeyboxRepository
import org.yasvara.laras.data.integrity.PifChannel
import org.yasvara.laras.data.integrity.PifRepository
import org.yasvara.laras.data.integrity.PifUpdate
import org.yasvara.laras.data.integrity.PlayServices
import org.yasvara.laras.data.integrity.Revocation
import org.yasvara.laras.data.integrity.SecureStore
import org.yasvara.laras.data.integrity.TargetsRepository

class IntegrityViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = SecureStore(app.contentResolver)
    private val pif = PifRepository(app, store)
    private val keybox = KeyboxRepository(app, store)
    private val targets = TargetsRepository(app, store)
    private val checker = IntegrityChecker(app, keybox)
    private var keyboxJob: Job? = null

    private val _state = MutableStateFlow(IntegrityUiState())
    val state: StateFlow<IntegrityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val dropped = KEYBOX_OPERATION.withLock { keybox.migrate() }
            reload()
            if (dropped) {
                _state.update {
                    it.copy(keybox = it.keybox.copy(
                        notice = Notice(app.getString(R.string.keybox_reimport_needed), error = true)))
                }
            }
        }
        viewModelScope.launch {
            PlayServices.restartPending.collect { p -> _state.update { it.copy(restartPending = p) } }
        }
    }

    /** Re-reads everything from Settings; call when returning from another screen. */
    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val pendingSerial: String? = KEYBOX_OPERATION.withLock {
                val info = keybox.info()
                val revocation = if (info == null) Revocation.Unknown() else keybox.lastRevocation()
                _state.update {
                    it.copy(
                        loaded = true,
                        pif = it.pif.copy(
                            stored = pif.current(),
                            enabled = store.getBoolean(IntegrityKeys.PIF_ENABLED, false),
                            customUrl = pif.customUrl,
                            channel = pif.channel,
                            devices = pif.library(),
                            googleDevices = pif.cachedGoogleDevices(),
                            activeDevice = pif.device,
                        ),
                        keybox = it.keybox.copy(
                            info = info,
                            revocation = revocation,
                            enabled = store.getBoolean(IntegrityKeys.TS_ENABLED, true),
                            interim = info != null &&
                                keybox.custody() == KeyboxRepository.Custody.INTERIM,
                        ),
                        targetCount = targets.selected().size,
                    )
                }
                if (info != null && isRevocationStale(revocation)) info.serialHex else null
            }
            if (pendingSerial != null) {
                val fresh = keybox.checkRevocation(force = false)
                if (fresh !is Revocation.Unknown && keybox.info()?.serialHex == pendingSerial) {
                    _state.update {
                        it.copy(
                            check = if (fresh is Revocation.Revoked) CheckUi() else it.check,
                            keybox = it.keybox.copy(revocation = fresh),
                        )
                    }
                }
            }
            val current = _state.value
            if (current.keyboxUsable && current.check.report == null &&
                !current.check.running && !current.keybox.busy) {
                runCheck(forceRevocation = false)
            }
        }
    }

    private fun isRevocationStale(r: Revocation): Boolean {
        val now = System.currentTimeMillis()
        return when (r) {
            is Revocation.Unknown -> true
            is Revocation.Valid -> now - r.checkedAt > REVOCATION_TTL_MS
            is Revocation.Revoked -> now - r.checkedAt > REVOCATION_TTL_MS
        }
    }

    // ---- Play Integrity Fix -------------------------------------------------------------

    fun setPifEnabled(on: Boolean) {
        _state.update { it.copy(pif = it.pif.copy(enabled = on)) }
        write(autoHandledByService = true) { store.putBoolean(IntegrityKeys.PIF_ENABLED, on) }
    }

    fun setCustomUrl(url: String) {
        val clean = url.trim()
        if (clean.isNotEmpty() && !clean.startsWith("https://")) {
            pifNotice(app.getString(R.string.pif_custom_url_https), error = true)
            return
        }
        pif.customUrl = clean
        _state.update { it.copy(pif = it.pif.copy(customUrl = clean, notice = null)) }
    }

    fun updateProfile() {
        if (_state.value.pif.updating) return
        _state.update { it.copy(pif = it.pif.copy(updating = true, notice = null)) }
        viewModelScope.launch {
            val notice = when (val result = pif.update()) {
                is PifUpdate.Updated -> {
                    if (result.changed) {
                        withContext(Dispatchers.IO) { PlayServices.markRestartNeeded(autoHandledByService = true) }
                    }
                    val source = app.getString(result.stored.source.label())
                    Notice(
                        if (result.changed) app.getString(R.string.pif_updated, result.stored.profile.model, source)
                        else app.getString(R.string.pif_up_to_date, source),
                    )
                }
                is PifUpdate.Failed -> Notice(failedText(result), error = true)
            }
            _state.update {
                it.copy(pif = it.pif.copy(updating = false, notice = notice,
                    stored = pif.current(), devices = pif.library(), activeDevice = pif.device))
            }
        }
    }

    fun setChannel(channel: PifChannel) {
        if (_state.value.pif.channel == channel) return
        pif.channel = channel
        _state.update { it.copy(pif = it.pif.copy(channel = channel)) }
        updateProfile()
    }

    fun activateDevice(fingerprint: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = pif.activate(fingerprint)
            if (stored != null) {
                PlayServices.markRestartNeeded(autoHandledByService = true)
                _state.update {
                    it.copy(pif = it.pif.copy(stored = stored,
                        notice = Notice(app.getString(R.string.pif_activated, stored.profile.model))))
                }
            }
        }
    }

    /** Loads Google's Pixel list (cached 7 days); force re-scrapes developer.android.com. */
    fun refreshGoogleDevices(force: Boolean = false) {
        if (_state.value.pif.devicesLoading) return
        _state.update { it.copy(pif = it.pif.copy(devicesLoading = true, notice = null)) }
        viewModelScope.launch {
            try {
                val devices = withContext(Dispatchers.IO) { pif.refreshGoogleDevices(force) }
                val active = pif.device.ifEmpty { devices.firstOrNull()?.id.orEmpty() }
                _state.update {
                    it.copy(pif = it.pif.copy(devicesLoading = false, googleDevices = devices,
                        activeDevice = active))
                }
            } catch (e: Exception) {
                val failure = (e as? FetchException)?.failure
                    ?: FetchFailure(FailureKind.OTHER, e.message)
                _state.update {
                    it.copy(pif = it.pif.copy(devicesLoading = false,
                        notice = Notice(app.describe(failure), error = true)))
                }
            }
        }
    }

    /** Picks the Google device profiles are fetched for, then fetches for this channel. */
    fun setGoogleDevice(id: String) {
        pif.device = id
        _state.update { it.copy(pif = it.pif.copy(activeDevice = id)) }
        updateProfile()
    }

    fun importProfile(uri: Uri) {
        viewModelScope.launch {
            val stored = readText(uri)?.let { withContext(Dispatchers.IO) { pif.import(it) } }
            applyImported(stored)
        }
    }

    fun importProfileText(raw: String) {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { pif.import(raw) }
            applyImported(stored)
        }
    }

    fun saveManualProfile(fields: Map<String, String>) {
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { pif.saveManual(fields) }
            applyImported(stored)
        }
    }

    fun removeLibraryProfile(fingerprint: String) {
        viewModelScope.launch(Dispatchers.IO) {
            pif.removeFromLibrary(fingerprint)
            val updated = pif.library()
            _state.update { it.copy(pif = it.pif.copy(devices = updated)) }
        }
    }

    private suspend fun applyImported(stored: org.yasvara.laras.data.integrity.StoredPif?) {
        if (stored == null) {
            pifNotice(app.getString(R.string.pif_import_invalid), error = true)
        } else {
            withContext(Dispatchers.IO) { PlayServices.markRestartNeeded(autoHandledByService = true) }
            val updatedLib = withContext(Dispatchers.IO) { pif.library() }
            _state.update {
                it.copy(pif = it.pif.copy(stored = stored, devices = updatedLib,
                    notice = Notice(app.getString(R.string.pif_imported, stored.profile.model.ifEmpty { stored.profile.device }))))
            }
        }
    }

    fun resetProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            pif.resetToBuiltIn()
            PlayServices.markRestartNeeded(autoHandledByService = true)
            val stored = pif.current()
            _state.update {
                it.copy(pif = it.pif.copy(stored = stored,
                    notice = Notice(app.getString(R.string.pif_reset))))
            }
        }
    }

    private fun failedText(failed: PifUpdate.Failed): String {
        val lines = failed.attempts.joinToString("\n") {
            app.getString(R.string.pif_attempt, app.getString(it.source.label()), app.describe(it.failure))
        }
        val keeping = pif.current()?.let { app.getString(R.string.pif_keeping, app.getString(it.source.label())) }
        return listOfNotNull(app.getString(R.string.pif_update_failed), lines, keeping).joinToString("\n")
    }

    private fun pifNotice(text: String, error: Boolean) =
        _state.update { it.copy(pif = it.pif.copy(notice = Notice(text, error))) }

    // ---- Keybox -------------------------------------------------------------------------

    fun setKeyboxEnabled(on: Boolean) {
        _state.update { it.copy(keybox = it.keybox.copy(enabled = on)) }
        write(autoHandledByService = true) { store.putBoolean(IntegrityKeys.TS_ENABLED, on) }
    }

    fun importKeybox(uri: Uri) = keyboxOperation {
        val text = withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use {
                KeyboxPayload.utf8(it.readNBytes(KeyboxPayload.MAX_BYTES + 1))
            }
        }.orEmpty()
        installKeybox(text, R.string.keybox_imported)
    }

    /** Called only after the sheet's explicit remote-credential/replacement confirmation. */
    fun syncEvoker() = keyboxOperation(syncing = true) {
        val text = EvokerProvider.fetch()
        installKeybox(text, R.string.keybox_evoker_synced)
    }

    fun cancelKeyboxSync() {
        if (_state.value.keybox.syncing) keyboxJob?.cancel()
    }

    private suspend fun installKeybox(text: String, successMessage: Int) {
        currentCoroutineContext().ensureActive()
        _state.update { it.copy(keybox = it.keybox.copy(syncing = false)) }
        // Once publication starts, finish the service call and its public UI update as
        // one serialized operation. Cancellation before this point never changes custody.
        withContext(NonCancellable + Dispatchers.IO) {
            when (val result = keybox.import(text)) {
                is KeyboxImport.Rejected -> keyboxNotice(app.getString(result.problem.message()), error = true)
                is KeyboxImport.Imported -> {
                    PlayServices.markRestartNeeded(autoHandledByService = true)
                    val revocation = keybox.checkRevocation(force = true)
                    _state.update {
                        it.copy(check = CheckUi(), keybox = it.keybox.copy(info = result.info,
                            revocation = revocation, interim = false,
                            notice = Notice(app.getString(successMessage))))
                    }
                }
            }
        }
    }

    fun deleteKeybox() = keyboxOperation {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable + Dispatchers.IO) {
            if (keybox.delete()) {
                PlayServices.markRestartNeeded(autoHandledByService = true)
                _state.update {
                    it.copy(check = CheckUi(), keybox = it.keybox.copy(info = null,
                        revocation = Revocation.Unknown(), interim = false,
                        notice = Notice(app.getString(R.string.keybox_deleted))))
                }
            } else {
                keyboxNotice(app.getString(R.string.keybox_delete_failed), error = true)
            }
        }
    }

    fun checkRevocation() {
        if (_state.value.keybox.info == null) return
        keyboxOperation {
            _state.update { it.copy(keybox = it.keybox.copy(checking = true)) }
            val result = withContext(Dispatchers.IO) { keybox.checkRevocation(force = true) }
            val notice = (result as? Revocation.Unknown)?.failure?.let {
                Notice(app.getString(R.string.keybox_check_failed, app.describe(it)), error = true)
            }
            _state.update {
                it.copy(
                    check = if (result is Revocation.Revoked) CheckUi() else it.check,
                    keybox = it.keybox.copy(revocation = result, notice = notice ?: it.keybox.notice),
                )
            }
        }
    }

    private fun keyboxOperation(syncing: Boolean = false, block: suspend () -> Unit) {
        if (!_state.value.loaded || _state.value.check.running || !KEYBOX_OPERATION.tryLock()) return
        _state.update { it.copy(keybox = it.keybox.copy(busy = true, syncing = syncing, notice = null)) }
        keyboxJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
            } catch (e: CancellationException) {
                if (_state.value.keybox.syncing) {
                    keyboxNotice(app.getString(R.string.keybox_sync_cancelled), error = false)
                }
                throw e
            } catch (e: FetchException) {
                keyboxNotice(app.getString(R.string.keybox_sync_failed, app.describe(e.failure)), error = true)
            } catch (_: Exception) {
                keyboxNotice(app.getString(R.string.keybox_operation_failed), error = true)
            } finally {
                _state.update { it.copy(keybox = it.keybox.copy(busy = false, syncing = false, checking = false)) }
                KEYBOX_OPERATION.unlock()
            }
        }
    }

    private fun keyboxNotice(text: String, error: Boolean) =
        _state.update { it.copy(keybox = it.keybox.copy(notice = Notice(text, error))) }

    // ---- Attestation & keybox revocation check ------------------------------------------

    fun runCheck(forceRevocation: Boolean = true) {
        if (!_state.value.loaded || _state.value.check.running || _state.value.keybox.busy) return
        _state.update { it.copy(check = it.check.copy(running = true)) }
        viewModelScope.launch {
            val report = checker.run(forceRevocation = forceRevocation)
            _state.update { current ->
                val updatedRev = if (report.origin == ChainOrigin.KEYBOX &&
                    report.revocation !is Revocation.Unknown) {
                    report.revocation
                } else {
                    current.keybox.revocation
                }
                current.copy(
                    check = CheckUi(running = false, report = report),
                    keybox = current.keybox.copy(revocation = updatedRev),
                )
            }
        }
    }

    fun restartPlayServices() {
        viewModelScope.launch(Dispatchers.IO) { PlayServices.restart(app) }
    }

    private fun write(autoHandledByService: Boolean = false, block: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            block()
            PlayServices.markRestartNeeded(autoHandledByService = autoHandledByService)
        }
    }

    private suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_IMPORT_BYTES).toString(Charsets.UTF_8) }
        } catch (_: Exception) { null }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 300_000
        const val REVOCATION_TTL_MS = 6L * 60 * 60 * 1000
        // Includes migration, reload and revocation so a late response cannot describe
        // (or cache revocation for) a different credential. Shared across screen instances.
        val KEYBOX_OPERATION = Mutex()
    }
}
