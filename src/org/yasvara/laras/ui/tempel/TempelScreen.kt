/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.tempel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale
import org.yasvara.laras.R
import org.yasvara.laras.data.tempel.ProfileKind
import org.yasvara.laras.data.tempel.RevertType
import org.yasvara.laras.data.tempel.ScannedCard
import org.yasvara.laras.data.tempel.TempelProfile
import org.yasvara.laras.data.tempel.TempelRepository
import org.yasvara.laras.data.tempel.WriteTarget
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

@Composable
fun TempelScreen(
    onBack: () -> Unit,
    vm: TempelViewModel = viewModel(),
) {
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }

    var showManualDialog by remember { mutableStateOf(false) }
    var showNdefDialog by remember { mutableStateOf(false) }
    var showWalletDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    // Live-sync NFC ON/OFF state & refresh on resume (unregisters immediately when leaving screen).
    DisposableEffect(context, lifecycleOwner) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val st = intent?.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
                if (st == NfcAdapter.STATE_ON || st == NfcAdapter.STATE_OFF) {
                    vm.refresh()
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Enable NFC Reader Mode strictly while Scan tab or armed Write tab is open and NFC is ON.
    val readerActive = ui.caps.nfcOn && (ui.tab == TempelTab.SCAN || (ui.tab == TempelTab.WRITE && ui.writeArmed))
    DisposableEffect(activity, readerActive) {
        val adapter = activity?.let { NfcAdapter.getDefaultAdapter(it) }
        if (activity != null && adapter != null && readerActive) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            runCatching {
                adapter.enableReaderMode(activity, { tag -> vm.onTagDiscovered(tag) }, flags, Bundle())
            }
        }
        onDispose {
            if (activity != null && adapter != null) {
                runCatching { adapter.disableReaderMode(activity) }
            }
        }
    }

    LaunchedEffect(ui.statusMessage) {
        val msg = ui.statusMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        vm.clearMessage()
    }

    val activeProfile = ui.profiles.firstOrNull { it.id == ui.activeId }

    Page(
        title = stringResource(R.string.tempel_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (ui.caps.observeSupported) {
                IconButton(onClick = vm::toggleShield) {
                    Icon(
                        Icons.Rounded.Shield,
                        contentDescription = stringResource(R.string.tempel_shield),
                        tint = if (ui.caps.observeEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        // Active card hero
        item(key = "hero") {
            ActiveCardHero(
                profile = activeProfile,
                nfcOn = ui.caps.nfcOn,
                shieldOn = ui.caps.observeEnabled,
                observeSupported = ui.caps.observeSupported,
                onEnableNfc = vm::enableNfc,
                onReturnToPayment = { vm.activate(0) },
                onToggleShield = vm::toggleShield,
            )
            Spacer(Modifier.height(12.dp))
        }

        // 3-tab selector: Vault / Scan / Write
        item(key = "tabs") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val tabs = listOf(
                    TempelTab.VAULT to R.string.tempel_tab_vault,
                    TempelTab.SCAN to R.string.tempel_tab_scan,
                    TempelTab.WRITE to R.string.tempel_tab_write,
                )
                tabs.forEachIndexed { i, (t, labelRes) ->
                    SegmentedButton(
                        selected = ui.tab == t,
                        onClick = { vm.setTab(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                        label = { Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        when (ui.tab) {
            TempelTab.VAULT -> {
                item(key = "quick_actions") {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { showManualDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.tempel_add_uid), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { showNdefDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Wifi, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("NDEF", maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showWalletDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.CreditCard, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.tempel_add_wallet), maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.tempel_import), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (ui.profiles.isEmpty()) {
                    item(key = "empty") {
                        LarasEmptyState(
                            title = stringResource(R.string.tempel_empty_title),
                            body = stringResource(R.string.tempel_empty_body),
                        )
                    }
                } else {
                    item(key = "vault_hdr") {
                        LarasSectionHeader(stringResource(R.string.tempel_profiles_header))
                    }
                    items(ui.profiles.size, key = { ui.profiles[it].id }) { i ->
                        val p = ui.profiles[i]
                        ProfileRow(
                            profile = p,
                            nfcOn = ui.caps.nfcOn,
                            active = ui.caps.nfcOn && p.id == ui.activeId && !ui.caps.observeEnabled,
                            isDefaultPayment = p.id == ui.defaultPaymentId,
                            shape = groupedShape(i, ui.profiles.size),
                            onActivate = { vm.activate(p.id) },
                            onExport = {
                                vm.exportText(p.id, 1) { txt ->
                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                    cm?.setPrimaryClip(ClipData.newPlainText(p.name, txt))
                                }
                            },
                            onDelete = { vm.remove(p.id) },
                        )
                        Spacer(Modifier.height(LarasSpacing.ItemGap))
                    }
                }
            }

            TempelTab.SCAN -> {
                item(key = "scan_panel") {
                    ScanPanel(
                        nfcOn = ui.caps.nfcOn,
                        busy = ui.scanningBusy,
                        scanned = ui.scanned,
                        onSave = { name -> vm.saveScanned(name) },
                        onJumpWrite = { name ->
                            vm.saveScanned(name)
                            vm.setTab(TempelTab.WRITE)
                        },
                    )
                }
            }

            TempelTab.WRITE -> {
                item(key = "write_panel") {
                    WritePanel(
                        profiles = ui.profiles.filter { it.kind != ProfileKind.PAYMENT_WALLET },
                        sourceId = ui.writeSourceId,
                        target = ui.writeTarget,
                        gen4PwdHex = ui.gen4PwdHex,
                        armed = ui.writeArmed,
                        busy = ui.writeBusy,
                        report = ui.writeReport,
                        onSelectSource = vm::selectWriteSource,
                        onSelectTarget = vm::selectWriteTarget,
                        onGen4Pwd = vm::setGen4PwdHex,
                        onToggleArm = { vm.setWriteArmed(!ui.writeArmed) },
                    )
                }
            }
        }
    }

    if (showManualDialog) {
        ManualCardDialog(
            onDismiss = { showManualDialog = false },
            onSave = { name, kind, uid, atqa, sak, sc, pmm, rev, sec ->
                vm.addManualCard(name, kind, uid, atqa, sak, sc, pmm, rev, sec, 0xFF2563EB.toInt())
                showManualDialog = false
            },
        )
    }
    if (showNdefDialog) {
        NdefDialog(
            onDismiss = { showNdefDialog = false },
            onSave = { name, kind, a, b, c ->
                vm.addNdefCard(name, kind, a, b, c)
                showNdefDialog = false
            },
        )
    }
    if (showWalletDialog) {
        WalletDialog(
            wallets = ui.installedWallets,
            onDismiss = { showWalletDialog = false },
            onPick = { label, pkg ->
                vm.addPaymentWallet(label, pkg)
                showWalletDialog = false
            },
        )
    }
    if (showImportDialog) {
        ImportDumpDialog(
            onDismiss = { showImportDialog = false },
            onImport = { name, fmt, txt ->
                vm.importDumpText(name, fmt, txt)
                showImportDialog = false
            },
        )
    }
}

@Composable
private fun ActiveCardHero(
    profile: TempelProfile?,
    nfcOn: Boolean,
    shieldOn: Boolean,
    observeSupported: Boolean,
    onEnableNfc: () -> Unit,
    onReturnToPayment: () -> Unit,
    onToggleShield: () -> Unit,
) {
    val baseColor = when {
        !nfcOn -> MaterialTheme.colorScheme.surfaceVariant
        shieldOn -> Color(0xFF334155)
        profile != null -> Color(profile.colorArgb)
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = if (!nfcOn) MaterialTheme.colorScheme.onSurfaceVariant else Color.White

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(baseColor, baseColor.copy(alpha = 0.80f))))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            !nfcOn -> stringResource(R.string.tempel_tile_nfc_off)
                            shieldOn -> stringResource(R.string.tempel_shield_active)
                            profile != null -> profile.kind.name.replace('_', ' ')
                            else -> stringResource(R.string.tempel_tile_ready)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                    Icon(
                        if (shieldOn) Icons.Rounded.Security else Icons.Rounded.Contactless,
                        contentDescription = null,
                        tint = contentColor,
                    )
                }
                Text(
                    text = when {
                        !nfcOn -> stringResource(R.string.tempel_enable_nfc_hint)
                        shieldOn -> stringResource(R.string.tempel_shield)
                        profile != null -> profile.name
                        else -> stringResource(R.string.tempel_default_payment)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                if (profile != null && !shieldOn && nfcOn) {
                    val sub = when (profile.kind) {
                        ProfileKind.PAYMENT_WALLET -> profile.walletPkg
                        else -> buildString {
                            if (profile.uidHex.isNotEmpty()) {
                                append("UID ")
                                append(profile.uidHex.chunked(2).joinToString(":"))
                            }
                            if (profile.kind == ProfileKind.UID_TYPE_A) {
                                append(" · SAK ")
                                append(profile.sakHex)
                            }
                        }
                    }
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = contentColor.copy(alpha = 0.9f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!nfcOn) {
                        Button(onClick = onEnableNfc) {
                            Text(stringResource(R.string.tempel_turn_on_nfc))
                        }
                    }
                    if (nfcOn && (shieldOn || (profile != null && profile.kind != ProfileKind.PAYMENT_WALLET))) {
                        AssistChip(
                            onClick = onReturnToPayment,
                            label = { Text(stringResource(R.string.tempel_return_payment), color = contentColor) },
                        )
                    }
                    if (nfcOn && observeSupported) {
                        AssistChip(
                            onClick = onToggleShield,
                            label = {
                                Text(
                                    stringResource(if (shieldOn) R.string.tempel_unshield else R.string.tempel_shield),
                                    color = contentColor,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: TempelProfile,
    nfcOn: Boolean,
    active: Boolean,
    isDefaultPayment: Boolean,
    shape: Shape,
    onActivate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = shape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else LarasSurfaces.card,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (nfcOn) 1f else 0.5f)
            .clip(shape)
            .clickable(enabled = nfcOn, onClick = onActivate),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Color(profile.colorArgb)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when (profile.kind) {
                        ProfileKind.PAYMENT_WALLET -> Icons.Rounded.CreditCard
                        ProfileKind.NDEF_T4T -> Icons.Rounded.Nfc
                        else -> Icons.Rounded.DoorFront
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (active) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                val meta = when (profile.kind) {
                    ProfileKind.PAYMENT_WALLET ->
                        if (isDefaultPayment) "${profile.walletPkg} · Default" else profile.walletPkg
                    else -> buildString {
                        append(profile.kind.name.replace('_', ' '))
                        if (profile.uidHex.isNotEmpty()) {
                            append(" · ")
                            append(profile.uidHex.chunked(2).joinToString(":"))
                        }
                        if (profile.payloadLen > 0) append(" · ${profile.payloadLen} B")
                    }
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (profile.kind != ProfileKind.PAYMENT_WALLET) {
                IconButton(onClick = onExport) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Export .nfc")
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete")
            }
        }
    }
}

@Composable
private fun ScanPanel(
    nfcOn: Boolean,
    busy: Boolean,
    scanned: ScannedCard?,
    onSave: (String) -> Unit,
    onJumpWrite: (String) -> Unit,
) {
    var saveName by remember(scanned) { mutableStateOf(scanned?.techLabel.orEmpty()) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LarasSurfaces.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                else Icon(Icons.Rounded.Nfc, null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (!nfcOn) stringResource(R.string.tempel_enable_nfc_hint)
                    else if (busy) stringResource(R.string.tempel_scanning)
                    else stringResource(R.string.tempel_scan_prompt),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (scanned != null) {
                val uidStr = TempelRepository.bytesToHex(scanned.uid, colon = true)
                val atqaStr = TempelRepository.bytesToHex(scanned.atqa)
                Text(
                    "${scanned.techLabel}\nUID: $uidStr\nATQA: $atqaStr · SAK: %02X".format(scanned.sak),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                if (scanned.totalSectors > 0) {
                    val hwTag = if (scanned.usedHardware2f45) " · SN220U 2F45 HW" else ""
                    Text(
                        "MIFARE sectors readable: ${scanned.readableSectors}/${scanned.totalSectors}$hwTag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                scanned.eMoney?.let { em ->
                    val idr = NumberFormat.getNumberInstance(Locale("in", "ID")).format(em.balanceIdr)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Rp $idr",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (em.cardNumber.isNotEmpty()) {
                                Text(
                                    em.cardNumber.chunked(4).joinToString(" "),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
                val verdictText = when (scanned.analysis.verdict) {
                    0 -> stringResource(R.string.tempel_verdict_uid)
                    1 -> stringResource(R.string.tempel_verdict_t4t)
                    2 -> stringResource(R.string.tempel_verdict_felica)
                    3 -> stringResource(R.string.tempel_verdict_typeb)
                    4 -> stringResource(R.string.tempel_verdict_mfc_enc)
                    5 -> stringResource(R.string.tempel_verdict_emoney)
                    6 -> stringResource(R.string.tempel_verdict_ntag)
                    else -> stringResource(R.string.tempel_verdict_nfcv)
                }
                Text(
                    verdictText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    label = { Text(stringResource(R.string.tempel_card_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(saveName) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tempel_save_vault))
                    }
                    if (scanned.analysis.canWrite) {
                        FilledTonalButton(onClick = { onJumpWrite(saveName) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.tempel_clone_to_card))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WritePanel(
    profiles: List<TempelProfile>,
    sourceId: Int,
    target: WriteTarget,
    gen4PwdHex: String,
    armed: Boolean,
    busy: Boolean,
    report: org.yasvara.laras.data.tempel.WriteReport?,
    onSelectSource: (Int) -> Unit,
    onSelectTarget: (WriteTarget) -> Unit,
    onGen4Pwd: (String) -> Unit,
    onToggleArm: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LarasSurfaces.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.EditNote, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.tempel_writer_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.tempel_writer_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (profiles.isEmpty()) {
                Text(stringResource(R.string.tempel_writer_no_source), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.tempel_writer_source), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    profiles.forEach { p ->
                        FilterChip(
                            selected = p.id == sourceId,
                            onClick = { onSelectSource(p.id) },
                            label = { Text("${p.name} (${p.uidHex.ifEmpty { p.kind.name }})") },
                        )
                    }
                }

                Text(stringResource(R.string.tempel_writer_target), style = MaterialTheme.typography.labelLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WriteTarget.entries.forEach { t ->
                        FilterChip(
                            selected = t == target,
                            onClick = { onSelectTarget(t) },
                            label = { Text(t.label) },
                        )
                    }
                }

                if (target == WriteTarget.GEN4_UMC) {
                    OutlinedTextField(
                        value = gen4PwdHex,
                        onValueChange = onGen4Pwd,
                        label = { Text("Gen4 Password (8 hex, default 00000000)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Button(
                    onClick = onToggleArm,
                    enabled = sourceId != 0 && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        stringResource(
                            if (armed) R.string.tempel_writer_waiting else R.string.tempel_writer_arm,
                        ),
                    )
                }

                report?.let { r ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (r.ok) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            r.message,
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualCardDialog(
    onDismiss: () -> Unit,
    onSave: (String, ProfileKind, String, String, String, String, String, RevertType, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ProfileKind.UID_TYPE_A) }
    var uid by remember { mutableStateOf("") }
    var atqa by remember { mutableStateOf("0400") }
    var sak by remember { mutableStateOf("08") }
    var sc by remember { mutableStateOf("12FC") }
    var pmm by remember { mutableStateOf("00F1000000014300") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tempel_add_uid)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.tempel_card_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ProfileKind.UID_TYPE_A to "Type-A", ProfileKind.UID_TYPE_B to "Type-B", ProfileKind.FELICA_HCEF to "FeliCa")
                        .forEach { (k, lbl) ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(lbl) })
                        }
                }
                OutlinedTextField(
                    uid, { uid = it },
                    label = {
                        Text(
                            when (kind) {
                                ProfileKind.UID_TYPE_B -> "PUPI (4B hex)"
                                ProfileKind.FELICA_HCEF -> "IDm (8B hex)"
                                else -> "UID (4/7/10B hex)"
                            },
                        )
                    },
                    singleLine = true,
                )
                if (kind == ProfileKind.UID_TYPE_A) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(atqa, { atqa = it }, label = { Text("ATQA") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(sak, { sak = it }, label = { Text("SAK") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                } else if (kind == ProfileKind.FELICA_HCEF) {
                    OutlinedTextField(sc, { sc = it }, label = { Text("System Code (2B)") }, singleLine = true)
                    OutlinedTextField(pmm, { pmm = it }, label = { Text("PMm (8B)") }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, kind, uid, atqa, sak, sc, pmm, RevertType.TIMEOUT, 60) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun NdefDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) } // 0=URI, 1=Text, 2=Wi-Fi, 3=vCard
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New NDEF Card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.tempel_card_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("URL", "Text", "Wi-Fi", "vCard").forEachIndexed { idx, lbl ->
                        FilterChip(selected = kind == idx, onClick = { kind = idx }, label = { Text(lbl) })
                    }
                }
                when (kind) {
                    0 -> OutlinedTextField(f1, { f1 = it }, label = { Text("https://...") }, singleLine = true)
                    1 -> OutlinedTextField(f2, { f2 = it }, label = { Text("Text") })
                    2 -> {
                        OutlinedTextField(f1, { f1 = it }, label = { Text("Wi-Fi SSID") }, singleLine = true)
                        OutlinedTextField(f2, { f2 = it }, label = { Text("Password") }, singleLine = true)
                    }
                    else -> {
                        OutlinedTextField(f1, { f1 = it }, label = { Text("Full Name") }, singleLine = true)
                        OutlinedTextField(f2, { f2 = it }, label = { Text("Phone") }, singleLine = true)
                        OutlinedTextField(f3, { f3 = it }, label = { Text("Email") }, singleLine = true)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, kind, f1, f2, f3) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun WalletDialog(
    wallets: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tempel_add_wallet)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (wallets.isEmpty()) {
                    Text("No NFC payment apps found.")
                } else {
                    wallets.forEach { (pkg, label) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = LarasSurfaces.card,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(label, pkg) },
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun ImportDumpDialog(
    onDismiss: () -> Unit,
    onImport: (String, Int, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var fmt by remember { mutableIntStateOf(1) } // 0=MCT, 1=Flipper .nfc
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tempel_import)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.tempel_card_name)) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = fmt == 1, onClick = { fmt = 1 }, label = { Text("Flipper .nfc") })
                    FilterChip(selected = fmt == 0, onClick = { fmt = 0 }, label = { Text("MCT .mct") })
                }
                OutlinedTextField(text, { text = it }, label = { Text("Paste dump text") }, maxLines = 6)
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(name, fmt, text) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
