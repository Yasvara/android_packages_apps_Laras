/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.connectivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.connectivity.BandAvailability
import org.yasvara.laras.data.connectivity.RegionState
import org.yasvara.laras.data.connectivity.WifiRegion
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

/**
 * Laras › Connectivity › Wi-Fi region (SPEC-0014 §3.4): automatic detection, or a manual
 * country behind the Advanced gate. The firmware applies that country's rules, radar
 * protection included, in both modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiRegionScreen(prefs: PrefSnapshot, onBack: () -> Unit) {
    val context = LocalContext.current
    val stateRaw = prefs[Prefs.wifiRegionState]
    val state = RegionState.parse(stateRaw)
    val manual = prefs[Prefs.wifiRegion]?.takeIf { it.length == 2 }?.uppercase(Locale.US)
    val advanced = prefs[Prefs.larasAdvanced]
    // The country waiting for the user to accept the warning.
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val bands by produceState<BandAvailability?>(null, stateRaw) {
        value = withContext(Dispatchers.IO) { WifiRegion.allowedBands(context) }
    }
    val countries = remember {
        Locale.getISOCountries().map { it to countryName(it) }.sortedBy { it.second }
    }

    Page(stringResource(R.string.wifi_region_title), onBack) {
        item(key = "now") { NowCard(state, bands) }
        item(key = "auto") {
            ModeRow(
                title = R.string.wifi_region_auto,
                summary = stringResource(R.string.wifi_region_auto_summary),
                selected = manual == null,
                shape = groupedShape(0, 2),
                enabled = true,
                onClick = { WifiRegion.setManual(context, null) },
            )
        }
        item(key = "manual") {
            ModeRow(
                title = R.string.wifi_region_manual,
                summary = if (advanced) {
                    manual?.let(::countryName) ?: stringResource(R.string.wifi_region_manual_summary)
                } else {
                    stringResource(R.string.wifi_region_manual_locked)
                },
                selected = manual != null,
                shape = groupedShape(1, 2),
                enabled = advanced,
                onClick = {},
            )
        }
        if (advanced) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.wifi_region_search)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
                )
            }
            val shown = countries.filter { (cc, name) ->
                query.isBlank() || name.contains(query, ignoreCase = true) || cc.equals(query, true)
            }
            items(shown.size, key = { "cc:" + shown[it].first }) { i ->
                val (cc, name) = shown[i]
                ModeRow(
                    title = null,
                    titleText = name,
                    summary = cc,
                    selected = cc == manual,
                    shape = groupedShape(i, shown.size),
                    enabled = true,
                    onClick = { if (cc != manual) confirm = cc },
                )
            }
        }
        item(key = "note") {
            Text(
                stringResource(R.string.wifi_region_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }

    confirm?.let { cc ->
        ModalBottomSheet(onDismissRequest = { confirm = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.wifi_region_warning_title, countryName(cc)),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.wifi_region_warning_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton({ confirm = null }) { Text(stringResource(R.string.lab_advanced_warning_cancel)) }
                    Button({
                        WifiRegion.setManual(context, cc)
                        confirm = null
                    }) { Text(stringResource(R.string.wifi_region_warning_confirm)) }
                }
            }
        }
    }
}

/** The country in use, its source, and which bands it opens for Wi-Fi and the hotspot. */
@Composable
private fun NowCard(state: RegionState, bands: BandAvailability?) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.padding(bottom = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                state.country?.let(::countryName) ?: stringResource(R.string.wifi_region_world),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(state.source.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BandChip("2.4 GHz", true)
                BandChip("5 GHz", bands?.sta5 == true)
                BandChip("6 GHz", bands?.sta6 == true)
            }
            Text(
                stringResource(
                    R.string.wifi_region_hotspot,
                    stringResource(if (bands?.sap5 == true) R.string.option_yes else R.string.option_no),
                    stringResource(if (bands?.sap6 == true) R.string.option_yes else R.string.option_no),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            bands?.channels5?.takeIf { it.isNotEmpty() }?.let { channels ->
                Text(
                    stringResource(R.string.wifi_region_channels5, channels.joinToString(" ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BandChip(label: String, on: Boolean) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (on) c.primaryContainer else c.surfaceContainerHighest,
        contentColor = if (on) c.onPrimaryContainer else c.onSurfaceVariant,
    ) {
        Text(
            if (on) label else "$label ✕",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ModeRow(
    title: Int?,
    summary: String,
    selected: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    enabled: Boolean,
    onClick: () -> Unit,
    titleText: String? = null,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    titleText ?: stringResource(title!!),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}
