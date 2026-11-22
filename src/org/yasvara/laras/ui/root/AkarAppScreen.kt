/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.root.AuditEvent
import org.yasvara.laras.data.root.GrantFlag
import org.yasvara.laras.data.root.InstalledApp
import org.yasvara.laras.data.root.RootGrant
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.data.root.RootScope
import org.yasvara.laras.data.root.RootTier
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.groupedShape

private enum class ScopeOption { KEEP, ALWAYS, UNTIL_REBOOT, TEN_MIN, ONE_HOUR, ONE_DAY }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AkarAppScreen(
    packageName: String,
    onBack: () -> Unit,
    viewModel: AkarViewModel = rememberAkarViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val repo = remember { viewModel.repository() }
    val gate = rememberCredentialGate()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (ui.loading) viewModel.load()
    }

    val appInfo by produceState<InstalledApp?>(
        initialValue = if (packageName == RootRepository.SHELL_PACKAGE) {
            InstalledApp(
                RootRepository.SHELL_PACKAGE,
                stringResource(R.string.akar_adb_title),
                RootRepository.UID_SHELL,
                true,
                listOf(RootRepository.SHELL_PACKAGE),
            )
        } else {
            ui.installedApps.firstOrNull { packageName in it.sharedPackages }
        },
        packageName,
        ui.installedApps,
    ) {
        if (value == null) value = repo.resolveApp(packageName)
    }

    val existing: RootGrant? = remember(ui.grants, packageName) {
        ui.grants.firstOrNull { packageName in it.packages }
    }
    val savedPreset = remember(packageName) { repo.savedProfile(packageName) }

    val hasLiveTimed = existing != null &&
        existing.scope == RootScope.TIMED &&
        existing.expiresMs > System.currentTimeMillis()

    var grantEnabled by rememberSaveable(packageName, existing != null) {
        mutableStateOf(existing != null)
    }
    var tier by rememberSaveable(packageName, existing?.tier) {
        mutableStateOf(
            existing?.tier
                ?: savedPreset?.tier
                ?: if (packageName == RootRepository.SHELL_PACKAGE) RootTier.ROOT else RootTier.SHELL,
        )
    }
    var scopeOption by rememberSaveable(packageName, existing?.scope, existing?.expiresMs) {
        mutableStateOf(
            when {
                hasLiveTimed -> ScopeOption.KEEP
                existing?.scope == RootScope.ALWAYS -> ScopeOption.ALWAYS
                existing?.scope == RootScope.UNTIL_REBOOT -> ScopeOption.UNTIL_REBOOT
                savedPreset?.scope == RootScope.UNTIL_REBOOT -> ScopeOption.UNTIL_REBOOT
                savedPreset?.scope == RootScope.TIMED -> when {
                    savedPreset.durationMs <= 10 * MINUTE_MS -> ScopeOption.TEN_MIN
                    savedPreset.durationMs <= HOUR_MS -> ScopeOption.ONE_HOUR
                    else -> ScopeOption.ONE_DAY
                }
                packageName == RootRepository.SHELL_PACKAGE -> ScopeOption.ONE_HOUR
                else -> ScopeOption.ALWAYS
            },
        )
    }
    var flags by rememberSaveable(packageName, existing?.flags) {
        mutableIntStateOf(existing?.flags ?: savedPreset?.flags ?: 0)
    }
    var confirmRevoke by rememberSaveable { mutableStateOf(false) }
    var inspectedLog by remember { mutableStateOf<AuditEvent?>(null) }

    val appLabel = appInfo?.label ?: repo.labelOf(packageName)
    val appUid = appInfo?.uid ?: existing?.uid
    val sharedPkgs = appInfo?.sharedPackages ?: existing?.packages ?: listOf(packageName)
    val recentLogs = remember(ui.audit, appUid) {
        if (appUid == null) emptyList() else ui.audit.filter { it.uid == appUid }.take(8)
    }

    fun saveChanges() {
        if (!grantEnabled) {
            if (existing != null) {
                confirmRevoke = true
            } else {
                onBack()
            }
            return
        }
        val (targetScope, durationMs) = when (scopeOption) {
            ScopeOption.KEEP -> {
                val left = ((existing?.expiresMs ?: 0L) - System.currentTimeMillis())
                    .coerceAtLeast(MINUTE_MS)
                RootScope.TIMED to left
            }
            ScopeOption.ALWAYS -> RootScope.ALWAYS to 0L
            ScopeOption.UNTIL_REBOOT -> RootScope.UNTIL_REBOOT to 0L
            ScopeOption.TEN_MIN -> RootScope.TIMED to (10 * MINUTE_MS)
            ScopeOption.ONE_HOUR -> RootScope.TIMED to HOUR_MS
            ScopeOption.ONE_DAY -> RootScope.TIMED to DAY_MS
        }
        gate {
            viewModel.enroll(
                pkg = packageName,
                tier = tier,
                scope = targetScope,
                durationMs = durationMs,
                globalNamespace = existing?.globalNamespace ?: true,
                flags = flags,
            ) { ok ->
                if (ok) onBack()
            }
        }
    }

    val surface = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        containerColor = surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (appUid != null) {
                            Text(
                                stringResource(R.string.akar_profile_uid, appUid),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    if (grantEnabled || existing != null) {
                        FilledTonalButton(
                            onClick = ::saveChanges,
                            colors = if (!grantEnabled && existing != null) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(
                                stringResource(
                                    if (!grantEnabled && existing != null) R.string.akar_revoke
                                    else if (existing == null) R.string.akar_allow
                                    else R.string.akar_save,
                                ),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surface,
                    scrolledContainerColor = surface,
                ),
            )
        },
    ) { padding ->
        if (ui.loading && appInfo == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Identity + Master Grant Switch
            item("identity") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Surface(
                        shape = groupedShape(0, 2),
                        color = MaterialTheme.colorScheme.surfaceBright,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (packageName == RootRepository.SHELL_PACKAGE) {
                                Surface(
                                    Modifier.size(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Terminal,
                                            null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            } else {
                                AppIcon(packageName, size = 44.dp)
                            }
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        appLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (existing != null) {
                                        TierChip(existing.tier)
                                    } else {
                                        CompactTag(
                                            text = stringResource(R.string.akar_not_granted),
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                existing?.let { g ->
                                    Text(
                                        grantSummaryLine(g),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        shape = groupedShape(1, 2),
                        color = MaterialTheme.colorScheme.surfaceBright,
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(grantEnabled, role = Role.Switch) { grantEnabled = it }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.akar_grant_access),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.akar_grant_access_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            CheckSwitch(grantEnabled)
                        }
                    }
                }
            }

            if (!grantEnabled && existing == null) {
                item("grant_hint") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.akar_grant_prompt_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            if (grantEnabled) {
                // Tier & Scope Bento Card
                item("tier_scope") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceBright,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.akar_tier),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    RootTier.entries.forEachIndexed { i, t ->
                                        SegmentedButton(
                                            selected = tier == t,
                                            onClick = { tier = t },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                i,
                                                RootTier.entries.size,
                                            ),
                                        ) {
                                            Text(
                                                stringResource(
                                                    if (t == RootTier.ROOT) R.string.akar_tier_root
                                                    else R.string.akar_tier_shell,
                                                ),
                                            )
                                        }
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (tier == RootTier.ROOT) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    },
                                ) {
                                    Text(
                                        stringResource(
                                            if (tier == RootTier.ROOT) R.string.akar_tier_root_help
                                            else R.string.akar_tier_shell_help,
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (tier == RootTier.ROOT) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.akar_scope),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                val keepText = existing?.takeIf { hasLiveTimed }?.let { grant ->
                                    DateUtils.getRelativeTimeSpanString(
                                        grant.expiresMs,
                                        System.currentTimeMillis(),
                                        MINUTE_MS,
                                        DateUtils.FORMAT_ABBREV_RELATIVE,
                                    ).toString()
                                }

                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (keepText != null) {
                                        FilterChip(
                                            selected = scopeOption == ScopeOption.KEEP,
                                            onClick = { scopeOption = ScopeOption.KEEP },
                                            label = {
                                                Text(stringResource(R.string.akar_scope_keep, keepText))
                                            },
                                        )
                                    }
                                    listOf(
                                        ScopeOption.ALWAYS to R.string.akar_scope_always,
                                        ScopeOption.UNTIL_REBOOT to R.string.akar_scope_reboot,
                                        ScopeOption.TEN_MIN to R.string.akar_scope_10min,
                                        ScopeOption.ONE_HOUR to R.string.akar_scope_1h,
                                        ScopeOption.ONE_DAY to R.string.akar_scope_24h,
                                    ).forEach { (opt, labelRes) ->
                                        FilterChip(
                                            selected = scopeOption == opt,
                                            onClick = { scopeOption = opt },
                                            label = { Text(stringResource(labelRes)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Flags & Namespace Card
                item("flags") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.akar_flags_section),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                        )
                        FlagSwitchCardRow(
                            icon = Icons.Rounded.LockOpen,
                            title = stringResource(R.string.akar_before_unlock),
                            summary = stringResource(R.string.akar_before_unlock_summary),
                            checked = flags and GrantFlag.ALLOW_BEFORE_UNLOCK != 0,
                            shape = groupedShape(0, 4),
                            onChange = { on ->
                                flags = if (on) flags or GrantFlag.ALLOW_BEFORE_UNLOCK
                                else flags and GrantFlag.ALLOW_BEFORE_UNLOCK.inv()
                            },
                        )
                        FlagSwitchCardRow(
                            icon = Icons.Rounded.Notifications,
                            title = stringResource(R.string.akar_notify_on_use),
                            summary = stringResource(R.string.akar_notify_on_use_summary),
                            checked = flags and GrantFlag.NOTIFY_ON_USE != 0,
                            shape = groupedShape(1, 4),
                            onChange = { on ->
                                flags = if (on) flags or GrantFlag.NOTIFY_ON_USE
                                else flags and GrantFlag.NOTIFY_ON_USE.inv()
                            },
                        )
                        FlagSwitchCardRow(
                            icon = Icons.Rounded.Terminal,
                            title = stringResource(R.string.akar_log_full),
                            summary = stringResource(R.string.akar_log_full_app_summary),
                            checked = flags and GrantFlag.LOG_FULL != 0,
                            shape = groupedShape(2, 4),
                            onChange = { on ->
                                flags = if (on) flags or GrantFlag.LOG_FULL
                                else flags and GrantFlag.LOG_FULL.inv()
                            },
                        )
                        Surface(
                            shape = groupedShape(3, 4),
                            color = MaterialTheme.colorScheme.surfaceBright,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Public,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.akar_global_ns),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        stringResource(R.string.akar_global_ns_locked),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                CompactTag(
                                    text = "GLOBAL",
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Shared UID Packages
            if (sharedPkgs.size > 1) {
                item("shared_uid") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.akar_shared_uid_title, sharedPkgs.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                        )
                        sharedPkgs.forEachIndexed { idx, pkg ->
                            Surface(
                                shape = groupedShape(idx, sharedPkgs.size),
                                color = MaterialTheme.colorScheme.surfaceBright,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AppIcon(pkg, size = 32.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            repo.labelOf(pkg),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            pkg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Activity for this UID
            if (recentLogs.isNotEmpty()) {
                item("recent_logs") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.akar_app_activity),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                        )
                        recentLogs.forEachIndexed { idx, ev ->
                            Surface(
                                shape = groupedShape(idx, recentLogs.size),
                                color = MaterialTheme.colorScheme.surfaceBright,
                                onClick = { inspectedLog = ev },
                            ) {
                                Column(
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            listOfNotNull(
                                                ev.event,
                                                ev.tier.takeIf { it != "-" && it != "0" },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            DateUtils.getRelativeTimeSpanString(
                                                ev.time,
                                                System.currentTimeMillis(),
                                                MINUTE_MS,
                                            ).toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (ev.detail.isNotEmpty()) {
                                        Text(
                                            ev.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Explicit Revoke button when already enrolled
            if (existing != null) {
                item("revoke_btn") {
                    OutlinedButton(
                        onClick = { confirmRevoke = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        Text(stringResource(R.string.akar_revoke))
                    }
                }
            }
        }
    }

    if (confirmRevoke && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text(stringResource(R.string.akar_revoke_confirm_title)) },
            text = { Text(stringResource(R.string.akar_revoke_confirm_body, appLabel)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevoke = false
                    gate {
                        viewModel.revoke(existing.uid) { ok ->
                            if (ok) onBack()
                        }
                    }
                }) {
                    Text(
                        stringResource(R.string.akar_revoke),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    inspectedLog?.let { ev ->
        val copiedMsg = stringResource(R.string.akar_copied)
        LogDetailDialog(
            event = ev,
            repo = repo,
            onDismiss = { inspectedLog = null },
            onCopied = { scope.launch { snackbar.showSnackbar(copiedMsg) } },
        )
    }
}

@Composable
private fun FlagSwitchCardRow(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onChange: (Boolean) -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                icon,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CheckSwitch(checked)
        }
    }
}
