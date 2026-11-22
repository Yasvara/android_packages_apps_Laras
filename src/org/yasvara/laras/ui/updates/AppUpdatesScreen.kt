/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.updates

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.security.yasvara.YasvaraUpdatePolicy
import org.yasvara.laras.R
import org.yasvara.laras.data.updates.UpdateApp
import org.yasvara.laras.data.updates.UpdateEvent
import org.yasvara.laras.data.updates.UpdateNotifier
import org.yasvara.laras.data.updates.UpdatePolicy
import org.yasvara.laras.data.updates.VaultEntry
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private val DELAY_PRESETS = listOf(1, 3, 7, 14, 30)

/**
 * App updates: the per-app Auto · Delay · Ask · Never policy. Core apps (Play
 * Store, Play services, system apps) are listed only when [advanced] (the Lab gate) is on.
 * [focus] opens that app's sheet straight away (the Settings › App info deep link, §4.9).
 */
@Composable
fun AppUpdatesScreen(
    advanced: Boolean,
    onBack: () -> Unit,
    focus: String? = null,
    viewModel: AppUpdatesViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(focus) }
    val visible = ui.visible

    LaunchedEffect(advanced) { viewModel.load(advanced) }
    LaunchedEffect(focus) { focus?.let(viewModel::open) }

    Page(stringResource(R.string.updates_title), onBack) {
        item("summary") { SummaryCard(ui) }
        item("search") {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.updates_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton({ viewModel.setQuery("") }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.updates_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdateFilter.entries.forEach { f ->
                        FilterChip(
                            selected = ui.filter == f,
                            onClick = { viewModel.setFilter(f) },
                            label = { Text(stringResource(filterLabel(f))) },
                        )
                    }
                }
            }
        }
        when {
            ui.loading -> item("loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !ui.available -> item("unavailable") { Hint(stringResource(R.string.updates_unavailable)) }
            visible.isEmpty() -> item("empty") { Hint(stringResource(R.string.updates_empty)) }
        }
        items(visible.size, key = { visible[it].packageName }) { i ->
            AppRow(visible[i], groupedShape(i, visible.size)) {
                open = visible[i].packageName
                viewModel.open(visible[i].packageName)
            }
        }
    }

    val selected = open?.let { pkg -> ui.apps.firstOrNull { it.packageName == pkg } }
    if (selected != null) {
        PolicySheet(selected, viewModel, onDismiss = { open = null })
    }
}

@Composable
private fun SummaryCard(ui: AppUpdatesUi) {
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(stringResource(R.string.updates_summary_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.updates_summary_counts, ui.count(UpdatePolicy.NEVER),
                        ui.count(UpdatePolicy.DELAY), ui.count(UpdatePolicy.ASK)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ui.vaultBytes > 0) {
                    Text(
                        stringResource(R.string.updates_vault_size,
                            Formatter.formatShortFileSize(LocalContext.current, ui.vaultBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: UpdateApp, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(app.versionName.ifEmpty { app.versionCode.toString() },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PolicyChip(app.policy, app.delayDays)
        }
    }
}

@Composable
private fun PolicyChip(policy: UpdatePolicy, delayDays: Int) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (policy) {
        UpdatePolicy.AUTO -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        UpdatePolicy.DELAY -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        UpdatePolicy.ASK -> scheme.secondaryContainer to scheme.onSecondaryContainer
        UpdatePolicy.NEVER, UpdatePolicy.SKIP -> scheme.primaryContainer to scheme.onPrimaryContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            if (policy == UpdatePolicy.DELAY) stringResource(R.string.updates_chip_delay, delayDays)
            else stringResource(policyLabel(policy)),
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium, color = fg,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PolicySheet(app: UpdateApp, viewModel: AppUpdatesViewModel, onDismiss: () -> Unit) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var custom by rememberSaveable(app.packageName) { mutableStateOf("") }
    val delay = if (app.delayDays > 0) app.delayDays else YasvaraUpdatePolicy.DEFAULT_DELAY_DAYS

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(app.packageName, size = 48.dp)
                Column {
                    Text(app.label, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.updates_sheet_from, app.versionName,
                            viewModel.installerLabel(app.installer) ?: app.installer
                                ?: stringResource(R.string.updates_installer_unknown)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (app.core) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(stringResource(R.string.updates_core_warning), Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                UpdatePolicy.LADDER.forEachIndexed { i, policy ->
                    SegmentedButton(
                        selected = app.policy == policy,
                        onClick = { viewModel.setPolicy(app.packageName, policy, delay) },
                        shape = SegmentedButtonDefaults.itemShape(i, UpdatePolicy.LADDER.size),
                    ) { Text(stringResource(policyLabel(policy)), maxLines = 1) }
                }
            }
            Text(
                if (app.policy == UpdatePolicy.SKIP) stringResource(R.string.updates_help_skip, detail.skippedVersion)
                else if (app.policy == UpdatePolicy.NEVER && detail.shownInStore) stringResource(R.string.updates_help_never_shown)
                else stringResource(policyHelp(app.policy)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (app.policy == UpdatePolicy.NEVER && detail.since > 0) {
                val days = ((System.currentTimeMillis() - detail.since) / DateUtils.DAY_IN_MILLIS).toInt()
                Text(pluralStringResource(R.plurals.updates_paused_for, days, days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (days >= YasvaraUpdatePolicy.STALE_REMINDER_DAYS) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (app.policy == UpdatePolicy.DELAY) {
                Text(stringResource(R.string.updates_delay_label), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DELAY_PRESETS.forEach { days ->
                        FilterChip(
                            selected = app.delayDays == days,
                            onClick = { viewModel.setPolicy(app.packageName, UpdatePolicy.DELAY, days) },
                            label = { Text(stringResource(R.string.updates_chip_delay, days)) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val parsed = custom.toIntOrNull()
                    val valid = parsed != null &&
                        parsed in YasvaraUpdatePolicy.MIN_DELAY_DAYS..YasvaraUpdatePolicy.MAX_DELAY_DAYS
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.updates_delay_custom)) },
                        placeholder = { Text(app.delayDays.toString()) },
                        isError = custom.isNotEmpty() && !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button({
                        viewModel.setPolicy(app.packageName, UpdatePolicy.DELAY, parsed!!)
                        custom = ""
                    }, enabled = valid) { Text(stringResource(R.string.updates_delay_apply)) }
                }
                Text(stringResource(R.string.updates_delay_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (app.policy == UpdatePolicy.NEVER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.updates_show_in_store), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(if (detail.shownInStore) R.string.updates_show_in_store_on
                                else R.string.updates_show_in_store_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(detail.shownInStore, { viewModel.setShownInStore(app.packageName, it) })
                }
                val context = LocalContext.current
                if (detail.allowOnceUntil > 0) {
                    Text(stringResource(R.string.updates_allowed_until,
                            DateUtils.formatDateTime(context, detail.allowOnceUntil,
                                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY)),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ openStore(context, app) }) { Text(stringResource(R.string.updates_open_store)) }
                        OutlinedButton({ viewModel.allowOnce(app.packageName, false) }) {
                            Text(stringResource(R.string.updates_allow_once_cancel))
                        }
                    }
                } else {
                    Button({
                        viewModel.allowOnce(app.packageName, true)
                        openStore(context, app)
                    }) { Text(stringResource(R.string.updates_allow_once)) }
                }
                // The newest version a store tried; skipping it resumes updates from the next one.
                val offered = detail.history.firstOrNull { it.toVersionCode > app.versionCode }
                if (offered != null) {
                    OutlinedButton({ viewModel.skipVersion(app.packageName, offered.toVersionCode) }) {
                        Text(stringResource(R.string.updates_skip_version, offered.toVersionCode))
                    }
                }
            }

            VaultSection(app, detail.vault, detail.vaultEnabled, viewModel)

            if (detail.history.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.updates_history), style = MaterialTheme.typography.titleSmall)
                detail.history.forEach { HistoryRow(it, viewModel) }
            }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }
}

private fun openStore(context: android.content.Context, app: UpdateApp) {
    runCatching { context.startActivity(UpdateNotifier.storeIntent(context, app.packageName, app.installer)) }
}

@Composable
private fun HistoryRow(event: UpdateEvent, viewModel: AppUpdatesViewModel) {
    val color = when (event.decision) {
        "blocked" -> MaterialTheme.colorScheme.error
        "deferred" -> MaterialTheme.colorScheme.tertiary
        "allowed" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.size(8.dp)) {}
        Column(Modifier.weight(1f)) {
            Text(stringResource(decisionLabel(event.decision), event.toVersionCode),
                style = MaterialTheme.typography.bodyMedium)
            Text(
                "${viewModel.installerLabel(event.installer) ?: event.installer} · " +
                    DateUtils.getRelativeTimeSpanString(event.time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun policyLabel(policy: UpdatePolicy) = when (policy) {
    UpdatePolicy.AUTO -> R.string.updates_policy_auto
    UpdatePolicy.DELAY -> R.string.updates_policy_delay
    UpdatePolicy.ASK -> R.string.updates_policy_ask
    UpdatePolicy.NEVER -> R.string.updates_policy_never
    UpdatePolicy.SKIP -> R.string.updates_policy_skip
}

private fun policyHelp(policy: UpdatePolicy) = when (policy) {
    UpdatePolicy.AUTO -> R.string.updates_help_auto
    UpdatePolicy.DELAY -> R.string.updates_help_delay
    UpdatePolicy.ASK -> R.string.updates_help_ask
    UpdatePolicy.NEVER -> R.string.updates_help_never
    UpdatePolicy.SKIP -> R.string.updates_policy_skip
}

private fun filterLabel(filter: UpdateFilter) = when (filter) {
    UpdateFilter.ALL -> R.string.updates_filter_all
    UpdateFilter.PAUSED -> R.string.updates_filter_paused
    UpdateFilter.DELAYED -> R.string.updates_filter_delayed
    UpdateFilter.ASK -> R.string.updates_filter_ask
}

private fun decisionLabel(decision: String) = when (decision) {
    "approved" -> R.string.updates_event_approved
    "rolledback" -> R.string.updates_event_rolledback
    "blocked" -> R.string.updates_event_blocked
    "deferred" -> R.string.updates_event_deferred
    "skipped" -> R.string.updates_event_skipped
    "ask" -> R.string.updates_event_ask
    else -> R.string.updates_event_allowed
}

@Composable
private fun VaultSection(app: UpdateApp, vault: List<VaultEntry>, enabled: Boolean, viewModel: AppUpdatesViewModel) {
    val rollback by viewModel.rollback.collectAsStateWithLifecycle()
    var confirm by remember(app.packageName) { mutableStateOf<VaultEntry?>(null) }
    val context = LocalContext.current

    HorizontalDivider()
    Text(stringResource(R.string.updates_vault_title), style = MaterialTheme.typography.titleSmall)
    if (app.policy == UpdatePolicy.AUTO) {
        // Other policies always save; Auto apps opt in (§4.4).
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.updates_vault_keep), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge)
            Switch(checked = enabled, onCheckedChange = { viewModel.setVaultEnabled(app.packageName, it) })
        }
    }
    if (vault.isEmpty()) {
        Text(
            stringResource(if (!enabled) R.string.updates_vault_auto else R.string.updates_vault_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    vault.forEach { entry ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(entry.versionName.ifEmpty { entry.versionCode.toString() },
                    style = MaterialTheme.typography.bodyLarge)
                Text(
                    Formatter.formatShortFileSize(context, entry.bytes) + " · " +
                        DateUtils.getRelativeTimeSpanString(entry.savedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton({ viewModel.deleteVault(app.packageName, entry.versionCode) }) {
                Text(stringResource(R.string.updates_vault_delete))
            }
            FilledTonalButton({ confirm = entry },
                enabled = rollback !is RollbackState.Running && entry.versionCode != app.versionCode) {
                Text(stringResource(R.string.updates_vault_rollback))
            }
        }
    }
    when (val r = rollback) {
        is RollbackState.Running -> if (r.pkg == app.packageName) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.updates_rollback_running), style = MaterialTheme.typography.bodyMedium)
            }
        }
        is RollbackState.Done -> if (r.pkg == app.packageName) {
            Text(
                if (r.ok) stringResource(R.string.updates_rollback_done)
                else stringResource(R.string.updates_rollback_failed, r.message.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        RollbackState.Idle -> Unit
    }

    confirm?.let { entry ->
        var clearData by remember { mutableStateOf(false) }
        var pin by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(stringResource(R.string.updates_rollback_title,
                    entry.versionName.ifEmpty { entry.versionCode.toString() }))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.updates_rollback_body))
                    CheckRow(stringResource(R.string.updates_rollback_pin), pin) { pin = it }
                    CheckRow(stringResource(R.string.updates_rollback_clear), clearData) { clearData = it }
                }
            },
            confirmButton = {
                Button({
                    viewModel.rollback(app.packageName, entry.versionCode, clearData, pin)
                    confirm = null
                }) { Text(stringResource(R.string.updates_vault_rollback)) }
            },
            dismissButton = {
                TextButton({ confirm = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheckedChange = null)
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
