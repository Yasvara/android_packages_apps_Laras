/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.glyph

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.Pref

/**
 * A superuser guard's icon, drawn between its two states: [open] is 0 while the guard holds
 * and 1 once it is relaxed. Each picture shows what relaxing does: the seal cracks for the
 * signature check, the arrow drops for downgrade, the padlock opens for secure windows.
 */
@Composable
fun GuardGlyph(pref: Pref<*>, open: () -> Float, color: Color, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        translate((size.width - unit * 24f) / 2f, (size.height - unit * 24f) / 2f) {
            scale(unit, pivot = Offset.Zero) {
                drawGuard(pref, open().coerceIn(0f, 1f), color, accent)
            }
        }
    }
}

private val Line = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.drawGuard(pref: Pref<*>, t: Float, color: Color, accent: Color) = when (pref) {
    Prefs.suDisableSigVerify -> seal(t, color, accent)
    Prefs.suAllowReplaceSystem -> replace(t, color)
    Prefs.suAllowDowngrade -> downgrade(t, color)
    Prefs.suBypassMinSdk -> steps(t, color)
    Prefs.suAllowTestOnly -> flask(t, color)
    Prefs.suDisablePkgVerify -> scan(t, color)
    Prefs.suNoStorageRestrict -> storage(t, color)
    Prefs.suSecureWindows -> padlockPhone(t, color)
    Prefs.suHideCaptureStatus -> eye(t, color)
    Prefs.suSpoofEncrypted -> maskedLock(t, color)
    else -> drawCircle(color, 8f, Offset(12f, 12f), style = Line)
}

/** Draws the first [t] of [path]. */
private fun DrawScope.partial(path: Path, t: Float, color: Color) {
    if (t <= 0f) return
    val measure = PathMeasure().apply { setPath(path, false) }
    val part = Path()
    measure.getSegment(0f, measure.length * t, part, true)
    drawPath(part, color, style = Line)
}

/** A wax seal and its ribbon; relaxed, a crack runs through it. */
private fun DrawScope.seal(t: Float, color: Color, accent: Color) {
    drawCircle(color, 6f, Offset(12f, 9f), style = Line)
    drawPath(Path().apply { moveTo(8.5f, 14f); lineTo(7f, 21f); lineTo(12f, 18.5f); lineTo(17f, 21f); lineTo(15.5f, 14f) }, color, style = Line)
    partial(Path().apply { moveTo(10f, 4.5f); lineTo(12.5f, 8f); lineTo(10.5f, 9.5f); lineTo(13f, 13.5f) }, t, accent)
}

/** An app box; relaxed, a second one slides in over it, as an update replaces it. */
private fun DrawScope.replace(t: Float, color: Color) {
    drawRoundRect(color, Offset(4f, 8f), Size(11f, 12f), CornerRadius(2.5f), style = Line)
    val shift = 5f * (1f - t)
    drawRoundRect(color.copy(alpha = t), Offset(9f + shift, 4f - shift * 0.4f), Size(11f, 12f), CornerRadius(2.5f), style = Line)
}

/** Version lines with an arrow that drops below them when downgrades are allowed. */
private fun DrawScope.downgrade(t: Float, color: Color) {
    drawLine(color, Offset(5f, 5f), Offset(19f, 5f), 2f, StrokeCap.Round)
    drawLine(color, Offset(5f, 9.5f), Offset(14f, 9.5f), 2f, StrokeCap.Round)
    translate(top = -3f + 6f * t) {
        drawLine(color, Offset(12f, 12f), Offset(12f, 19f), 2f, StrokeCap.Round)
        drawPath(Path().apply { moveTo(8.5f, 15.5f); lineTo(12f, 19f); lineTo(15.5f, 15.5f) }, color, style = Line)
    }
}

/** SDK levels as steps over a floor; relaxed, the floor drops away. */
private fun DrawScope.steps(t: Float, color: Color) {
    drawPath(
        Path().apply { moveTo(3f, 17f); lineTo(7f, 17f); lineTo(7f, 13f); lineTo(11f, 13f); lineTo(11f, 9f); lineTo(15f, 9f); lineTo(15f, 5f); lineTo(21f, 5f) },
        color, style = Line,
    )
    drawLine(color.copy(alpha = 1f - 0.6f * t), Offset(3f, 20f + 2f * t), Offset(21f, 20f + 2f * t), 2f, StrokeCap.Round)
}

/** A test flask; relaxed, a check appears in it. */
private fun DrawScope.flask(t: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(9f, 3f); lineTo(15f, 3f); moveTo(10f, 3f); lineTo(10f, 9f); lineTo(5f, 18f)
            quadraticTo(4f, 21f, 7f, 21f); lineTo(17f, 21f); quadraticTo(20f, 21f, 19f, 18f); lineTo(14f, 9f); lineTo(14f, 3f)
        },
        color, style = Line,
    )
    partial(Path().apply { moveTo(9f, 16.5f); lineTo(11f, 18.5f); lineTo(15f, 14.5f) }, t, color)
}

/** A shield under a lens; relaxed, the lens slides away and the scan is skipped. */
private fun DrawScope.scan(t: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(12f, 3f); lineTo(20f, 6f); lineTo(20f, 12f); cubicTo(20f, 16.5f, 16.6f, 20f, 12f, 21f)
            cubicTo(7.4f, 20f, 4f, 16.5f, 4f, 12f); lineTo(4f, 6f); close()
        },
        color, style = Line,
    )
    translate(left = 6f * t) {
        val lens = color.copy(alpha = 1f - t)
        drawCircle(lens, 3f, Offset(11f, 11f), style = Line)
        drawLine(lens, Offset(13.2f, 13.2f), Offset(15.5f, 15.5f), 2f, StrokeCap.Round)
    }
}

/** A drive with its reserved band; relaxed, the band fades out. */
private fun DrawScope.storage(t: Float, color: Color) {
    drawOval(color, Offset(5f, 3.2f), Size(14f, 5.6f), style = Line)
    drawPath(Path().apply { moveTo(5f, 6f); lineTo(5f, 18f); cubicTo(5f, 21.7f, 19f, 21.7f, 19f, 18f); lineTo(19f, 6f) }, color, style = Line)
    drawArc(color.copy(alpha = 1f - t), 0f, 180f, false, Offset(5f, 10.2f), Size(14f, 5.6f), style = Line)
}

/** A phone with a padlock whose shackle swings open. */
private fun DrawScope.padlockPhone(t: Float, color: Color) {
    drawRoundRect(color, Offset(6f, 2.5f), Size(12f, 19f), CornerRadius(2.5f), style = Line)
    drawRoundRect(color, Offset(9.5f, 12f), Size(5f, 4f), CornerRadius(1f), style = Line)
    rotate(28f * t, pivot = Offset(13.5f, 11f)) {
        drawArc(color, 180f, 180f, false, Offset(10.5f, 9f), Size(3f, 3f), style = Line)
    }
}

/** An eye that closes, as apps stop being told about capture: the lid comes down, lashes out. */
private fun DrawScope.eye(t: Float, color: Color) {
    val open = 1f - t
    if (open > 0f) {
        scale(1f, open, pivot = Offset(12f, 12f)) {
            drawPath(
                Path().apply {
                    moveTo(2.5f, 12f); quadraticTo(12f, 0f, 21.5f, 12f); quadraticTo(12f, 24f, 2.5f, 12f); close()
                },
                color.copy(alpha = open), style = Line,
            )
            drawCircle(color.copy(alpha = open), 2.8f, Offset(12f, 12f), style = Line)
        }
    }
    if (t > 0f) {
        val shut = color.copy(alpha = t)
        drawPath(Path().apply { moveTo(2.5f, 11f); quadraticTo(12f, 19f, 21.5f, 11f) }, shut, style = Line)
        listOf(6f to 14.5f, 12f to 16f, 18f to 14.5f).forEachIndexed { i, (x, y) ->
            val dx = (i - 1) * 1.2f
            drawLine(shut, Offset(x, y), Offset(x + dx, y + 2.8f * t), 2f, StrokeCap.Round)
        }
    }
}

/** A padlock that, relaxed, wears a disguise: the device says it is encrypted. */
private fun DrawScope.maskedLock(t: Float, color: Color) {
    drawRoundRect(color, Offset(5f, 10f), Size(14f, 11f), CornerRadius(2f), style = Line)
    drawArc(color, 180f, 180f, false, Offset(8f, 4f), Size(8f, 12f), style = Line)
    // The disguise: a band drawn across the body.
    if (t > 0f) drawRoundRect(color, Offset(7f, 13.5f), Size(10f * t, 3f), CornerRadius(1.5f))
}
