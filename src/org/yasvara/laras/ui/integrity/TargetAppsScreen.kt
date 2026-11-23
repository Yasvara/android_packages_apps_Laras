/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.integrity.RungReport
import org.yasvara.laras.data.integrity.TargetApp
import org.yasvara.laras.data.integrity.TargetsRepository
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.ChoiceGroup
import org.yasvara.laras.ui.components.HeroBackdropKind
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasCollapsibleCard
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasHeroBackdrop
import org.yasvara.laras.ui.components.LarasMascotIcon
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.larasStickyControls
import org.yasvara.laras.ui.screens.Page

@Composable
fun TargetAppsScreen(onBack: () -> Unit, viewModel: TargetAppsViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val visible = ui.visible

    LaunchedEffect(Unit) { viewModel.refreshRungs() }

    Page(stringResource(R.string.targets_title), onBack) {
        item("summary") {
            Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = LarasShapes.Hero, color = LarasSurfaces.card) {
                Box(Modifier.fillMaxWidth()) {
                    LarasHeroBackdrop(
                        kind = HeroBackdropKind.INTEGRITY_KEYBOX,
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.primary,
                        alpha = 0.16f,
                    )
                    Row(
                        Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        LarasMascotIcon(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(stringResource(R.string.targets_manage), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                pluralStringResource(R.plurals.targets_count, ui.selected.size, ui.selected.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        larasStickyControls(key = "targets_sticky_controls") {
            LarasSearchBar(
                query = ui.query,
                onQueryChange = viewModel::setQuery,
                placeholder = stringResource(R.string.targets_search),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ui.showSystem,
                    onClick = { viewModel.setShowSystem(!ui.showSystem) },
                    label = { Text(stringResource(R.string.targets_show_system)) },
                    leadingIcon = { Icon(Icons.Rounded.Android, null, Modifier.size(FilterChipDefaults.IconSize)) },
                )
                Button(
                    onClick = { viewModel.setAllVisible(true) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.targets_select_all), maxLines = 1)
                }
                OutlinedButton(
                    onClick = viewModel::autoSelect,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.targets_auto_select), maxLines = 1)
                }
            }
        }
        when {
            ui.loading -> item("loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            visible.isEmpty() -> item("empty") {
                LarasEmptyState(title = stringResource(R.string.targets_empty))
            }
            else -> items(visible.size, key = { visible[it].packageName }) { i ->
                val app = visible[i]
                TargetRow(app, app.packageName in ui.selected, ui.rungs[app.packageName], groupedShape(i, visible.size)) {
                    viewModel.toggle(app.packageName, it)
                }
            }
        }
        item("advanced") { AdvancedCard(ui, viewModel) }
    }
}

@Composable
private fun TargetRow(
    app: TargetApp,
    checked: Boolean,
    rung: RungReport?,
    shape: Shape,
    onChange: (Boolean) -> Unit,
) {
    val required = app.packageName in TargetsRepository.REQUIRED
    val protected = checked || required
    Surface(shape = shape, color = LarasSurfaces.card) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(protected, enabled = !required, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppIcon(app.packageName, size = 38.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (app.isSystem) LarasBadge(stringResource(R.string.targets_system_badge))
                }
                Text(
                    outcomeLine(app.packageName, protected, rung),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = if (rung != null && protected && rung.rung == "failed") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CheckSwitch(checked = protected, enabled = !required)
        }
    }
}

@Composable
private fun outcomeLine(pkg: String, protected: Boolean, rung: RungReport?): String {
    if (!protected) return pkg
    if (rung == null) return stringResource(R.string.rung_waiting)
    val what = when (rung.rung) {
        "passthrough" -> stringResource(R.string.rung_passthrough)
        "patched" -> stringResource(R.string.rung_patched)
        "synthesized" -> stringResource(R.string.rung_synthesized)
        "original" -> stringResource(R.string.rung_original)
        "failed" -> stringResource(R.string.rung_failed, rung.reason.ifEmpty { stringResource(R.string.rung_failed_unknown) })
        else -> rung.rung
    }
    val whenText = rungAt(rung.at)
    return if (rung.rung == "failed" || whenText.isEmpty()) what else stringResource(R.string.rung_ago, what, whenText)
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
    val forceOptions = listOf(
        "" to R.string.adv_force_auto,
        "patch" to R.string.adv_force_patch,
        "passthrough" to R.string.adv_force_passthrough,
    )

    LarasCollapsibleCard(
        title = stringResource(R.string.adv_title),
        summary = stringResource(R.string.adv_summary),
        leadingIcon = Icons.Rounded.Tune,
        expanded = open,
        onToggleExpand = { open = !open },
        modifier = Modifier.padding(top = 10.dp),
    ) {
        Text(stringResource(R.string.adv_force_label), style = MaterialTheme.typography.titleSmall)
        ChoiceGroup(
            options = forceOptions,
            selected = forceOptions.firstOrNull { it.first == ui.force } ?: forceOptions.first(),
            label = { stringResource(it.second) },
            onSelect = { viewModel.setForce(it.first) },
        )
        Text(stringResource(R.string.adv_patch_label), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.adv_patch_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                pkgInput, { pkgInput = it.trim() },
                label = { Text(stringResource(R.string.adv_patch_package)) },
                singleLine = true,
                shape = LarasShapes.Inset,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                dateInput, { dateInput = it.trim() },
                label = { Text(stringResource(R.string.adv_patch_date)) },
                placeholder = { Text("2026-09-05") },
                singleLine = true,
                shape = LarasShapes.Inset,
                modifier = Modifier.weight(1f),
            )
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
                Text("$pkg · $date", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton({ viewModel.setPatchOverride(pkg, "") }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.adv_patch_remove))
                }
            }
        }
    }
}
