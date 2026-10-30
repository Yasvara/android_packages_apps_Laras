/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.app.Activity
import android.app.KeyguardManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.root.AuditEvent
import org.yasvara.laras.data.root.DeniedAttempt
import org.yasvara.laras.data.root.GrantFlag
import org.yasvara.laras.data.root.RootGrant
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.data.root.RootScope
import org.yasvara.laras.data.root.RootTier
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.SectionCard
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private const val MINUTE = 60_000L

/** The scopes the sheet offers: a label, the scope and, for timed ones, how long. */
private data class ScopeChoice(val label: Int, val scope: RootScope, val durationMs: Long = 0)

private val SCOPES = listOf(
    ScopeChoice(R.string.akar_scope_always, RootScope.ALWAYS),
    ScopeChoice(R.string.akar_scope_reboot, RootScope.UNTIL_REBOOT),
    ScopeChoice(R.string.akar_scope_10min, RootScope.TIMED, 10 * MINUTE),
    ScopeChoice(R.string.akar_scope_1h, RootScope.TIMED, 60 * MINUTE),
    ScopeChoice(R.string.akar_scope_24h, RootScope.TIMED, 24 * 60 * MINUTE),
)

/**
 * Akar, Root access (SPEC-0006). Apps never ask for root: the owner enrols them here, behind
 * the device credential. Everything on this page is written to the root service at once.
 */
@Composable
fun AkarScreen(onBack: () -> Unit, focus: String? = null, viewModel: AkarViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(focus) }
    var confirmRevokeAll by rememberSaveable { mutableStateOf(false) }
    val gate = rememberCredentialGate()
    val repo = remember { viewModel.repository() }

    LaunchedEffect(Unit) { viewModel.load() }

    Page(stringResource(R.string.akar_title), onBack) {
        item("hero") { Hero(ui, gate, viewModel) }
        when {
            ui.loading -> item("loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !ui.available -> item("unavailable") { Hint(stringResource(R.string.akar_unavailable)) }
            !ui.state.daemonInstalled -> item("nodaemon") { Hint(stringResource(R.string.akar_no_daemon)) }
        }
        if (ui.available && ui.state.daemonInstalled) {
            if (ui.error) item("error") { Hint(stringResource(R.string.akar_error), error = true) }

            item("apps-title") { SectionTitle(stringResource(R.string.akar_apps)) }
            if (ui.termuxInstalled && !ui.termuxEnrolled) {
                item("termux") {
                    FilledTonalButton(
                        { editing = RootRepository.TERMUX },
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) { Text(stringResource(R.string.akar_add_termux)) }
                }
            }
            val apps = ui.apps
            if (apps.isEmpty()) item("no-apps") { Hint(stringResource(R.string.akar_no_apps)) }
            items(apps.size, key = { apps[it].uid }) { i ->
                GrantRow(apps[i], repo, groupedShape(i, apps.size)) { editing = apps[i].primaryPackage }
            }
            item("add") {
                OutlinedButton({ picking = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.akar_add_app))
                }
            }

            if (ui.denied.isNotEmpty()) {
                item("denied-title") { SectionTitle(stringResource(R.string.akar_denied)) }
                items(ui.denied.size, key = { "d${ui.denied[it].uid}" }) { i ->
                    DeniedRow(ui.denied[i], repo, groupedShape(i, ui.denied.size)) {
                        editing = ui.denied[i].packageName
                    }
                }
                item("denied-clear") {
                    TextButton(viewModel::clearDenied) { Text(stringResource(R.string.akar_denied_clear)) }
                }
            }

            item("adb") {
                SectionCard(stringResource(R.string.akar_adb_title), Modifier.padding(top = 12.dp),
                    subtitle = stringResource(R.string.akar_adb_summary)) {
                    SwitchRow(
                        title = stringResource(R.string.akar_adb_switch),
                        summary = ui.adbShell?.let { adbSummary(it) },
                        checked = ui.adbShell != null,
                        onChange = { on -> if (on) gate { viewModel.setAdbShell(true) } else viewModel.setAdbShell(false) },
                    )
                }
            }

            item("behaviour") {
                SectionCard(stringResource(R.string.akar_behaviour), Modifier.padding(top = 12.dp)) {
                    SwitchRow(
                        title = stringResource(R.string.akar_notify_denied),
                        summary = stringResource(R.string.akar_notify_denied_summary),
                        checked = ui.notifyDenied,
                        onChange = { viewModel.setOption("notify_denied", if (it) 1 else 0) },
                    )
                    SwitchRow(
                        title = stringResource(R.string.akar_log_full),
                        summary = stringResource(R.string.akar_log_full_summary),
                        checked = ui.logFull,
                        onChange = { viewModel.setOption("log_full", if (it) 1 else 0) },
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.akar_idle), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.akar_idle_summary), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 7, 30, 90).forEach { days ->
                                FilterChip(
                                    selected = ui.idleDays == days,
                                    onClick = { viewModel.setOption("idle_days", days) },
                                    label = {
                                        Text(if (days == 0) stringResource(R.string.akar_idle_never)
                                        else stringResource(R.string.akar_idle_days, days))
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item("safety") {
                SectionCard(stringResource(R.string.akar_safety), Modifier.padding(top = 12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(viewModel::runSelfTest) { Text(stringResource(R.string.akar_selftest)) }
                        OutlinedButton({ confirmRevokeAll = true }, enabled = ui.grants.isNotEmpty()) {
                            Text(stringResource(R.string.akar_revoke_all))
                        }
                    }
                    ui.selfTest?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item("activity-title") { SectionTitle(stringResource(R.string.akar_activity)) }
            if (ui.audit.isEmpty()) item("no-audit") { Hint(stringResource(R.string.akar_no_activity)) }
            val shown = ui.audit.take(40)
            items(shown.size, key = { "a$it" }) { i -> AuditRow(shown[i], repo, groupedShape(i, shown.size)) }
        }
    }

    editing?.let { pkg ->
        val existing = ui.grants.firstOrNull { pkg in it.packages }
        EnrollSheet(
            pkg = pkg,
            label = repo.labelOf(pkg),
            existing = existing,
            onDismiss = { editing = null },
            onSave = { tier, choice, global, flags ->
                gate {
                    viewModel.enroll(pkg, tier, choice.scope, choice.durationMs, global, flags)
                    editing = null
                }
            },
            onRevoke = existing?.let { g -> { viewModel.revoke(g.uid); editing = null } },
        )
    }
    if (picking) {
        AppPicker(repo, onDismiss = { picking = false }) { pkg ->
            picking = false
            editing = pkg
        }
    }
    if (confirmRevokeAll) {
        AlertDialog(
            onDismissRequest = { confirmRevokeAll = false },
            title = { Text(stringResource(R.string.akar_revoke_all)) },
            text = { Text(stringResource(R.string.akar_revoke_all_body)) },
            confirmButton = {
                TextButton({ confirmRevokeAll = false; viewModel.revokeAll() }) {
                    Text(stringResource(R.string.akar_revoke_all_confirm))
                }
            },
            dismissButton = { TextButton({ confirmRevokeAll = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}

/**
 * Runs an action only after the device credential was entered (a secure lock is required:
 * without one root cannot be handed out at all).
 */
@Composable
private fun rememberCredentialGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var noLock by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }
    val title = stringResource(R.string.akar_credential_title)
    val body = stringResource(R.string.akar_credential_body)
    if (noLock) {
        AlertDialog(
            onDismissRequest = { noLock = false },
            title = { Text(stringResource(R.string.akar_no_lock_title)) },
            text = { Text(stringResource(R.string.akar_no_lock_body)) },
            confirmButton = { TextButton({ noLock = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
    return { action ->
        val intent = context.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(title, body)
        if (intent == null) {
            noLock = true
        } else {
            pending = action
            launcher.launch(intent)
        }
    }
}

@Composable
private fun Hero(ui: AkarUi, gate: (() -> Unit) -> Unit, vm: AkarViewModel) {
    val canUse = ui.available && ui.state.daemonInstalled
    Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Rounded.Security, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.akar_hero_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(when {
                            !canUse -> R.string.akar_status_unavailable
                            ui.state.frozen -> R.string.akar_status_frozen
                            !ui.state.enabled -> R.string.akar_status_off
                            ui.state.daemonRunning -> R.string.akar_status_ready
                            else -> R.string.akar_status_starting
                        }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.akar_enable),
                summary = stringResource(R.string.akar_enable_summary),
                checked = ui.state.enabled,
                enabled = canUse,
                onChange = { on -> if (on) gate { vm.setEnabled(true) } else vm.setEnabled(false) },
            )
            SwitchRow(
                title = stringResource(R.string.akar_freeze),
                summary = stringResource(R.string.akar_freeze_summary),
                checked = ui.state.frozen,
                enabled = canUse && ui.state.enabled,
                // Freezing is always safe; thawing gives root back, so it needs the credential.
                onChange = { on -> if (on) vm.setFrozen(true) else gate { vm.setFrozen(false) } },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(16.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TierChip(tier: RootTier) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(8.dp),
        color = if (tier == RootTier.ROOT) scheme.errorContainer else scheme.secondaryContainer) {
        Text(
            stringResource(if (tier == RootTier.ROOT) R.string.akar_tier_root else R.string.akar_tier_shell),
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (tier == RootTier.ROOT) scheme.onErrorContainer else scheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun GrantRow(g: RootGrant, repo: RootRepository, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppIcon(g.primaryPackage)
            Column(Modifier.weight(1f)) {
                Text(repo.labelOf(g.primaryPackage), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(grantSummary(g), style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TierChip(g.tier)
        }
    }
}

@Composable
private fun grantSummary(g: RootGrant): String {
    val now = System.currentTimeMillis()
    val scope = when (g.scope) {
        RootScope.ALWAYS -> stringResource(R.string.akar_scope_always)
        RootScope.UNTIL_REBOOT -> stringResource(R.string.akar_scope_reboot)
        RootScope.TIMED -> stringResource(R.string.akar_scope_until,
            DateUtils.getRelativeTimeSpanString(g.expiresMs, now, MINUTE).toString())
    }
    val used = if (g.lastUsedMs > 0) stringResource(R.string.akar_last_used,
        DateUtils.getRelativeTimeSpanString(g.lastUsedMs, now, MINUTE).toString())
    else stringResource(R.string.akar_never_used)
    return "$scope · $used"
}

@Composable
private fun adbSummary(g: RootGrant): String = grantSummary(g)

@Composable
private fun DeniedRow(d: DeniedAttempt, repo: RootRepository, shape: androidx.compose.ui.graphics.Shape, onEnroll: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (d.packageName.isNotEmpty()) AppIcon(d.packageName) else Icon(Icons.Rounded.Lock, null, Modifier.size(40.dp))
            Column(Modifier.weight(1f)) {
                Text(if (d.packageName.isEmpty()) "uid ${d.uid}" else repo.labelOf(d.packageName),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(stringResource(R.string.akar_denied_row, d.count,
                    DateUtils.getRelativeTimeSpanString(d.lastMs, System.currentTimeMillis(), MINUTE).toString()),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (d.packageName.isNotEmpty() && d.reason == "not_enrolled") {
                TextButton(onEnroll) { Text(stringResource(R.string.akar_review)) }
            }
        }
    }
}

@Composable
private fun AuditRow(e: AuditEvent, repo: RootRepository, shape: androidx.compose.ui.graphics.Shape) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            val who = e.packages.split(',').firstOrNull { it.isNotEmpty() && it != "-" }?.let { repo.labelOf(it) }
            Text(
                listOfNotNull(e.event, who).joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                listOf(DateUtils.getRelativeTimeSpanString(e.time, System.currentTimeMillis(), MINUTE).toString(),
                    e.tier.takeIf { it != "-" && it != "0" }, e.detail.takeIf { it.isNotEmpty() })
                    .filterNotNull().joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EnrollSheet(
    pkg: String,
    label: String,
    existing: RootGrant?,
    onDismiss: () -> Unit,
    onSave: (RootTier, ScopeChoice, Boolean, Int) -> Unit,
    onRevoke: (() -> Unit)?,
) {
    var tier by rememberSaveable(pkg) { mutableStateOf(existing?.tier ?: RootTier.SHELL) }
    var scope by rememberSaveable(pkg) { mutableStateOf(existing?.let { scopeIndexOf(it) } ?: 0) }
    var global by rememberSaveable(pkg) { mutableStateOf(existing?.globalNamespace ?: true) }
    var beforeUnlock by rememberSaveable(pkg) {
        mutableStateOf((existing?.flags ?: 0) and GrantFlag.ALLOW_BEFORE_UNLOCK != 0)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(pkg, size = 48.dp)
                Column {
                    Text(label, style = MaterialTheme.typography.headlineSmall)
                    Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.akar_tier), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RootTier.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = tier == t,
                        onClick = { tier = t },
                        shape = SegmentedButtonDefaults.itemShape(i, RootTier.entries.size),
                    ) { Text(stringResource(if (t == RootTier.ROOT) R.string.akar_tier_root else R.string.akar_tier_shell)) }
                }
            }
            Text(
                stringResource(if (tier == RootTier.ROOT) R.string.akar_tier_root_help else R.string.akar_tier_shell_help),
                style = MaterialTheme.typography.bodyMedium,
                color = if (tier == RootTier.ROOT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.akar_scope), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SCOPES.forEachIndexed { i, c ->
                    FilterChip(selected = scope == i, onClick = { scope = i }, label = { Text(stringResource(c.label)) })
                }
            }
            SwitchRow(stringResource(R.string.akar_global_ns), global, { global = it },
                summary = stringResource(R.string.akar_global_ns_summary))
            SwitchRow(stringResource(R.string.akar_before_unlock), beforeUnlock, { beforeUnlock = it },
                summary = stringResource(R.string.akar_before_unlock_summary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    onSave(tier, SCOPES[scope], global,
                        if (beforeUnlock) GrantFlag.ALLOW_BEFORE_UNLOCK else 0)
                }) { Text(stringResource(if (existing == null) R.string.akar_allow else R.string.akar_save)) }
                onRevoke?.let {
                    OutlinedButton(it) { Text(stringResource(R.string.akar_revoke)) }
                }
            }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }
}

/** The sheet's scope chip that best describes an existing grant. */
private fun scopeIndexOf(g: RootGrant): Int = when (g.scope) {
    RootScope.ALWAYS -> 0
    RootScope.UNTIL_REBOOT -> 1
    RootScope.TIMED -> {
        val left = (g.expiresMs - System.currentTimeMillis()).coerceAtLeast(0)
        when {
            left <= 10 * MINUTE -> 2
            left <= 60 * MINUTE -> 3
            else -> 4
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPicker(repo: RootRepository, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val apps by produceState<List<Pair<String, String>>?>(null) { value = repo.installedApps() }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.updates_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            val list = apps?.filter { query.isBlank() || it.second.contains(query, true) || it.first.contains(query, true) }
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp).padding(top = 8.dp, bottom = 16.dp)) {
                    items(list, key = { it.first }) { (pkg, name) ->
                        Surface(onClick = { onPick(pkg) }, color = androidx.compose.ui.graphics.Color.Transparent) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                AppIcon(pkg)
                                Column {
                                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
