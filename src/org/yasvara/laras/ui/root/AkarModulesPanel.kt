/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yasvara.laras.AkarWebUiActivity
import org.yasvara.laras.R
import org.yasvara.laras.data.root.AkarModule
import org.yasvara.laras.data.root.BootguardStatus
import org.yasvara.laras.data.root.InstalledApp
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape

@Composable
fun BootguardRecoveryBanner(
    status: BootguardStatus,
    onReset: () -> Unit,
) {
    if (!status.safeMode && status.disabledSuspects.isEmpty() && !status.zygiskBypassed) return
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = LarasShapes.Card,
        color = scheme.errorContainer,
        contentColor = scheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.WarningAmber, null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(
                        if (status.safeMode) R.string.akar_bg_safemode_title
                        else R.string.akar_bg_rollback_title,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = when {
                    status.disabledSuspects.isNotEmpty() -> stringResource(
                        R.string.akar_bg_suspects_body,
                        status.disabledSuspects.joinToString(", "),
                    )
                    status.keyTrigger.isNotEmpty() -> stringResource(
                        R.string.akar_bg_key_body,
                        status.keyTrigger,
                    )
                    else -> stringResource(R.string.akar_bg_safemode_body, status.reason)
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onErrorContainer.copy(alpha = 0.88f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onReset) {
                    Text(stringResource(R.string.akar_bg_clear_action))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AkarModulesTab(
    ui: AkarUi,
    canUse: Boolean,
    gate: (() -> Unit) -> Unit,
    viewModel: AkarViewModel,
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val context = LocalContext.current
    var showDenyListSheet by rememberSaveable { mutableStateOf(false) }
    var scopeEditModuleId by rememberSaveable { mutableStateOf<String?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            gate { viewModel.installModule(uri) }
        }
    }

    val filtered = remember(ui.modules, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) ui.modules
        else ui.modules.filter {
            it.name.lowercase().contains(q) ||
                it.id.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = { viewModel.load(isRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui.bootguard.safeMode || ui.bootguard.disabledSuspects.isNotEmpty() || ui.bootguard.zygiskBypassed) {
                item("bg_banner") {
                    BootguardRecoveryBanner(
                        status = ui.bootguard,
                        onReset = { gate { viewModel.clearSafeMode() } },
                    )
                }
            }

            if (showSearch) {
                item("search") {
                    LarasSearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.akar_mod_search_hint),
                    )
                }
            }

            // Master Module Engine toggle (default OFF = 0 overhead) + Native Zygisk (default OFF) + Tabir Unmount
            item("runtime_controls") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LarasSectionHeader(stringResource(R.string.akar_zygisk_section))
                    LarasSwitchRow(
                        title = stringResource(R.string.akar_mod_engine_title),
                        summary = stringResource(
                            if (ui.zygisk.modulesEnabled) R.string.akar_mod_engine_sub_on else R.string.akar_mod_engine_sub_off,
                        ),
                        checked = ui.zygisk.modulesEnabled,
                        enabled = canUse,
                        leading = { Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.primary) },
                        shape = groupedShape(0, 4),
                        onCheckedChange = { on -> gate { viewModel.setModulesEnabled(on) } },
                    )
                    LarasSwitchRow(
                        title = stringResource(R.string.akar_zygisk_title),
                        summary = stringResource(
                            if (ui.zygisk.enabled) R.string.akar_zygisk_sub_on else R.string.akar_zygisk_sub_off,
                        ),
                        checked = ui.zygisk.enabled && ui.zygisk.modulesEnabled,
                        enabled = canUse && ui.zygisk.modulesEnabled,
                        leading = { Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary) },
                        shape = groupedShape(1, 4),
                        onCheckedChange = { on -> gate { viewModel.setZygiskEnabled(on) } },
                    )
                    LarasSwitchRow(
                        title = stringResource(R.string.akar_unmount_title),
                        summary = stringResource(R.string.akar_unmount_sub),
                        checked = ui.zygisk.defaultUnmount,
                        enabled = canUse && ui.zygisk.modulesEnabled,
                        leading = { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.secondary) },
                        shape = groupedShape(2, 4),
                        onCheckedChange = { on -> gate { viewModel.setDefaultUnmount(on) } },
                    )
                    Surface(
                        shape = groupedShape(3, 4),
                        color = LarasSurfaces.card,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(
                                enabled = canUse && !ui.moduleBusy,
                                onClick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.akar_mod_install_zip), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                enabled = canUse,
                                onClick = { showDenyListSheet = true },
                            ) {
                                Icon(Icons.Rounded.FilterList, null, Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text(stringResource(R.string.akar_zygisk_denylist_btn, ui.zygisk.denylist.size))
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item("empty") {
                    LarasEmptyState(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.akar_mod_empty_title),
                        body = stringResource(R.string.akar_mod_empty_body),
                    )
                }
            } else {
                item("hdr_installed") {
                    LarasSectionHeader(stringResource(R.string.akar_mod_installed_hdr, filtered.size))
                }
                items(filtered, key = { it.id }) { mod ->
                    AkarModuleCard(
                        mod = mod,
                        scopedCount = ui.zygisk.scopes[mod.id]?.size ?: 0,
                        enabled = canUse && !ui.moduleBusy,
                        onToggle = { on -> gate { viewModel.setModuleEnabled(mod.id, on) } },
                        onAction = { gate { viewModel.runModuleAction(mod) } },
                        onWebUi = {
                            context.startActivity(
                                Intent(context, AkarWebUiActivity::class.java)
                                    .putExtra(AkarWebUiActivity.EXTRA_MODULE_ID, mod.id)
                                    .putExtra(AkarWebUiActivity.EXTRA_MODULE_NAME, mod.name),
                            )
                        },
                        onScope = { scopeEditModuleId = mod.id },
                        onToggleRemove = { gate { viewModel.setModuleRemove(mod.id, !mod.remove) } },
                    )
                }
            }
        }
    }

    if (showDenyListSheet) {
        UidPickerSheet(
            title = stringResource(R.string.akar_zygisk_denylist_title),
            subtitle = stringResource(R.string.akar_zygisk_denylist_sub),
            apps = ui.installedApps,
            initialSelected = ui.zygisk.denylist,
            onDismiss = { showDenyListSheet = false },
            onSave = { selected ->
                showDenyListSheet = false
                gate { viewModel.updateZygiskDenylist(selected) }
            },
        )
    }

    val editModId = scopeEditModuleId
    if (editModId != null) {
        val modName = ui.modules.find { it.id == editModId }?.name ?: editModId
        UidPickerSheet(
            title = stringResource(R.string.akar_zygisk_scope_title, modName),
            subtitle = stringResource(R.string.akar_zygisk_scope_sub),
            apps = ui.installedApps,
            initialSelected = ui.zygisk.scopes[editModId].orEmpty(),
            onDismiss = { scopeEditModuleId = null },
            onSave = { selected ->
                scopeEditModuleId = null
                gate { viewModel.updateModuleScope(editModId, selected) }
            },
        )
    }

    if (ui.consoleTitle != null) {
        AlertDialog(
            onDismissRequest = { if (!ui.moduleBusy) viewModel.dismissConsole() },
            title = { Text(ui.consoleTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ui.moduleBusy) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Surface(
                        shape = LarasShapes.Card,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 340.dp),
                    ) {
                        Text(
                            text = ui.consoleLog.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !ui.moduleBusy, onClick = viewModel::dismissConsole) {
                    Text(stringResource(R.string.akar_close))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AkarModuleCard(
    mod: AkarModule,
    scopedCount: Int,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onAction: () -> Unit,
    onWebUi: () -> Unit,
    onScope: () -> Unit,
    onToggleRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = LarasShapes.Card,
        color = LarasSurfaces.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = mod.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${mod.version} · ${mod.author.ifBlank { mod.id }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = mod.enabled && !mod.remove,
                    enabled = enabled && !mod.remove,
                    onCheckedChange = onToggle,
                )
            }

            if (mod.description.isNotBlank()) {
                Text(
                    text = mod.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (mod.hasZygisk) {
                    LarasBadge(
                        text = if (scopedCount > 0) "Zygisk · $scopedCount" else "Zygisk",
                        tone = Tone.GOOD,
                    )
                }
                if (mod.metamodule) LarasBadge(text = "Metamodule", tone = Tone.NEUTRAL)
                if (mod.mountStrategy != "none" && !mod.metamodule) {
                    LarasBadge(text = mod.mountStrategy.uppercase(), tone = Tone.NEUTRAL)
                }
                if (mod.update) LarasBadge(text = stringResource(R.string.akar_mod_badge_reboot), tone = Tone.WARN)
                if (mod.remove) LarasBadge(text = stringResource(R.string.akar_mod_badge_removing), tone = Tone.BAD)
                for (feat in mod.managedFeatures) {
                    LarasBadge(text = feat, tone = Tone.NEUTRAL)
                }
            }

            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mod.hasAction && !mod.remove) {
                    FilledTonalButton(
                        enabled = enabled,
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.akar_mod_action_btn), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (mod.hasWebroot && !mod.remove) {
                    FilledTonalButton(
                        enabled = enabled,
                        onClick = onWebUi,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Public, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.akar_mod_webui_btn), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (mod.hasZygisk && !mod.remove) {
                    OutlinedButton(
                        enabled = enabled,
                        onClick = onScope,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(stringResource(R.string.akar_mod_scope_btn), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(enabled = enabled, onClick = onToggleRemove) {
                    Icon(
                        if (mod.remove) Icons.Rounded.Restore else Icons.Rounded.DeleteOutline,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = if (mod.remove) scheme.primary else scheme.error,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        stringResource(if (mod.remove) R.string.akar_mod_undo_remove else R.string.akar_mod_remove),
                        color = if (mod.remove) scheme.primary else scheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UidPickerSheet(
    title: String,
    subtitle: String,
    apps: List<InstalledApp>,
    initialSelected: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (Set<Int>) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var selected by remember(initialSelected) { mutableStateOf(initialSelected) }
    val shown = remember(apps, search, selected) {
        val q = search.trim().lowercase()
        apps.filter {
            q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
        }.sortedWith(compareByDescending<InstalledApp> { it.uid in selected }.thenBy { it.label.lowercase() })
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { onSave(selected) }) {
                    Text(stringResource(R.string.akar_mod_save))
                }
            }
            LarasSearchBar(
                query = search,
                onQueryChange = { search = it },
                placeholder = stringResource(R.string.akar_search_apps),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown, key = { it.uid }) { app ->
                    val checked = app.uid in selected
                    Surface(
                        shape = LarasShapes.Card,
                        color = LarasSurfaces.card,
                        onClick = {
                            selected = if (checked) selected - app.uid else selected + app.uid
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AppIcon(app.packageName, size = 32.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { c ->
                                    selected = if (c) selected + app.uid else selected - app.uid
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
