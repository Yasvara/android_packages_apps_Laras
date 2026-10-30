/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
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

    private val _state = MutableStateFlow(IntegrityUiState())
    val state: StateFlow<IntegrityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val dropped = keybox.migrate()
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
            val info = keybox.info()
            _state.update {
                it.copy(
                    loaded = true,
                    pif = it.pif.copy(
                        stored = pif.current(),
                        enabled = store.getBoolean(IntegrityKeys.PIF_ENABLED, true),
                        mode = store.getInt(IntegrityKeys.SPOOF_MODE, IntegrityKeys.MODE_AUTO),
                        customUrl = pif.customUrl,
                        channel = pif.channel,
                        devices = pif.library(),
                        activeDevice = pif.device,
                    ),
                    keybox = it.keybox.copy(
                        info = info,
                        revocation = if (info == null) Revocation.Unknown() else keybox.lastRevocation(),
                        enabled = store.getBoolean(IntegrityKeys.TS_ENABLED, true),
                        interim = info != null &&
                            keybox.custody() == KeyboxRepository.Custody.INTERIM,
                    ),
                    targetCount = targets.selected().size,
                )
            }
        }
    }

    // ---- Play Integrity Fix -------------------------------------------------------------

    fun setPifEnabled(on: Boolean) {
        _state.update { it.copy(pif = it.pif.copy(enabled = on)) }
        write { store.putBoolean(IntegrityKeys.PIF_ENABLED, on) }
    }

    fun setSpoofMode(mode: Int) {
        _state.update { it.copy(pif = it.pif.copy(mode = mode)) }
        write { store.putInt(IntegrityKeys.SPOOF_MODE, mode) }
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
                    if (result.changed) PlayServices.markRestartNeeded()
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
                PlayServices.markRestartNeeded()
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
            if (stored == null) {
                pifNotice(app.getString(R.string.pif_import_invalid), error = true)
            } else {
                PlayServices.markRestartNeeded()
                _state.update {
                    it.copy(pif = it.pif.copy(stored = stored, devices = pif.library(),
                        notice = Notice(app.getString(R.string.pif_imported, stored.profile.model))))
                }
            }
        }
    }

    fun resetProfile() {
        PlayServices.markRestartNeeded()
        viewModelScope.launch(Dispatchers.IO) {
            pif.resetToBuiltIn()
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
        write { store.putBoolean(IntegrityKeys.TS_ENABLED, on) }
    }

    fun importKeybox(uri: Uri) {
        viewModelScope.launch {
            val text = readText(uri).orEmpty()
            when (val result = withContext(Dispatchers.IO) { keybox.import(text) }) {
                is KeyboxImport.Rejected -> keyboxNotice(app.getString(result.problem.message()), error = true)
                is KeyboxImport.Imported -> {
                    PlayServices.markRestartNeeded()
                    _state.update {
                        it.copy(keybox = it.keybox.copy(info = result.info, revocation = Revocation.Unknown(),
                            notice = Notice(app.getString(R.string.keybox_imported))))
                    }
                    checkRevocation()
                }
            }
        }
    }

    fun deleteKeybox() {
        PlayServices.markRestartNeeded()
        viewModelScope.launch(Dispatchers.IO) {
            keybox.delete()
            _state.update {
                it.copy(keybox = it.keybox.copy(info = null, revocation = Revocation.Unknown(),
                    notice = Notice(app.getString(R.string.keybox_deleted))))
            }
        }
    }

    fun checkRevocation() {
        if (_state.value.keybox.checking || _state.value.keybox.info == null) return
        _state.update { it.copy(keybox = it.keybox.copy(checking = true)) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { keybox.checkRevocation() }
            val notice = (result as? Revocation.Unknown)?.failure?.let {
                Notice(app.getString(R.string.keybox_check_failed, app.describe(it)), error = true)
            }
            _state.update {
                it.copy(keybox = it.keybox.copy(checking = false, revocation = result,
                    notice = notice ?: it.keybox.notice))
            }
        }
    }

    private fun keyboxNotice(text: String, error: Boolean) =
        _state.update { it.copy(keybox = it.keybox.copy(notice = Notice(text, error))) }

    // ---- Integrity check ----------------------------------------------------------------

    fun runCheck() {
        if (_state.value.check.running) return
        _state.update { it.copy(check = it.check.copy(running = true)) }
        viewModelScope.launch {
            val report = checker.run()
            _state.update { it.copy(check = CheckUi(running = false, report = report)) }
        }
    }

    fun restartPlayServices() = PlayServices.restart(app)

    private fun write(block: () -> Unit) {
        PlayServices.markRestartNeeded()
        viewModelScope.launch(Dispatchers.IO) { block() }
    }

    private suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            app.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_IMPORT_BYTES).toString(Charsets.UTF_8) }
        } catch (_: Exception) { null }
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 300_000
    }
}
