/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.brightness

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import org.yasvara.laras.R
import org.yasvara.laras.data.brightness.BrightnessProfile
import org.yasvara.laras.data.brightness.BrightnessSettings
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

private const val LUX_MAX = 100000f
private val SPEEDS = floatArrayOf(0.5f, 0.6f, 0.8f, 1f, 1.3f, 1.6f, 2f, 2.5f, 3f, 4f)
// Where each zone label sits on the lux axis.
private val ZONES = listOf(
    1f to R.string.pijar_zone_dark,
    300f to R.string.pijar_zone_room,
    5000f to R.string.pijar_zone_shade,
    50000f to R.string.pijar_zone_sun,
)

/**
 * Display › Brightness: the auto curve with the live light level on it, the response profile and
 * speeds, the auto limits, manual sunlight and the heat guard. Every change applies at once.
 */
@Composable
fun BrightnessScreen(onBack: () -> Unit, viewModel: BrightnessViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val lux by viewModel.lux.collectAsStateWithLifecycle()
    val s = ui.settings

    Page(
        stringResource(R.string.pijar_title),
        onBack,
        actions = {
            if (ui.loaded && !s.isDefault) {
                TextButton(onClick = viewModel::resetAll) { Text(stringResource(R.string.pijar_reset_all)) }
            }
        },
    ) {
        if (!ui.loaded) return@Page
        val curve = ui.curve
        if (curve != null) {
            item(key = "hero") { CurveHero(curve, s, lux) }
        }
        item(key = "response") {
            ResponseCard(s, groupedShape(0, if (curve != null) 3 else 2), viewModel::setProfile)
        }
        item(key = "speed") {
            SpeedCard(s, groupedShape(1, if (curve != null) 3 else 2), viewModel::setSpeed)
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

private fun luxPos(lux: Float) = log10(lux.coerceIn(0f, LUX_MAX) + 1f) / log10(LUX_MAX + 1f)

private fun nitsPos(nits: Float, min: Float, max: Float): Float =
    ((log10(nits.coerceIn(min, max)) - log10(min)) / (log10(max) - log10(min))).coerceIn(0f, 1f)

private fun nitsAtPos(pos: Float, min: Float, max: Float): Float =
    10f.pow(log10(min) + pos * (log10(max) - log10(min)))

private fun limited(nits: Float, s: BrightnessSettings): Float {
    var n = nits
    s.floorNits?.let { n = maxOf(n, it) }
    s.ceilingNits?.let { n = minOf(n, it) }
    return n
}

private fun formatNits(nits: Float): String =
    if (nits < 10f) "%.1f".format(nits) else NumberFormat.getIntegerInstance().format(nits.roundToInt())

private fun formatLux(lux: Float): String = NumberFormat.getIntegerInstance().format(lux.roundToInt())

/** The curve on log axes, the auto limits as shaded bands, and a dot at the light right now. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CurveHero(curve: BrightnessCurve, s: BrightnessSettings, lux: Float?) {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.onSurfaceVariant)
    val zoneLabels = ZONES.map { (l, res) -> l to stringResource(res) }
    val min = curve.minNits
    val max = curve.maxNits
    val target = lux?.let { limited(curve.nitsAt(it), s) }
    val dotX by animateFloatAsState(luxPos(lux ?: 0f), MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "dotX")
    val dotY by animateFloatAsState(nitsPos(target ?: min, min, max),
        MaterialTheme.motionScheme.slowSpatialSpec(), label = "dotY")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "pulse",
    )

    Surface(shape = LarasShapes.Hero, color = LarasSurfaces.card,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    target?.let { stringResource(R.string.pijar_nits, formatNits(it)) } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                    color = c.onSurface,
                    modifier = Modifier.weight(1f),
                )
                lux?.let {
                    Text(stringResource(R.string.pijar_lux, formatLux(it)),
                        style = MaterialTheme.typography.titleMedium, color = c.primary)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 12.dp)) {
                val labelH = 18.dp.toPx()
                val w = size.width
                val h = size.height - labelH
                fun x(l: Float) = luxPos(l) * w
                fun y(n: Float) = h - nitsPos(n, min, max) * h

                s.ceilingNits?.let { ceil ->
                    drawRect(c.primary.copy(alpha = 0.08f), Offset.Zero, Size(w, y(ceil)))
                    drawLine(c.primary.copy(alpha = 0.5f), Offset(0f, y(ceil)), Offset(w, y(ceil)),
                        1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
                s.floorNits?.let { floor ->
                    drawRect(c.primary.copy(alpha = 0.08f), Offset(0f, y(floor)), Size(w, h - y(floor)))
                    drawLine(c.primary.copy(alpha = 0.5f), Offset(0f, y(floor)), Offset(w, y(floor)),
                        1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                }
                for ((l, _) in zoneLabels) {
                    drawLine(c.outlineVariant.copy(alpha = 0.6f), Offset(x(l), 0f), Offset(x(l), h), 1.dp.toPx())
                }

                val samples = 96
                fun path(limit: Boolean) = Path().apply {
                    for (i in 0..samples) {
                        val p = i / samples.toFloat()
                        val l = 10f.pow(p * log10(LUX_MAX + 1f)) - 1f
                        val n = curve.nitsAt(l).let { if (limit) limited(it, s) else it }
                        if (i == 0) moveTo(p * w, y(n)) else lineTo(p * w, y(n))
                    }
                }
                val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                if (s.floorNits != null || s.ceilingNits != null) {
                    drawPath(path(false), c.outlineVariant, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                drawPath(path(true), c.primary, style = stroke)

                if (lux != null) {
                    val center = Offset(dotX * w, h - dotY * h)
                    drawCircle(c.primary.copy(alpha = 0.3f * (1f - pulse)), 7.dp.toPx() + pulse * 14.dp.toPx(), center)
                    drawCircle(c.surfaceBright, 9.dp.toPx(), center)
                    drawCircle(c.primary, 6.dp.toPx(), center)
                }

                for ((l, text) in zoneLabels) {
                    val layout = measurer.measure(text, labelStyle)
                    val left = (x(l) - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
                    drawText(layout, topLeft = Offset(left, h + 4.dp.toPx()))
                }
            }
        }
    }
}

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
private fun SpeedCard(s: BrightnessSettings, shape: Shape, onSpeed: (Float, Float) -> Unit) {
    Surface(shape = shape, color = LarasSurfaces.card, modifier = Modifier.padding(bottom = 2.dp)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            SpeedSlider(R.string.pijar_speed_up, s.speedUp) { onSpeed(it, s.speedDown) }
            SpeedSlider(R.string.pijar_speed_down, s.speedDown) { onSpeed(s.speedUp, it) }
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pijar_speed_slow), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.pijar_speed_fast), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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

private fun roundNits(nits: Float): Float = when {
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
