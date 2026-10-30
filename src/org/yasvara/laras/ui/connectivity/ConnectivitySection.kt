/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.connectivity

import android.content.Context
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.connectivity.CellInfo
import org.yasvara.laras.data.connectivity.Cellular
import org.yasvara.laras.data.connectivity.Hotspot
import org.yasvara.laras.data.connectivity.HotspotWidth
import org.yasvara.laras.data.connectivity.NetworkMode
import org.yasvara.laras.data.connectivity.RegionState
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.groupedShape

/** Seconds a risky network mode stays before it reverts on its own, unless the user keeps it. */
private const val KEEP_SECONDS = 15

/** "Indonesia · from SIM", for the region row and the home card. */
@Composable
fun regionSummary(raw: String?): String {
    val state = RegionState.parse(raw)
    val country = state.country ?: return stringResource(R.string.wifi_region_not_detected)
    return stringResource(R.string.wifi_region_summary, countryName(country), stringResource(state.source.label))
}

fun countryName(cc: String): String =
    Locale("", cc).getDisplayCountry().takeIf { it.isNotEmpty() && it != cc } ?: cc

/** Region entry plus the experiments; the group's plain settings follow it in GroupScreen. */
@Composable
fun ConnectivitySection(prefs: PrefSnapshot, onOpenRegion: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            shape = groupedShape(0, 1),
            color = MaterialTheme.colorScheme.surfaceBright,
            onClick = onOpenRegion,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(stringResource(R.string.wifi_region_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    regionSummary(prefs[Prefs.wifiRegionState]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shown after the group's settings: changes that can cost signal or battery, behind Advanced. */
@Composable
fun ConnectivityExperiments(prefs: PrefSnapshot) {
    val advanced = prefs[Prefs.larasAdvanced]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Header(R.string.conn_experiments)
        if (!advanced) {
            Note(R.string.conn_experiments_locked)
        } else {
            HotspotWidthRow(groupedShapeIndex = 0)
            NetworkModeRow()
            HonestBarsRow()
        }
        Header(R.string.cell_title)
        CellCard()
    }
}

@Composable
private fun Header(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun Note(text: Int) {
    Surface(shape = groupedShape(0, 1), color = MaterialTheme.colorScheme.surfaceBright) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

@Composable
private fun ExperimentCard(index: Int, title: Int, summary: Int, content: @Composable () -> Unit) {
    Surface(shape = groupedShape(index, 3), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun HotspotWidthRow(groupedShapeIndex: Int) {
    val context = LocalContext.current
    var width by remember { mutableStateOf(Hotspot.width(context)) }
    ExperimentCard(groupedShapeIndex, R.string.hotspot_width, R.string.hotspot_width_summary) {
        ChoiceButtons(HotspotWidth.entries, width, onSelect = { option ->
            val next = option as HotspotWidth
            if (Hotspot.setWidth(context, next)) width = next else failed(context)
        })
    }
}

@Composable
private fun NetworkModeRow() {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(Cellular.mode(context)) }
    // The mode to fall back to while a risky one waits for the user's "Keep".
    var pendingRevert by remember { mutableStateOf<NetworkMode?>(null) }
    val current = mode
    ExperimentCard(1, R.string.net_mode, R.string.net_mode_summary) {
        if (current == null) {
            Text(
                stringResource(R.string.net_mode_no_sim),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ChoiceButtons(NetworkMode.entries, current, onSelect = { option ->
                val next = option as NetworkMode
                if (next == current) return@ChoiceButtons
                if (Cellular.setMode(context, next)) {
                    if (next.risky) pendingRevert = current
                    mode = next
                } else {
                    failed(context)
                }
            })
        }
    }
    pendingRevert?.let { previous ->
        var left by remember(previous) { mutableIntStateOf(KEEP_SECONDS) }
        val revert = {
            Cellular.setMode(context, previous)
            mode = previous
            pendingRevert = null
        }
        LaunchedEffect(previous) {
            while (left > 0) {
                delay(1000)
                left--
            }
            revert()
        }
        AlertDialog(
            onDismissRequest = revert,
            title = { Text(stringResource(R.string.net_mode_keep_title)) },
            text = { Text(stringResource(R.string.net_mode_keep_body, left)) },
            confirmButton = { TextButton({ pendingRevert = null }) { Text(stringResource(R.string.net_mode_keep)) } },
            dismissButton = { TextButton(revert) { Text(stringResource(R.string.net_mode_revert)) } },
        )
    }
}

@Composable
private fun HonestBarsRow() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Cellular.honestBars(context)) }
    Surface(shape = groupedShape(2, 3), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            SwitchRow(
                title = stringResource(R.string.nr_bars),
                summary = stringResource(R.string.nr_bars_summary),
                checked = on,
                onChange = { next -> if (Cellular.setHonestBars(context, next)) on = next else failed(context) },
            )
        }
    }
}

private fun failed(context: Context) =
    Toast.makeText(context, R.string.conn_change_failed, Toast.LENGTH_SHORT).show()

/** The serving cell, live while the page is open: band, channel width, carrier aggregation, signal. */
@Composable
private fun CellCard() {
    val context = LocalContext.current
    val info by produceState<CellInfo?>(null) {
        val tm = Cellular.telephony(context) ?: return@produceState
        var service: ServiceState? = tm.serviceState
        var signal: SignalStrength? = tm.signalStrength
        value = CellInfo.of(tm, service, signal)
        val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener,
            TelephonyCallback.SignalStrengthsListener {
            override fun onServiceStateChanged(state: ServiceState) {
                service = state
                value = CellInfo.of(tm, service, signal)
            }

            override fun onSignalStrengthsChanged(strength: SignalStrength) {
                signal = strength
                value = CellInfo.of(tm, service, signal)
            }
        }
        try {
            tm.registerTelephonyCallback(context.mainExecutor, callback)
            awaitDispose { tm.unregisterTelephonyCallback(callback) }
        } catch (e: SecurityException) {
            // No phone-state access on this build: the snapshot above stays.
        }
    }
    Surface(shape = groupedShape(0, 1), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val cell = info
            if (cell == null) {
                Text(
                    stringResource(R.string.net_mode_no_sim),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val where = when {
                cell.lteBand != null -> stringResource(R.string.cell_lte_band, cell.lteBand, cell.channel)
                cell.nrFrequencyMhz != null -> stringResource(R.string.cell_nr_freq, cell.nrFrequencyMhz)
                else -> ""
            }
            Text(
                listOf(cell.networkType, where).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
            )
            if (cell.bandwidthsKhz.isNotEmpty()) {
                val widths = cell.bandwidthsKhz.joinToString(" + ") { "${it / 1000} MHz" }
                Text(
                    stringResource(if (cell.aggregated) R.string.cell_ca else R.string.cell_single, widths),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (cell.lteRsrp != null) {
                Metric("LTE", cell.lteRsrp, cell.lteRsrq, cell.lteSinr)
            }
            if (cell.nrRsrp != null) {
                Metric("5G", cell.nrRsrp, cell.nrRsrq, cell.nrSinr)
            }
            Text(
                stringResource(R.string.cell_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: String, rsrp: Int, rsrq: Int?, sinr: Int?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 2.dp))
        Text(
            stringResource(R.string.cell_metric, rsrp, rsrq?.toString() ?: "–", sinr?.toString() ?: "–"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
