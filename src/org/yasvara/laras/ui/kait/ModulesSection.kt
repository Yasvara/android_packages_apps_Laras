/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.kait

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.data.kait.InjectedProcess
import org.yasvara.laras.data.kait.KaitModule
import org.yasvara.laras.data.kait.OnlineModule
import org.yasvara.laras.data.kait.OnlineRelease
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasModalSheet
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape

/* -------------------------------------------------------------------------- */
/* Installed Modules Tab (hosted inside AkarScreen)                           */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaitModulesTab(
    ui: KaitUi,
    viewModel: ModulesViewModel,
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ModuleListFilter,
    onFilterChange: (ModuleListFilter) -> Unit,
    onOpenScope: (String) -> Unit,
    onOpenActions: (KaitModule) -> Unit,
    onBrowseRepo: () -> Unit,
) {
    val allCount = ui.modules.size
    val enabledCount = ui.modules.count { it.enabled }
    val disabledCount = allCount - enabledCount
    val updatesCount = ui.upgradableModulesCount

    val filtered = remember(ui.modules, query, filter, ui.onlineCatalog) {
        val q = query.trim()
        ui.modules.filter { m ->
            val matchesQuery = q.isEmpty() ||
                m.label.contains(q, ignoreCase = true) ||
                m.packageName.contains(q, ignoreCase = true) ||
                m.description.contains(q, ignoreCase = true) ||
                m.author.contains(q, ignoreCase = true)
            val matchesFilter = when (filter) {
                ModuleListFilter.ALL -> true
                ModuleListFilter.ENABLED -> m.enabled
                ModuleListFilter.DISABLED -> !m.enabled
                ModuleListFilter.UPDATES -> ui.hasUpdate(m)
            }
            matchesQuery && matchesFilter
        }
    }

    val filterOptions = remember(updatesCount, filter) {
        buildList {
            add(ModuleListFilter.ALL)
            add(ModuleListFilter.ENABLED)
            add(ModuleListFilter.DISABLED)
            if (updatesCount > 0 || filter == ModuleListFilter.UPDATES) add(ModuleListFilter.UPDATES)
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.loading && ui.modules.isNotEmpty(),
        onRefresh = { viewModel.refresh(loadRepo = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = LarasSpacing.ScreenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(visible = showSearch || query.isNotEmpty()) {
                        LarasSearchBar(
                            query = query,
                            onQueryChange = onQueryChange,
                            placeholder = stringResource(R.string.kait_search_modules),
                        )
                    }
                    LarasFilterRow(
                        options = filterOptions,
                        selected = filter,
                        label = {
                            when (it) {
                                ModuleListFilter.ALL -> stringResource(R.string.kait_filter_all, allCount)
                                ModuleListFilter.ENABLED -> stringResource(R.string.kait_filter_enabled, enabledCount)
                                ModuleListFilter.DISABLED -> stringResource(R.string.kait_filter_disabled, disabledCount)
                                ModuleListFilter.UPDATES -> stringResource(R.string.kait_filter_updates, updatesCount)
                            }
                        },
                        onSelect = onFilterChange,
                    )
                }
            }

            when {
                ui.loading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !ui.available -> item(key = "unavailable") {
                    LarasEmptyState(
                        title = stringResource(R.string.kait_unavailable),
                        body = stringResource(R.string.kait_unavailable_body),
                    )
                }
                ui.modules.isEmpty() -> item(key = "empty") {
                    LarasEmptyState(
                        title = stringResource(R.string.kait_empty),
                        body = stringResource(R.string.kait_empty_body),
                        icon = Icons.Rounded.CloudDownload,
                        actionLabel = stringResource(R.string.kait_bento_repo_tap),
                        onAction = onBrowseRepo,
                    )
                }
                filtered.isEmpty() -> item(key = "no_match") {
                    LarasEmptyState(title = stringResource(R.string.akar_no_matching_apps))
                }
                else -> {
                    itemsIndexed(filtered, key = { _, m -> m.packageName }) { _, module ->
                        val runningTargets = module.scope.count { it in ui.injected }
                        val onlineEntry = ui.onlineByPackage[module.packageName]
                        KaitModuleCard(
                            module = module,
                            runningTargetsCount = runningTargets,
                            hasUpdate = ui.hasUpdate(module),
                            latestOnlineVersion = onlineEntry?.latestVersion?.versionName,
                            onToggleEnabled = { viewModel.setEnabled(module.packageName, it) },
                            onOpenScope = { onOpenScope(module.packageName) },
                            onOpenUi = { viewModel.repository.openModuleUi(module.packageName) },
                            onOpenUpdate = { onlineEntry?.let(viewModel::openOnlineModuleDetail) },
                            onMoreActions = { onOpenActions(module) },
                        )
                    }
                    item(key = "restart_note") {
                        Text(
                            stringResource(R.string.kait_restart_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean, zero-redundancy module card:
 * - Header toggles the module or opens overflow actions;
 * - Scope strip is the single canonical tap target for target apps (no duplicate button underneath);
 * - Complementary actions (`Open UI` / `Update`) appear only when applicable.
 */
@Composable
private fun KaitModuleCard(
    module: KaitModule,
    runningTargetsCount: Int,
    hasUpdate: Boolean,
    latestOnlineVersion: String?,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenScope: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenUpdate: () -> Unit,
    onMoreActions: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val loadable = module.problem == null

    Surface(shape = LarasShapes.Card, color = LarasSurfaces.card) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppIcon(module.packageName, size = 44.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = module.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val verText = if (module.versionName.isNotBlank()) "v${module.versionName}" else "v${module.versionCode}"
                        Text(verText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        if (module.apiLabel.isNotBlank()) LarasBadge(module.apiLabel)
                        if (module.hasNativeHook) LarasBadge("NATIVE", tone = Tone.GOOD)
                    }
                    if (module.author.isNotBlank()) {
                        Text(
                            text = module.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onMoreActions) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.kait_action_app_info),
                        tint = scheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier.toggleable(
                        value = module.enabled,
                        enabled = loadable || module.enabled,
                        role = Role.Switch,
                        onValueChange = onToggleEnabled,
                    ),
                ) {
                    CheckSwitch(checked = module.enabled, enabled = loadable || module.enabled)
                }
            }

            if (!loadable) {
                Surface(shape = LarasShapes.Inset, color = scheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = scheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.kait_problem, module.problem!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onErrorContainer,
                        )
                    }
                }
            } else if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Single canonical Scope entry point (no duplicate button underneath).
            Surface(
                onClick = onOpenScope,
                shape = LarasShapes.Inset,
                color = LarasSurfaces.nestedCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (module.scope.isEmpty()) {
                        Icon(
                            Icons.Rounded.Checklist,
                            contentDescription = null,
                            tint = if (module.enabled) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (module.enabled) stringResource(R.string.kait_btn_scope_empty)
                            else stringResource(R.string.kait_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-6).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            module.scope.take(5).forEach { AppIcon(it, size = 24.dp) }
                        }
                        if (module.scope.size > 5) LarasBadge("+${module.scope.size - 5}")
                        Text(
                            text = pluralStringResource(
                                if (runningTargetsCount > 0) R.plurals.kait_scope_running else R.plurals.kait_scope_count,
                                module.scope.size,
                                module.scope.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (runningTargetsCount > 0 && module.enabled) {
                            LarasBadge(stringResource(R.string.kait_running_badge), tone = Tone.GOOD)
                        }
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            if (module.canOpenUi || hasUpdate) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (module.canOpenUi) {
                        FilledTonalButton(onClick = onOpenUi, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.kait_btn_open_ui), maxLines = 1)
                        }
                    }
                    if (hasUpdate) {
                        OutlinedButton(onClick = onOpenUpdate, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (!latestOnlineVersion.isNullOrBlank()) {
                                    "${stringResource(R.string.kait_repo_update_badge)} $latestOnlineVersion"
                                } else {
                                    stringResource(R.string.kait_repo_update_badge)
                                },
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

/* -------------------------------------------------------------------------- */
/* Online Xposed Repository Tab                                               */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaitRepoTab(
    ui: KaitUi,
    viewModel: ModulesViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: RepoListFilter,
    onFilterChange: (RepoListFilter) -> Unit,
    sort: RepoSortOrder,
    onSortChange: (RepoSortOrder) -> Unit,
    onOpenScope: (String) -> Unit,
) {
    val catalog = ui.onlineCatalog
    val installedPkgs = ui.installedVersions.keys
    val installedInRepoCount = remember(catalog.modules, installedPkgs) {
        catalog.modules.count { it.packageName in installedPkgs }
    }
    val updatesInRepoCount = remember(catalog.modules, ui.installedVersions) {
        catalog.modules.count { ui.hasRepoUpdate(it) }
    }

    val filtered = remember(catalog.modules, query, filter, sort, ui.installedVersions) {
        val q = query.trim()
        val list = catalog.modules.filter { m ->
            val matchesQuery = q.isEmpty() ||
                m.title.contains(q, ignoreCase = true) ||
                m.packageName.contains(q, ignoreCase = true) ||
                m.summary.contains(q, ignoreCase = true) ||
                m.authors.contains(q, ignoreCase = true) ||
                m.scope.any { it.contains(q, ignoreCase = true) }
            val matchesFilter = when (filter) {
                RepoListFilter.ALL -> true
                RepoListFilter.INSTALLED -> m.packageName in installedPkgs
                RepoListFilter.UPDATES -> ui.hasRepoUpdate(m)
            }
            matchesQuery && matchesFilter
        }
        when (sort) {
            RepoSortOrder.UPDATED -> list.sortedByDescending { it.updatedAt }
            RepoSortOrder.STARS -> list.sortedByDescending { it.stargazerCount }
            RepoSortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.repoRefreshing,
        onRefresh = { viewModel.refreshOnlineRepo(forceNetwork = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = LarasSpacing.ScreenHorizontal, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "repo_controls") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LarasSearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        placeholder = stringResource(R.string.kait_search_repo),
                    )
                    LarasFilterRow(
                        options = RepoListFilter.entries,
                        selected = filter,
                        label = {
                            when (it) {
                                RepoListFilter.ALL -> stringResource(R.string.kait_filter_all, catalog.modules.size)
                                RepoListFilter.INSTALLED -> stringResource(R.string.kait_filter_installed, installedInRepoCount)
                                RepoListFilter.UPDATES -> stringResource(R.string.kait_filter_updates, updatesInRepoCount)
                            }
                        },
                        onSelect = onFilterChange,
                    )
                    LarasFilterRow(
                        options = RepoSortOrder.entries,
                        selected = sort,
                        label = {
                            stringResource(
                                when (it) {
                                    RepoSortOrder.UPDATED -> R.string.kait_sort_updated
                                    RepoSortOrder.STARS -> R.string.kait_sort_stars
                                    RepoSortOrder.NAME -> R.string.kait_sort_name
                                },
                            )
                        },
                        onSelect = onSortChange,
                    )
                }
            }

            if (catalog.fromCache) {
                item(key = "offline_cached") {
                    Surface(
                        shape = LarasShapes.Inset,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.kait_repo_offline_cached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            when {
                ui.repoRefreshing && catalog.modules.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                catalog.modules.isEmpty() -> item(key = "empty") {
                    LarasEmptyState(
                        title = stringResource(R.string.kait_repo_empty),
                        actionLabel = stringResource(R.string.akar_refresh),
                        onAction = { viewModel.refreshOnlineRepo(forceNetwork = true) },
                    )
                }
                filtered.isEmpty() -> item(key = "no_match") {
                    LarasEmptyState(title = stringResource(R.string.akar_no_matching_apps))
                }
                else -> {
                    itemsIndexed(filtered, key = { _, m -> m.packageName }) { _, online ->
                        val isInstalled = online.packageName in installedPkgs
                        OnlineModuleCard(
                            module = online,
                            isInstalled = isInstalled,
                            hasUpdate = ui.hasRepoUpdate(online),
                            onClick = { viewModel.openOnlineModuleDetail(online) },
                            onOpenScope = if (isInstalled) ({ onOpenScope(online.packageName) }) else null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineModuleCard(
    module: OnlineModule,
    isInstalled: Boolean,
    hasUpdate: Boolean,
    onClick: () -> Unit,
    onOpenScope: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = LarasShapes.Card, color = LarasSurfaces.card) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isInstalled) {
                    AppIcon(module.packageName, size = 42.dp)
                } else {
                    Surface(shape = RoundedCornerShape(14.dp), color = scheme.primaryContainer, modifier = Modifier.size(42.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                module.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            module.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (hasUpdate) {
                            LarasBadge(stringResource(R.string.kait_repo_update_badge), tone = Tone.WARN)
                        } else if (isInstalled) {
                            LarasBadge(stringResource(R.string.kait_repo_installed_badge), tone = Tone.GOOD)
                        }
                    }
                    Text(
                        module.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (module.stargazerCount > 0) {
                    Surface(shape = LarasShapes.Tag, color = LarasSurfaces.nestedCard) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Rounded.Star, null, tint = scheme.primary, modifier = Modifier.size(14.dp))
                            Text("${module.stargazerCount}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (module.summary.isNotBlank()) {
                Text(
                    module.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val meta = buildList {
                    if (module.authors.isNotBlank()) add(module.authors)
                    module.latestVersion?.versionName?.takeIf { it.isNotBlank() }?.let(::add)
                    if (module.updatedAt.isNotBlank()) add(module.updatedAt)
                }.joinToString(" · ")
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                onOpenScope?.let {
                    TextButton(onClick = it, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)) {
                        Icon(Icons.Rounded.Checklist, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.kait_scope_title), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (module.scope.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    module.scope.take(4).forEach { LarasBadge(it, tone = Tone.NEUTRAL) }
                    if (module.scope.size > 4) LarasBadge("+${module.scope.size - 4}", tone = Tone.NEUTRAL)
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Bottom Sheets & Shared Helpers                                             */
/* -------------------------------------------------------------------------- */

@Composable
fun LiveProcessesSheet(
    processes: List<InjectedProcess>,
    onDismiss: () -> Unit,
    onRestart: (String) -> Unit,
) {
    LarasModalSheet(onDismiss = onDismiss, scrollable = false) {
        Text(
            stringResource(R.string.kait_processes_sheet_title, processes.size),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (processes.isEmpty()) {
            Text(
                stringResource(R.string.kait_processes_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap)) {
                itemsIndexed(processes, key = { i, p -> "${p.processName}:${p.pid}:$i" }) { idx, proc ->
                    Surface(shape = groupedShape(idx, processes.size), color = LarasSurfaces.sheetCard) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(proc.packageName, size = 38.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    proc.processName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${proc.packageName} · pid ${proc.pid}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (proc.packageName != "android") {
                                IconButton(onClick = { onRestart(proc.packageName) }) {
                                    Icon(
                                        Icons.Rounded.RestartAlt,
                                        contentDescription = stringResource(R.string.kait_btn_restart_targets),
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
}

@Composable
fun ModuleActionsSheet(
    module: KaitModule,
    hasOnlineEntry: Boolean,
    onDismiss: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenScope: () -> Unit,
    onRestartTargets: () -> Unit,
    onViewInRepo: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    LarasModalSheet(onDismiss = onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(module.packageName, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(module.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${module.packageName} · v${module.versionName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SheetActionItem(
                Icons.Rounded.Checklist,
                if (module.scope.isEmpty()) stringResource(R.string.kait_btn_scope_empty)
                else stringResource(R.string.kait_btn_scope, module.scope.size),
                onClick = onOpenScope,
            )
            if (module.canOpenUi) {
                SheetActionItem(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.kait_btn_open_ui), onClick = onOpenUi)
            }
            if (module.scope.isNotEmpty()) {
                SheetActionItem(Icons.Rounded.RestartAlt, stringResource(R.string.kait_btn_restart_targets), onClick = onRestartTargets)
            }
            if (hasOnlineEntry) {
                SheetActionItem(Icons.Rounded.CloudDownload, stringResource(R.string.kait_action_view_repo), onClick = onViewInRepo)
            }
            SheetActionItem(Icons.Rounded.Info, stringResource(R.string.kait_action_app_info), onClick = onAppInfo)
            SheetActionItem(Icons.Rounded.DeleteOutline, stringResource(R.string.kait_action_uninstall), destructive = true, onClick = onUninstall)
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, shape = LarasShapes.Inset, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnlineModuleDetailSheet(
    module: OnlineModule,
    loading: Boolean,
    installedModule: KaitModule?,
    hasUpdate: Boolean,
    onDismiss: () -> Unit,
    onOpenScope: () -> Unit,
    onOpenModuleUi: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme

    LarasModalSheet(onDismiss = onDismiss, scrollable = false) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (installedModule != null) {
                            AppIcon(module.packageName, size = 50.dp)
                        } else {
                            Surface(shape = LarasShapes.Inset, color = scheme.primaryContainer, modifier = Modifier.size(50.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        module.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(module.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                module.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariant,
                            )
                            if (module.authors.isNotBlank()) {
                                Text(module.authors, style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                            }
                        }
                    }
                    if (module.summary.isNotBlank()) {
                        Text(module.summary, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    if (installedModule != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onOpenScope, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Checklist, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (installedModule.scope.isEmpty()) stringResource(R.string.kait_btn_scope_empty)
                                    else stringResource(R.string.kait_btn_scope, installedModule.scope.size),
                                )
                            }
                            if (installedModule.canOpenUi) {
                                OutlinedButton(onClick = onOpenModuleUi) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kait_btn_open_ui))
                                }
                            }
                        }
                    }
                    if (module.repoUrl.isNotBlank() || module.homepageUrl.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (module.repoUrl.isNotBlank()) {
                                OutlinedButton(onClick = { openUrl(context, module.repoUrl) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Code, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kait_repo_open_github))
                                }
                            }
                            if (module.homepageUrl.isNotBlank() && module.homepageUrl != module.repoUrl) {
                                OutlinedButton(onClick = { openUrl(context, module.homepageUrl) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Public, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kait_repo_open_homepage))
                                }
                            }
                        }
                    }
                }
            }

            if (module.scope.isNotEmpty()) {
                item(key = "recommended_scope") {
                    Surface(shape = LarasShapes.Inset, color = LarasSurfaces.sheetCard, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.kait_repo_recommended_scope),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                module.scope.forEach { pkg ->
                                    Surface(shape = LarasShapes.Tag, color = LarasSurfaces.card) {
                                        Row(
                                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            AppIcon(pkg, size = 18.dp)
                                            Text(pkg, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (module.releases.isNotEmpty()) {
                item(key = "releases_header") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.kait_repo_releases), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (hasUpdate) LarasBadge(stringResource(R.string.kait_repo_update_badge), tone = Tone.WARN)
                    }
                }
                itemsIndexed(module.releases.take(5), key = { i, r -> "${r.tagName}:$i" }) { _, rel ->
                    ReleaseDownloadCard(rel) { openUrl(context, it) }
                }
            } else if (loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            if (module.readmeText.isNotBlank()) {
                item(key = "readme") {
                    Surface(shape = LarasShapes.Inset, color = LarasSurfaces.sheetCard, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.kait_repo_readme), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(module.readmeText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseDownloadCard(release: OnlineRelease, onDownload: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = LarasShapes.Inset, color = LarasSurfaces.sheetCard, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(release.name.ifBlank { release.tagName }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (release.publishedAt.isNotBlank()) {
                    Text(release.publishedAt, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            if (release.descriptionText.isNotBlank()) {
                Text(release.descriptionText, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
            }
            release.apkAssets.forEach { asset ->
                FilledTonalButton(onClick = { onDownload(asset.downloadUrl) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.kait_repo_download_apk, asset.name), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Kept as source-compatible alias for `ScopeScreen.kt`. */
@Composable
internal fun KaitPillBadge(text: String, containerColor: Color, contentColor: Color) {
    LarasBadge(text = text, containerColor = containerColor, contentColor = contentColor)
}

@Composable
fun CrashBypassBanner(pkg: String, onReset: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = Modifier.fillMaxWidth(), shape = LarasShapes.Inset, color = c.errorContainer) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.kait_crash_bypassed_title), style = MaterialTheme.typography.titleSmall, color = c.onErrorContainer)
                Text(stringResource(R.string.kait_crash_bypassed_body, pkg), style = MaterialTheme.typography.bodySmall, color = c.onErrorContainer)
            }
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.kait_crash_bypassed_reset), color = c.onErrorContainer)
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
