/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.visibility

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.server.yasvara.visibility.IYasvaraAppVisibility as Rules
import org.yasvara.laras.R
import org.yasvara.laras.data.visibility.ObserverConfig
import org.yasvara.laras.data.visibility.TabirTemplate
import org.yasvara.laras.data.visibility.UserApp
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasTabBar
import org.yasvara.laras.ui.components.SectionCard
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private enum class TabirTab { RULES, TEMPLATES, SIMULATE }
private enum class Picking { HIDDEN, CALLERS, PER_APP_OBSERVER, VIEW_AS }

@Composable
fun AppVisibilityScreen(onBack: () -> Unit, viewModel: AppVisibilityViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(TabirTab.RULES) }
    var picking by rememberSaveable { mutableStateOf<Picking?>(null) }
    var editingObserver by rememberSaveable { mutableStateOf<String?>(null) }
    var editingTemplateName by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingTemplate by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

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

    Page(
        title = stringResource(R.string.visibility_title),
        onBack = onBack,
        actions = {
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
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tabir_action_export)) },
                        onClick = {
                            menuOpen = false
                            exportLauncher.launch("yasvara_tabir_backup.json")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.tabir_action_import)) },
                        onClick = {
                            menuOpen = false
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        },
                    )
                }
            }
        },
        topContent = {
            LarasTabBar(
                tabs = TabirTab.entries,
                selected = tab,
                label = { t ->
                    when (t) {
                        TabirTab.RULES -> stringResource(R.string.tabir_tab_rules)
                        TabirTab.TEMPLATES -> "${stringResource(R.string.tabir_tab_templates)} (${ui.templates.size})"
                        TabirTab.SIMULATE -> stringResource(R.string.tabir_tab_simulate)
                    }
                },
                onSelect = { tab = it },
            )
        },
    ) {
        if (!ui.loading && !ui.available) {
            item("unavailable") {
                Text(stringResource(R.string.visibility_unavailable), Modifier.padding(16.dp))
            }
            return@Page
        }

        when (tab) {
            TabirTab.RULES -> rulesContent(
                ui = ui,
                viewModel = viewModel,
                onPick = { picking = it },
                onEditObserver = { editingObserver = it },
            )
            TabirTab.TEMPLATES -> templatesContent(
                ui = ui,
                onNew = { creatingTemplate = true },
                onEdit = { editingTemplateName = it },
            )
            TabirTab.SIMULATE -> simulateContent(
                ui = ui,
                onPickObserver = { picking = Picking.VIEW_AS },
            )
        }
    }

    picking?.let { mode ->
        val selected = when (mode) {
            Picking.HIDDEN -> ui.hidden
            Picking.CALLERS -> ui.callers
            Picking.PER_APP_OBSERVER -> ui.observerConfigs.keys
            Picking.VIEW_AS -> setOfNotNull(ui.viewAs)
        }
        PickerSheet(ui.apps, selected, onDismiss = { picking = null }) { pkg ->
            when (mode) {
                Picking.HIDDEN -> viewModel.setHidden(pkg, pkg !in selected)
                Picking.CALLERS -> viewModel.setCaller(pkg, pkg !in selected)
                Picking.PER_APP_OBSERVER -> {
                    picking = null
                    editingObserver = pkg
                }
                Picking.VIEW_AS -> {
                    viewModel.viewAs(pkg)
                    picking = null
                }
            }
        }
    }

    editingObserver?.let { obsPkg ->
        val existing = ui.observerConfigs[obsPkg]
            ?: ObserverConfig(Rules.MODE_BLACKLIST, emptySet(), emptySet())
        ObserverConfigSheet(
            observerPkg = obsPkg,
            observerLabel = ui.label(obsPkg),
            initial = existing,
            templates = ui.templates,
            candidateApps = (ui.apps + ui.romApps).distinctBy { it.packageName },
            onDismiss = { editingObserver = null },
            onSave = { mode, pkgs, tpls ->
                viewModel.saveObserverConfig(obsPkg, mode, pkgs, tpls)
                editingObserver = null
            },
            onClear = {
                viewModel.clearObserverConfig(obsPkg)
                editingObserver = null
            },
        )
    }

    if (creatingTemplate || editingTemplateName != null) {
        val existing = editingTemplateName?.let { name ->
            ui.templates.firstOrNull { it.name == name }
        }
        TemplateEditorSheet(
            existing = existing,
            candidateApps = (ui.apps + ui.romApps).distinctBy { it.packageName },
            onDismiss = {
                creatingTemplate = false
                editingTemplateName = null
            },
            onSave = { name, mode, pkgs ->
                if (existing != null && existing.name != name) {
                    viewModel.deleteTemplate(existing.name)
                }
                viewModel.saveTemplate(name, mode, pkgs)
                creatingTemplate = false
                editingTemplateName = null
            },
            onDelete = existing?.let { tpl ->
                {
                    viewModel.deleteTemplate(tpl.name)
                    creatingTemplate = false
                    editingTemplateName = null
                }
            },
        )
    }
}

private fun LazyListScope.rulesContent(
    ui: AppVisibilityUi,
    viewModel: AppVisibilityViewModel,
    onPick: (Picking) -> Unit,
    onEditObserver: (String) -> Unit,
) {
    if (ui.hasSuggestion) {
        item("suggestion") {
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.visibility_suggestion_title),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
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

    item("per_app_header") {
        SectionHeader(R.string.tabir_per_app_title, R.string.tabir_per_app_add) {
            onPick(Picking.PER_APP_OBSERVER)
        }
    }

    val perAppEntries = ui.observerConfigs.entries.toList()
    if (perAppEntries.isEmpty()) {
        item("per_app_empty") {
            Text(
                stringResource(R.string.tabir_per_app_empty),
                Modifier.padding(vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        items(perAppEntries.size, key = { "obsrule:" + perAppEntries[it].key }) { i ->
            val (pkg, cfg) = perAppEntries[i]
            val summary = if (cfg.mode == Rules.MODE_WHITELIST) {
                stringResource(
                    R.string.tabir_observer_summary_whitelist,
                    cfg.templates.size,
                    cfg.packages.size,
                )
            } else {
                stringResource(
                    R.string.tabir_observer_summary_blacklist,
                    cfg.templates.size,
                    cfg.packages.size,
                )
            }
            AppRow(
                pkg = pkg,
                label = ui.label(pkg),
                subtitle = summary,
                shape = groupedShape(i, perAppEntries.size),
                onClick = { onEditObserver(pkg) },
            ) {
                IconButton({ viewModel.clearObserverConfig(pkg) }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.visibility_remove))
                }
            }
        }
    }

    item("global_scope") {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tabir_global_title),
            Modifier.padding(vertical = 6.dp),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(
            Modifier.padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
    }

    appList(
        key = "hidden",
        title = R.string.visibility_hidden,
        packages = ui.hidden.sorted(),
        ui = ui,
        onAdd = { onPick(Picking.HIDDEN) },
        onRemove = { viewModel.setHidden(it, false) },
    )
    appList(
        key = "callers",
        title = if (ui.scope == Rules.SCOPE_ALL_USER_APPS) {
            R.string.visibility_exceptions
        } else {
            R.string.visibility_observers
        },
        packages = ui.callers.sorted(),
        ui = ui,
        onAdd = { onPick(Picking.CALLERS) },
        onRemove = { viewModel.setCaller(it, false) },
    )
}

private fun LazyListScope.templatesContent(
    ui: AppVisibilityUi,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
) {
    item("tpl:header") {
        SectionHeader(R.string.tabir_tab_templates, R.string.tabir_templates_new, onNew)
    }
    if (ui.templates.isEmpty()) {
        item("tpl:empty") {
            Text(
                stringResource(R.string.tabir_templates_empty),
                Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    items(ui.templates.size, key = { "tpl:" + ui.templates[it].name }) { i ->
        val tpl = ui.templates[i]
        val summary = if (tpl.mode == Rules.MODE_WHITELIST) {
            stringResource(R.string.tabir_template_summary_whitelist, tpl.packages.size)
        } else {
            stringResource(R.string.tabir_template_summary_blacklist, tpl.packages.size)
        }
        Surface(
            shape = groupedShape(i, ui.templates.size),
            color = MaterialTheme.colorScheme.surfaceBright,
            modifier = Modifier.fillMaxWidth().clickable { onEdit(tpl.name) },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(tpl.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.simulateContent(
    ui: AppVisibilityUi,
    onPickObserver: () -> Unit,
) {
    item("sim:header") {
        SectionHeader(
            R.string.visibility_view_as,
            R.string.visibility_view_as_pick,
            onPickObserver,
        )
    }
    val observer = ui.viewAs
    if (observer == null) {
        item("sim:empty") {
            Text(
                stringResource(R.string.tabir_sim_empty),
                Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val results = ui.viewAsResult.entries.toList()
    val hiddenCount = results.count { it.value }
    val visibleCount = results.size - hiddenCount

    item("sim:summary") {
        SectionCard(
            title = ui.label(observer),
            subtitle = observer,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
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

    items(results.size, key = { "as:" + results[it].key }) { i ->
        val (pkg, hidden) = results[i]
        AppRow(pkg = pkg, label = ui.label(pkg), shape = groupedShape(i, results.size)) {
            Text(
                stringResource(
                    if (hidden) R.string.visibility_state_hidden else R.string.visibility_state_visible,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (hidden) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

private fun LazyListScope.appList(
    key: String,
    title: Int,
    packages: List<String>,
    ui: AppVisibilityUi,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    item("$key:header") { SectionHeader(title, R.string.visibility_add, onAdd) }
    items(packages.size, key = { "$key:" + packages[it] }) { i ->
        val pkg = packages[i]
        AppRow(pkg = pkg, label = ui.label(pkg), shape = groupedShape(i, packages.size)) {
            IconButton({ onRemove(pkg) }) {
                Icon(Icons.Rounded.Close, stringResource(R.string.visibility_remove))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: Int, action: Int, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(title),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onAction) { Text(stringResource(action)) }
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
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            rowMod.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(pkg)
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
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
    val visible = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LarasSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = stringResource(R.string.visibility_search),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(visible, key = { it.packageName }) { app ->
                val on = app.packageName in selected
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onPick(app.packageName) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
    initial: ObserverConfig,
    templates: List<TabirTemplate>,
    candidateApps: List<UserApp>,
    onDismiss: () -> Unit,
    onSave: (Int, Set<String>, Set<String>) -> Unit,
    onClear: () -> Unit,
) {
    var mode by remember { mutableIntStateOf(initial.mode) }
    var selectedTemplates by remember { mutableStateOf(initial.templates) }
    var selectedPackages by remember { mutableStateOf(initial.packages) }
    var query by remember { mutableStateOf("") }

    val matchingTemplates = templates.filter { it.mode == mode }
    val visibleApps = candidateApps.filter {
        it.packageName != observerPkg && (
            query.isBlank() ||
                it.label.contains(query, true) ||
                it.packageName.contains(query, true)
            )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(observerLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        observerPkg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClear) {
                    Text(stringResource(R.string.tabir_mode_global))
                }
                FilledTonalButton(
                    onClick = {
                        val validTplNames = matchingTemplates.map { it.name }.toSet()
                        onSave(mode, selectedPackages, selectedTemplates.intersect(validTplNames))
                    },
                ) {
                    Text(stringResource(R.string.tabir_save))
                }
            }

            Row(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == Rules.MODE_BLACKLIST,
                    onClick = { mode = Rules.MODE_BLACKLIST },
                    label = { Text(stringResource(R.string.tabir_mode_blacklist)) },
                )
                FilterChip(
                    selected = mode == Rules.MODE_WHITELIST,
                    onClick = { mode = Rules.MODE_WHITELIST },
                    label = { Text(stringResource(R.string.tabir_mode_whitelist)) },
                )
            }

            if (matchingTemplates.isNotEmpty()) {
                Text(
                    stringResource(R.string.tabir_assign_templates),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
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
                            label = { Text(tpl.name) },
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
                modifier = Modifier.padding(top = 6.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.visibility_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }

        LazyColumn {
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                    if (checked) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorSheet(
    existing: TabirTemplate?,
    candidateApps: List<UserApp>,
    onDismiss: () -> Unit,
    onSave: (String, Int, Set<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var mode by remember { mutableIntStateOf(existing?.mode ?: Rules.MODE_BLACKLIST) }
    var selected by remember { mutableStateOf(existing?.packages ?: emptySet()) }
    var query by remember { mutableStateOf("") }

    val visibleApps = candidateApps.filter {
        query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
                    onClick = { onSave(name.trim(), mode, selected) },
                ) {
                    Text(stringResource(R.string.tabir_save))
                }
            }

            Row(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = mode == Rules.MODE_BLACKLIST,
                    onClick = { mode = Rules.MODE_BLACKLIST },
                    label = { Text(stringResource(R.string.tabir_mode_blacklist)) },
                )
                FilterChip(
                    selected = mode == Rules.MODE_WHITELIST,
                    onClick = { mode = Rules.MODE_WHITELIST },
                    label = { Text(stringResource(R.string.tabir_mode_whitelist)) },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.visibility_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }

        LazyColumn {
            items(visibleApps, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(
                    Modifier.fillMaxWidth()
                        .clickable {
                            selected = if (checked) {
                                selected - app.packageName
                            } else {
                                selected + app.packageName
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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
                    if (checked) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
