/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.brightness

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.brightness.BrightnessProfile
import org.yasvara.laras.data.brightness.BrightnessSettings
import org.yasvara.laras.data.brightness.DEBOUNCE_DOWN_DEFAULT
import org.yasvara.laras.data.brightness.DEBOUNCE_MAX
import org.yasvara.laras.data.brightness.DEBOUNCE_MIN
import org.yasvara.laras.data.brightness.DEBOUNCE_UP_DEFAULT
import org.yasvara.laras.data.brightness.LIMIT_GAP_NITS
import org.yasvara.laras.data.brightness.SUN_LUX_DEFAULT
import org.yasvara.laras.data.brightness.SUN_LUX_MAX
import org.yasvara.laras.data.brightness.SUN_LUX_MIN
import org.yasvara.laras.data.brightness.SunMode
import org.yasvara.laras.ui.components.ChangedDot
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

internal const val LUX_MAX = 100000f
private val SPEEDS = floatArrayOf(0.5f, 0.6f, 0.8f, 1f, 1.3f, 1.6f, 2f, 2.5f, 3f, 4f)

/**
 * Display › Brightness: the auto curve with the live light level on it, the response profile and
 * speeds, the auto limits, manual sunlight and the heat guard. Every change applies at once.
 */
@Composable
fun BrightnessScreen(onBack: () -> Unit, viewModel: BrightnessViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val lux by viewModel.lux.collectAsStateWithLifecycle()
    val s = ui.settings
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.pijar_undo)
    val resetText = stringResource(R.string.pijar_curve_restored)
    val removedText = stringResource(R.string.pijar_point_removed)

    // The curve is stored as it changes; an undo puts the one before back.
    fun offerUndo(message: String, before: BrightnessCurve?) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, undoLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.setCurve(before)
        }
    }

    Page(
        stringResource(R.string.pijar_title),
        onBack,
        snackbarHostState = snackbar,
        actions = {
            if (ui.loaded && (!s.isDefault || ui.curveEdited)) {
                TextButton(onClick = {
                    viewModel.resetAll()
                    if (ui.curveEdited) viewModel.setCurve(null)
                }) { Text(stringResource(R.string.pijar_reset_all)) }
            }
        },
    ) {
        if (!ui.loaded) return@Page
        val curve = ui.curve
        if (curve != null) {
            item(key = "hero") {
                CurveHero(
                    curve = curve,
                    defaultCurve = ui.defaultCurve,
                    edited = ui.curveEdited,
                    s = s,
                    lux = lux,
                    onCommit = viewModel::setCurve,
                    onReset = {
                        val before = curve
                        viewModel.setCurve(null)
                        offerUndo(resetText, before)
                    },
                    onRemoved = { before -> offerUndo(removedText, before) },
                )
            }
        }
        item(key = "response") {
            ResponseCard(s, groupedShape(0, if (curve != null) 3 else 2), viewModel::setProfile)
        }
        item(key = "speed") {
            SpeedCard(s, groupedShape(1, if (curve != null) 3 else 2), viewModel::setSpeed,
                viewModel::setDebounce)
        }
        if (curve != null) {
            item(key = "limits") { LimitsCard(curve, s, groupedShape(2, 3), viewModel::setLimits) }
        }
        item(key = "sun") {
            SunCard(s, Modifier.padding(top = 12.dp), viewModel::setSun)
        }
        item(key = "thermal") {
            Surface(shape = LarasShapes.Card, color = LarasSurfaces.card,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    SwitchRow(
                        stringResource(R.string.pijar_thermal),
                        s.thermalGuard,
                        viewModel::setThermalGuard,
                        summary = stringResource(R.string.pijar_thermal_summary),
                    )
                }
            }
        }
    }
}

internal fun luxPos(lux: Float) = log10(lux.coerceIn(0f, LUX_MAX) + 1f) / log10(LUX_MAX + 1f)

internal fun nitsPos(nits: Float, min: Float, max: Float): Float =
    ((log10(nits.coerceIn(min, max)) - log10(min)) / (log10(max) - log10(min))).coerceIn(0f, 1f)

internal fun nitsAtPos(pos: Float, min: Float, max: Float): Float =
    10f.pow(log10(min) + pos * (log10(max) - log10(min)))

internal fun limited(nits: Float, s: BrightnessSettings): Float {
    var n = nits
    s.floorNits?.let { n = maxOf(n, it) }
    s.ceilingNits?.let { n = minOf(n, it) }
    return n
}

internal fun formatNits(nits: Float): String =
    if (nits < 10f) "%.1f".format(nits) else NumberFormat.getIntegerInstance().format(nits.roundToInt())

internal fun formatLux(lux: Float): String = NumberFormat.getIntegerInstance().format(lux.roundToInt())

@Composable
private fun ResponseCard(s: BrightnessSettings, shape: Shape, onProfile: (BrightnessProfile) -> Unit) {
    val options = if (s.profile == BrightnessProfile.CUSTOM) BrightnessProfile.presets + BrightnessProfile.CUSTOM
    else BrightnessProfile.presets
    Surface(shape = shape, color = LarasSurfaces.card, modifier = Modifier.padding(bottom = 2.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.pijar_response), style = MaterialTheme.typography.titleMedium)
            ChoiceButtons(
                options = options,
                selected = s.profile,
                onSelect = { if (it != BrightnessProfile.CUSTOM) onProfile(it as BrightnessProfile) },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun SpeedCard(
    s: BrightnessSettings,
    shape: Shape,
    onSpeed: (Float, Float) -> Unit,
    onDebounce: (Int?, Int?) -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    Surface(shape = shape, color = LarasSurfaces.card, modifier = Modifier.padding(bottom = 2.dp)) {
        Column(Modifier.animateContentSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            SpeedSlider(R.string.pijar_speed_up, s.speedUp) { onSpeed(it, s.speedDown) }
            SpeedSlider(R.string.pijar_speed_down, s.speedDown) { onSpeed(s.speedUp, it) }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pijar_speed_slow), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.pijar_speed_fast), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.pijar_advanced))
            }
            if (advanced) {
                val up = s.debounceUp ?: DEBOUNCE_UP_DEFAULT
                val down = s.debounceDown ?: DEBOUNCE_DOWN_DEFAULT
                DebounceSlider(R.string.pijar_debounce_up, up) { onDebounce(it, s.debounceDown) }
                DebounceSlider(R.string.pijar_debounce_down, down) { onDebounce(s.debounceUp, it) }
            }
        }
    }
}

@Composable
private fun DebounceSlider(title: Int, value: Int, onChange: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = ((dragging ?: value.toFloat()) / 50f).roundToInt() * 50
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.pijar_ms, NumberFormat.getIntegerInstance().format(shown)),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = dragging ?: value.toFloat(),
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onChange((it / 50f).roundToInt() * 50) }
            dragging = null
        },
        valueRange = DEBOUNCE_MIN.toFloat()..DEBOUNCE_MAX.toFloat(),
    )
}

@Composable
private fun SpeedSlider(title: Int, value: Float, onChange: (Float) -> Unit) {
    // Follows the finger locally and stores once, when the drag ends.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val index = dragging ?: SPEEDS.indices.minBy { abs(SPEEDS[it] - value) }.toFloat()
    val shown = SPEEDS[index.roundToInt()]
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.pijar_speed_value, "%.1f".format(shown)),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = index,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            dragging?.let { onChange(SPEEDS[it.roundToInt()]) }
            dragging = null
        },
        valueRange = 0f..SPEEDS.lastIndex.toFloat(),
        steps = SPEEDS.size - 2,
    )
}

@Composable
private fun LimitsCard(
    curve: BrightnessCurve,
    s: BrightnessSettings,
    shape: Shape,
    onLimits: (Float?, Float?) -> Unit,
) {
    val min = curve.minNits
    val max = curve.maxNits
    var dragging by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    val stored = nitsPos(s.floorNits ?: min, min, max)..nitsPos(s.ceilingNits ?: max, min, max)
    val shown = dragging ?: stored
    val changed = s.floorNits != null || s.ceilingNits != null
    Surface(shape = shape, color = LarasSurfaces.card) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pijar_limits), style = MaterialTheme.typography.titleMedium)
                ChangedDot(changed, Modifier.padding(start = 8.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.pijar_limits_value,
                        formatNits(nitsAtPos(shown.start, min, max)),
                        formatNits(nitsAtPos(shown.endInclusive, min, max))),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            RangeSlider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { r ->
                        val ceiling = if (r.endInclusive >= 0.995f) null else nitsAtPos(r.endInclusive, min, max)
                        var floor = if (r.start <= 0.005f) null else nitsAtPos(r.start, min, max)
                        if (floor != null && floor > (ceiling ?: max) - LIMIT_GAP_NITS) {
                            floor = ((ceiling ?: max) - LIMIT_GAP_NITS).takeIf { it > min }
                        }
                        onLimits(floor?.let(::roundNits), ceiling?.let(::roundNits))
                    }
                    dragging = null
                },
            )
        }
    }
}

internal fun roundNits(nits: Float): Float = when {
    nits < 10f -> (nits * 10).roundToInt() / 10f
    nits < 100f -> nits.roundToInt().toFloat()
    else -> (nits / 10).roundToInt() * 10f
}

@Composable
private fun SunCard(s: BrightnessSettings, modifier: Modifier, onSun: (Boolean, SunMode, Float?) -> Unit) {
    Surface(shape = LarasShapes.Card, color = LarasSurfaces.card, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize().padding(horizontal = 20.dp, vertical = 10.dp)) {
            SwitchRow(
                stringResource(R.string.pijar_sun),
                s.sunOn,
                { onSun(it, s.sunMode, s.sunLux) },
                summary = stringResource(R.string.pijar_sun_summary),
            )
            if (!s.sunOn) return@Column
            ChoiceButtons(
                options = SunMode.entries,
                selected = s.sunMode,
                onSelect = { onSun(true, it as SunMode, s.sunLux) },
                modifier = Modifier.padding(top = 8.dp),
            )
            var dragging by remember { mutableStateOf<Float?>(null) }
            val lux = s.sunLux ?: SUN_LUX_DEFAULT
            val range = log10(SUN_LUX_MIN)..log10(SUN_LUX_MAX)
            val shown = dragging ?: log10(lux)
            val shownLux = (10f.pow(shown) / 500f).roundToInt() * 500f
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pijar_sun_lux), style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f))
                Text(stringResource(R.string.pijar_lux, formatLux(if (dragging == null) lux else shownLux)),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSun(true, s.sunMode, (10f.pow(it) / 500f).roundToInt() * 500f) }
                    dragging = null
                },
                valueRange = range,
            )
        }
    }
}
