/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.yasvara.laras.ui.preview

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.roundToInt
import org.json.JSONObject
import org.yasvara.laras.R

/**
 * States the shade canvas shows instead of the phone's own (`sim`), for states
 * users can't easily produce: charging, a nearly empty battery, Battery Saver, 5G, roaming. A
 * null field keeps the phone's own state. Nothing here is ever saved; it lives as long as the
 * canvas.
 */
data class PreviewSim(
    val battery: Int? = null,
    val charging: Boolean? = null,
    val saver: Boolean? = null,
    val mobile: Mobile? = null,
    val wifi: Wifi? = null,
) {
    /** A cellular signal of [level] bars out of four, with a network [type] such as "5G". */
    data class Mobile(val level: Int = 4, val type: String? = "5G", val roaming: Boolean = false)

    /** Wi-Fi at [level] of four, or off when [level] is null. */
    data class Wifi(val level: Int? = 4)

    val simulatesBattery: Boolean get() = battery != null || charging != null || saver != null
    val simulatesIcons: Boolean get() = mobile != null || wifi != null

    fun withoutBattery() = copy(battery = null, charging = null, saver = null)
    fun withoutIcons() = copy(mobile = null, wifi = null)

    fun toJson(): String = JSONObject().apply {
        battery?.let { put("battery", it) }
        charging?.let { put("charging", it) }
        saver?.let { put("saver", it) }
        mobile?.let {
            put("mobile", JSONObject().apply {
                put("level", it.level)
                put("levels", 4)
                it.type?.let { type -> put("type", type) }
                put("roaming", it.roaming)
            })
        }
        wifi?.let { put("wifi", JSONObject().apply { it.level?.let { level -> put("level", level) } }) }
    }.toString()
}

/** The phone's battery as it is now: where the controls start before anything is simulated. */
private data class PhoneBattery(val level: Int, val charging: Boolean, val saver: Boolean) {
    companion object {
        fun read(context: Context): PhoneBattery {
            val battery = context.getSystemService(BatteryManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            return PhoneBattery(
                level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    .coerceIn(0, 100),
                charging = battery.isCharging,
                saver = power.isPowerSaveMode,
            )
        }
    }
}

/**
 * Battery states to try on the canvas: a level slider and the charging and saver states. The
 * first change simulates all three, starting from the phone's own values, so the canvas never
 * mixes a simulated level with a real charger.
 */
@Composable
fun BatterySimCard(sim: PreviewSim, onChange: (PreviewSim) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val phone = remember(context) { PhoneBattery.read(context) }
    val level = sim.battery ?: phone.level
    val charging = sim.charging ?: phone.charging
    val saver = sim.saver ?: phone.saver
    fun change(transform: (PreviewSim) -> PreviewSim) =
        onChange(transform(sim.copy(battery = level, charging = charging, saver = saver)))

    SimCard(
        active = sim.simulatesBattery,
        onReset = { onChange(sim.withoutBattery()) },
        modifier = modifier,
    ) {
        val percent = remember(level) { NumberFormat.getPercentInstance().format(level / 100.0) }
        val levelLabel = stringResource(R.string.sim_battery_level)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = level.toFloat(),
                onValueChange = { value -> change { it.copy(battery = value.roundToInt()) } },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = levelLabel
                    stateDescription = percent
                },
            )
            Text(
                percent,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 52.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimChip(charging, Icons.Rounded.Bolt, R.string.sim_charging) {
                change { it.copy(charging = !charging) }
            }
            SimChip(saver, Icons.Rounded.BatterySaver, R.string.sim_saver) {
                change { it.copy(saver = !saver) }
            }
        }
    }
}

/**
 * Signal and Wi-Fi states to try on the canvas, for the icon sets: bars, network type, roaming
 * and Wi-Fi strength or off. The first change simulates both, from four bars of 5G and full
 * Wi-Fi.
 */
@Composable
fun IconSimCard(sim: PreviewSim, onChange: (PreviewSim) -> Unit, modifier: Modifier = Modifier) {
    val mobile = sim.mobile ?: PreviewSim.Mobile()
    val wifi = sim.wifi ?: PreviewSim.Wifi()
    fun change(m: PreviewSim.Mobile = mobile, w: PreviewSim.Wifi = wifi) =
        onChange(sim.copy(mobile = m, wifi = w))

    SimCard(
        active = sim.simulatesIcons,
        onReset = { onChange(sim.withoutIcons()) },
        modifier = modifier,
    ) {
        LevelSlider(
            Icons.Rounded.SignalCellularAlt, R.string.sim_signal, mobile.level,
            onLevel = { change(m = mobile.copy(level = it)) },
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (type in NETWORK_TYPES) {
                SimChip(mobile.type == type, type ?: stringResource(R.string.sim_no_type)) {
                    change(m = mobile.copy(type = type))
                }
            }
            SimChip(mobile.roaming, Icons.Rounded.Public, R.string.sim_roaming) {
                change(m = mobile.copy(roaming = !mobile.roaming))
            }
        }
        LevelSlider(
            Icons.Rounded.Wifi, R.string.sim_wifi, wifi.level ?: 0,
            enabled = wifi.level != null,
            onLevel = { change(w = PreviewSim.Wifi(it)) },
        ) {
            val label = stringResource(R.string.sim_wifi)
            Switch(
                checked = wifi.level != null,
                onCheckedChange = { on -> change(w = PreviewSim.Wifi(if (on) 4 else null)) },
                modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = label },
            )
        }
    }
}

private val NETWORK_TYPES = listOf("5G", "LTE", "4G", "3G", null)

/** A discrete 0-4 slider for a signal level, with its value as "3 of 4". */
@Composable
private fun LevelSlider(
    icon: ImageVector,
    @StringRes label: Int,
    level: Int,
    enabled: Boolean = true,
    onLevel: (Int) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val name = stringResource(label)
    val value = stringResource(R.string.sim_level, level, 4)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = level.toFloat(),
            onValueChange = { onLevel(it.roundToInt()) },
            valueRange = 0f..4f,
            steps = 3,
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics {
                contentDescription = name
                stateDescription = value
            },
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        trailing()
    }
}

/** A card of simulation controls, headed as preview-only, with a way back to the real state. */
@Composable
private fun SimCard(
    active: Boolean,
    onReset: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .background(colors.surfaceContainerHigh, RoundedCornerShape(24.dp))
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp), tint = colors.primary)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(stringResource(R.string.sim_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.sim_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            ResetButton(active, onReset)
        }
        Column(Modifier.padding(end = 12.dp), content = content)
    }
}

/**
 * Returns the canvas to the phone's own state. Keeps its place while hidden, so the header
 * doesn't jump. Receiver-free, so the animation never binds to the header row's scope.
 */
@Composable
private fun ResetButton(active: Boolean, onReset: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            active,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            IconButton(onReset) {
                Icon(Icons.Rounded.Restore, stringResource(R.string.sim_reset))
            }
        }
    }
}

@Composable
private fun SimChip(
    selected: Boolean,
    icon: ImageVector,
    @StringRes label: Int,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) },
    )
}

@Composable
private fun SimChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
