/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.LarasViewModel
import org.yasvara.laras.ui.Route
import org.yasvara.laras.ui.apps.AppsSection
import org.yasvara.laras.ui.components.EntryRow
import org.yasvara.laras.ui.components.GroupBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasNavRow
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.connectivity.ConnectivityExperiments
import org.yasvara.laras.ui.connectivity.ConnectivitySection
import org.yasvara.laras.ui.integrity.IntegritySection
import org.yasvara.laras.ui.keyboard.KeyboardSection
import org.yasvara.laras.ui.lab.LabSection
import org.yasvara.laras.ui.superuser.SuperuserSection

private data class NavLink(val title: Int, val summary: Int, val route: Route)

@Composable
fun GroupScreen(
    group: SystemGroup,
    prefs: PrefSnapshot,
    viewModel: LarasViewModel,
    onOpen: (Route) -> Unit,
    onBack: () -> Unit,
) {
    val links = navLinksFor(group)
    // INTEGRITY leads directly with its own expressive Hero card; skip redundant tall badge header.
    val hasOwnHero = group == SystemGroup.INTEGRITY

    Page(stringResource(group.title), onBack) {
        if (!hasOwnHero) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    GroupBadge(group, 56.dp)
                    Text(
                        stringResource(group.summary),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (group.planned) {
            item(key = "planned") {
                LarasEmptyState(
                    title = stringResource(R.string.group_planned),
                    body = stringResource(R.string.group_planned_body),
                )
            }
        }
        if (links.isNotEmpty()) {
            item(key = "links") {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = LarasSpacing.GroupGap),
                    verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap),
                ) {
                    links.forEachIndexed { i, link ->
                        LarasNavRow(
                            title = stringResource(link.title),
                            summary = stringResource(link.summary),
                            shape = groupedShape(i, links.size),
                            onClick = { onOpen(link.route) },
                        )
                    }
                }
            }
        }
        when (group) {
            SystemGroup.INTEGRITY -> item(key = "integrity") {
                IntegritySection(onOpenTargets = { onOpen(Route.TargetApps) })
            }
            SystemGroup.APPS -> item(key = "apps") { AppsSection() }
            SystemGroup.CONNECTIVITY -> item(key = "wifi_region") {
                ConnectivitySection(prefs, onOpenRegion = { onOpen(Route.WifiRegion) })
            }
            SystemGroup.KEYBOARD -> item(key = "keyboard") { KeyboardSection() }
            SystemGroup.LAB -> item(key = "lab") { LabSection(prefs, viewModel, viewModel) }
            SystemGroup.SUPERUSER -> item(key = "superuser") { SuperuserSection(prefs, viewModel) }
            else -> Unit
        }
        if (group != SystemGroup.SUPERUSER && group.entries.isNotEmpty()) {
            items(group.entries.size, key = { group.entries[it].pref.toString() }) { i ->
                EntryRow(group.entries[i], prefs, viewModel, groupedShape(i, group.entries.size))
            }
        }
        if (group == SystemGroup.CONNECTIVITY) {
            item(key = "experiments") {
                Spacer(Modifier.height(4.dp))
                ConnectivityExperiments(prefs)
            }
        }
    }
}

private fun navLinksFor(group: SystemGroup): List<NavLink> = when (group) {
    SystemGroup.STYLE -> listOf(
        NavLink(R.string.color_title, R.string.color_entry_summary, Route.Color),
    )
    SystemGroup.APPS -> listOf(
        NavLink(R.string.updates_title, R.string.updates_entry_summary, Route.AppUpdates()),
    )
    SystemGroup.POWER -> listOf(
        NavLink(R.string.lelap_apps_title, R.string.lelap_apps_entry_summary, Route.LelapApps),
        NavLink(R.string.lelap_cuts_title, R.string.lelap_cuts_entry_summary, Route.LelapCuts),
        NavLink(R.string.padat_title, R.string.padat_entry_summary, Route.Padat()),
    )
    SystemGroup.SOUND -> listOf(
        NavLink(R.string.nada_title, R.string.nada_entry_summary, Route.Nada),
        NavLink(R.string.swara_title, R.string.swara_entry_summary, Route.SoundApps),
        NavLink(R.string.raba_title, R.string.raba_entry_summary, Route.Haptics),
    )
    SystemGroup.CONNECTIVITY -> listOf(
        NavLink(R.string.saring_title, R.string.saring_entry_summary, Route.Saring),
    )
    SystemGroup.SUPERUSER -> listOf(
        NavLink(R.string.akar_title, R.string.akar_entry_summary, Route.Akar()),
        NavLink(R.string.visibility_title, R.string.visibility_entry_summary, Route.AppVisibility),
    )
    else -> emptyList()
}
