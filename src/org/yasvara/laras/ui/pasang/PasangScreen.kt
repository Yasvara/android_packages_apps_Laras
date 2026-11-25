/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.pasang

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.pasang.PasangPlan
import org.yasvara.laras.data.pasang.PasangSlotInfo
import org.yasvara.laras.data.pasang.PasangStatus
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page
import java.util.Locale

private fun formatSpeed(bps: Long): String {
    if (bps <= 0L) return "…"
    val mb = bps.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 10.0) String.format(Locale.US, "%.0f MB/s", mb)
    else String.format(Locale.US, "%.1f MB/s", mb)
}

private fun formatEta(sec: Long): String {
    if (sec < 0L) return "…"
    val m = sec / 60L
    val s = sec % 60L
    return if (m > 0L) "${m}m ${s}s" else "${s}s"
}

@Composable
fun PasangScreen(
    onBack: () -> Unit,
    viewModel: PasangViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var devExpanded by rememberSaveable { mutableStateOf(state.isDebuggableBuild) }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.onPickUri(uri)
    }

    Page(stringResource(R.string.pasang_title), onBack) {
        // 1. Hero System & Slot Status Card
        item(key = "hero") {
            SystemStatusHeroCard(
                state = state,
                onRefresh = viewModel::refresh,
            )
        }

        if (!state.daemonAvailable) {
            item(key = "unavailable") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            stringResource(R.string.pasang_service_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        // 2. Active OTA Progress OR Reboot-Ready Banner
        if (state.status.busy || state.status.rebootRequired || state.status.phase == "error" || state.slotInfo.pendingSlotSwitch) {
            item(key = "progress") {
                Spacer(Modifier.height(LarasSpacing.GroupGap))
                OtaProgressCard(
                    status = state.status,
                    slotInfo = state.slotInfo,
                    onCancel = viewModel::cancelInstall,
                    onReboot = viewModel::rebootNow,
                )
            }
        }

        // 3. Local OTA Package Picker & Hyper-Flash Plan Preview
        item(key = "local-header") {
            LarasSectionHeader(stringResource(R.string.pasang_local_section))
        }
        item(key = "local-picker") {
            LocalPackageCard(
                state = state,
                onPick = { zipPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                onAnalyze = viewModel::reanalyzePlan,
                onInstall = viewModel::startInstall,
            )
        }

        if (state.message != null) {
            item(key = "msg") {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = state.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // 4. Developer & Debug Options (strictly hidden on production user builds)
        if (state.isDevVisible) {
            item(key = "dev-header") {
                Spacer(Modifier.height(LarasSpacing.GroupGap))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { devExpanded = !devExpanded }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.DeveloperMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.pasang_dev_section),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (devExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        AnimatedVisibility(visible = devExpanded) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap),
                            ) {
                                val devRowCount = if (state.isDebuggableBuild) 5 else 3
                                var idx = 0

                                DevSwitchRow(
                                    title = stringResource(R.string.pasang_dev_auto_clean_overlay),
                                    summary = stringResource(R.string.pasang_dev_auto_clean_overlay_summary),
                                    checked = state.devOptions.autoCleanOverlay,
                                    shape = groupedShape(idx++, devRowCount),
                                    onToggle = viewModel::setAutoCleanOverlay,
                                )

                                // Manual Clean OverlayFS Now row
                                Surface(
                                    shape = groupedShape(idx++, devRowCount),
                                    color = MaterialTheme.colorScheme.surfaceBright,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.CleaningServices,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                stringResource(R.string.pasang_dev_clean_now),
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            val ovlSummary = when {
                                                state.lastOverlayClean != null -> {
                                                    val r = state.lastOverlayClean!!
                                                    stringResource(
                                                        R.string.pasang_dev_overlay_cleaned,
                                                        r.removedEntries,
                                                        Formatter.formatShortFileSize(context, r.freedBytes),
                                                    )
                                                }
                                                state.slotInfo.overlayFiles > 0L -> stringResource(
                                                    R.string.pasang_dev_overlay_files,
                                                    state.slotInfo.overlayFiles,
                                                    Formatter.formatShortFileSize(context, state.slotInfo.overlayBytes),
                                                )
                                                else -> stringResource(R.string.pasang_dev_overlay_clean)
                                            }
                                            Text(
                                                ovlSummary,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        FilledTonalButton(
                                            onClick = viewModel::cleanOverlaysNow,
                                            enabled = !state.cleaningOverlay && !state.status.busy && state.daemonAvailable,
                                        ) {
                                            if (state.cleaningOverlay) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            } else {
                                                Text(stringResource(R.string.pasang_dev_clean_now).substringBefore(' '))
                                            }
                                        }
                                    }
                                }

                                DevSwitchRow(
                                    title = stringResource(R.string.pasang_dev_extent_aliasing),
                                    summary = stringResource(R.string.pasang_dev_extent_aliasing_summary),
                                    checked = state.devOptions.enableExtentAliasing,
                                    shape = groupedShape(idx++, devRowCount),
                                    onToggle = viewModel::setExtentAliasing,
                                )

                                if (state.isDebuggableBuild) {
                                    DevSwitchRow(
                                        title = stringResource(R.string.pasang_dev_fast_verify),
                                        summary = stringResource(R.string.pasang_dev_fast_verify_summary),
                                        checked = state.devOptions.fastVerify,
                                        shape = groupedShape(idx++, devRowCount),
                                        onToggle = viewModel::setFastVerify,
                                    )
                                    DevSwitchRow(
                                        title = stringResource(R.string.pasang_dev_allow_dev_key),
                                        summary = stringResource(R.string.pasang_dev_allow_dev_key_summary),
                                        checked = state.devOptions.allowDevKey,
                                        shape = groupedShape(idx, devRowCount),
                                        onToggle = viewModel::setAllowDevKey,
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

@Composable
private fun SystemStatusHeroCard(
    state: PasangUiState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val slot = state.slotInfo
    val usedFrac = if (slot.superTotalBytes > 0L) {
        (slot.superUsedBytes.toDouble() / slot.superTotalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else 0f

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.buildDisplay,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.pasang_hero_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
                IconButton(onClick = onRefresh, enabled = !state.loading) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.pasang_refresh),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricChip(
                    icon = Icons.Rounded.Memory,
                    label = stringResource(R.string.pasang_slot_label, slot.activeSlotLetter),
                    modifier = Modifier.weight(1f),
                )
                MetricChip(
                    icon = Icons.Rounded.Bolt,
                    label = if (slot.mergeStatus == "none") {
                        stringResource(R.string.pasang_merge_ready)
                    } else {
                        stringResource(R.string.pasang_merge_busy, slot.mergeStatus)
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }

            if (slot.superTotalBytes > 0L) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(R.string.pasang_super_space),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            stringResource(
                                R.string.pasang_super_free,
                                Formatter.formatShortFileSize(context, slot.superFreeBytes),
                                Formatter.formatShortFileSize(context, slot.superTotalBytes),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { usedFrac },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        strokeCap = StrokeCap.Round,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OtaProgressCard(
    status: PasangStatus,
    slotInfo: PasangSlotInfo,
    onCancel: () -> Unit,
    onReboot: () -> Unit,
) {
    val context = LocalContext.current
    val readyToReboot = status.rebootRequired || (!status.busy && slotInfo.pendingSlotSwitch)
    val isError = status.phase == "error"

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            readyToReboot -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceBright
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val phaseText = when (status.phase) {
                "preflight" -> stringResource(R.string.pasang_phase_preflight)
                "indexing" -> stringResource(R.string.pasang_phase_indexing)
                "planning" -> stringResource(R.string.pasang_phase_planning)
                "flashing" -> stringResource(R.string.pasang_phase_flashing, status.currentPartition)
                "verifying" -> stringResource(R.string.pasang_phase_verifying)
                "committing" -> stringResource(R.string.pasang_phase_committing)
                "cleaning_overlay" -> stringResource(R.string.pasang_phase_cleaning_overlay)
                "done" -> stringResource(R.string.pasang_phase_done)
                "cancelled" -> stringResource(R.string.pasang_phase_cancelled)
                "error" -> stringResource(R.string.pasang_phase_error)
                else -> if (readyToReboot) stringResource(R.string.pasang_reboot_ready_title) else ""
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = when {
                        isError -> Icons.Rounded.Warning
                        readyToReboot -> Icons.Rounded.CheckCircle
                        else -> Icons.Rounded.Bolt
                    },
                    contentDescription = null,
                    tint = when {
                        isError -> MaterialTheme.colorScheme.onErrorContainer
                        readyToReboot -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Text(
                    phaseText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (status.busy) {
                    Text(
                        String.format(Locale.US, "%.0f%%", status.progressPct),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (status.busy) {
                LinearProgressIndicator(
                    progress = { (status.progressPct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    strokeCap = StrokeCap.Round,
                )
                if (status.bytesToWrite > 0L) {
                    Text(
                        stringResource(
                            R.string.pasang_telemetry_line,
                            Formatter.formatShortFileSize(context, status.bytesWritten),
                            Formatter.formatShortFileSize(context, status.bytesToWrite),
                            formatSpeed(status.speedBps),
                            formatEta(status.etaSeconds),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (status.aliasPct > 0.5f) {
                    Text(
                        stringResource(
                            R.string.pasang_alias_badge,
                            String.format(Locale.US, "%.1f", status.aliasPct),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pasang_cancel_action))
                    }
                }
            }

            if (isError && status.error.isNotEmpty()) {
                Text(
                    status.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            if (readyToReboot) {
                val nextLetter = if (slotInfo.activeBootSlot == 0) "A" else "B"
                Text(
                    stringResource(R.string.pasang_reboot_ready_body, nextLetter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Button(
                    onClick = onReboot,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pasang_reboot_action))
                }
            }
        }
    }
}

@Composable
private fun LocalPackageCard(
    state: PasangUiState,
    onPick: () -> Unit,
    onAnalyze: () -> Unit,
    onInstall: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.selectedLabel ?: stringResource(R.string.pasang_pick_zip),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.selectedPath ?: stringResource(R.string.pasang_pick_zip_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (state.planning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round,
                )
            }

            state.plan?.let { plan ->
                PlanSummaryBox(plan = plan)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onPick,
                    enabled = !state.status.busy && !state.planning && state.daemonAvailable,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pasang_pick_zip))
                }
                if (state.selectedPath != null && state.plan != null && state.plan.error.isEmpty()) {
                    Button(
                        onClick = onInstall,
                        enabled = !state.status.busy && !state.planning && state.daemonAvailable,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.pasang_install_action))
                    }
                } else if (state.selectedPath != null) {
                    OutlinedButton(
                        onClick = onAnalyze,
                        enabled = !state.status.busy && !state.planning && state.daemonAvailable,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.pasang_analyze_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanSummaryBox(plan: PasangPlan) {
    val context = LocalContext.current
    if (plan.error.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    plan.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    if (plan.signatureVerified) Icons.Rounded.Verified else Icons.Rounded.DeveloperMode,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.pasang_plan_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                stringResource(
                    R.string.pasang_plan_saved,
                    String.format(Locale.US, "%.1f", plan.savedPct),
                    Formatter.formatShortFileSize(context, plan.bytesToWrite),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(
                    R.string.pasang_plan_extents,
                    plan.dynamicPartitions,
                    plan.rawPartitions,
                    plan.totalExtents,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            )
            Text(
                if (plan.signatureVerified) stringResource(R.string.pasang_plan_sig_ok)
                else stringResource(R.string.pasang_plan_sig_dev),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun DevSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
