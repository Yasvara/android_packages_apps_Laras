/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.visibility

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.server.yasvara.visibility.IYasvaraAppVisibility as Rules
import org.yasvara.laras.R
import org.yasvara.laras.data.visibility.ObserverConfig
import org.yasvara.laras.data.visibility.TabirTemplate
import org.yasvara.laras.data.visibility.UserApp
import org.yasvara.laras.data.visibility.VisibilitySuggestions
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasCollapsibleCard
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasIconCircle
import org.yasvara.laras.ui.components.LarasRadioRow
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.LarasStatBar
import org.yasvara.laras.ui.components.LarasTabBar
import org.yasvara.laras.ui.components.SectionCard
import org.yasvara.laras.ui.components.StatFigure
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.larasStickyControls
import org.yasvara.laras.ui.screens.Page

private enum class TabirTab { HOME, APPS, TEMPLATES, SIMULATE }
private enum class AppListFilter { ALL, PROTECTED, DETECTORS, UNCONFIGURED }
private enum class SimFilter { ALL, HIDDEN, VISIBLE }
private enum class Picking { HIDDEN, CALLERS, VIEW_AS }
private enum class TemplateEditorTab { TARGETS, APPLIED }

@Composable
fun AppVisibilityScreen(onBack: () -> Unit, viewModel: AppVisibilityViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(TabirTab.HOME) }
    var appQuery by rememberSaveable { mutableStateOf("") }
    var appFilter by rememberSaveable { mutableStateOf(AppListFilter.ALL) }
    var bulkMode by rememberSaveable { mutableStateOf(false) }
    var bulkSelected by remember { mutableStateOf(emptySet<String>()) }
    var showBulkSheet by rememberSaveable { mutableStateOf(false) }
    var globalExpanded by rememberSaveable { mutableStateOf(false) }
    var simFilter by rememberSaveable { mutableStateOf(SimFilter.ALL) }
    var showGuide by rememberSaveable { mutableStateOf(false) }

    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    var editingObserver by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTemplateName by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingTemplateMode by rememberSaveable { mutableStateOf<Int?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportJson { json ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, R.string.tabir_export_done, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
            }.getOrNull()
            if (!content.isNullOrBlank()) {
                viewModel.importJson(content) { ok ->
                    Toast.makeText(
                        context,
                        if (ok) R.string.tabir_import_ok else R.string.tabir_import_fail,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    val candidateApps = remember(ui.apps, ui.romApps) {
        (ui.apps + ui.romApps).distinctBy { it.packageName }
    }

    Page(
        title = stringResource(R.string.visibility_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = { showGuide = true }) {
                Icon(
                    Icons.AutoMirrored.Rounded.HelpOutline,
                    stringResource(R.string.tabir_action_guide),
                )
            }
            IconButton(
                onClick = {
                    viewModel.forceStopHidden { count ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.tabir_stopped_toast, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Icon(Icons.Rounded.StopCircle, stringResource(R.string.tabir_action_force_stop))
            }
            IconButton(onClick = { viewModel.refresh(reloadApps = true) }) {
                Icon(Icons.Rounded.Refresh, null)
            }
        },
        topContent = {
            LarasTabBar(
                tabs = TabirTab.entries,
                selected = tab,
                label = { t ->
                    when (t) {
                        TabirTab.HOME -> stringResource(R.string.tabir_tab_home)
                        TabirTab.APPS -> stringResource(R.string.tabir_tab_apps)
                        TabirTab.TEMPLATES -> stringResource(R.string.tabir_tab_templates)
                        TabirTab.SIMULATE -> stringResource(R.string.tabir_tab_simulate)
                    }
                },
                onSelect = {
                    tab = it
                    if (it != TabirTab.APPS && bulkMode) {
                        bulkMode = false
                        bulkSelected = emptySet()
                    }
                },
            )
        },
    ) {
        if (!ui.loading && !ui.available) {
            item("unavailable") {
                LarasEmptyState(
                    icon = Icons.Rounded.Shield,
                    title = stringResource(R.string.visibility_unavailable),
                    body = stringResource(R.string.tabir_surfaces_subtitle),
                )
            }
            return@Page
        }

        when (tab) {
            TabirTab.HOME -> homeContent(
                ui = ui,
                viewModel = viewModel,
                globalExpanded = globalExpanded,
                onToggleGlobal = { globalExpanded = !globalExpanded },
                onNavigate = { tab = it },
                onPick = { picking = it },
                onBackup = { exportLauncher.launch("yasvara_tabir_backup.json") },
                onRestore = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onForceStop = {
                    viewModel.forceStopHidden { count ->
                        Toast.makeText(
                            context,
                            context.getString(R.string.tabir_stopped_toast, count),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )

            TabirTab.APPS -> appsContent(
                ui = ui,
                query = appQuery,
                onQueryChange = { appQuery = it },
                filter = appFilter,
                onFilterChange = { appFilter = it },
                bulkMode = bulkMode,
                bulkSelected = bulkSelected,
                onToggleBulkMode = {
                    bulkMode = !bulkMode
                    if (!bulkMode) bulkSelected = emptySet()
                },
                onToggleSelectApp = { pkg ->
                    bulkSelected = if (pkg in bulkSelected) bulkSelected - pkg else bulkSelected + pkg
                },
                onOpenBulkSheet = { showBulkSheet = true },
                onOpenApp = { editingObserver = it },
            )

            TabirTab.TEMPLATES -> templatesContent(
                ui = ui,
                onNewWhitelist = { creatingTemplateMode = Rules.MODE_WHITELIST },
                onNewBlacklist = { creatingTemplateMode = Rules.MODE_BLACKLIST },
                onEdit = { editingTemplateName = it },
            )

            TabirTab.SIMULATE -> simulateContent(
                ui = ui,
                filter = simFilter,
                onFilterChange = { simFilter = it },
                onPickObserver = { picking = Picking.VIEW_AS },
                onConfigureObserver = { editingObserver = it },
            )
        }
    }

    if (showGuide) {
        AlertDialog(
            onDismissRequest = { showGuide = false },
            icon = { Icon(Icons.Rounded.Shield, null) },
            title = { Text(stringResource(R.string.tabir_guide_title)) },
            text = { Text(stringResource(R.string.tabir_guide_body)) },
            confirmButton = {
                TextButton(onClick = { showGuide = false }) {
                    Text(stringResource(R.string.tabir_ok))
                }
            },
        )
    }

    picking?.let { mode ->
        val selected = when (mode) {
            Picking.HIDDEN -> ui.hidden
            Picking.CALLERS -> ui.callers
            Picking.VIEW_AS -> setOfNotNull(ui.viewAs)
        }
        PickerSheet(ui.apps, selected, onDismiss = { picking = null }) { pkg ->
            when (mode) {
                Picking.HIDDEN -> viewModel.setHidden(pkg, pkg !in selected)
                Picking.CALLERS -> viewModel.setCaller(pkg, pkg !in selected)
                Picking.VIEW_AS -> {
                    viewModel.viewAs(pkg)
                    picking = null
                }
            }
        }
    }

    editingObserver?.let { obsPkg ->
        val existing = ui.observerConfigs[obsPkg]
        ObserverConfigSheet(
            observerPkg = obsPkg,
            observerLabel = ui.label(obsPkg),
            initial = existing,
            templates = ui.templates,
            candidateApps = candidateApps,
            onDismiss = { editingObserver = null },
            onSave = { enabled, mode, pkgs, tpls ->
                if (!enabled) {
                    viewModel.clearObserverConfig(obsPkg)
                } else {
                    viewModel.saveObserverConfig(obsPkg, mode, pkgs, tpls)
                }
                editingObserver = null
            },
            onStopAndOpen = {
                viewModel.forceStopAndLaunch(obsPkg) { launched ->
                    if (!launched) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.tabir_stop_only_toast, ui.label(obsPkg)),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onResetRiskCache = {
                viewModel.resetObserverRiskCache(obsPkg) { ok ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (ok) R.string.tabir_reset_risk_ok_toast else R.string.tabir_reset_risk_fail_toast,
                            ui.label(obsPkg),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
        )
    }

    if (showBulkSheet && bulkSelected.isNotEmpty()) {
        BulkConfigSheet(
            selectedApps = bulkSelected,
            templates = ui.templates,
            candidateApps = candidateApps,
            onDismiss = { showBulkSheet = false },
            onApply = { enabled, mode, pkgs, tpls ->
                val count = bulkSelected.size
                viewModel.applyBulkObserverConfig(
                    observers = bulkSelected,
                    mode = if (enabled) mode else Rules.MODE_GLOBAL,
                    packages = if (enabled) pkgs else emptySet(),
                    templates = if (enabled) tpls else emptySet(),
                )
                showBulkSheet = false
                bulkMode = false
                bulkSelected = emptySet()
                Toast.makeText(
                    context,
                    context.getString(R.string.tabir_bulk_done, count),
                    Toast.LENGTH_SHORT,
                ).show()
            },
        )
    }

    if (creatingTemplateMode != null || editingTemplateName != null) {
        val existing = editingTemplateName?.let { name ->
            ui.templates.firstOrNull { it.name == name }
        }
        val initialMode = existing?.mode ?: creatingTemplateMode ?: Rules.MODE_BLACKLIST
        val initialApplied = existing?.let { ui.appliedObserversForTemplate(it.name) } ?: emptySet()
        TemplateEditorSheet(
            existing = existing,
            initialMode = initialMode,
            initialApplied = initialApplied,
            ui = ui,
            candidateApps = candidateApps,
            onDismiss = {
                creatingTemplateMode = null
                editingTemplateName = null
            },
            onSave = { newName, mode, targetPkgs, appliedObservers ->
                viewModel.saveTemplateWithApplied(
                    oldName = existing?.name,
                    newName = newName,
                    mode = mode,
                    targetPackages = targetPkgs,
                    appliedObservers = appliedObservers,
                )
                creatingTemplateMode = null
                editingTemplateName = null
            },
            onDelete = existing?.let { tpl ->
                {
                    viewModel.deleteTemplate(tpl.name)
                    creatingTemplateMode = null
                    editingTemplateName = null
                }
            },
        )
    }
}

private fun LazyListScope.homeContent(
    ui: AppVisibilityUi,
    viewModel: AppVisibilityViewModel,
    globalExpanded: Boolean,
    onToggleGlobal: () -> Unit,
    onNavigate: (TabirTab) -> Unit,
    onPick: (Picking) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onForceStop: () -> Unit,
) {
    item("hero") {
        val active = ui.isShieldActive
        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            shape = LarasShapes.Hero,
            color = if (active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                LarasSurfaces.card
            },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LarasIconCircle(
                    icon = if (active) Icons.Rounded.VerifiedUser else Icons.Rounded.Shield,
                    containerColor = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            if (active) R.string.tabir_hero_active_title else R.string.tabir_hero_idle_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        stringResource(
                            if (active) R.string.tabir_hero_active_desc else R.string.tabir_hero_idle_desc,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }

    item("stats") {
        LarasStatBar(
            figures = listOf(
                StatFigure(
                    value = ui.protectedAppCount.toString(),
                    label = stringResource(R.string.tabir_stat_protected),
                    onClick = { onNavigate(TabirTab.APPS) },
                ),
                StatFigure(
                    value = ui.templates.size.toString(),
                    label = stringResource(R.string.tabir_stat_templates),
                    onClick = { onNavigate(TabirTab.TEMPLATES) },
                ),
                StatFigure(
                    value = ui.totalHiddenTargetCount.toString(),
                    label = stringResource(R.string.tabir_stat_hidden),
                    onClick = { onNavigate(TabirTab.SIMULATE) },
                ),
            ),
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }

    if (ui.hasSuggestion) {
        item("suggestion") {
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = LarasShapes.Card,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.visibility_suggestion_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    FilledTonalButton(viewModel::applySuggestion) {
                        Text(stringResource(R.string.visibility_suggestion_apply))
                    }
                }
            }
        }
    }

    item("surfaces") {
        SectionCard(
            title = stringResource(R.string.tabir_surfaces_title),
            subtitle = stringResource(R.string.tabir_surfaces_subtitle),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            SwitchRow(
                title = stringResource(R.string.visibility_hide_xposed),
                checked = ui.hideXposed,
                onChange = viewModel::setHideXposed,
                summary = stringResource(R.string.tabir_hide_xposed_summary, ui.modules.size),
            )
            SwitchRow(
                title = stringResource(R.string.tabir_hide_rom),
                checked = ui.hideRomPackages,
                onChange = viewModel::setHideRomPackages,
                summary = stringResource(R.string.tabir_hide_rom_summary, ui.detectedRom.size),
            )
            SwitchRow(
                title = stringResource(R.string.tabir_hide_root),
                checked = ui.hideRootTooling,
                onChange = viewModel::setHideRootTooling,
                summary = stringResource(R.string.tabir_hide_root_summary, ui.detectedRoot.size),
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.tabir_dev_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.tabir_dev_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ui.devOptionsHideMode == Rules.DEV_HIDE_OFF,
                        onClick = { viewModel.setDevOptionsHideMode(Rules.DEV_HIDE_OFF) },
                        label = { Text(stringResource(R.string.tabir_dev_off)) },
                    )
                    FilterChip(
                        selected = ui.devOptionsHideMode == Rules.DEV_HIDE_OBSERVERS,
                        onClick = { viewModel.setDevOptionsHideMode(Rules.DEV_HIDE_OBSERVERS) },
                        label = { Text(stringResource(R.string.tabir_dev_observers)) },
                    )
                    FilterChip(
                        selected = ui.devOptionsHideMode == Rules.DEV_HIDE_ALL_NON_SYSTEM,
                        onClick = {
                            viewModel.setDevOptionsHideMode(Rules.DEV_HIDE_ALL_NON_SYSTEM)
                        },
                        label = { Text(stringResource(R.string.tabir_dev_all)) },
                    )
                }
            }
        }
    }

    item("global_collapsible") {
        LarasCollapsibleCard(
            title = stringResource(R.string.tabir_global_title),
            summary = stringResource(R.string.tabir_global_summary),
            leadingIcon = Icons.Rounded.Public,
            expanded = globalExpanded,
            onToggleExpand = onToggleGlobal,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = ui.scope == Rules.SCOPE_SELECTED,
                    onClick = { viewModel.setScope(Rules.SCOPE_SELECTED) },
                    label = { Text(stringResource(R.string.visibility_scope_selected)) },
                )
                FilterChip(
                    selected = ui.scope == Rules.SCOPE_ALL_USER_APPS,
                    onClick = { viewModel.setScope(Rules.SCOPE_ALL_USER_APPS) },
                    label = { Text(stringResource(R.string.visibility_scope_all)) },
                )
            }
            CompactPackageGroup(
                title = stringResource(R.string.visibility_hidden),
                packages = ui.hidden.sorted(),
                ui = ui,
                onAdd = { onPick(Picking.HIDDEN) },
                onRemove = { viewModel.setHidden(it, false) },
            )
            HorizontalDivider(Modifier.padding(vertical = 2.dp))
            CompactPackageGroup(
                title = stringResource(
                    if (ui.scope == Rules.SCOPE_ALL_USER_APPS) {
                        R.string.visibility_exceptions
                    } else {
                        R.string.visibility_observers
                    },
                ),
                packages = ui.callers.sorted(),
                ui = ui,
                onAdd = { onPick(Picking.CALLERS) },
                onRemove = { viewModel.setCaller(it, false) },
            )
        }
    }

    item("quick_actions") {
        LarasSectionHeader(stringResource(R.string.tabir_quick_actions))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onBackup,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.CloudUpload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.tabir_action_backup), maxLines = 1)
            }
            FilledTonalButton(
                onClick = onRestore,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.tabir_action_restore), maxLines = 1)
            }
        }
        OutlinedButton(
            onClick = onForceStop,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.StopCircle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tabir_action_force_stop), maxLines = 1)
        }
    }
}

@Composable
private fun CompactPackageGroup(
    title: String,
    packages: List<String>,
    ui: AppVisibilityUi,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$title (${packages.size})",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            TextButton(onClick = onAdd) {
                Text(stringResource(R.string.visibility_add))
            }
        }
        packages.forEach { pkg ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(pkg)
                Column(Modifier.weight(1f)) {
                    Text(ui.label(pkg), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(
                        pkg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onRemove(pkg) }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.visibility_remove))
                }
            }
        }
    }
}

private fun LazyListScope.appsContent(
    ui: AppVisibilityUi,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: AppListFilter,
    onFilterChange: (AppListFilter) -> Unit,
    bulkMode: Boolean,
    bulkSelected: Set<String>,
    onToggleBulkMode: () -> Unit,
    onToggleSelectApp: (String) -> Unit,
    onOpenBulkSheet: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val sortedApps = ui.apps.sortedWith(
        compareByDescending<UserApp> { it.packageName in ui.observerConfigs }
            .thenByDescending { ui.isProtectedApp(it.packageName) }
            .thenByDescending { VisibilitySuggestions.isDetector(it.packageName) }
            .thenBy { it.label.lowercase() },
    )

    val filtered = sortedApps.filter { app ->
        val matchesQuery = query.isBlank() ||
            app.label.contains(query, ignoreCase = true) ||
            app.packageName.contains(query, ignoreCase = true)
        if (!matchesQuery) return@filter false
        when (filter) {
            AppListFilter.ALL -> true
            AppListFilter.PROTECTED -> ui.isProtectedApp(app.packageName)
            AppListFilter.DETECTORS -> VisibilitySuggestions.isDetector(app.packageName)
            AppListFilter.UNCONFIGURED -> app.packageName !in ui.observerConfigs
        }
    }

    larasStickyControls("apps_controls") {
        LarasSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.visibility_search),
        )
        LarasFilterRow(
            options = AppListFilter.entries,
            selected = filter,
            label = { f ->
                when (f) {
                    AppListFilter.ALL -> stringResource(R.string.tabir_filter_all)
                    AppListFilter.PROTECTED -> stringResource(R.string.tabir_filter_protected)
                    AppListFilter.DETECTORS -> stringResource(R.string.tabir_filter_detectors)
                    AppListFilter.UNCONFIGURED -> stringResource(R.string.tabir_filter_unconfigured)
                }
            },
            count = { f ->
                when (f) {
                    AppListFilter.ALL -> ui.apps.size
                    AppListFilter.PROTECTED -> ui.apps.count { ui.isProtectedApp(it.packageName) }
                    AppListFilter.DETECTORS -> ui.apps.count { VisibilitySuggestions.isDetector(it.packageName) }
                    AppListFilter.UNCONFIGURED -> ui.apps.count { it.packageName !in ui.observerConfigs }
                }
            },
            onSelect = onFilterChange,
            trailing = {
                FilterChip(
                    selected = bulkMode,
                    onClick = onToggleBulkMode,
                    leadingIcon = { Icon(Icons.Rounded.Checklist, null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.tabir_bulk_mode)) },
                )
            },
        )
        AnimatedVisibility(visible = bulkMode && bulkSelected.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = LarasShapes.Card,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.tabir_bulk_apply, bulkSelected.size),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    FilledTonalButton(onClick = onOpenBulkSheet) {
                        Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.tabir_per_app_add))
                    }
                }
            }
        }
    }

    if (filtered.isEmpty()) {
        item("apps_empty") {
            LarasEmptyState(
                icon = Icons.Rounded.Shield,
                title = stringResource(R.string.tabir_per_app_empty),
                body = "",
            )
        }
        return
    }

    items(filtered.size, key = { "app:" + filtered[it].packageName }) { i ->
        val app = filtered[i]
        val cfg = ui.observerConfigs[app.packageName]
        val globalProtected = cfg == null && ui.isProtectedApp(app.packageName)
        val isDetector = VisibilitySuggestions.isDetector(app.packageName)

        val subtitle = when {
            cfg != null && cfg.mode == Rules.MODE_WHITELIST -> stringResource(
                R.string.tabir_observer_summary_whitelist,
                cfg.templates.size,
                cfg.packages.size,
            )
            cfg != null -> stringResource(
                R.string.tabir_observer_summary_blacklist,
                cfg.templates.size,
                cfg.packages.size,
            )
            globalProtected -> stringResource(R.string.tabir_observer_summary_global_active)
            else -> app.packageName
        }

        AppRow(
            pkg = app.packageName,
            label = app.label,
            subtitle = subtitle,
            shape = groupedShape(i, filtered.size),
            onClick = {
                if (bulkMode) onToggleSelectApp(app.packageName) else onOpenApp(app.packageName)
            },
        ) {
            if (bulkMode) {
                Checkbox(
                    checked = app.packageName in bulkSelected,
                    onCheckedChange = { onToggleSelectApp(app.packageName) },
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isDetector) {
                        LarasBadge(
                            text = stringResource(R.string.tabir_badge_detector),
                            tone = Tone.WARN,
                        )
                    }
                    when {
                        cfg != null && cfg.mode == Rules.MODE_WHITELIST -> LarasBadge(
                            text = stringResource(R.string.tabir_mode_whitelist),
                            tone = Tone.GOOD,
                            pill = true,
                        )
                        cfg != null -> LarasBadge(
                            text = stringResource(R.string.tabir_mode_blacklist),
                            tone = Tone.NEUTRAL,
                            pill = true,
                        )
                        globalProtected -> LarasBadge(
                            text = stringResource(R.string.tabir_mode_global),
                            tone = Tone.GOOD,
                            pill = true,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.templatesContent(
    ui: AppVisibilityUi,
    onNewWhitelist: () -> Unit,
    onNewBlacklist: () -> Unit,
    onEdit: (String) -> Unit,
) {
    item("tpl:actions") {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = onNewWhitelist,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tabir_mode_whitelist), maxLines = 1)
            }
            OutlinedButton(
                onClick = onNewBlacklist,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.tabir_mode_blacklist), maxLines = 1)
            }
        }
    }

    if (ui.templates.isEmpty()) {
        item("tpl:empty") {
            LarasEmptyState(
                icon = Icons.Rounded.Layers,
                title = stringResource(R.string.tabir_tab_templates),
                body = stringResource(R.string.tabir_templates_empty),
            )
        }
        return
    }

    items(ui.templates.size, key = { "tpl:" + ui.templates[it].name }) { i ->
        val tpl = ui.templates[i]
        val appliedCount = ui.appliedObserversForTemplate(tpl.name).size
        val summary = if (tpl.mode == Rules.MODE_WHITELIST) {
            stringResource(R.string.tabir_template_summary_whitelist, tpl.packages.size, appliedCount)
        } else {
            stringResource(R.string.tabir_template_summary_blacklist, tpl.packages.size, appliedCount)
        }
        Surface(
            shape = groupedShape(i, ui.templates.size),
            color = LarasSurfaces.card,
            modifier = Modifier.fillMaxWidth().clickable { onEdit(tpl.name) },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LarasIconCircle(
                    icon = if (tpl.mode == Rules.MODE_WHITELIST) Icons.Rounded.VerifiedUser else Icons.Rounded.Layers,
                    containerColor = if (tpl.mode == Rules.MODE_WHITELIST) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (tpl.mode == Rules.MODE_WHITELIST) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(tpl.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LarasBadge(
                    text = stringResource(
                        if (tpl.mode == Rules.MODE_WHITELIST) R.string.tabir_mode_whitelist else R.string.tabir_mode_blacklist,
                    ),
                    tone = if (tpl.mode == Rules.MODE_WHITELIST) Tone.GOOD else Tone.NEUTRAL,
                    pill = true,
                )
            }
        }
    }
}

private fun LazyListScope.simulateContent(
    ui: AppVisibilityUi,
    filter: SimFilter,
    onFilterChange: (SimFilter) -> Unit,
    onPickObserver: () -> Unit,
    onConfigureObserver: (String) -> Unit,
) {
    item("sim:header") {
        LarasSectionHeader(
            title = stringResource(R.string.visibility_view_as),
            actionLabel = stringResource(R.string.visibility_view_as_pick),
            onAction = onPickObserver,
        )
    }
    val observer = ui.viewAs
    if (observer == null) {
        item("sim:empty") {
            LarasEmptyState(
                icon = Icons.Rounded.Visibility,
                title = stringResource(R.string.visibility_view_as),
                body = stringResource(R.string.tabir_sim_empty),
                actionLabel = stringResource(R.string.visibility_view_as_pick),
                onAction = onPickObserver,
            )
        }
        return
    }

    val allResults = ui.viewAsResult.entries.toList()
    val hiddenCount = allResults.count { it.value }
    val visibleCount = allResults.size - hiddenCount
    val filtered = when (filter) {
        SimFilter.ALL -> allResults
        SimFilter.HIDDEN -> allResults.filter { it.value }
        SimFilter.VISIBLE -> allResults.filter { !it.value }
    }

    item("sim:summary") {
        SectionCard(
            title = ui.label(observer),
            subtitle = observer,
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LarasBadge(
                    text = when (ui.viewAsMode) {
                        Rules.MODE_WHITELIST -> stringResource(R.string.tabir_mode_whitelist)
                        Rules.MODE_BLACKLIST -> stringResource(R.string.tabir_mode_blacklist)
                        else -> stringResource(R.string.tabir_mode_global)
                    },
                    tone = if (ui.viewAsMode == Rules.MODE_WHITELIST) Tone.GOOD else Tone.NEUTRAL,
                    pill = true,
                )
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = { onConfigureObserver(observer) }) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tabir_sim_configure))
                }
            }
            StatusLine(
                icon = if (ui.viewAsDevHidden) Icons.Rounded.Shield else Icons.Rounded.Visibility,
                tone = if (ui.viewAsDevHidden) Tone.GOOD else Tone.WARN,
                text = stringResource(
                    if (ui.viewAsDevHidden) {
                        R.string.tabir_sim_dev_hidden
                    } else {
                        R.string.tabir_sim_dev_visible
                    },
                ),
                detail = stringResource(R.string.tabir_sim_counts, hiddenCount, visibleCount),
            )
        }
    }

    item("sim:filters") {
        LarasFilterRow(
            options = SimFilter.entries,
            selected = filter,
            label = { f ->
                when (f) {
                    SimFilter.ALL -> stringResource(R.string.tabir_sim_filter_all)
                    SimFilter.HIDDEN -> stringResource(R.string.tabir_sim_filter_hidden)
                    SimFilter.VISIBLE -> stringResource(R.string.tabir_sim_filter_visible)
                }
            },
            count = { f ->
                when (f) {
                    SimFilter.ALL -> allResults.size
                    SimFilter.HIDDEN -> hiddenCount
                    SimFilter.VISIBLE -> visibleCount
                }
            },
            onSelect = onFilterChange,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }

    items(filtered.size, key = { "as:" + filtered[it].key }) { i ->
        val (pkg, hidden) = filtered[i]
        AppRow(pkg = pkg, label = ui.label(pkg), shape = groupedShape(i, filtered.size)) {
            LarasBadge(
                text = stringResource(
                    if (hidden) R.string.visibility_state_hidden else R.string.visibility_state_visible,
                ),
                tone = if (hidden) Tone.GOOD else Tone.BAD,
                pill = true,
            )
        }
    }
}

@Composable
private fun AppRow(
    pkg: String,
    label: String,
    shape: Shape,
    subtitle: String = pkg,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val rowMod = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Surface(shape = shape, color = LarasSurfaces.card) {
        Row(
            rowMod.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(pkg)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    apps: List<UserApp>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, selected, query) {
        apps.filter {
            query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
        }.sortedWith(compareByDescending<UserApp> { it.packageName in selected }.thenBy { it.label.lowercase() })
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LarasSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.visibility_search),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(visible, key = { it.packageName }) { app ->
                val on = app.packageName in selected
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onPick(app.packageName) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppIcon(app.packageName)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (on) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObserverConfigSheet(
    observerPkg: String,
    observerLabel: String,
    initial: ObserverConfig?,
    templates: List<TabirTemplate>,
    candidateApps: List<UserApp>,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Set<String>, Set<String>) -> Unit,
    onStopAndOpen: () -> Unit,
    onResetRiskCache: () -> Unit,
) {
    var enabled by remember { mutableStateOf(initial != null) }
    var mode by remember { mutableIntStateOf(initial?.mode ?: Rules.MODE_WHITELIST) }
    var selectedTemplates by remember { mutableStateOf(initial?.templates ?: emptySet()) }
    var selectedPackages by remember { mutableStateOf(initial?.packages ?: emptySet()) }
    var query by remember { mutableStateOf("") }

    val matchingTemplates = remember(templates, mode) { templates.filter { it.mode == mode } }
    val visibleApps = remember(candidateApps, observerPkg, selectedPackages, query) {
        candidateApps.filter {
            it.packageName != observerPkg && (
                query.isBlank() ||
                    it.label.contains(query, true) ||
                    it.packageName.contains(query, true)
                )
        }.sortedWith(
            compareByDescending<UserApp> { it.packageName in selectedPackages }
                .thenBy { it.label.lowercase() },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(observerPkg)
                Column(Modifier.weight(1f)) {
                    Text(observerLabel, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        observerPkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        val validTplNames = matchingTemplates.mapTo(HashSet()) { it.name }
                        onSave(enabled, mode, selectedPackages, selectedTemplates.intersect(validTplNames))
                    },
                ) {
                    Text(stringResource(R.string.tabir_save))
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = onResetRiskCache, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tabir_reset_risk_cache), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = onStopAndOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.tabir_stop_and_open), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp))
                }
            }
            Text(
                text = stringResource(R.string.tabir_reset_risk_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LarasSwitchRow(
                title = stringResource(R.string.tabir_enable_per_app),
                summary = stringResource(R.string.tabir_enable_per_app_summary),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )

            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        LarasRadioRow(
                            title = stringResource(R.string.tabir_mode_whitelist),
                            summary = stringResource(R.string.tabir_mode_whitelist_desc),
                            selected = mode == Rules.MODE_WHITELIST,
                            onClick = { mode = Rules.MODE_WHITELIST },
                            shape = groupedShape(0, 2),
                        )
                        LarasRadioRow(
                            title = stringResource(R.string.tabir_mode_blacklist),
                            summary = stringResource(R.string.tabir_mode_blacklist_desc),
                            selected = mode == Rules.MODE_BLACKLIST,
                            onClick = { mode = Rules.MODE_BLACKLIST },
                            shape = groupedShape(1, 2),
                        )
                    }

                    if (matchingTemplates.isNotEmpty()) {
                        Text(
                            stringResource(R.string.tabir_assign_templates),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            matchingTemplates.forEach { tpl ->
                                val active = tpl.name in selectedTemplates
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        selectedTemplates = if (active) {
                                            selectedTemplates - tpl.name
                                        } else {
                                            selectedTemplates + tpl.name
                                        }
                                    },
                                    label = { Text("${tpl.name} (${tpl.packages.size})") },
                                )
                            }
                        }
                    }

                    Text(
                        stringResource(
                            if (mode == Rules.MODE_WHITELIST) {
                                R.string.tabir_custom_whitelist_apps
                            } else {
                                R.string.tabir_custom_blacklist_apps
                            },
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    LarasSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.visibility_search),
                    )
                }
            }
        }

        if (enabled) {
            LazyColumn(
                Modifier.fillMaxWidth().height(260.dp).navigationBarsPadding().padding(top = 6.dp),
            ) {
                items(visibleApps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedPackages
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                selectedPackages = if (checked) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkConfigSheet(
    selectedApps: Set<String>,
    templates: List<TabirTemplate>,
    candidateApps: List<UserApp>,
    onDismiss: () -> Unit,
    onApply: (Boolean, Int, Set<String>, Set<String>) -> Unit,
) {
    var enabled by remember { mutableStateOf(true) }
    var mode by remember { mutableIntStateOf(Rules.MODE_WHITELIST) }
    var selectedTemplates by remember { mutableStateOf(emptySet<String>()) }
    var selectedPackages by remember { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }

    val matchingTemplates = remember(templates, mode) { templates.filter { it.mode == mode } }
    val visibleApps = remember(candidateApps, selectedPackages, query) {
        candidateApps.filter {
            query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
        }.sortedWith(
            compareByDescending<UserApp> { it.packageName in selectedPackages }
                .thenBy { it.label.lowercase() },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tabir_bulk_title, selectedApps.size),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalButton(
                    onClick = {
                        val valid = matchingTemplates.mapTo(HashSet()) { it.name }
                        onApply(enabled, mode, selectedPackages, selectedTemplates.intersect(valid))
                    },
                ) {
                    Text(stringResource(R.string.tabir_save))
                }
            }

            LarasSwitchRow(
                title = stringResource(R.string.tabir_enable_per_app),
                summary = stringResource(R.string.tabir_enable_per_app_summary),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )

            AnimatedVisibility(visible = enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == Rules.MODE_WHITELIST,
                            onClick = { mode = Rules.MODE_WHITELIST },
                            label = { Text(stringResource(R.string.tabir_mode_whitelist)) },
                        )
                        FilterChip(
                            selected = mode == Rules.MODE_BLACKLIST,
                            onClick = { mode = Rules.MODE_BLACKLIST },
                            label = { Text(stringResource(R.string.tabir_mode_blacklist)) },
                        )
                    }
                    if (matchingTemplates.isNotEmpty()) {
                        Text(stringResource(R.string.tabir_assign_templates), style = MaterialTheme.typography.labelLarge)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            matchingTemplates.forEach { tpl ->
                                val active = tpl.name in selectedTemplates
                                FilterChip(
                                    selected = active,
                                    onClick = {
                                        selectedTemplates = if (active) {
                                            selectedTemplates - tpl.name
                                        } else {
                                            selectedTemplates + tpl.name
                                        }
                                    },
                                    label = { Text("${tpl.name} (${tpl.packages.size})") },
                                )
                            }
                        }
                    }
                    LarasSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.visibility_search),
                    )
                }
            }
        }
        if (enabled) {
            LazyColumn(Modifier.fillMaxWidth().height(240.dp).navigationBarsPadding().padding(top = 6.dp)) {
                items(visibleApps, key = { it.packageName }) { app ->
                    val checked = app.packageName in selectedPackages
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                selectedPackages = if (checked) {
                                    selectedPackages - app.packageName
                                } else {
                                    selectedPackages + app.packageName
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName)
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorSheet(
    existing: TabirTemplate?,
    initialMode: Int,
    initialApplied: Set<String>,
    ui: AppVisibilityUi,
    candidateApps: List<UserApp>,
    onDismiss: () -> Unit,
    onSave: (String, Int, Set<String>, Set<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var mode by remember { mutableIntStateOf(initialMode) }
    var selectedTargets by remember { mutableStateOf(existing?.packages ?: emptySet()) }
    var selectedApplied by remember { mutableStateOf(initialApplied) }
    var editorTab by remember { mutableStateOf(TemplateEditorTab.TARGETS) }
    var query by remember { mutableStateOf("") }

    val activeList = if (editorTab == TemplateEditorTab.TARGETS) candidateApps else ui.apps
    val activeSelection = if (editorTab == TemplateEditorTab.TARGETS) selectedTargets else selectedApplied
    val visibleApps = remember(activeList, activeSelection, query) {
        activeList.filter {
            query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
        }.sortedWith(
            compareByDescending<UserApp> { it.packageName in activeSelection }
                .thenBy { it.label.lowercase() },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tabir_template_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    IconButton(onDelete) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.tabir_delete))
                    }
                }
                FilledTonalButton(
                    enabled = name.trim().isNotEmpty(),
                    onClick = { onSave(name.trim(), mode, selectedTargets, selectedApplied) },
                ) {
                    Text(stringResource(R.string.tabir_save))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == Rules.MODE_WHITELIST,
                    onClick = { mode = Rules.MODE_WHITELIST },
                    label = { Text(stringResource(R.string.tabir_mode_whitelist)) },
                )
                FilterChip(
                    selected = mode == Rules.MODE_BLACKLIST,
                    onClick = { mode = Rules.MODE_BLACKLIST },
                    label = { Text(stringResource(R.string.tabir_mode_blacklist)) },
                )
            }

            LarasTabBar(
                tabs = TemplateEditorTab.entries,
                selected = editorTab,
                label = { t ->
                    when (t) {
                        TemplateEditorTab.TARGETS -> stringResource(
                            R.string.tabir_template_tab_targets,
                            selectedTargets.size,
                        )
                        TemplateEditorTab.APPLIED -> stringResource(
                            R.string.tabir_template_tab_applied,
                            selectedApplied.size,
                        )
                    }
                },
                onSelect = { editorTab = it },
            )

            if (editorTab == TemplateEditorTab.TARGETS && mode == Rules.MODE_BLACKLIST) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (ui.detectedRoot.isNotEmpty()) {
                        FilterChip(
                            selected = ui.detectedRoot.all { it in selectedTargets },
                            onClick = { selectedTargets = selectedTargets + ui.detectedRoot },
                            label = { Text(stringResource(R.string.tabir_template_quick_root)) },
                        )
                    }
                    if (ui.modules.isNotEmpty()) {
                        FilterChip(
                            selected = ui.modules.all { it in selectedTargets },
                            onClick = { selectedTargets = selectedTargets + ui.modules },
                            label = { Text(stringResource(R.string.tabir_template_quick_xposed)) },
                        )
                    }
                    if (ui.detectedRom.isNotEmpty()) {
                        FilterChip(
                            selected = ui.detectedRom.all { it in selectedTargets },
                            onClick = { selectedTargets = selectedTargets + ui.detectedRom },
                            label = { Text(stringResource(R.string.tabir_template_quick_rom)) },
                        )
                    }
                }
            }

            LarasSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.visibility_search),
            )
        }

        LazyColumn(Modifier.fillMaxWidth().height(280.dp).navigationBarsPadding().padding(top = 6.dp)) {
            items(visibleApps, key = { it.packageName }) { app ->
                val checked = app.packageName in activeSelection
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            if (editorTab == TemplateEditorTab.TARGETS) {
                                selectedTargets = if (checked) {
                                    selectedTargets - app.packageName
                                } else {
                                    selectedTargets + app.packageName
                                }
                            } else {
                                selectedApplied = if (checked) {
                                    selectedApplied - app.packageName
                                } else {
                                    selectedApplied + app.packageName
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(app.packageName)
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
    }
}
