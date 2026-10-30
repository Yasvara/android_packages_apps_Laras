/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.xposed

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.xposed.ScopeApp
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

/** Which apps one module is loaded into. */
@Composable
fun ScopeScreen(module: String, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as Application
    val viewModel: ScopeViewModel = viewModel(key = "scope:$module") { ScopeViewModel(app, module) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val visible = ui.visible
    val recommended = ui.module?.recommended.orEmpty().toSet()

    Page(ui.module?.label ?: module, onBack) {
        item("summary") {
            Surface(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceBright,
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AppIcon(module, size = 48.dp)
                    Column {
                        Text(stringResource(R.string.xposed_scope_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                !ui.available -> stringResource(R.string.xposed_unavailable)
                                ui.module?.enabled == false -> stringResource(R.string.xposed_scope_module_off)
                                else -> pluralStringResource(R.plurals.xposed_scope_count, ui.selected.size, ui.selected.size)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                FilterChip(
                    selected = ui.showSystem,
                    onClick = { viewModel.setShowSystem(!ui.showSystem) },
                    label = { Text(stringResource(R.string.targets_show_system)) },
                    leadingIcon = if (ui.showSystem) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }
                    } else null,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        viewModel::addRecommended,
                        Modifier.weight(1f),
                        enabled = viewModel.recommendedInstalled().any { it !in ui.selected },
                    ) {
                        Text(stringResource(R.string.xposed_scope_recommended))
                    }
                    OutlinedButton(viewModel::clear, Modifier.weight(1f), enabled = ui.selected.isNotEmpty()) {
                        Text(stringResource(R.string.xposed_scope_clear))
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
                    stringResource(R.string.targets_empty), Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(visible.size, key = { visible[it].packageName }) { i ->
            val app = visible[i]
            ScopeRow(
                app, app.packageName in ui.selected, app.packageName in recommended,
                enabled = ui.available, shape = groupedShape(i, visible.size),
            ) { viewModel.toggle(app.packageName, it) }
        }
    }
}

@Composable
private fun ScopeRow(
    app: ScopeApp,
    checked: Boolean,
    recommended: Boolean,
    enabled: Boolean,
    shape: Shape,
    onChange: (Boolean) -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (recommended) Badge(stringResource(R.string.xposed_recommended_badge), primary = true)
                    if (app.isSystem) Badge(stringResource(R.string.targets_system_badge), primary = false)
                }
                Text(
                    app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun Badge(text: String, primary: Boolean) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(6.dp), color = if (primary) c.primaryContainer else c.secondaryContainer) {
        Text(
            text, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall,
            color = if (primary) c.onPrimaryContainer else c.onSecondaryContainer,
        )
    }
}
