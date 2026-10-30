/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.integrity.IntegrityKeys
import org.yasvara.laras.data.integrity.PifChannel
import org.yasvara.laras.data.integrity.PifProfile
import org.yasvara.laras.data.integrity.PifSource
import org.yasvara.laras.data.integrity.Revocation
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape

private enum class SpoofMode(override val value: Int, override val label: Int, val hint: Int) : ChoiceOption {
    AUTO(IntegrityKeys.MODE_AUTO, R.string.integrity_mode_auto, R.string.mode_auto_hint),
    FULL(IntegrityKeys.MODE_FULL, R.string.integrity_mode_full, R.string.mode_full_hint),
    MODEL(IntegrityKeys.MODE_MODEL_ONLY, R.string.integrity_mode_model, R.string.mode_model_hint);

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

/** Profile, keybox and target apps as one grouped list; a row opens its sheet. */
@Composable
internal fun ProtectionList(
    state: IntegrityUiState,
    onProfile: () -> Unit,
    onPifEnabled: (Boolean) -> Unit,
    onKeybox: () -> Unit,
    onKeyboxEnabled: (Boolean) -> Unit,
    onTargets: () -> Unit,
) {
    val pif = state.pif
    val keybox = state.keybox
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ListRow(
            icon = Icons.Rounded.PhoneAndroid,
            title = stringResource(R.string.integrity_pif_title),
            summary = pif.notice?.takeIf { it.error }?.text
                ?: pif.stored?.let { profileLine(it.profile) }
                ?: stringResource(R.string.integrity_pif_empty),
            summaryColor = if (pif.notice?.error == true) MaterialTheme.colorScheme.error else null,
            index = 0,
            onClick = onProfile,
            checked = pif.enabled,
            onCheckedChange = onPifEnabled,
        )
        ListRow(
            icon = Icons.Rounded.Key,
            title = stringResource(R.string.integrity_ts_title),
            summary = keyboxLine(keybox),
            summaryColor = when {
                keybox.revocation is Revocation.Revoked || keybox.info?.expired == true ->
                    MaterialTheme.colorScheme.error
                else -> null
            },
            index = 1,
            onClick = onKeybox,
            checked = keybox.enabled && keybox.info != null,
            onCheckedChange = onKeyboxEnabled,
            switchEnabled = keybox.info != null,
        )
        ListRow(
            icon = Icons.Rounded.Apps,
            title = stringResource(R.string.targets_title),
            summary = pluralStringResource(R.plurals.targets_count, state.targetCount, state.targetCount),
            index = 2,
            onClick = onTargets,
        )
        Text(
            stringResource(R.string.integrity_bank_body),
            Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val ROWS = 3

/**
 * A list row: tapping it opens its detail, and a switch, when given, sits behind a divider so
 * turning the feature on and off never needs the detail.
 */
@Composable
private fun ListRow(
    icon: ImageVector,
    title: String,
    summary: String,
    index: Int,
    onClick: () -> Unit,
    summaryColor: Color? = null,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    switchEnabled: Boolean = true,
) {
    Surface(shape = groupedShape(index, ROWS), color = MaterialTheme.colorScheme.surfaceBright) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(onClick = onClick)
                    .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (checked == null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
            if (checked != null) {
                VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 18.dp))
                Box(
                    Modifier.fillMaxHeight()
                        .toggleable(checked, enabled = switchEnabled, role = Role.Switch, onValueChange = onCheckedChange)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CheckSwitch(checked, enabled = switchEnabled)
                }
            }
        }
    }
}

@Composable
private fun profileLine(profile: PifProfile): String =
    listOf(profile.model, profile.securityPatch).filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
private fun keyboxLine(ui: KeyboxUi): String {
    val info = ui.info ?: return stringResource(R.string.integrity_keybox_none)
    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(info.notAfter))
    return when {
        ui.revocation is Revocation.Revoked -> stringResource(R.string.integrity_keybox_revoked)
        info.expired -> stringResource(R.string.keybox_expired_on, date)
        ui.revocation is Revocation.Valid ->
            stringResource(R.string.integrity_keybox_valid) + " · " + stringResource(R.string.keybox_expires_on, date)
        else -> stringResource(R.string.keybox_expires_on, date)
    }
}

@Composable
private fun relative(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
private fun NoticeLine(notice: Notice?) {
    notice ?: return
    StatusLine(
        if (notice.error) Icons.Rounded.Error else Icons.Rounded.CheckCircle,
        if (notice.error) Tone.BAD else Tone.GOOD,
        notice.text,
    )
}

/** The profile sheet: what is in effect and one button to refresh it, then where it comes from. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileSheet(
    ui: PifUi,
    onDismiss: () -> Unit,
    onMode: (Int) -> Unit,
    onUpdate: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    onCustomUrl: (String) -> Unit,
    onChannel: (PifChannel) -> Unit,
    onActivateDevice: (String) -> Unit,
    onGoogleDevice: (String) -> Unit,
    onRefreshDevices: () -> Unit,
) {
    val stored = ui.stored
    val mode = SpoofMode.of(ui.mode)
    var custom by rememberSaveable { mutableStateOf(ui.customUrl.isNotEmpty()) }
    var url by rememberSaveable(ui.customUrl) { mutableStateOf(ui.customUrl) }

    Sheet(onDismiss) {
        SheetHeader(
            Icons.Rounded.PhoneAndroid,
            stored?.profile?.model?.ifEmpty { null } ?: stringResource(R.string.integrity_pif_empty),
            stored?.profile?.fingerprint,
        )
        if (stored != null) {
            val age = stored.profile.patchAgeDays()
            StatusLine(
                if (age != null && age > 90) Icons.Rounded.Warning else Icons.Rounded.Info,
                if (age != null && age > 90) Tone.WARN else Tone.NEUTRAL,
                stringResource(R.string.pif_patch, stored.profile.securityPatch.ifEmpty { "—" }),
                detail = buildString {
                    append(stringResource(stored.source.label()))
                    if (stored.source != PifSource.BUILT_IN && stored.fetchedAt > 0) {
                        append(" · ").append(relative(stored.fetchedAt))
                    }
                },
            )
        }
        Button(onUpdate, Modifier.fillMaxWidth().height(48.dp), enabled = !ui.updating) {
            if (ui.updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.integrity_pif_update))
        }
        NoticeLine(ui.notice)

        HorizontalDivider()

        Setting(stringResource(R.string.pif_channel)) {
            val channels = listOf(
                PifChannel.STABLE to R.string.pif_channel_stable,
                PifChannel.BETA to R.string.pif_channel_beta,
                PifChannel.CANARY to R.string.pif_channel_canary,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                channels.forEachIndexed { i, (channel, label) ->
                    SegmentedButton(
                        selected = ui.channel == channel,
                        onClick = { onChannel(channel) },
                        shape = SegmentedButtonDefaults.itemShape(i, channels.size),
                    ) { Text(stringResource(label)) }
                }
            }
            if (ui.channel != PifChannel.STABLE) {
                StatusLine(Icons.Rounded.Warning, Tone.WARN, stringResource(R.string.pif_canary_warning))
            }
        }

        Setting(stringResource(R.string.pif_device)) {
            DevicePicker(ui, onGoogleDevice, onRefreshDevices)
            if (ui.devices.isNotEmpty()) LibraryPicker(ui, onActivateDevice)
        }

        Setting(stringResource(R.string.integrity_spoof_mode)) {
            ChoiceButtons(SpoofMode.entries, mode, { onMode((it as SpoofMode).value) }, Modifier.fillMaxWidth())
            Text(stringResource(mode.hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        AnimatedVisibility(custom) {
            Setting(stringResource(R.string.pif_custom_source)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.pif_custom_url)) },
                    supportingText = { Text(stringResource(R.string.pif_custom_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton({ onCustomUrl(url) }, enabled = url.trim() != ui.customUrl) {
                    Text(stringResource(R.string.pif_custom_url_save))
                }
            }
        }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onImport) { Text(stringResource(R.string.integrity_pif_import)) }
            if (!custom) TextButton({ custom = true }) { Text(stringResource(R.string.pif_custom_source)) }
            TextButton(onReset, enabled = stored?.source != PifSource.BUILT_IN) {
                Text(stringResource(R.string.pif_reset_action))
            }
        }
    }
}

@Composable
private fun DevicePicker(ui: PifUi, onGoogleDevice: (String) -> Unit, onRefreshDevices: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    if (ui.googleDevices.isEmpty() && !ui.devicesLoading) {
        OutlinedButton(onRefreshDevices, Modifier.fillMaxWidth()) { Text(stringResource(R.string.pif_devices_load)) }
        return
    }
    Box {
        PickerField(
            ui.googleDevices.firstOrNull { it.id == ui.activeDevice }?.model ?: stringResource(R.string.pif_device_none),
            loading = ui.devicesLoading,
        ) { open = true }
        DropdownMenu(open, { open = false }) {
            for (device in ui.googleDevices) {
                val active = device.id == ui.activeDevice
                DropdownMenuItem(
                    text = { TwoLines(device.model, device.id) },
                    trailingIcon = if (active) {
                        { Icon(Icons.Rounded.CheckCircle, null) }
                    } else null,
                    onClick = {
                        open = false
                        if (!active) onGoogleDevice(device.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryPicker(ui: PifUi, onActivateDevice: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(stringResource(R.string.pif_library)) }
        DropdownMenu(open, { open = false }) {
            for (device in ui.devices) {
                val active = device.fingerprint == ui.stored?.profile?.fingerprint
                DropdownMenuItem(
                    text = {
                        TwoLines(device.model.ifEmpty { device.fingerprint.take(24) },
                            listOf(device.securityPatch.ifEmpty { "—" },
                                device.fingerprint.split('/').getOrNull(1).orEmpty())
                                .filter { it.isNotEmpty() }.joinToString(" · "))
                    },
                    trailingIcon = if (active) {
                        { Icon(Icons.Rounded.CheckCircle, null) }
                    } else null,
                    onClick = {
                        open = false
                        if (!active) onActivateDevice(device.fingerprint)
                    },
                )
            }
        }
    }
}

/** A read-only field that opens a menu, like an exposed dropdown. */
@Composable
private fun PickerField(text: String, loading: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = !loading, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.ExpandMore, null)
        }
    }
}

/** The keybox sheet: the certificate, whether Google still trusts it, and what to do with it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboxSheet(
    ui: KeyboxUi,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    val info = ui.info

    Sheet(onDismiss) {
        SheetHeader(
            Icons.Rounded.Key,
            stringResource(R.string.integrity_ts_title),
            stringResource(R.string.integrity_ts_summary),
            monospace = false,
        )
        if (info == null) {
            StatusLine(Icons.Rounded.Info, Tone.NEUTRAL, stringResource(R.string.integrity_keybox_none),
                detail = stringResource(R.string.keybox_own_hint))
        } else {
            val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(java.util.Date(info.notAfter))
            StatusLine(
                if (info.expired) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                if (info.expired) Tone.WARN else Tone.NEUTRAL,
                stringResource(R.string.keybox_summary, info.algorithm.uppercase(), info.chainLength),
                detail = stringResource(
                    if (info.expired) R.string.keybox_expired_on else R.string.keybox_expires_on, date) +
                    " · #" + info.serialHex.take(8),
            )
            when (val r = ui.revocation) {
                is Revocation.Revoked -> StatusLine(Icons.Rounded.Error, Tone.BAD,
                    stringResource(R.string.integrity_keybox_revoked), detail = r.reason.ifEmpty { null })
                is Revocation.Valid -> StatusLine(Icons.Rounded.CheckCircle, Tone.GOOD,
                    stringResource(R.string.integrity_keybox_valid),
                    detail = stringResource(R.string.keybox_checked, relative(r.checkedAt)))
                is Revocation.Unknown -> StatusLine(Icons.Rounded.Info, Tone.NEUTRAL,
                    stringResource(R.string.integrity_keybox_present))
            }
            if (ui.interim) {
                StatusLine(Icons.Rounded.Info, Tone.NEUTRAL, stringResource(R.string.keybox_interim_custody))
            }
        }
        NoticeLine(ui.notice)

        Button(onImport, Modifier.fillMaxWidth().height(48.dp)) {
            Text(stringResource(if (info == null) R.string.integrity_keybox_import else R.string.keybox_replace))
        }
        if (info != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onCheck, Modifier.weight(1f), enabled = !ui.checking) {
                    if (ui.checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.integrity_keybox_check))
                }
                TextButton({ confirm = true }) {
                    Text(stringResource(R.string.integrity_keybox_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.keybox_delete_title)) },
            text = { Text(stringResource(R.string.keybox_delete_body)) },
            confirmButton = {
                TextButton({ confirm = false; onDelete() }) { Text(stringResource(R.string.integrity_keybox_delete)) }
            },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) { content() }
    }
}

@Composable
private fun SheetHeader(icon: ImageVector, title: String, detail: String?, monospace: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(Modifier.size(48.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A labelled group inside a sheet. */
@Composable
private fun Setting(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun TwoLines(title: String, detail: String) {
    Column {
        Text(title)
        Text(detail, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
