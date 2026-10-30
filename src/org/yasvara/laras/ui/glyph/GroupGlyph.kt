/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.glyph

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.ui.theme.EmphasizedEasing

/**
 * An icon whose motion says what its group does: the sun's rays reach out for Display, the
 * battery fills for Performance, the eye blinks for Privacy, and so on.
 *
 * The motion plays once when the icon appears and again whenever [replay] changes, and every
 * drawing settles on a still pose at t = 1, so a finished glyph reads as an ordinary icon.
 */
@Composable
fun GroupGlyph(group: SystemGroup, color: Color, modifier: Modifier = Modifier, replay: Int = 0) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(replay) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 1100, easing = EmphasizedEasing))
    }
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        translate((size.width - unit * 24f) / 2f, (size.height - unit * 24f) / 2f) {
            scale(unit, pivot = Offset.Zero) {
                drawGlyph(group, progress.value, color)
            }
        }
    }
}

/** Draws [group]'s glyph at progress [t] on a 24×24 grid, like a Material icon. */
private fun DrawScope.drawGlyph(group: SystemGroup, t: Float, color: Color) = when (group) {
    SystemGroup.STYLE -> palette(t, color)
    SystemGroup.DISPLAY -> sun(t, color)
    SystemGroup.SOUND -> speaker(t, color)
    SystemGroup.GESTURES -> swipe(t, color)
    SystemGroup.POWER -> battery(t, color)
    SystemGroup.CONNECTIVITY -> waves(t, color)
    SystemGroup.MULTITASKING -> floating(t, color)
    SystemGroup.PRIVACY -> eye(t, color)
    SystemGroup.SUPERUSER -> key(t, color)
    SystemGroup.INTEGRITY -> shield(t, color)
    SystemGroup.APPS -> tiles(t, color)
    SystemGroup.MODULES -> hook(t, color)
    SystemGroup.LAB -> flask(t, color)
    SystemGroup.PENTAS -> edge(t, color)
}

/** The Wi-Fi dot lands and its three arcs open outward one after another. */
private fun DrawScope.waves(t: Float, color: Color) {
    val center = Offset(12f, 19f)
    drawCircle(color, 1.8f, center)
    listOf(5f, 9.5f, 14f).forEachIndexed { i, r ->
        val a = stagger(t, i, 3)
        if (a <= 0f) return@forEachIndexed
        val sweep = 90f * a
        drawArc(
            color, startAngle = 270f - sweep / 2f, sweepAngle = sweep, useCenter = false,
            topLeft = center - Offset(r, r), size = Size(r * 2f, r * 2f), style = Line,
        )
    }
}

/** Pentas's mark: a landscape screen, and the sidebar pill slides in along its edge. */
private fun DrawScope.edge(t: Float, color: Color) {
    drawRoundRect(color, Offset(2.5f, 6f), Size(19f, 12f), CornerRadius(4f), style = Line)
    val s = ((t - 0.2f) / 0.6f).coerceIn(0f, 1f)
    drawLine(color, Offset(5.5f, 9.5f + (1f - s) * 5f), Offset(5.5f, 14.5f), 3f, StrokeCap.Round)
    drawCircle(color, 2.2f * s, Offset(16.5f, 12f))
}

private val Line = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private const val DEG = (PI / 180).toFloat()

/** A small window rises out of the screen and settles in its corner (Layang). */
private fun DrawScope.floating(t: Float, color: Color) {
    drawRoundRect(
        color, topLeft = Offset(3f, 5f), size = androidx.compose.ui.geometry.Size(18f, 14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f), style = Line,
    )
    val rise = (1f - t) * 4f
    drawRoundRect(
        color, topLeft = Offset(11f, 11f - rise),
        size = androidx.compose.ui.geometry.Size(8f, 6f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f),
    )
}

/** Three swatches make one full turn, as if mixing colours. */
private fun DrawScope.palette(t: Float, color: Color) {
    val pulse = 1f + 0.18f * sin(PI.toFloat() * t)
    listOf(1f, 0.7f, 0.45f).forEachIndexed { i, alpha ->
        val angle = (t * 360f + i * 120f - 90f) * DEG
        val c = Offset(12f + 5f * cos(angle), 12f + 5f * sin(angle))
        drawCircle(color.copy(alpha = alpha), radius = 3.6f * pulse, center = c)
    }
}

/** The rays grow out one after another while the sun settles its rotation. */
private fun DrawScope.sun(t: Float, color: Color) {
    val center = Offset(12f, 12f)
    drawCircle(color, radius = 4f, center = center)
    rotate(degrees = (1f - t) * -45f, pivot = center) {
        repeat(8) { i ->
            val grow = stagger(t, i, 8)
            if (grow <= 0f) return@repeat
            val a = i * 45f * DEG
            val dir = Offset(cos(a), sin(a))
            drawLine(color, center + dir * 7f, center + dir * (7f + 2.6f * grow), 2f, StrokeCap.Round)
        }
    }
}

/** The speaker nudges and its waves arrive one by one. */
private fun DrawScope.speaker(t: Float, color: Color) {
    val shake = sin(t * 6f * PI.toFloat()) * (1f - t) * 0.8f
    translate(left = shake) {
        drawPath(
            Path().apply {
                moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4.5f); lineTo(12f, 19.5f)
                lineTo(7f, 15f); lineTo(3f, 15f); close()
            },
            color,
        )
    }
    listOf(4f, 7.5f).forEachIndexed { i, r ->
        val a = stagger(t, i, 2)
        if (a <= 0f) return@forEachIndexed
        drawArc(
            color.copy(alpha = a), startAngle = -50f, sweepAngle = 100f, useCenter = false,
            topLeft = Offset(12f - r, 12f - r), size = Size(2 * r, 2 * r), style = Line,
        )
    }
}

/** A fingertip swipes up the screen and leaves a trail. */
private fun DrawScope.swipe(t: Float, color: Color) {
    drawRoundRect(color, Offset(6f, 2f), Size(12f, 20f), CornerRadius(3f), style = Line)
    val y = 17f - 10f * t
    drawLine(color.copy(alpha = 0.35f), Offset(12f, 17f), Offset(12f, y), 2f, StrokeCap.Round)
    drawCircle(color, radius = 2.2f, center = Offset(12f, y))
}

/** The cell fills up, as if charging. */
private fun DrawScope.battery(t: Float, color: Color) {
    drawRoundRect(color, Offset(10f, 2f), Size(4f, 2.5f), CornerRadius(0.8f))
    drawRoundRect(color, Offset(7f, 4f), Size(10f, 18f), CornerRadius(2.5f), style = Line)
    val level = 0.2f + 0.7f * t
    val h = 14f * level
    drawRoundRect(color, Offset(9f, 20f - h), Size(6f, h), CornerRadius(1f))
}

/** The eye blinks halfway through and glances around. */
private fun DrawScope.eye(t: Float, color: Color) {
    val blink = sin(PI.toFloat() * ((t - 0.3f) / 0.35f).coerceIn(0f, 1f))
    val open = 1f - 0.92f * blink
    val lid = Path().apply {
        moveTo(2f, 12f)
        quadraticTo(12f, 12f - 9f * open, 22f, 12f)
        quadraticTo(12f, 12f + 9f * open, 2f, 12f)
        close()
    }
    drawPath(lid, color, style = Line)
    clipPath(lid) {
        val glance = sin(2f * PI.toFloat() * t) * (1f - t) * 2f
        drawCircle(color, radius = 3.4f, center = Offset(12f + glance, 12f))
    }
}

/** The shield steadies and a check mark is drawn inside it. */
private fun DrawScope.shield(t: Float, color: Color) {
    val s = 0.85f + 0.15f * t
    scale(s, pivot = Offset(12f, 12f)) {
        drawPath(
            Path().apply {
                moveTo(12f, 2.5f); lineTo(19.5f, 5.5f); lineTo(19.5f, 11f)
                cubicTo(19.5f, 15.8f, 16.3f, 19.6f, 12f, 21.5f)
                cubicTo(7.7f, 19.6f, 4.5f, 15.8f, 4.5f, 11f); lineTo(4.5f, 5.5f); close()
            },
            color, style = Line,
        )
        val check = Path().apply { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f) }
        drawPath(trim(check, ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)), color, style = Line)
    }
}

/** Four tiles pop in and the last one turns, like an app being picked up. */
/** A key turns a quarter as it "unlocks", its bit swinging into place. */
private fun DrawScope.key(t: Float, color: Color) {
    val center = Offset(9f, 9f)
    rotate(degrees = (1f - t) * -90f, pivot = center) {
        drawCircle(color, radius = 4.5f, center = center, style = Line)
        drawCircle(color, radius = 1.6f, center = center)
        // Shaft and teeth reach out to the lower right.
        drawLine(color, center + Offset(3.2f, 3.2f), Offset(19f, 19f), 2f, StrokeCap.Round)
        val reach = stagger(t, 1, 2)
        if (reach > 0f) {
            drawLine(color, Offset(16f, 16f), Offset(16f, 16f + 3f * reach), 2f, StrokeCap.Round)
            drawLine(color, Offset(19f, 19f), Offset(19f, 19f + 2.4f * reach), 2f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.tiles(t: Float, color: Color) {
    repeat(4) { i ->
        val pop = stagger(t, i, 4)
        if (pop <= 0f) return@repeat
        val origin = Offset(4f + (i % 2) * 9f, 4f + (i / 2) * 9f)
        val center = origin + Offset(3.5f, 3.5f)
        val turn = if (i == 3) 45f * ((t - 0.6f) / 0.4f).coerceIn(0f, 1f) else 0f
        rotate(turn, pivot = center) {
            scale(pop, pivot = center) {
                drawRoundRect(color, origin, Size(7f, 7f), CornerRadius(2f))
            }
        }
    }
}

/** Bubbles rise out of the liquid. */
private fun DrawScope.flask(t: Float, color: Color) {
    val body = Path().apply {
        moveTo(10f, 3f); lineTo(10f, 9f); lineTo(4.6f, 18.4f)
        cubicTo(3.9f, 19.7f, 4.8f, 21f, 6.2f, 21f); lineTo(17.8f, 21f)
        cubicTo(19.2f, 21f, 20.1f, 19.7f, 19.4f, 18.4f); lineTo(14f, 9f); lineTo(14f, 3f)
    }
    clipPath(Path().apply { addPath(body); close() }) {
        drawRect(color.copy(alpha = 0.4f), Offset(0f, 15f), Size(24f, 7f))
    }
    drawPath(body, color, style = Line)
    drawLine(color, Offset(8.5f, 3f), Offset(15.5f, 3f), 2f, StrokeCap.Round)
    listOf(10.5f to 0f, 13.5f to 0.2f, 12f to 0.4f).forEach { (x, delay) ->
        val p = ((t - delay) / 0.6f).coerceIn(0f, 1f)
        if (p <= 0f || p >= 1f) return@forEach
        drawCircle(color.copy(alpha = 1f - p), radius = 1.1f, center = Offset(x, 18f - 12f * p))
    }
}

/** A hook drawn down its shank and round its bend, then the barb catches. */
private fun DrawScope.hook(t: Float, color: Color) {
    drawCircle(color, radius = 2f, center = Offset(15f, 4f), style = Line)
    val hook = Path().apply {
        moveTo(15f, 6f); lineTo(15f, 15f)
        cubicTo(15f, 22f, 6f, 22f, 6f, 15f)
        lineTo(6f, 12.5f)
    }
    drawPath(trim(hook, (t / 0.8f).coerceIn(0f, 1f)), color, style = Line)
    val barb = ((t - 0.75f) / 0.25f).coerceIn(0f, 1f)
    if (barb > 0f) drawLine(color, Offset(6f, 12.5f), Offset(6f + 2.6f * barb, 12.5f + 2.6f * barb), 2f, StrokeCap.Round)
}

private fun trim(path: Path, fraction: Float): Path {
    val measure = PathMeasure().apply { setPath(path, false) }
    return Path().also { measure.getSegment(0f, measure.length * fraction, it, true) }
}

/** Progress of item [i] of [n] when their animations start one after another. */
private fun stagger(t: Float, i: Int, n: Int): Float {
    val window = 0.5f
    val start = i * (1f - window) / (n - 1).coerceAtLeast(1)
    return ((t - start) / window).coerceIn(0f, 1f)
}
