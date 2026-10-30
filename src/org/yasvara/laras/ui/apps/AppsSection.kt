/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.SectionCard
import org.yasvara.laras.ui.components.SwitchRow

private const val NETFLIX = "com.netflix.mediaclient"
private const val MEDIA_PROFILE = "s25u"

/** The Apps group: Photos, Snapchat, and per-app device profiles (games, streaming). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsSection(viewModel: AppsViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var pkg by rememberSaveable { mutableStateOf("") }
    var profile by rememberSaveable { mutableStateOf(MEDIA_PROFILE) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chosen = ui.profiles.firstOrNull { it.id == profile }
    val installed = remember(pkg) { pkg.isNotBlank() && viewModel.labelOf(pkg.trim()) != null }
    val hasNetflix = remember { viewModel.labelOf(NETFLIX) != null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.apps_photos_title), subtitle = stringResource(R.string.apps_photos_summary)) {
            SwitchRow(stringResource(R.string.apps_photos_enable), ui.photos, viewModel::setPhotos)
        }
        SectionCard(stringResource(R.string.apps_snap_title), subtitle = stringResource(R.string.apps_snap_summary)) {
            SwitchRow(stringResource(R.string.apps_snap_enable), ui.snapchat, viewModel::setSnapchat)
        }
        SectionCard(stringResource(R.string.apps_perapp_title), subtitle = stringResource(R.string.apps_perapp_summary)) {
            OutlinedTextField(
                value = pkg,
                onValueChange = { pkg = it.trim() },
                label = { Text(stringResource(R.string.apps_perapp_package)) },
                placeholder = { Text(NETFLIX) },
                supportingText = {
                    if (pkg.isNotBlank() && !installed) Text(stringResource(R.string.apps_perapp_not_installed))
                },
                isError = pkg.isNotBlank() && !installed,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ExposedDropdownMenuBox(expanded, { expanded = it }) {
                OutlinedTextField(
                    value = chosen?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.apps_perapp_profile)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    ui.profiles.forEach {
                        DropdownMenuItem({ Text(it.name) }, {
                            profile = it.id
                            expanded = false
                        })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    viewModel.assign(pkg, profile)
                    pkg = ""
                }, enabled = installed && chosen != null) { Text(stringResource(R.string.apps_perapp_add)) }
                if (hasNetflix) {
                    TextButton({ viewModel.assign(NETFLIX, MEDIA_PROFILE) }) {
                        Text(stringResource(R.string.apps_perapp_netflix))
                    }
                }
            }
            ui.assignments.forEach { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(item.packageName, size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.titleSmall)
                        Text(item.profile?.name ?: item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({ viewModel.unassign(item.packageName) }) {
                        Text(stringResource(R.string.apps_perapp_remove))
                    }
                }
            }
        }
    }
}
