/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

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
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.integrity.TargetApp
import org.yasvara.laras.data.integrity.TargetsRepository
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

@Composable
fun TargetAppsScreen(onBack: () -> Unit, viewModel: TargetAppsViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val visible = ui.visible

    LaunchedEffect(Unit) { viewModel.refreshRungs() }

    Page(stringResource(R.string.targets_title), onBack) {
        item("summary") {
            Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceBright) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.Shield, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.targets_manage), style = MaterialTheme.typography.titleMedium)
                        Text(pluralStringResource(R.plurals.targets_count, ui.selected.size, ui.selected.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Button({ viewModel.setAllVisible(true) }, Modifier.weight(1f)) {
                        Text(stringResource(R.string.targets_select_all))
                    }
                    OutlinedButton(viewModel::autoSelect, Modifier.weight(1f)) {
                        Text(stringResource(R.string.targets_auto_select))
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
                Text(stringResource(R.string.targets_empty), Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(visible.size, key = { visible[it].packageName }) { i ->
            val app = visible[i]
            TargetRow(app, app.packageName in ui.selected, ui.rungs[app.packageName],
                groupedShape(i, visible.size)) {
                viewModel.toggle(app.packageName, it)
            }
        }
        item("advanced") { AdvancedCard(ui, viewModel) }
    }
}

@Composable
private fun TargetRow(app: TargetApp, checked: Boolean, rung: org.yasvara.laras.data.integrity.RungReport?,
        shape: androidx.compose.ui.graphics.Shape, onChange: (Boolean) -> Unit) {
    val required = app.packageName in TargetsRepository.REQUIRED
    val protected = checked || required
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(protected, enabled = !required, role = Role.Switch, onValueChange = onChange)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app.packageName)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false))
                    if (app.isSystem) Badge(stringResource(R.string.targets_system_badge))
                }
                Text(
                    outcomeLine(app.packageName, protected, rung),
                    style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    color = if (rung != null && protected && rung.rung == "failed")
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = protected, onCheckedChange = null, enabled = !required)
        }
    }
}

@Composable
private fun outcomeLine(pkg: String, protected: Boolean,
        rung: org.yasvara.laras.data.integrity.RungReport?): String {
    if (!protected) return pkg
    if (rung == null) return stringResource(R.string.rung_waiting)
    val what = when (rung.rung) {
        "passthrough" -> stringResource(R.string.rung_passthrough)
        "patched" -> stringResource(R.string.rung_patched)
        "synthesized" -> stringResource(R.string.rung_synthesized)
        "original" -> stringResource(R.string.rung_original)
        "failed" -> stringResource(R.string.rung_failed,
            rung.reason.ifEmpty { stringResource(R.string.rung_failed_unknown) })
        else -> rung.rung
    }
    val whenText = rungAt(rung.at)
    return if (rung.rung == "failed" || whenText.isEmpty()) what
    else stringResource(R.string.rung_ago, what, whenText)
}

@Composable
private fun rungAt(at: Long): String {
    if (at <= 0L) return ""
    val mins = (System.currentTimeMillis() - at) / 60_000L
    return when {
        mins < 1 -> stringResource(R.string.rung_just_now)
        mins < 60 -> stringResource(R.string.rung_minutes, mins.toInt())
        else -> stringResource(R.string.rung_hours, (mins / 60).toInt())
    }
}

@Composable
private fun AdvancedCard(ui: TargetAppsUi, viewModel: TargetAppsViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var pkgInput by rememberSaveable { mutableStateOf("") }
    var dateInput by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth()
                .toggleable(open, role = Role.Switch) { open = it },
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.adv_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.adv_summary), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = open, onCheckedChange = null)
            }
            if (!open) return@Column
            Text(stringResource(R.string.adv_force_label), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ForceChip(stringResource(R.string.adv_force_auto), "", ui.force, viewModel::setForce)
                ForceChip(stringResource(R.string.adv_force_patch), "patch", ui.force, viewModel::setForce)
                ForceChip(stringResource(R.string.adv_force_passthrough), "passthrough", ui.force,
                    viewModel::setForce)
            }
            Text(stringResource(R.string.adv_patch_label), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.adv_patch_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(pkgInput, { pkgInput = it.trim() },
                    label = { Text(stringResource(R.string.adv_patch_package)) },
                    singleLine = true, modifier = Modifier.weight(2f))
                OutlinedTextField(dateInput, { dateInput = it.trim() },
                    label = { Text(stringResource(R.string.adv_patch_date)) },
                    placeholder = { Text("2026-09-05") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    if (pkgInput.isNotEmpty()) {
                        viewModel.setPatchOverride(pkgInput, dateInput)
                        pkgInput = ""
                        dateInput = ""
                    }
                }) { Text(stringResource(R.string.adv_patch_add)) }
                OutlinedButton(viewModel::reprobeTee) { Text(stringResource(R.string.adv_reprobe)) }
            }
            ui.patchOverrides.forEach { (pkg, date) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$pkg · $date", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall)
                    IconButton({ viewModel.setPatchOverride(pkg, "") }) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.adv_patch_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun ForceChip(label: String, value: String, current: String, onPick: (String) -> Unit) {
    FilterChip(selected = current == value, onClick = { onPick(value) }, label = { Text(label) })
}

@Composable
private fun Badge(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
