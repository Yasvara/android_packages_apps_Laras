/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
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
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.data.integrity.PifChannel
import org.yasvara.laras.data.integrity.PifProfile
import org.yasvara.laras.data.integrity.PifSource
import org.yasvara.laras.data.integrity.Revocation
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape

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
    Surface(shape = groupedShape(index, ROWS), color = LarasSurfaces.card) {
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

/** Material 3 Expressive PIF profile sheet: active card, channel selector, device/history rows, 2×2 action grid. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileSheet(
    ui: PifUi,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onOpenDevicePicker: () -> Unit,
    onOpenSavedProfiles: () -> Unit,
    onOpenManualEditor: () -> Unit,
    onImport: () -> Unit,
    onReset: () -> Unit,
    onCustomUrl: (String) -> Unit,
    onChannel: (PifChannel) -> Unit,
) {
    val stored = ui.stored
    val profile = stored?.profile
    var customFeedOpen by rememberSaveable { mutableStateOf(ui.customUrl.isNotEmpty()) }
    var url by rememberSaveable(ui.customUrl) { mutableStateOf(ui.customUrl) }

    Sheet(onDismiss) {
        // Expressive active profile hero card
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        Modifier.size(52.dp),
                        shape = MaterialShapes.Gem.toShape(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            profile?.model?.ifEmpty { null } ?: stringResource(R.string.integrity_pif_empty),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (stored != null) {
                            Text(
                                buildString {
                                    append(stringResource(stored.source.label()))
                                    if (stored.source != PifSource.BUILT_IN && stored.fetchedAt > 0) {
                                        append(" · ").append(relative(stored.fetchedAt))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (profile != null) {
                    Surface(
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            profile.fingerprint,
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (profile.release.isNotEmpty()) {
                            MetaPill(stringResource(R.string.pif_release_badge, profile.release))
                        }
                        if (profile.securityPatch.isNotEmpty()) {
                            val stale = (profile.patchAgeDays() ?: 0L) > 90L
                            MetaPill(profile.securityPatch, warn = stale)
                        }
                        if (profile.device.isNotEmpty()) {
                            MetaPill(profile.device, monospace = true)
                        }
                    }
                }
            }
        }

        Button(
            onClick = onUpdate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape,
            enabled = !ui.updating,
        ) {
            if (ui.updating) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.integrity_pif_update))
            }
        }

        NoticeLine(ui.notice)

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
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            if (ui.channel != PifChannel.STABLE) {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                ) {
                    Box(Modifier.padding(12.dp)) {
                        StatusLine(Icons.Rounded.Warning, Tone.WARN, stringResource(R.string.pif_canary_warning))
                    }
                }
            }
        }

        // Expressive grouped rows for Pixel device picker & saved profiles history
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val activeModel = ui.googleDevices.firstOrNull { it.id == ui.activeDevice }
                ?.let { "${it.model} · ${it.id}" }
                ?: ui.activeDevice.ifEmpty { stringResource(R.string.pif_device_none) }
            SheetActionRow(
                icon = Icons.Rounded.Devices,
                title = stringResource(R.string.pif_device),
                subtitle = activeModel,
                index = 0,
                count = 2,
                loading = ui.devicesLoading,
                onClick = onOpenDevicePicker,
            )
            SheetActionRow(
                icon = Icons.Rounded.History,
                title = stringResource(R.string.pif_library),
                subtitle = if (ui.devices.isEmpty()) stringResource(R.string.pif_library_empty)
                else pluralStringResource(R.plurals.pif_library_count, ui.devices.size, ui.devices.size),
                index = 1,
                count = 2,
                onClick = onOpenSavedProfiles,
            )
        }

        AnimatedVisibility(customFeedOpen) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.pif_custom_source),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.pif_custom_url)) },
                        supportingText = { Text(stringResource(R.string.pif_custom_url_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton({ customFeedOpen = false }) { Text(stringResource(R.string.cancel)) }
                        FilledTonalButton(
                            onClick = { onCustomUrl(url) },
                            enabled = url.trim() != ui.customUrl,
                        ) { Text(stringResource(R.string.pif_custom_url_save)) }
                    }
                }
            }
        }

        // Expressive 2×2 tonal action tiles (never wraps awkwardly onto 2 lines)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveActionTile(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.pif_action_manual),
                    modifier = Modifier.weight(1f),
                    emphasized = true,
                    onClick = onOpenManualEditor,
                )
                ExpressiveActionTile(
                    icon = Icons.Rounded.UploadFile,
                    label = stringResource(R.string.pif_action_import),
                    modifier = Modifier.weight(1f),
                    onClick = onImport,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveActionTile(
                    icon = Icons.Rounded.Link,
                    label = stringResource(R.string.pif_action_feed),
                    modifier = Modifier.weight(1f),
                    onClick = { customFeedOpen = !customFeedOpen },
                )
                ExpressiveActionTile(
                    icon = Icons.Rounded.RestartAlt,
                    label = stringResource(R.string.pif_action_builtin),
                    modifier = Modifier.weight(1f),
                    enabled = stored?.source != PifSource.BUILT_IN,
                    onClick = onReset,
                )
            }
        }
    }
}

/** Full-width searchable Material 3 Expressive Pixel device picker (newest Pixel 11 first). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PixelDevicePickerSheet(
    ui: PifUi,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: (Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var genFilter by rememberSaveable { mutableStateOf("all") }

    // Auto-fetch if list is empty on first open.
    LaunchedEffect(ui.googleDevices.isEmpty()) {
        if (ui.googleDevices.isEmpty() && !ui.devicesLoading) {
            onRefresh(true)
        }
    }

    val generations = listOf(
        "all" to stringResource(R.string.pif_filter_all),
        "11" to "Pixel 11",
        "10" to "Pixel 10",
        "9" to "Pixel 9",
        "8" to "Pixel 8",
        "older" to stringResource(R.string.pif_filter_older),
    )

    val newestFirst = ui.googleDevices.reversed()
    val q = query.trim().lowercase()
    val filtered = newestFirst.filter { dev ->
        val matchesGen = when (genFilter) {
            "all" -> true
            "11" -> dev.model.contains("Pixel 11", ignoreCase = true)
            "10" -> dev.model.contains("Pixel 10", ignoreCase = true)
            "9" -> dev.model.contains("Pixel 9", ignoreCase = true)
            "8" -> dev.model.contains("Pixel 8", ignoreCase = true)
            "older" -> !dev.model.contains("Pixel 11", ignoreCase = true) &&
                !dev.model.contains("Pixel 10", ignoreCase = true) &&
                !dev.model.contains("Pixel 9", ignoreCase = true) &&
                !dev.model.contains("Pixel 8", ignoreCase = true)
            else -> true
        }
        val matchesQuery = q.isEmpty() ||
            dev.model.lowercase().contains(q) ||
            dev.id.lowercase().contains(q)
        matchesGen && matchesQuery
    }

    Sheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pif_device), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.pif_device_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(
                onClick = { onRefresh(true) },
                enabled = !ui.devicesLoading,
            ) {
                if (ui.devicesLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.pif_devices_refresh))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.pif_devices_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton({ query = "" }) { Icon(Icons.Rounded.Close, null) } }
            } else null,
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            generations.forEach { (key, label) ->
                FilterChip(
                    selected = genFilter == key,
                    onClick = { genFilter = key },
                    label = { Text(label) },
                    shape = CircleShape,
                )
            }
        }

        NoticeLine(ui.notice)

        if (filtered.isEmpty() && !ui.devicesLoading) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.pif_devices_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                filtered.forEachIndexed { index, device ->
                    val active = device.id == ui.activeDevice
                    Surface(
                        onClick = {
                            onSelect(device.id)
                            onDismiss()
                        },
                        shape = groupedShape(index, filtered.size),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    device.model,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    device.id,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (active) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Full-width Material 3 Expressive saved PIF library sheet with delete support. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedProfilesSheet(
    ui: PifUi,
    onDismiss: () -> Unit,
    onActivate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Sheet(onDismiss) {
        SheetHeader(
            Icons.Rounded.History,
            stringResource(R.string.pif_library),
            if (ui.devices.isEmpty()) stringResource(R.string.pif_library_empty)
            else pluralStringResource(R.plurals.pif_library_count, ui.devices.size, ui.devices.size),
            monospace = false,
        )

        if (ui.devices.isEmpty()) {
            Surface(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.pif_library_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ui.devices.forEachIndexed { index, device ->
                    val active = device.fingerprint == ui.stored?.profile?.fingerprint
                    val product = device.fingerprint.split('/').getOrNull(1).orEmpty()
                    Surface(
                        onClick = {
                            if (!active) onActivate(device.fingerprint)
                            onDismiss()
                        },
                        shape = groupedShape(index, ui.devices.size),
                        color = if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    device.model.ifEmpty { device.device.ifEmpty { device.fingerprint.take(24) } },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(
                                        device.securityPatch.ifEmpty { "—" },
                                        if (device.release.isNotEmpty()) "A${device.release}" else "",
                                        product,
                                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (active) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    null,
                                    Modifier.padding(end = 8.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                IconButton(onClick = { onDelete(device.fingerprint) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = stringResource(R.string.integrity_keybox_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Material 3 Expressive custom PIF editor: structured manual fields + raw JSON/prop paste. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomPifEditorSheet(
    activeProfile: PifProfile?,
    onDismiss: () -> Unit,
    onSaveFields: (Map<String, String>) -> Unit,
    onSaveRaw: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var fingerprint by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var manufacturer by rememberSaveable { mutableStateOf("Google") }
    var securityPatch by rememberSaveable { mutableStateOf("") }
    var initialSdk by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var product by rememberSaveable { mutableStateOf("") }
    var device by rememberSaveable { mutableStateOf("") }
    var release by rememberSaveable { mutableStateOf("") }
    var buildId by rememberSaveable { mutableStateOf("") }
    var incremental by rememberSaveable { mutableStateOf("") }
    var showMore by rememberSaveable { mutableStateOf(false) }
    var rawText by rememberSaveable { mutableStateOf("") }

    fun populateFrom(p: PifProfile) {
        fingerprint = p.fingerprint
        model = p.model
        manufacturer = p.manufacturer.ifEmpty { "Google" }
        securityPatch = p.securityPatch
        initialSdk = p.initialSdk
        brand = p.brand
        product = p.product
        device = p.device
        release = p.release
        buildId = p.id
        incremental = p.incremental
        rawText = p.toPrettyJson()
    }

    fun autoFillFromFingerprint() {
        val parsed = PifProfile.fromFields(mapOf("FINGERPRINT" to fingerprint)) ?: return
        if (parsed.brand.isNotEmpty()) brand = parsed.brand
        if (parsed.product.isNotEmpty()) product = parsed.product
        if (parsed.device.isNotEmpty()) device = parsed.device
        if (parsed.release.isNotEmpty()) release = parsed.release
        if (parsed.id.isNotEmpty()) buildId = parsed.id
        if (parsed.incremental.isNotEmpty()) incremental = parsed.incremental
    }

    val fieldsValid = PifProfile.isValidFingerprint(fingerprint.trim())
    val rawPreview = if (tab == 1 && rawText.isNotBlank()) PifProfile.parse(rawText) else null

    Sheet(onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pif_manual_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.pif_manual_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (activeProfile != null) {
                FilledTonalButton(onClick = { populateFrom(activeProfile) }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pif_manual_copy_active))
                }
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = tab == 0,
                onClick = { tab = 0 },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.pif_manual_tab_fields)) }
            SegmentedButton(
                selected = tab == 1,
                onClick = { tab = 1 },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.pif_manual_tab_raw)) }
        }

        if (tab == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fingerprint,
                    onValueChange = { fingerprint = it },
                    label = { Text("FINGERPRINT *") },
                    placeholder = { Text(stringResource(R.string.pif_manual_fp_hint)) },
                    isError = fingerprint.isNotBlank() && !fieldsValid,
                    supportingText = if (fingerprint.isNotBlank() && !fieldsValid) {
                        { Text(stringResource(R.string.pif_import_invalid)) }
                    } else null,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                FilledTonalButton(
                    onClick = { autoFillFromFingerprint() },
                    enabled = fieldsValid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pif_manual_autofill))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("MODEL") },
                        placeholder = { Text("Pixel 11 Pro XL") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = manufacturer,
                        onValueChange = { manufacturer = it },
                        label = { Text("MANUFACTURER") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = securityPatch,
                        onValueChange = { securityPatch = it },
                        label = { Text("SECURITY_PATCH") },
                        placeholder = { Text("2026-11-05") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = initialSdk,
                        onValueChange = { v -> initialSdk = v.filter { it.isDigit() } },
                        label = { Text("INITIAL_SDK") },
                        placeholder = { Text("37") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                }

                TextButton(onClick = { showMore = !showMore }) {
                    Icon(if (showMore) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pif_manual_more_fields))
                }

                AnimatedVisibility(showMore) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = brand,
                                onValueChange = { brand = it },
                                label = { Text("BRAND") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = product,
                                onValueChange = { product = it },
                                label = { Text("PRODUCT") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = device,
                                onValueChange = { device = it },
                                label = { Text("DEVICE") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = release,
                                onValueChange = { release = it },
                                label = { Text("RELEASE") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = buildId,
                                onValueChange = { buildId = it },
                                label = { Text("ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = incremental,
                                onValueChange = { incremental = it },
                                label = { Text("INCREMENTAL") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = { Text(stringResource(R.string.pif_manual_raw_hint)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 7,
                    maxLines = 14,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rawText.isNotBlank()) {
                    if (rawPreview != null) {
                        StatusLine(
                            Icons.Rounded.CheckCircle,
                            Tone.GOOD,
                            stringResource(
                                R.string.pif_manual_valid_preview,
                                rawPreview.model.ifEmpty { rawPreview.device },
                                rawPreview.securityPatch.ifEmpty { "—" },
                            ),
                        )
                    } else {
                        StatusLine(Icons.Rounded.Error, Tone.BAD, stringResource(R.string.pif_import_invalid))
                    }
                }
            }
        }

        val canSave = if (tab == 0) fieldsValid else rawPreview != null
        Button(
            onClick = {
                if (tab == 0) {
                    onSaveFields(
                        mapOf(
                            "FINGERPRINT" to fingerprint,
                            "MODEL" to model,
                            "MANUFACTURER" to manufacturer,
                            "SECURITY_PATCH" to securityPatch,
                            "DEVICE_INITIAL_SDK_INT" to initialSdk,
                            "BRAND" to brand,
                            "PRODUCT" to product,
                            "DEVICE" to device,
                            "RELEASE" to release,
                            "ID" to buildId,
                            "INCREMENTAL" to incremental,
                        )
                    )
                } else {
                    onSaveRaw(rawText)
                }
                onDismiss()
            },
            enabled = canSave,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.pif_manual_save))
        }
    }
}

@Composable
private fun MetaPill(text: String, warn: Boolean = false, monospace: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (warn) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (warn) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else null,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = groupedShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

@Composable
private fun ExpressiveActionTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
            emphasized -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            emphasized -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier.height(56.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Material 3 Expressive keybox sheet: certificate card, live revocation status, and actions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun KeyboxSheet(
    ui: KeyboxUi,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    onSync: () -> Unit,
    onCancelSync: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var confirmSync by rememberSaveable { mutableStateOf(false) }
    var confirmImport by rememberSaveable { mutableStateOf(false) }
    val info = ui.info
    val colors = MaterialTheme.colorScheme

    Sheet(onDismiss) {
        val revoked = ui.revocation is Revocation.Revoked
        val expired = info?.expired == true
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = when {
                revoked || expired -> colors.errorContainer
                else -> colors.surfaceContainerHigh
            },
            contentColor = when {
                revoked || expired -> colors.onErrorContainer
                else -> colors.onSurface
            },
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        Modifier.size(52.dp),
                        shape = MaterialShapes.Gem.toShape(),
                        color = if (revoked || expired) colors.error else colors.primaryContainer,
                        contentColor = if (revoked || expired) colors.onError else colors.onPrimaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Key, null, Modifier.size(24.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (info != null) {
                                stringResource(R.string.keybox_summary, info.algorithm.uppercase(), info.chainLength)
                            } else {
                                stringResource(R.string.integrity_ts_title)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (info == null) stringResource(R.string.integrity_keybox_none)
                            else "#" + info.serialHex.take(12),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = if (info != null) FontFamily.Monospace else null,
                            ),
                            color = if (revoked || expired) colors.onErrorContainer else colors.onSurfaceVariant,
                        )
                    }
                }

                if (info == null) {
                    Text(
                        stringResource(R.string.keybox_own_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                        .format(java.util.Date(info.notAfter))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (info.rkpManufacturer.isNotEmpty()) {
                            MetaPill(stringResource(R.string.keybox_rkp_manufacturer, info.rkpManufacturer))
                        }
                        MetaPill(
                            stringResource(if (expired) R.string.keybox_expired_on else R.string.keybox_expires_on, date),
                            warn = expired,
                        )
                        when (val r = ui.revocation) {
                            is Revocation.Revoked -> MetaPill(
                                listOf(stringResource(R.string.integrity_keybox_revoked), r.reason.ifEmpty { null })
                                    .filterNotNull().joinToString(" · "),
                                warn = true,
                            )
                            is Revocation.Valid -> MetaPill(
                                stringResource(R.string.integrity_keybox_valid) + " · " +
                                    stringResource(R.string.keybox_checked, relative(r.checkedAt)),
                            )
                            is Revocation.Unknown -> MetaPill(stringResource(R.string.integrity_keybox_present))
                        }
                    }
                    if (ui.interim) {
                        StatusLine(Icons.Rounded.Info, Tone.NEUTRAL, stringResource(R.string.keybox_interim_custody))
                    }
                }
            }
        }
        NoticeLine(ui.notice)

        Button(
            onClick = { if (info == null) onImport() else confirmImport = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = CircleShape,
            enabled = !ui.busy,
        ) {
            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (info == null) R.string.integrity_keybox_import else R.string.keybox_replace))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { confirmSync = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
                enabled = !ui.busy,
            ) {
                if (ui.syncing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.keybox_evoker_sync))
                }
            }
            if (ui.syncing) {
                TextButton(onCancelSync, Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
            }
            Text(
                stringResource(R.string.keybox_evoker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        if (info != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onCheck,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                    enabled = !ui.busy,
                ) {
                    if (ui.checking) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.integrity_keybox_check))
                    }
                }
                TextButton(
                    onClick = { confirm = true },
                    modifier = Modifier.height(48.dp),
                    enabled = !ui.busy,
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp), tint = colors.error)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.integrity_keybox_delete), color = colors.error)
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
                TextButton({ confirm = false; onDelete() }, enabled = !ui.busy) {
                    Text(stringResource(R.string.integrity_keybox_delete))
                }
            },
            dismissButton = { TextButton({ confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmSync) {
        AlertDialog(
            onDismissRequest = { confirmSync = false },
            title = { Text(stringResource(R.string.keybox_evoker_confirm_title)) },
            text = { Text(stringResource(R.string.keybox_evoker_confirm_body)) },
            confirmButton = {
                TextButton({ confirmSync = false; onSync() }, enabled = !ui.busy) {
                    Text(stringResource(R.string.keybox_evoker_sync))
                }
            },
            dismissButton = { TextButton({ confirmSync = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.keybox_replace)) },
            text = { Text(stringResource(R.string.keybox_replace_confirm_body)) },
            confirmButton = {
                TextButton({ confirmImport = false; onImport() }, enabled = !ui.busy) {
                    Text(stringResource(R.string.keybox_replace))
                }
            },
            dismissButton = { TextButton({ confirmImport = false }) { Text(stringResource(R.string.cancel)) } },
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
