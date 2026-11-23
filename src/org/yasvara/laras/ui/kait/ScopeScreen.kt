/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.kait

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.kait.ScopeApp
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.HeroBackdropKind
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasHeroBackdrop
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.larasStickyControls
import org.yasvara.laras.ui.screens.Page

/**
 * Which apps one Kait module is loaded into (Scope / Aplikasi yang dikaitkan).
 * Unified on [LarasSearchBar], [LarasFilterRow], [larasStickyControls], [LarasBadge], and [CheckSwitch].
 */
@Composable
fun ScopeScreen(module: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: ScopeViewModel = viewModel(key = "scope:$module") { ScopeViewModel(app, module) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val visible = ui.visible
    val recommended = ui.recommended
    val scheme = MaterialTheme.colorScheme

    val recommendedCount = ui.apps.count { it.packageName in recommended }
    val runningCount = ui.apps.count { it.packageName in ui.runningPackages }

    val filterOptions = remember(recommendedCount, ui.filter) {
        buildList {
            add(ScopeListFilter.ALL)
            add(ScopeListFilter.SELECTED)
            if (recommendedCount > 0 || ui.filter == ScopeListFilter.RECOMMENDED) add(ScopeListFilter.RECOMMENDED)
            add(ScopeListFilter.RUNNING)
        }
    }

    Page(ui.module?.label ?: module, onBack) {
        item("summary") {
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = LarasShapes.Hero,
                color = LarasSurfaces.card,
            ) {
                Box(Modifier.fillMaxWidth()) {
                    LarasHeroBackdrop(
                        kind = HeroBackdropKind.KAIT_MODULES,
                        modifier = Modifier.matchParentSize(),
                        color = scheme.primary,
                        alpha = 0.16f,
                    )
                    Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppIcon(module, size = 42.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                stringResource(R.string.kait_scope_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            val apiLabel = ui.module?.apiLabel.orEmpty()
                            if (apiLabel.isNotBlank()) {
                                LarasBadge(text = apiLabel)
                            }
                        }
                        Text(
                            when {
                                !ui.available -> stringResource(R.string.kait_unavailable)
                                ui.module?.enabled == false -> stringResource(R.string.kait_scope_module_off)
                                else -> pluralStringResource(
                                    R.plurals.kait_scope_count,
                                    ui.selected.size,
                                    ui.selected.size,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (ui.module?.canOpenUi == true) {
                        OutlinedButton(
                            onClick = { viewModel.repository.openModuleUi(module) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.kait_btn_open_ui))
                        }
                    }
                }
                }
            }
        }
        larasStickyControls(key = "scope_sticky_controls") {
            LarasSearchBar(
                query = ui.query,
                onQueryChange = viewModel::setQuery,
                placeholder = stringResource(R.string.targets_search),
            )
            LarasFilterRow(
                options = filterOptions,
                selected = ui.filter,
                label = { f ->
                    when (f) {
                        ScopeListFilter.ALL -> stringResource(R.string.kait_scope_filter_all)
                        ScopeListFilter.SELECTED -> stringResource(R.string.kait_scope_filter_selected, ui.selected.size)
                        ScopeListFilter.RECOMMENDED -> stringResource(R.string.kait_scope_filter_recommended, recommendedCount)
                        ScopeListFilter.RUNNING -> stringResource(R.string.kait_scope_filter_running, runningCount)
                    }
                },
                leadingIcon = { f ->
                    when (f) {
                        ScopeListFilter.ALL -> Icons.Rounded.Apps
                        ScopeListFilter.SELECTED -> Icons.Rounded.CheckCircle
                        ScopeListFilter.RECOMMENDED -> Icons.Rounded.AutoAwesome
                        ScopeListFilter.RUNNING -> Icons.Rounded.PlayArrow
                    }
                },
                onSelect = viewModel::setFilter,
                trailing = {
                    FilterChip(
                        selected = ui.showSystem,
                        onClick = { viewModel.setShowSystem(!ui.showSystem) },
                        label = { Text(stringResource(R.string.targets_show_system)) },
                        leadingIcon = { Icon(Icons.Rounded.Android, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = viewModel::addRecommended,
                    modifier = Modifier.weight(1f),
                    enabled = viewModel.recommendedInstalled().any { it !in ui.selected },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.kait_scope_recommended),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        viewModel.restartScopedApps { count ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.kait_restarted_targets, count),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = ui.selected.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.kait_btn_restart_targets),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = viewModel::clear,
                    enabled = ui.selected.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        stringResource(R.string.kait_scope_clear),
                        maxLines = 1,
                    )
                }
            }
        }
        if (ui.loading) {
            item("loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (visible.isEmpty()) {
            item("empty") {
                LarasEmptyState(title = stringResource(R.string.targets_empty))
            }
        }
        items(visible.size, key = { visible[it].packageName }) { i ->
            val appItem = visible[i]
            ScopeRow(
                app = appItem,
                checked = appItem.packageName in ui.selected,
                recommended = appItem.packageName in recommended,
                running = appItem.packageName in ui.runningPackages,
                enabled = ui.available,
                shape = groupedShape(i, visible.size),
            ) { viewModel.toggle(appItem.packageName, it) }
        }
    }
}

@Composable
private fun ScopeRow(
    app: ScopeApp,
    checked: Boolean,
    recommended: Boolean,
    running: Boolean,
    enabled: Boolean,
    shape: Shape,
    onChange: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(shape = shape, color = LarasSurfaces.card) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(app.packageName, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (recommended) {
                        LarasBadge(text = stringResource(R.string.kait_recommended_badge), tone = Tone.GOOD)
                    }
                    if (running) {
                        LarasBadge(text = stringResource(R.string.kait_running_badge), tone = Tone.WARN)
                    }
                    if (app.isSystem) {
                        LarasBadge(text = stringResource(R.string.targets_system_badge), tone = Tone.NEUTRAL)
                    }
                }
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = c.onSurfaceVariant,
                )
            }
            CheckSwitch(checked = checked, enabled = enabled)
        }
    }
}
