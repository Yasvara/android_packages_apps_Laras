/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.brightness

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import org.yasvara.laras.R
import org.yasvara.laras.data.brightness.BrightnessSettings
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces

// Where each zone label sits on the lux axis.
private val ZONES = listOf(
    1f to R.string.pijar_zone_dark,
    300f to R.string.pijar_zone_room,
    5000f to R.string.pijar_zone_shade,
    50000f to R.string.pijar_zone_sun,
)

/** Lux the editor samples a dense panel curve at; also the most points a curve may have. */
private val ANCHORS = floatArrayOf(0f, 2f, 10f, 30f, 100f, 300f, 1000f, 3000f, 10000f, 20000f, 40000f, 64000f)
private val MAX_POINTS = ANCHORS.size
private const val AXIS_MIN_NITS = 1f
// Neighbouring points keep at least this much of the lux axis between them.
private const val MIN_GAP = 0.03f
private const val TICKS_PER_AXIS = 24

/** Editable points: the curve itself when it is short enough, else samples of it. */
private fun controlPoints(curve: BrightnessCurve): Pair<FloatArray, FloatArray> =
    if (curve.size <= MAX_POINTS) curve.lux.copyOf() to curve.nits.copyOf()
    else ANCHORS.copyOf() to FloatArray(ANCHORS.size) { curve.nitsAt(ANCHORS[it]) }

private fun luxAtPos(pos: Float): Float = 10f.pow(pos * log10(LUX_MAX + 1f)) - 1f

private fun roundLux(lux: Float): Float = when {
    lux < 10f -> lux.roundToInt().toFloat()
    lux < 1000f -> (lux / 5f).roundToInt() * 5f
    else -> (lux / 100f).roundToInt() * 100f
}

/**
 * The auto curve on log axes with the live light level on it, and the auto limits as shaded bands.
 * In edit mode its points can be dragged, added with a double tap and removed with a long press;
 * each finished gesture is stored at once.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CurveHero(
    curve: BrightnessCurve,
    defaultCurve: BrightnessCurve?,
    edited: Boolean,
    s: BrightnessSettings,
    lux: Float?,
    onCommit: (BrightnessCurve) -> Unit,
    onReset: () -> Unit,
    onRemoved: (BrightnessCurve) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val view = LocalView.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = c.onSurfaceVariant)
    val zoneLabels = ZONES.map { (l, res) -> l to stringResource(res) }
    val max = maxOf(curve.maxNits, defaultCurve?.maxNits ?: 0f)
    val min = minOf(AXIS_MIN_NITS, curve.minNits)

    var editing by remember { mutableStateOf(false) }
    // The points being edited, seeded from the stored curve whenever it changes.
    var points by remember(curve) { mutableStateOf(controlPoints(curve)) }
    var active by remember { mutableIntStateOf(-1) }
    val latestCurve by rememberUpdatedState(curve)

    val target = lux?.let { limited(curve.nitsAt(it), s) }
    val dotX by animateFloatAsState(luxPos(lux ?: 0f), MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "dotX")
    val dotY by animateFloatAsState(nitsPos(target ?: min, min, max),
        MaterialTheme.motionScheme.slowSpatialSpec(), label = "dotY")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "pulse",
    )

    fun commit(next: Pair<FloatArray, FloatArray>) {
        points = next
        onCommit(BrightnessCurve(next.first, next.second))
    }

    Surface(shape = LarasShapes.Hero, color = LarasSurfaces.card,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One number at a time: the point under the finger, else the light right now.
                val shownNits = if (active >= 0) points.second[active] else target
                val shownLux = if (active >= 0) points.first[active] else lux
                Column(Modifier.weight(1f)) {
                    Text(
                        shownNits?.let { stringResource(R.string.pijar_nits, formatNits(it)) } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        color = c.onSurface,
                    )
                    shownLux?.let {
                        Text(stringResource(R.string.pijar_lux, formatLux(it)),
                            style = MaterialTheme.typography.titleSmall, color = c.primary)
                    }
                }
                if (editing && edited) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.pijar_curve_reset)) }
                }
                FilledTonalIconButton(onClick = {
                    editing = !editing
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }) {
                    Icon(
                        if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                        stringResource(if (editing) R.string.pijar_done else R.string.pijar_edit_curve),
                    )
                }
            }
            if (editing) {
                Text(stringResource(R.string.pijar_curve_hint), style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            var plot by remember { mutableStateOf(Size.Zero) }
            val labelH = with(LocalDensity.current) { 18.dp.toPx() }
            val grab = with(LocalDensity.current) { 28.dp.toPx() }

            fun toScreen(l: Float, n: Float) = Offset(luxPos(l) * plot.width,
                (plot.height - labelH) * (1f - nitsPos(n, min, max)))

            fun nearest(at: Offset): Int {
                var best = -1
                var bestD = grab
                for (i in points.first.indices) {
                    val p = toScreen(points.first[i], points.second[i])
                    val d = hypot(p.x - at.x, p.y - at.y)
                    if (d < bestD) {
                        best = i
                        bestD = d
                    }
                }
                return best
            }

            val gestures = if (!editing) Modifier else Modifier
                .pointerInput(curve, min, max) {
                    var tick = -1
                    detectDragGestures(
                        onDragStart = { at ->
                            active = nearest(at)
                            if (active >= 0) view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
                        },
                        onDragEnd = {
                            if (active >= 0) {
                                view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                commit(points)
                            }
                            active = -1
                        },
                        onDragCancel = {
                            points = controlPoints(latestCurve)
                            active = -1
                        },
                        onDrag = { change, _ ->
                            val i = active
                            if (i < 0) return@detectDragGestures
                            change.consume()
                            val (lx, nt) = points.first.copyOf() to points.second.copyOf()
                            val h = plot.height - labelH
                            val pos = (change.position.x / plot.width).coerceIn(0f, 1f)
                            if (i > 0) {
                                val lo = luxPos(lx[i - 1]) + MIN_GAP
                                val hi = if (i < lx.lastIndex) luxPos(lx[i + 1]) - MIN_GAP else 1f
                                val l = roundLux(luxAtPos(pos.coerceIn(lo, maxOf(lo, hi))))
                                val floorLux = lx[i - 1] + 1f
                                val ceilLux = if (i < lx.lastIndex) lx[i + 1] - 1f else LUX_MAX
                                if (floorLux <= ceilLux) lx[i] = l.coerceIn(floorLux, ceilLux)
                            }
                            val y = (1f - change.position.y / h).coerceIn(0f, 1f)
                            val nLo = if (i > 0) nt[i - 1] else min
                            val nHi = if (i < nt.lastIndex) nt[i + 1] else max
                            nt[i] = roundNits(nitsAtPos(y, min, max)).coerceIn(nLo, nHi)
                            val now = floor(nitsPos(nt[i], min, max) * TICKS_PER_AXIS).toInt()
                            if (now != tick) {
                                if (tick >= 0) view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                                tick = now
                            }
                            points = lx to nt
                        },
                    )
                }
                .pointerInput(curve, min, max) {
                    detectTapGestures(
                        onDoubleTap = { at ->
                            val (lx, nt) = points
                            if (lx.size >= MAX_POINTS) {
                                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                                return@detectTapGestures
                            }
                            val l = roundLux(luxAtPos((at.x / plot.width).coerceIn(0f, 1f)))
                            val at0 = lx.indexOfFirst { it >= l }.let { if (it < 0) lx.size else it }
                            if (at0 == 0 || (at0 < lx.size && lx[at0] == l) || lx[at0 - 1] == l) return@detectTapGestures
                            val n = BrightnessCurve(lx, nt).nitsAt(l)
                            val nl = (lx.take(at0) + l + lx.drop(at0)).toFloatArray()
                            val nn = (nt.take(at0) + n + nt.drop(at0)).toFloatArray()
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            commit(nl to nn)
                        },
                        onLongPress = { at ->
                            val i = nearest(at)
                            val (lx, nt) = points
                            if (i <= 0 || lx.size <= 2) return@detectTapGestures
                            val before = BrightnessCurve(lx.copyOf(), nt.copyOf())
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            commit(lx.filterIndexed { k, _ -> k != i }.toFloatArray() to
                                nt.filterIndexed { k, _ -> k != i }.toFloatArray())
                            onRemoved(before)
                        },
                    )
                }

            Canvas(
                Modifier.fillMaxWidth().height(if (editing) 260.dp else 200.dp).padding(top = 12.dp)
                    .then(gestures),
            ) {
                plot = size
                val w = size.width
                val h = size.height - labelH
                fun x(l: Float) = luxPos(l) * w
                fun y(n: Float) = h - nitsPos(n, min, max) * h
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

                s.ceilingNits?.let { ceil ->
                    drawRect(c.primary.copy(alpha = 0.08f), Offset.Zero, Size(w, y(ceil)))
                    drawLine(c.primary.copy(alpha = 0.5f), Offset(0f, y(ceil)), Offset(w, y(ceil)),
                        1.5.dp.toPx(), pathEffect = dash)
                }
                s.floorNits?.let { floor ->
                    drawRect(c.primary.copy(alpha = 0.08f), Offset(0f, y(floor)), Size(w, h - y(floor)))
                    drawLine(c.primary.copy(alpha = 0.5f), Offset(0f, y(floor)), Offset(w, y(floor)),
                        1.5.dp.toPx(), pathEffect = dash)
                }
                for ((l, _) in zoneLabels) {
                    drawLine(c.outlineVariant.copy(alpha = 0.6f), Offset(x(l), 0f), Offset(x(l), h), 1.dp.toPx())
                }

                val shown = if (editing) BrightnessCurve(points.first, points.second) else curve
                val samples = 96
                fun path(of: BrightnessCurve, limit: Boolean) = Path().apply {
                    for (i in 0..samples) {
                        val p = i / samples.toFloat()
                        val n = of.nitsAt(luxAtPos(p)).let { if (limit) limited(it, s) else it }
                        if (i == 0) moveTo(p * w, y(n)) else lineTo(p * w, y(n))
                    }
                }
                val thin = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash)
                if (editing && edited && defaultCurve != null) {
                    drawPath(path(defaultCurve, false), c.outline, style = thin)
                }
                if (!editing && (s.floorNits != null || s.ceilingNits != null)) {
                    drawPath(path(shown, false), c.outlineVariant, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                drawPath(path(shown, !editing), c.primary,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                if (editing) {
                    for (i in points.first.indices) {
                        val p = Offset(x(points.first[i]), y(points.second[i]))
                        val r = if (i == active) 11.dp.toPx() else 7.dp.toPx()
                        drawCircle(c.surfaceBright, r + 3.dp.toPx(), p)
                        drawCircle(if (i == active) c.tertiary else c.primary, r, p)
                    }
                } else if (lux != null) {
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
