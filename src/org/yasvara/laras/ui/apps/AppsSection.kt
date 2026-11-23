/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.apps

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.integrity.IntegrityKeys
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.LarasAppPickerSheet
import org.yasvara.laras.ui.components.LarasIconCircle
import org.yasvara.laras.ui.components.LarasNavRow
import org.yasvara.laras.ui.components.LarasPickerItem
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.SectionCard
import org.yasvara.laras.ui.components.groupedShape

private const val NETFLIX = "com.netflix.mediaclient"
private const val MEDIA_PROFILE = "s25u"

private enum class AppSpoofMode(override val value: Int, override val label: Int, val hint: Int) : ChoiceOption {
    AUTO(IntegrityKeys.MODE_AUTO, R.string.integrity_mode_auto, R.string.mode_auto_hint),
    FULL(IntegrityKeys.MODE_FULL, R.string.integrity_mode_full, R.string.mode_full_hint),
    MODEL(IntegrityKeys.MODE_MODEL_ONLY, R.string.integrity_mode_model, R.string.mode_model_hint);

    companion object {
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: AUTO
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsSection(viewModel: AppsViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mode = AppSpoofMode.of(ui.spoofMode)

    var selectedPkg by rememberSaveable { mutableStateOf("") }
    var profile by rememberSaveable { mutableStateOf(MEDIA_PROFILE) }
    var profileExpanded by rememberSaveable { mutableStateOf(false) }
    var pickingApp by rememberSaveable { mutableStateOf(false) }

    val installedApps by produceState<List<LarasPickerItem>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launch, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != context.packageName }
                .map { pkg ->
                    val label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }.getOrDefault(pkg)
                    LarasPickerItem(pkg, label)
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val chosenProfile = ui.profiles.firstOrNull { it.id == profile }
    val selectedLabel = remember(selectedPkg) { selectedPkg.takeIf { it.isNotEmpty() }?.let(viewModel::labelOf) }
    val hasNetflix = remember { viewModel.labelOf(NETFLIX) != null }

    Column(verticalArrangement = Arrangement.spacedBy(LarasSpacing.GroupGap)) {
        SectionCard(stringResource(R.string.integrity_spoof_mode), subtitle = stringResource(mode.hint)) {
            ChoiceButtons(
                AppSpoofMode.entries,
                mode,
                { viewModel.setSpoofMode((it as AppSpoofMode).value) },
                Modifier.fillMaxWidth(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap)) {
            LarasSwitchRow(
                title = stringResource(R.string.apps_photos_title),
                summary = stringResource(R.string.apps_photos_summary),
                checked = ui.photos,
                shape = groupedShape(0, 2),
                onCheckedChange = viewModel::setPhotos,
            )
            LarasSwitchRow(
                title = stringResource(R.string.apps_snap_title),
                summary = stringResource(R.string.apps_snap_summary),
                checked = ui.snapchat,
                shape = groupedShape(1, 2),
                onCheckedChange = viewModel::setSnapchat,
            )
        }

        SectionCard(stringResource(R.string.apps_perapp_title), subtitle = stringResource(R.string.apps_perapp_summary)) {
            LarasNavRow(
                title = selectedLabel ?: stringResource(R.string.apps_perapp_package),
                summary = selectedPkg.ifEmpty { NETFLIX },
                leading = {
                    if (selectedPkg.isNotEmpty()) AppIcon(selectedPkg, size = 36.dp)
                    else LarasIconCircle(Icons.Rounded.Apps)
                },
                shape = LarasShapes.Inset,
                containerColor = LarasSurfaces.nestedCard,
                onClick = { pickingApp = true },
            )
            ExposedDropdownMenuBox(profileExpanded, { profileExpanded = it }) {
                OutlinedTextField(
                    value = chosenProfile?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.apps_perapp_profile)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileExpanded) },
                    shape = LarasShapes.Inset,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(profileExpanded, { profileExpanded = false }) {
                    ui.profiles.forEach {
                        DropdownMenuItem({ Text(it.name) }, {
                            profile = it.id
                            profileExpanded = false
                        })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.assign(selectedPkg, profile)
                        selectedPkg = ""
                    },
                    enabled = selectedLabel != null && chosenProfile != null,
                ) { Text(stringResource(R.string.apps_perapp_add)) }
                if (hasNetflix && ui.assignments.none { it.packageName == NETFLIX }) {
                    TextButton({ viewModel.assign(NETFLIX, MEDIA_PROFILE) }) {
                        Text(stringResource(R.string.apps_perapp_netflix))
                    }
                }
            }
            ui.assignments.forEach { item ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(item.packageName, size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            item.profile?.name ?: item.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton({ viewModel.unassign(item.packageName) }) {
                        Text(stringResource(R.string.apps_perapp_remove))
                    }
                }
            }
        }
    }

    if (pickingApp) {
        LarasAppPickerSheet(
            apps = installedApps,
            selected = setOfNotNull(selectedPkg.takeIf { it.isNotEmpty() }),
            searchPlaceholder = stringResource(R.string.visibility_search),
            title = stringResource(R.string.apps_perapp_package),
            onDismiss = { pickingApp = false },
            onPick = { pkg ->
                selectedPkg = pkg
                pickingApp = false
            },
        )
    }
}
