/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.components.EntryRow
import org.yasvara.laras.ui.components.GroupBadge
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.LarasViewModel
import org.yasvara.laras.ui.Route
import org.yasvara.laras.ui.keyboard.KeyboardSection
import org.yasvara.laras.ui.lab.LabSection
import org.yasvara.laras.ui.superuser.SuperuserSection
import org.yasvara.laras.ui.apps.AppsSection
import org.yasvara.laras.ui.connectivity.ConnectivityExperiments
import org.yasvara.laras.ui.connectivity.ConnectivitySection
import org.yasvara.laras.ui.integrity.IntegritySection
import org.yasvara.laras.ui.kait.ModulesSection

@Composable
fun GroupScreen(
    group: SystemGroup,
    prefs: PrefSnapshot,
    viewModel: LarasViewModel,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
) {
    Page(stringResource(group.title), onBack) {
        item(key = "header") {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GroupBadge(group, 96.dp)
                Text(
                    stringResource(group.summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
        if (group.planned) {
            item(key = "planned") { Planned() }
        }
        if (group == SystemGroup.INTEGRITY) {
            item(key = "integrity") {
                Column(Modifier.fillMaxWidth()) { IntegritySection(onOpenTargets = { onOpen(Route.TargetApps) }) }
            }
        }
        if (group == SystemGroup.APPS) {
            item(key = "updates") {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    NavRow(R.string.updates_title, R.string.updates_entry_summary, groupedShape(0, 1)) {
                        onOpen(Route.AppUpdates())
                    }
                }
            }
            item(key = "apps") {
                Column(Modifier.fillMaxWidth()) { AppsSection() }
            }
        }
        if (group == SystemGroup.POWER) {
            item(key = "lelap") {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    NavRow(R.string.lelap_apps_title, R.string.lelap_apps_entry_summary, groupedShape(0, 3)) {
                        onOpen(Route.LelapApps)
                    }
                    NavRow(R.string.lelap_cuts_title, R.string.lelap_cuts_entry_summary, groupedShape(1, 3)) {
                        onOpen(Route.LelapCuts)
                    }
                    NavRow(R.string.padat_title, R.string.padat_entry_summary, groupedShape(2, 3)) {
                        onOpen(Route.Padat())
                    }
                }
            }
        }
        if (group == SystemGroup.SOUND) {
            item(key = "sound_apps") {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    NavRow(R.string.nada_title, R.string.nada_entry_summary, groupedShape(0, 3)) {
                        onOpen(Route.Nada)
                    }
                    NavRow(R.string.swara_title, R.string.swara_entry_summary, groupedShape(1, 3)) {
                        onOpen(Route.SoundApps)
                    }
                    NavRow(R.string.raba_title, R.string.raba_entry_summary, groupedShape(2, 3)) {
                        onOpen(Route.Haptics)
                    }
                }
            }
        }
        if (group == SystemGroup.CONNECTIVITY) {
            item(key = "saring") {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    NavRow(R.string.saring_title, R.string.saring_entry_summary, groupedShape(0, 1)) {
                        onOpen(Route.Saring)
                    }
                }
            }
            item(key = "wifi_region") {
                ConnectivitySection(prefs, onOpenRegion = { onOpen(Route.WifiRegion) })
            }
        }
        if (group == SystemGroup.STYLE) {
            item(key = "color") {
                NavRow(R.string.color_title, R.string.color_entry_summary, groupedShape(0, 1)) {
                    onOpen(Route.Color)
                }
            }
        }
        if (group == SystemGroup.KEYBOARD) {
            item(key = "keyboard") { KeyboardSection() }
        }
        if (group == SystemGroup.MODULES) {
            item(key = "modules") {
                ModulesSection(
                    onOpenScope = { onOpen(Route.XposedScope(it)) },
                    onOpenKait = { onOpen(Route.Kait()) },
                )
            }
        }
        if (group == SystemGroup.LAB) {
            item(key = "lab") { LabSection(prefs, viewModel, viewModel) }
        }
        if (group == SystemGroup.SUPERUSER) {
            item(key = "akar") {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    NavRow(R.string.akar_title, R.string.akar_entry_summary, groupedShape(0, 2)) {
                        onOpen(Route.Akar())
                    }
                    NavRow(R.string.visibility_title, R.string.visibility_entry_summary, groupedShape(1, 2)) {
                        onOpen(Route.AppVisibility)
                    }
                }
            }
            item(key = "superuser") { SuperuserSection(prefs, viewModel) }
        } else items(group.entries.size, key = { group.entries[it].pref.toString() }) { i ->
            EntryRow(group.entries[i], prefs, viewModel, groupedShape(i, group.entries.size))
        }
        if (group == SystemGroup.CONNECTIVITY) {
            item(key = "experiments") { ConnectivityExperiments(prefs) }
        }
    }
}

/** A row that opens a screen of its own. */
@Composable
private fun NavRow(title: Int, summary: Int, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Planned() {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.group_planned), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.group_planned_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
