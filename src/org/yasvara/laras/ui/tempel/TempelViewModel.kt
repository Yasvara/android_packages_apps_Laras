/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.tempel

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.data.tempel.ProfileKind
import org.yasvara.laras.data.tempel.RevertType
import org.yasvara.laras.data.tempel.ScannedCard
import org.yasvara.laras.data.tempel.SecurityGate
import org.yasvara.laras.data.tempel.TempelCaps
import org.yasvara.laras.data.tempel.TempelProfile
import org.yasvara.laras.data.tempel.TempelRepository
import org.yasvara.laras.data.tempel.TempelTagEngine
import org.yasvara.laras.data.tempel.WriteReport
import org.yasvara.laras.data.tempel.WriteTarget
import org.yasvara.laras.tempel.TempelTile

enum class TempelTab { VAULT, SCAN, WRITE }

data class TempelUiState(
    val available: Boolean = true,
    val caps: TempelCaps = TempelCaps(false, false, false, "", 0),
    val profiles: List<TempelProfile> = emptyList(),
    val activeId: Int = 0,
    val defaultPaymentId: Int = 0,
    val installedWallets: List<Pair<String, String>> = emptyList(),
    val tab: TempelTab = TempelTab.VAULT,
    val scanningBusy: Boolean = false,
    val scanned: ScannedCard? = null,
    val writeTarget: WriteTarget = WriteTarget.GEN2_CUID,
    val writeSourceId: Int = 0,
    val writeArmed: Boolean = false,
    val writeBusy: Boolean = false,
    val writeReport: WriteReport? = null,
    val gen4PwdHex: String = "00000000",
    val statusMessage: String? = null,
)

class TempelViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = TempelRepository(app)
    private val _ui = MutableStateFlow(TempelUiState())
    val ui: StateFlow<TempelUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                val avail = repo.available
                val caps = if (avail) repo.capabilities() else TempelCaps(false, false, false, "", 0)
                val list = if (avail) repo.listProfiles() else emptyList()
                val active = if (avail) repo.activeProfileId() else 0
                val defPay = if (avail) repo.defaultPaymentId() else 0
                val wallets = repo.installedPaymentWallets()
                listOf(avail, caps, list, active, defPay, wallets)
            }
            @Suppress("UNCHECKED_CAST")
            val list = data[2] as List<TempelProfile>
            @Suppress("UNCHECKED_CAST")
            val wallets = data[5] as List<Pair<String, String>>
            _ui.update { cur ->
                val src = if (cur.writeSourceId != 0 && list.any { it.id == cur.writeSourceId }) {
                    cur.writeSourceId
                } else {
                    list.firstOrNull { it.kind != ProfileKind.PAYMENT_WALLET }?.id ?: 0
                }
                cur.copy(
                    available = data[0] as Boolean,
                    caps = data[1] as TempelCaps,
                    profiles = list,
                    activeId = data[3] as Int,
                    defaultPaymentId = data[4] as Int,
                    installedWallets = wallets,
                    writeSourceId = src,
                )
            }
        }
    }

    fun setTab(tab: TempelTab) {
        _ui.update { it.copy(tab = tab, writeArmed = if (tab != TempelTab.WRITE) false else it.writeArmed) }
    }

    fun clearMessage() {
        _ui.update { it.copy(statusMessage = null) }
    }

    fun activate(id: Int) {
        viewModelScope.launch {
            val caps = withContext(Dispatchers.IO) { repo.capabilities() }
            if (id != 0 && !caps.nfcOn) {
                _ui.update { it.copy(caps = caps, statusMessage = "Turn on NFC first") }
                return@launch
            }
            val ok = withContext(Dispatchers.IO) { repo.activate(id) }
            TempelTile.requestRefresh(getApplication())
            refresh()
            _ui.update { it.copy(statusMessage = if (ok) null else "Failed to switch profile (check NFC is ON)") }
        }
    }

    fun enableNfc() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val adapter = android.nfc.NfcAdapter.getDefaultAdapter(getApplication())
                    adapter?.enable() == true
                }.getOrDefault(false)
            }
            if (!ok) {
                _ui.update { it.copy(statusMessage = "Could not turn on NFC") }
            }
        }
    }

    fun toggleShield() {
        viewModelScope.launch {
            val target = !_ui.value.caps.observeEnabled
            val ok = withContext(Dispatchers.IO) { repo.setShield(target) }
            TempelTile.requestRefresh(getApplication())
            refresh()
            if (!ok) _ui.update { it.copy(statusMessage = "Observe Mode unavailable (check NFC is ON)") }
        }
    }

    fun remove(id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.remove(id) }
            TempelTile.requestRefresh(getApplication())
            refresh()
        }
    }

    fun setDefaultPayment(id: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.setDefaultPayment(id) }
            refresh()
        }
    }

    fun addManualCard(
        name: String,
        kind: ProfileKind,
        uidHex: String,
        atqaHex: String,
        sakHex: String,
        sysCodeHex: String,
        pmmHex: String,
        revertType: RevertType,
        revertSec: Int,
        colorArgb: Int,
    ) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val uid = TempelRepository.hexToBytes(uidHex)
                val atqa = TempelRepository.hexToBytes(atqaHex).takeIf { it.size == 2 }
                    ?: byteArrayOf(0x04, 0x00)
                val sak = sakHex.toIntOrNull(16) ?: 0x08
                val sc = TempelRepository.hexToBytes(sysCodeHex).takeIf { it.size == 2 }
                    ?: byteArrayOf(0x12, 0xFC.toByte())
                val pmm = TempelRepository.hexToBytes(pmmHex).takeIf { it.size == 8 }
                    ?: byteArrayOf(0x00, 0xF1.toByte(), 0, 0, 0, 1, 0x43, 0)
                repo.upsertProfile(
                    kind = kind,
                    name = name.ifBlank { "Card" },
                    colorArgb = colorArgb,
                    revertType = revertType,
                    revertSec = revertSec,
                    uid = uid,
                    atqa = atqa,
                    sak = sak,
                    systemCode = sc,
                    pmm = pmm,
                )
            }
            refresh()
            _ui.update {
                it.copy(statusMessage = if (id > 0) "Saved \"$name\"" else "Invalid card fields or vault full")
            }
        }
    }

    fun addPaymentWallet(name: String, pkg: String) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                repo.upsertProfile(
                    kind = ProfileKind.PAYMENT_WALLET,
                    name = name.ifBlank { pkg },
                    colorArgb = 0xFF00AA5B.toInt(),
                    gate = SecurityGate.UNLOCKED,
                    revertType = RevertType.STICKY,
                    revertSec = 0,
                    walletPkg = pkg,
                )
            }
            refresh()
            _ui.update { it.copy(statusMessage = if (id > 0) "Added wallet \"$name\"" else "Failed to add wallet") }
        }
    }

    fun addNdefCard(name: String, ndefKind: Int, a: String, b: String, c: String) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val payload = repo.buildNdef(ndefKind, a, b, c)
                if (payload.isEmpty()) return@withContext 0
                repo.upsertProfile(
                    kind = ProfileKind.NDEF_T4T,
                    name = name.ifBlank { "NDEF Tag" },
                    colorArgb = 0xFF7C3AED.toInt(),
                    sak = 0x20,
                    ratsTb1 = 0x78,
                    payload = payload,
                )
            }
            refresh()
            _ui.update { it.copy(statusMessage = if (id > 0) "Created NDEF card \"$name\"" else "Empty NDEF payload") }
        }
    }

    fun importDumpText(name: String, format: Int, text: String) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val parsed = repo.importDump(format, text) ?: return@withContext 0
                val kind = when (parsed.tech) {
                    2 -> ProfileKind.UID_TYPE_B
                    3 -> ProfileKind.FELICA_HCEF
                    else -> ProfileKind.UID_TYPE_A
                }
                val dumpBytes = when {
                    parsed.mfcBlocksHex.isNotEmpty() -> TempelRepository.hexToBytes(parsed.mfcBlocksHex)
                    parsed.ntagPagesHex.isNotEmpty() -> TempelRepository.hexToBytes(parsed.ntagPagesHex)
                    else -> ByteArray(0)
                }
                repo.upsertProfile(
                    kind = kind,
                    name = name.ifBlank { "Imported ${parsed.uidHex.take(8)}" },
                    uid = TempelRepository.hexToBytes(parsed.uidHex),
                    atqa = TempelRepository.hexToBytes(parsed.atqaHex).takeIf { it.size == 2 }
                        ?: byteArrayOf(0x04, 0x00),
                    sak = parsed.sakHex.toIntOrNull(16) ?: 0x08,
                    systemCode = TempelRepository.hexToBytes(parsed.sysCodeHex).takeIf { it.size == 2 }
                        ?: ByteArray(2),
                    pmm = TempelRepository.hexToBytes(parsed.pmmHex).takeIf { it.size == 8 }
                        ?: ByteArray(8),
                    payload = dumpBytes,
                )
            }
            refresh()
            _ui.update { it.copy(statusMessage = if (id > 0) "Imported card #$id" else "Could not parse dump") }
        }
    }

    fun exportText(id: Int, format: Int, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val txt = withContext(Dispatchers.IO) { repo.exportProfile(id, format) }
            if (txt != null) onResult(txt)
            else _ui.update { it.copy(statusMessage = "Export failed") }
        }
    }

    fun onTagDiscovered(tag: Tag) {
        val state = _ui.value
        if (state.tab == TempelTab.SCAN && !state.scanningBusy) {
            _ui.update { it.copy(scanningBusy = true) }
            viewModelScope.launch {
                val card = withContext(Dispatchers.IO) { TempelTagEngine.scan(tag, repo) }
                _ui.update { it.copy(scanningBusy = false, scanned = card) }
            }
        } else if (state.tab == TempelTab.WRITE && state.writeArmed && !state.writeBusy) {
            val profile = state.profiles.firstOrNull { it.id == state.writeSourceId } ?: return
            _ui.update { it.copy(writeBusy = true, writeReport = null) }
            viewModelScope.launch {
                val report = withContext(Dispatchers.IO) {
                    val payload = repo.getPayload(profile.id)
                    val pwd = state.gen4PwdHex.toLongOrNull(16)?.toInt() ?: 0
                    TempelTagEngine.writeToTag(tag, state.writeTarget, profile, payload, repo, pwd)
                }
                _ui.update { it.copy(writeBusy = false, writeArmed = false, writeReport = report) }
            }
        }
    }

    fun saveScanned(name: String) {
        val c = _ui.value.scanned ?: return
        viewModelScope.launch {
            val payload = if (c.ndefBytes.isNotEmpty() && c.suggestedKind == ProfileKind.NDEF_T4T) {
                c.ndefBytes
            } else {
                c.dumpBytes
            }
            val id = withContext(Dispatchers.IO) {
                repo.upsertProfile(
                    kind = c.suggestedKind,
                    name = name.ifBlank { c.techLabel },
                    uid = c.uid,
                    atqa = c.atqa,
                    sak = c.sak,
                    histBytes = c.histBytes,
                    appData = c.appData,
                    systemCode = c.systemCode,
                    pmm = c.pmm,
                    payload = payload,
                )
            }
            refresh()
            _ui.update {
                it.copy(
                    tab = TempelTab.VAULT,
                    statusMessage = if (id > 0) "Saved \"$name\" to vault" else "Failed to save card",
                )
            }
        }
    }

    fun selectWriteTarget(t: WriteTarget) = _ui.update { it.copy(writeTarget = t, writeReport = null) }
    fun selectWriteSource(id: Int) = _ui.update { it.copy(writeSourceId = id, writeReport = null) }
    fun setGen4PwdHex(hex: String) = _ui.update { it.copy(gen4PwdHex = hex.take(8)) }
    fun setWriteArmed(armed: Boolean) = _ui.update { it.copy(writeArmed = armed, writeReport = null) }
}
