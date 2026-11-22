/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.padat

import android.content.Context
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.padat.AppPolicyEntry
import org.yasvara.laras.data.padat.PadatAlgo
import org.yasvara.laras.data.padat.PadatAppItem
import org.yasvara.laras.data.padat.PadatPreset
import org.yasvara.laras.data.padat.PadatScanResult
import org.yasvara.laras.data.padat.PadatScopes
import org.yasvara.laras.data.padat.PadatStatus
import org.yasvara.laras.data.padat.ScopeBucket
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private enum class PadatFilter(val label: Int) {
    ALL(R.string.padat_filter_all),
    COMPRESSED(R.string.padat_filter_compressed),
    LARGE(R.string.padat_filter_large),
    RUNNING(R.string.padat_filter_running),
}

private enum class PadatSort(val label: Int) {
    LARGEST(R.string.padat_sort_largest),
    SAVED(R.string.padat_sort_saved),
    NAME(R.string.padat_sort_name),
}

private fun formatPctLabel(saved: Long, logical: Long): String? {
    if (saved <= 0L || logical <= 0L) return null
    val pct = (saved.toDouble() * 100.0) / logical.toDouble()
    return when {
        pct < 0.1 -> null
        pct < 10.0 -> String.format(Locale.US, "-%.1f%%", pct)
        else -> String.format(Locale.US, "-%d%%", pct.roundToInt())
    }
}

private fun formatEta(sec: Long): String {
    if (sec < 0L) return "…"
    val m = sec / 60L
    val s = sec % 60L
    return if (m > 0L) "${m}m ${s}s" else "${s}s"
}

private fun formatCount(n: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(n)

private fun formatPresetBadge(entry: AppPolicyEntry?, autoTier1: Boolean): String = when (entry) {
    null -> if (autoTier1) "Auto" else "Off"
    else -> when (entry.preset) {
        PadatPreset.OFF -> "Off"
        PadatPreset.FAST -> "Fast LZ4"
        PadatPreset.BALANCED -> "Balanced"
        PadatPreset.MAX -> "Max ZSTD"
        PadatPreset.CUSTOM -> entry.profileLabel()
    }
}

private fun formatBucketSize(ctx: Context, b: ScopeBucket): String {
    if (b.logicalBytes <= 0L) return "0 B"
    val log = Formatter.formatShortFileSize(ctx, b.logicalBytes)
    val pct = formatPctLabel(b.savedBytes, b.logicalBytes)
    return if (pct != null) {
        val phys = Formatter.formatShortFileSize(ctx, b.physicalBytes)
        "$phys / $log ($pct)"
    } else {
        log
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadatScreen(
    onBack: () -> Unit,
    focus: String? = null,
    viewModel: PadatViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(PadatFilter.ALL) }
    var sort by rememberSaveable { mutableStateOf(PadatSort.LARGEST) }
    var selectedPkg by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingJumpPkg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focus, ui.apps) {
        if (!focus.isNullOrEmpty() && selectedPkg == null && ui.apps.any { it.packageName == focus }) {
            selectedPkg = focus
        }
    }

    val shown = remember(ui.apps, query, filter, sort) {
        ui.apps
            .filter { app ->
                val matchesFilter = when (filter) {
                    PadatFilter.ALL -> true
                    PadatFilter.COMPRESSED -> app.compressedFiles > 0 || app.savedBytes > 0
                    PadatFilter.LARGE -> app.logicalBytes >= 200L * 1024L * 1024L
                    PadatFilter.RUNNING -> app.isRunning
                }
                matchesFilter && (
                    query.isBlank() ||
                        app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                    )
            }
            .let { list ->
                when (sort) {
                    PadatSort.LARGEST -> list.sortedByDescending { it.logicalBytes }
                    PadatSort.SAVED -> list.sortedByDescending { it.savedBytes }
                    PadatSort.NAME -> list.sortedBy { it.label.lowercase() }
                }
            }
    }

    // Smooth-scroll directly to the active app row when tapped from the off-screen mini-pill.
    LaunchedEffect(pendingJumpPkg, shown) {
        val target = pendingJumpPkg ?: return@LaunchedEffect
        val idx = shown.indexOfFirst { it.packageName == target }
        if (idx >= 0) {
            // Header items before the app list: hero (0) + controls (1).
            listState.animateScrollToItem((2 + idx).coerceAtLeast(0))
            pendingJumpPkg = null
        }
    }

    // Track whether the active app's inline card is currently visible in the viewport.
    val isActiveRowVisible by remember(ui.activePackage) {
        derivedStateOf {
            val pkg = ui.activePackage ?: return@derivedStateOf true
            listState.layoutInfo.visibleItemsInfo.any { it.key == pkg }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Page(
            title = stringResource(R.string.padat_title),
            onBack = onBack,
            listState = listState,
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !ui.refreshing) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.padat_refresh))
                }
            },
        ) {
            if (!ui.available) {
                item(key = "unavailable") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.padat_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
                return@Page
            }

            // 1. Hero summary card + opt-in Auto Tier-1 toggle (default OFF)
            item(key = "hero") {
                val savedText = Formatter.formatShortFileSize(context, ui.totalSavedBytes)
                val logicalText = Formatter.formatShortFileSize(context, ui.totalLogicalBytes)
                val physText = Formatter.formatShortFileSize(context, ui.totalPhysicalBytes)
                val compressedPct = formatPctLabel(ui.totalSavedBytes, ui.compressedAppsLogicalBytes)
                val status = ui.status
                val tempC = if (status != null && status.batteryTempMc > 0) {
                    String.format(Locale.US, "%.1f°C", status.batteryTempMc / 1000f)
                } else {
                    "—"
                }

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Compress,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.padat_hero_label),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Spacer(Modifier.weight(1f))
                            if (status != null && status.userdataCompression) {
                                Text(
                                    text = if (status.compressAutoReserve) "F2FS · WOF · $tempC" else "F2FS · $tempC",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = savedText,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (compressedPct != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "$compressedPct (${ui.compressedAppsCount} apps)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                        }
                        Text(
                            text = if (ui.totalLogicalBytes == 0L && (ui.loading || ui.refreshing)) {
                                stringResource(R.string.padat_calculating_size)
                            } else {
                                stringResource(R.string.padat_hero_breakdown, physText, logicalText, ui.apps.size)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setAutoTier1(!ui.policy.autoTier1) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.padat_auto_tier1_title),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.padat_auto_tier1_summary),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Switch(
                                    checked = ui.policy.autoTier1,
                                    onCheckedChange = viewModel::setAutoTier1,
                                )
                            }
                        }
                    }
                }
            }

            // 2. Search + filter/sort bar (no off-screen top progress card — progress lives inline in each app row!)
            item(key = "controls") {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(R.string.padat_search_hint)) },
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        PadatFilter.entries.forEach { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text(stringResource(f.label)) },
                            )
                        }
                        PadatSort.entries.forEach { s ->
                            FilterChip(
                                selected = sort == s,
                                onClick = { sort = s },
                                label = { Text("⇅ ${stringResource(s.label)}") },
                            )
                        }
                    }
                    AnimatedVisibility(visible = ui.refreshing && ui.apps.isNotEmpty()) {
                        LinearProgressIndicator(
                            strokeCap = StrokeCap.Round,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }

            if (ui.loading && ui.apps.isEmpty()) {
                item(key = "loading") {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(shown.size, key = { shown[it].packageName }) { idx ->
                    val app = shown[idx]
                    val isActive = ui.activePackage == app.packageName
                    PadatAppRow(
                        app = app,
                        autoTier1 = ui.policy.autoTier1,
                        isActive = isActive,
                        refreshing = ui.refreshing,
                        activeAction = if (isActive) ui.activeAction else null,
                        liveProgress = ui.liveStatusFor(app.packageName),
                        paused = ui.paused,
                        recentResult = ui.recentResults[app.packageName],
                        shape = groupedShape(idx, shown.size),
                        onClick = { selectedPkg = app.packageName },
                        onTogglePause = viewModel::togglePause,
                        onCancel = viewModel::cancelJob,
                        onDismissResult = { viewModel.dismissResult(app.packageName) },
                    )
                }
                // Extra bottom breathing room when floating off-screen pill could appear
                if (ui.activePackage != null) {
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(76.dp))
                    }
                }
            }
        }

        // 3. Contextual floating mini-pill ONLY when the active app row is scrolled off-screen
        val activePkg = ui.activePackage
        AnimatedVisibility(
            visible = activePkg != null && !isActiveRowVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (activePkg != null) {
                OffscreenActiveJobPill(
                    label = ui.activeLabel ?: activePkg,
                    status = ui.liveStatusFor(activePkg),
                    paused = ui.paused,
                    onJumpToApp = {
                        val idx = shown.indexOfFirst { it.packageName == activePkg }
                        if (idx >= 0) {
                            scope.launch { listState.animateScrollToItem(2 + idx) }
                        } else {
                            // Clear active search/filter so the working app becomes visible, then scroll to it.
                            filter = PadatFilter.ALL
                            query = ""
                            pendingJumpPkg = activePkg
                        }
                    },
                    onTogglePause = viewModel::togglePause,
                    onCancel = viewModel::cancelJob,
                )
            }
        }
    }

    // Per-app bottom sheet with full 6-bucket size breakdown + live progress when tapping the active app
    val selectedApp = ui.apps.firstOrNull { it.packageName == selectedPkg }
    if (selectedApp != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val isThisAppActive = ui.activePackage == selectedApp.packageName
        ModalBottomSheet(
            onDismissRequest = { selectedPkg = null },
            sheetState = sheetState,
        ) {
            PadatAppSheet(
                app = selectedApp,
                autoTier1 = ui.policy.autoTier1,
                busy = ui.activePackage != null,
                isThisAppActive = isThisAppActive,
                activeOtherLabel = if (!isThisAppActive) ui.activeLabel else null,
                activeAction = if (isThisAppActive) ui.activeAction else null,
                liveProgress = ui.liveStatusFor(selectedApp.packageName),
                paused = ui.paused,
                scanning = ui.scanningPkg == selectedApp.packageName,
                scanResult = ui.scanResults[selectedApp.packageName],
                onTogglePause = viewModel::togglePause,
                onCancel = viewModel::cancelJob,
                onScan = { entry -> viewModel.scanApp(selectedApp.packageName, entry) },
                onApply = { entry ->
                    selectedPkg = null
                    viewModel.startCompress(selectedApp, entry)
                },
                onDecompress = { entry ->
                    selectedPkg = null
                    viewModel.startDecompress(selectedApp, entry)
                },
            )
        }
    }

    // Running app warning dialog
    val prompt = ui.runningPrompt
    if (prompt != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRunningPrompt,
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.padat_running_title, prompt.label)) },
            text = { Text(stringResource(R.string.padat_running_body)) },
            confirmButton = {
                Button(onClick = { viewModel.resolveRunningPrompt(forceStop = true) }) {
                    Text(stringResource(R.string.padat_running_force_stop))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::dismissRunningPrompt) {
                        Text(stringResource(android.R.string.cancel))
                    }
                    FilledTonalButton(onClick = { viewModel.resolveRunningPrompt(forceStop = false) }) {
                        Text(stringResource(R.string.padat_running_continue))
                    }
                }
            },
        )
    }
}

/**
 * Reusable live telemetry + Pause/Cancel control panel.
 * Embedded INLINE inside the active [PadatAppRow] (and inside [PadatAppSheet] if the user taps it).
 */
@Composable
private fun InlineProgressPanel(
    status: PadatStatus?,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val hasWork = status != null && (status.totalBytes > 0L || status.totalFiles > 0L)
    val isThermal = status?.pauseReason == "thermal"
    val isPaused = paused || status?.pauseReason == "paused" || isThermal

    val animatedProgress by animateFloatAsState(
        targetValue = if (hasWork && status != null) status.progressFraction else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "padat_inline_progress",
    )

    Column(Modifier.fillMaxWidth()) {
        // Determinate smooth rounded bar (indeterminate during initial directory discovery)
        if (hasWork && status != null) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                strokeCap = StrokeCap.Round,
                color = if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        } else {
            LinearProgressIndicator(
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (hasWork && status != null) {
            // Row 1: Bytes + throughput on left, ETA / pause reason on right
            val doneB = Formatter.formatShortFileSize(ctx, status.doneBytes)
            val totB = Formatter.formatShortFileSize(ctx, status.totalBytes)
            val speedStr = if (!isPaused && status.speedBps > 0L) {
                " · ${Formatter.formatShortFileSize(ctx, status.speedBps)}/s"
            } else {
                ""
            }
            val rightStatus = when {
                isThermal -> stringResource(R.string.padat_job_thermal_label)
                isPaused -> stringResource(R.string.padat_job_paused_label)
                else -> "ETA ${formatEta(status.etaSeconds)}"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "$doneB / $totB$speedStr",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rightStatus,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPaused) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    },
                )
            }

            Spacer(Modifier.height(2.dp))

            // Row 2: File counters on left, live savings ticker on right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "${formatCount(status.doneFiles)} / ${formatCount(status.totalFiles)} files · " +
                        "${formatCount(status.compressedFiles)} ok · ${formatCount(status.skippedFiles)} skip",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (status.savedBytes > 0L) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "-${Formatter.formatShortFileSize(ctx, status.savedBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.padat_job_scanning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            )
        }

        Spacer(Modifier.height(8.dp))

        // Row 3: Current file path + direct inline Pause/Resume & Cancel controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = status?.currentFile?.takeIf { it.isNotEmpty() }
                    ?: stringResource(R.string.padat_job_preparing),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            // Compact Pause / Resume pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onTogglePause),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(
                            if (paused) R.string.padat_job_resume else R.string.padat_job_pause,
                        ),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (paused) R.string.padat_job_resume else R.string.padat_job_pause,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            // Compact Cancel button
            FilledTonalIconButton(
                onClick = onCancel,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.padat_job_cancel),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun OffscreenActiveJobPill(
    label: String,
    status: PadatStatus?,
    paused: Boolean,
    onJumpToApp: () -> Unit,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
) {
    val hasWork = status != null && (status.totalBytes > 0L || status.totalFiles > 0L)
    val pctText = if (hasWork && status != null) {
        String.format(Locale.US, "%d%%", (status.progressFraction * 100f).roundToInt())
    } else {
        "…"
    }
    val animatedProgress by animateFloatAsState(
        targetValue = if (hasWork && status != null) status.progressFraction else 0f,
        animationSpec = tween(durationMillis = 260),
        label = "padat_pill_progress",
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onJumpToApp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            if (hasWork && status != null) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "$label · $pctText",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (paused) {
                        stringResource(R.string.padat_job_paused_label)
                    } else {
                        stringResource(R.string.padat_job_jump_hint)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                )
            }
            IconButton(onClick = onTogglePause, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(
                        if (paused) R.string.padat_job_resume else R.string.padat_job_pause,
                    ),
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.padat_job_cancel),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PadatAppRow(
    app: PadatAppItem,
    autoTier1: Boolean,
    isActive: Boolean,
    refreshing: Boolean,
    activeAction: String?,
    liveProgress: PadatStatus?,
    paused: Boolean,
    recentResult: PadatAppResult?,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val ctx = LocalContext.current
    val physStr = Formatter.formatShortFileSize(ctx, app.physicalBytes)
    val logStr = Formatter.formatShortFileSize(ctx, app.logicalBytes)
    val savedStr = Formatter.formatShortFileSize(ctx, app.savedBytes)
    val pctLabel = formatPctLabel(app.savedBytes, app.logicalBytes)
    val modeLabel = formatPresetBadge(app.resolvedAppliedEntry(), autoTier1)

    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 220),
        label = "padat_row_bg",
    )
    val borderStroke = if (isActive) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
        null
    }

    val hasWork = liveProgress != null && (liveProgress.totalBytes > 0L || liveProgress.totalFiles > 0L)
    val isDecompressing = (liveProgress?.activeAction ?: activeAction) == "decompress"

    val activeShape = if (isActive) RoundedCornerShape(20.dp) else shape

    Surface(
        shape = activeShape,
        color = containerColor,
        contentColor = if (isActive) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isActive) 4.dp else 1.dp)
            .clip(activeShape)
            .clickable(onClick = onClick)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (app.isRunning && !isActive) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.padat_badge_running),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (isActive) {
                        val actionSubtitle = if (isDecompressing) {
                            stringResource(R.string.padat_job_decompressing)
                        } else {
                            stringResource(R.string.padat_job_compressing, modeLabel)
                        }
                        Text(
                            text = actionSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        Text(
                            text = if (app.logicalBytes == 0L && refreshing) {
                                stringResource(R.string.padat_calculating_size)
                            } else if (pctLabel != null) {
                                "$physStr / $logStr · -$savedStr ($pctLabel)"
                            } else if (app.savedBytes >= 1024L * 1024L) {
                                "$physStr / $logStr · -$savedStr"
                            } else {
                                logStr
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (app.savedBytes >= 1024L * 1024L) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))

                // Trailing badge: live percentage pill when active, preset badge when idle
                if (isActive) {
                    val isPaused = paused || liveProgress?.pauseReason == "paused" || liveProgress?.pauseReason == "thermal"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPaused) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (isPaused) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                    ) {
                        if (hasWork && liveProgress != null) {
                            val pctVal = liveProgress.progressFraction * 100f
                            val pctText = if (pctVal < 10f && pctVal > 0f) {
                                String.format(Locale.US, "%.1f%%", pctVal)
                            } else {
                                String.format(Locale.US, "%d%%", pctVal.roundToInt())
                            }
                            Text(
                                text = pctText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    color = if (isPaused) {
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (modeLabel == "Off") {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                    ) {
                        Text(
                            text = modeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (modeLabel == "Off") {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Inline live progress panel right inside this app's card!
            if (isActive) {
                Spacer(Modifier.height(10.dp))
                InlineProgressPanel(
                    status = liveProgress,
                    paused = paused,
                    onTogglePause = onTogglePause,
                    onCancel = onCancel,
                )
            } else if (recentResult != null) {
                // Inline completion / error / cancelled feedback right inside this app's card!
                Spacer(Modifier.height(8.dp))
                val chipBg = when {
                    recentResult.isError -> MaterialTheme.colorScheme.errorContainer
                    recentResult.isCancelled -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
                }
                val chipFg = when {
                    recentResult.isError -> MaterialTheme.colorScheme.onErrorContainer
                    recentResult.isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onTertiaryContainer
                }
                val chipIcon = when {
                    recentResult.isError -> Icons.Rounded.Warning
                    recentResult.isCancelled -> Icons.Rounded.Info
                    else -> Icons.Rounded.CheckCircle
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = chipBg,
                    contentColor = chipFg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        Icon(
                            imageVector = chipIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = recentResult.message,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = onDismissResult,
                            modifier = Modifier.size(26.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PadatAppSheet(
    app: PadatAppItem,
    autoTier1: Boolean,
    busy: Boolean,
    isThisAppActive: Boolean,
    activeOtherLabel: String?,
    activeAction: String?,
    liveProgress: PadatStatus?,
    paused: Boolean,
    scanning: Boolean,
    scanResult: PadatScanResult?,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onScan: (AppPolicyEntry) -> Unit,
    onApply: (AppPolicyEntry) -> Unit,
    onDecompress: (AppPolicyEntry) -> Unit,
) {
    val ctx = LocalContext.current
    val bd = scanResult?.breakdown ?: app.breakdown
    val bigThreshold = 32L * 1024L * 1024L
    val smartDefaultScopes = PadatScopes(
        apkLib = true,
        artOat = true,
        externalData = bd.externalData.logicalBytes >= bigThreshold,
        obb = bd.obb.logicalBytes >= bigThreshold,
        internalFiles = bd.internalFiles.logicalBytes >= bigThreshold,
    )
    // Prefer saved policy, falling back to real on-disk F2FS compression metadata
    // so an already-compressed app ALWAYS opens with its last-used algorithm/level/cluster.
    val applied = app.resolvedAppliedEntry()
    val initial = applied ?: AppPolicyEntry(
        preset = if (autoTier1) PadatPreset.FAST else PadatPreset.OFF,
        algo = PadatAlgo.LZ4,
        level = 0,
        clusterKb = 16,
        scopes = smartDefaultScopes,
    )

    var preset by remember(app.packageName, applied) { mutableStateOf(initial.preset) }
    var algo by remember(app.packageName, applied) { mutableStateOf(initial.effectiveAlgo()) }
    var level by remember(app.packageName, applied) {
        mutableIntStateOf(
            if (initial.effectiveAlgo().hasLevel) initial.effectiveLevel() else PadatAlgo.ZSTD.defaultLevel,
        )
    }
    var clusterKb by remember(app.packageName, applied) { mutableIntStateOf(initial.effectiveClusterKb()) }
    var apkLib by remember(app.packageName, applied) { mutableStateOf(initial.scopes.apkLib) }
    var artOat by remember(app.packageName, applied) { mutableStateOf(initial.scopes.artOat) }
    var extData by remember(app.packageName, applied) { mutableStateOf(initial.scopes.externalData) }
    var obb by remember(app.packageName, applied) { mutableStateOf(initial.scopes.obb) }
    var internalFiles by remember(app.packageName, applied) { mutableStateOf(initial.scopes.internalFiles) }

    fun currentEntry(): AppPolicyEntry = AppPolicyEntry(
        preset = preset,
        algo = algo,
        level = algo.clampLevel(level),
        clusterKb = clusterKb,
        scopes = PadatScopes(apkLib, artOat, extData, obb, internalFiles),
    ).normalized()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // If user taps the currently working app, surface live progress & controls right here too
        if (isThisAppActive) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    val isDecomp = (liveProgress?.activeAction ?: activeAction) == "decompress"
                    val modeLabel = formatPresetBadge(app.resolvedAppliedEntry(), autoTier1)
                    Text(
                        text = if (isDecomp) {
                            stringResource(R.string.padat_job_decompressing)
                        } else {
                            stringResource(R.string.padat_job_compressing, modeLabel)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    InlineProgressPanel(
                        status = liveProgress,
                        paused = paused,
                        onTogglePause = onTogglePause,
                        onCancel = onCancel,
                    )
                }
            }
        }

        // Live stats & Smart Probe projection box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                val physStr = Formatter.formatShortFileSize(ctx, app.physicalBytes)
                val logStr = Formatter.formatShortFileSize(ctx, app.logicalBytes)
                val savedStr = Formatter.formatShortFileSize(ctx, app.savedBytes)
                val pct = formatPctLabel(app.savedBytes, app.logicalBytes)?.let { " ($it)" } ?: ""
                Text(
                    text = stringResource(R.string.padat_sheet_stats, physStr, logStr, "$savedStr$pct"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                val activeAlgoLabel = scanResult?.detectedProfile?.shortLabel() ?: app.activeProfileLabel()
                if (activeAlgoLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Compress,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (app.detectedProfile != null || scanResult?.detectedProfile != null) {
                                        R.string.padat_active_algo_ondisk
                                    } else {
                                        R.string.padat_active_algo_policy
                                    },
                                    activeAlgoLabel,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                if (scanning) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (scanResult != null) {
                    val estStr = Formatter.formatShortFileSize(ctx, scanResult.estExtraSavingsBytes)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.padat_sheet_probe_result,
                            estStr,
                            scanResult.candidateFiles,
                            scanResult.skippedIncompressible,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.padat_preset_heading), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            PadatPreset.entries.forEach { p ->
                val title = when (p) {
                    PadatPreset.OFF -> "Off"
                    PadatPreset.FAST -> "Fast LZ4"
                    PadatPreset.BALANCED -> "Balanced"
                    PadatPreset.MAX -> "Max ZSTD"
                    PadatPreset.CUSTOM -> "Custom"
                }
                FilterChip(
                    selected = preset == p,
                    enabled = !isThisAppActive,
                    onClick = {
                        preset = p
                        if (p != PadatPreset.CUSTOM && p != PadatPreset.OFF) {
                            val nextAlgo = PadatAlgo.fromId(p.algo)
                            algo = nextAlgo
                            level = if (nextAlgo.hasLevel) nextAlgo.clampLevel(p.level) else nextAlgo.defaultLevel
                            clusterKb = p.clusterKb
                        }
                    },
                    label = { Text(title) },
                )
            }
        }

        if (preset == PadatPreset.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.padat_algo_heading), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadatAlgo.entries.forEach { a ->
                    FilterChip(
                        selected = algo == a,
                        enabled = !isThisAppActive,
                        onClick = {
                            algo = a
                            if (a.hasLevel && (level < a.minLevel || level > a.maxLevel)) {
                                level = a.defaultLevel
                            }
                        },
                        label = { Text(a.label) },
                    )
                }
            }
            if (algo.hasLevel) {
                val clamped = algo.clampLevel(level)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.padat_level_label, clamped),
                    style = MaterialTheme.typography.labelLarge,
                )
                Slider(
                    value = clamped.toFloat(),
                    enabled = !isThisAppActive,
                    onValueChange = { level = algo.clampLevel(it.roundToInt()) },
                    valueRange = algo.minLevel.toFloat()..algo.maxLevel.toFloat(),
                    steps = (algo.maxLevel - algo.minLevel - 1).coerceAtLeast(0),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.padat_cluster_heading), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(16, 32, 64, 128).forEach { kb ->
                    FilterChip(
                        selected = clusterKb == kb,
                        enabled = !isThisAppActive,
                        onClick = { clusterKb = kb },
                        label = { Text("${kb}K") },
                    )
                }
            }
        } else if (preset != PadatPreset.OFF) {
            Text(
                text = "${algo.label}${if (algo.hasLevel) " level $level" else ""} · ${clusterKb} KB cluster",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Complete 6-bucket storage breakdown (exact size of every part)
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.padat_scopes_heading), style = MaterialTheme.typography.labelLarge)
        Text(
            text = stringResource(R.string.padat_scopes_safety_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val scopesEditable = !isThisAppActive && preset != PadatPreset.OFF
        ScopeCheckRow(
            label = stringResource(R.string.padat_scope_apk),
            sizeText = formatBucketSize(ctx, bd.apkLib),
            checked = apkLib,
            enabled = scopesEditable,
        ) { apkLib = it }
        ScopeCheckRow(
            label = stringResource(R.string.padat_scope_art),
            sizeText = formatBucketSize(ctx, bd.artOat),
            checked = artOat,
            enabled = scopesEditable,
        ) { artOat = it }
        ScopeCheckRow(
            label = stringResource(R.string.padat_scope_ext),
            sizeText = formatBucketSize(ctx, bd.externalData),
            checked = extData,
            enabled = scopesEditable,
        ) { extData = it }
        ScopeCheckRow(
            label = stringResource(R.string.padat_scope_obb),
            sizeText = formatBucketSize(ctx, bd.obb),
            checked = obb,
            enabled = scopesEditable,
        ) { obb = it }
        ScopeCheckRow(
            label = stringResource(R.string.padat_scope_internal),
            sizeText = formatBucketSize(ctx, bd.internalFiles),
            checked = internalFiles,
            enabled = scopesEditable,
        ) { internalFiles = it }

        // Read-only excluded bucket (cache, databases, shared_prefs, user_de)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.padat_scope_excluded),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatBucketSize(ctx, bd.cacheOther),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!isThisAppActive) {
            val selected = currentEntry()
            val appliedAlgoChanged = applied != null && preset != PadatPreset.OFF && (
                selected.algo != applied.algo ||
                    selected.level != applied.level ||
                    selected.clusterKb != applied.clusterKb
                )
            val appliedScopesChanged = applied != null && preset != PadatPreset.OFF &&
                selected.scopes != applied.scopes

            if (busy && !activeOtherLabel.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.padat_job_busy_other, activeOtherLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            } else if (appliedAlgoChanged && applied != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(
                            R.string.padat_algo_switching,
                            applied.profileLabel(),
                            selected.profileLabel(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            } else if (appliedScopesChanged) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.padat_scopes_updated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (preset != PadatPreset.OFF) {
                    FilledTonalButton(
                        onClick = { onScan(selected) },
                        enabled = !scanning && !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.padat_action_scan))
                    }
                }
                val primaryLabelRes = when {
                    preset == PadatPreset.OFF -> R.string.padat_action_decompress
                    applied == null -> R.string.padat_action_compress
                    appliedAlgoChanged || appliedScopesChanged -> R.string.padat_action_recompress
                    else -> R.string.padat_action_sync
                }
                val primaryEnabled = !busy && when (preset) {
                    PadatPreset.OFF -> app.compressedFiles > 0L || applied != null
                    else -> selected.scopes.toMask() != 0
                }
                Button(
                    onClick = { onApply(selected) },
                    enabled = primaryEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(primaryLabelRes))
                }
            }
            if (app.compressedFiles > 0 && preset != PadatPreset.OFF) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onDecompress(selected) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.padat_action_decompress_all))
                }
            }
        }
    }
}

@Composable
private fun ScopeCheckRow(
    label: String,
    sizeText: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 3.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = sizeText,
            style = MaterialTheme.typography.labelMedium,
            color = if (sizeText == "0 B") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}
