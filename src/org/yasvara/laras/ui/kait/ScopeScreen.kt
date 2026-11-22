/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.kait

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

/**
 * Which apps one Kait module is loaded into (Scope / Aplikasi yang dikaitkan).
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

    Page(ui.module?.label ?: module, onBack) {
        item("summary") {
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = scheme.surfaceBright,
            ) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AppIcon(module, size = 48.dp)
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
                                    KaitPillBadge(
                                        text = apiLabel,
                                        containerColor = scheme.secondaryContainer,
                                        contentColor = scheme.onSecondaryContainer,
                                    )
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
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        if (ui.module?.canOpenUi == true) {
                            OutlinedButton(
                                onClick = { viewModel.repository.openModuleUi(module) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.kait_btn_open_ui))
                            }
                        }
                    }
                }
            }
        }
        item("search") {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.targets_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (ui.query.isNotEmpty()) {
                            IconButton({ viewModel.setQuery("") }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.targets_clear))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ui.filter == ScopeListFilter.ALL,
                        onClick = { viewModel.setFilter(ScopeListFilter.ALL) },
                        label = { Text(stringResource(R.string.kait_scope_filter_all)) },
                    )
                    FilterChip(
                        selected = ui.filter == ScopeListFilter.SELECTED,
                        onClick = { viewModel.setFilter(ScopeListFilter.SELECTED) },
                        label = { Text(stringResource(R.string.kait_scope_filter_selected, ui.selected.size)) },
                    )
                    if (recommendedCount > 0 || ui.filter == ScopeListFilter.RECOMMENDED) {
                        FilterChip(
                            selected = ui.filter == ScopeListFilter.RECOMMENDED,
                            onClick = { viewModel.setFilter(ScopeListFilter.RECOMMENDED) },
                            label = { Text(stringResource(R.string.kait_scope_filter_recommended, recommendedCount)) },
                        )
                    }
                    FilterChip(
                        selected = ui.filter == ScopeListFilter.RUNNING,
                        onClick = { viewModel.setFilter(ScopeListFilter.RUNNING) },
                        label = { Text(stringResource(R.string.kait_scope_filter_running, runningCount)) },
                    )
                    FilterChip(
                        selected = ui.showSystem,
                        onClick = { viewModel.setShowSystem(!ui.showSystem) },
                        label = { Text(stringResource(R.string.targets_show_system)) },
                        leadingIcon = if (ui.showSystem) {
                            { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
                        } else null,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = viewModel::addRecommended,
                        modifier = Modifier.weight(1f),
                        enabled = viewModel.recommendedInstalled().any { it !in ui.selected },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            stringResource(R.string.kait_scope_clear),
                            maxLines = 1,
                        )
                    }
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
                Text(
                    stringResource(R.string.targets_empty),
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
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
    Surface(shape = shape, color = c.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app.packageName)
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
                        KaitPillBadge(
                            text = stringResource(R.string.kait_recommended_badge),
                            containerColor = c.primaryContainer,
                            contentColor = c.onPrimaryContainer,
                        )
                    }
                    if (running) {
                        KaitPillBadge(
                            text = stringResource(R.string.kait_running_badge),
                            containerColor = c.tertiaryContainer,
                            contentColor = c.onTertiaryContainer,
                        )
                    }
                    if (app.isSystem) {
                        KaitPillBadge(
                            text = stringResource(R.string.targets_system_badge),
                            containerColor = c.secondaryContainer,
                            contentColor = c.onSecondaryContainer,
                        )
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
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}
