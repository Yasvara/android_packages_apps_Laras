/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hero card background kind — each major Laras screen gets a thematic animated vector watermark
 * behind its hero card paired with the Laras mascot silhouette.
 */
enum class HeroBackdropKind {
    NADA_AUDIO,
    SARING_SHIELD,
    LELAP_SLEEP,
    PADAT_STORAGE,
    INTEGRITY_KEYBOX,
    UPDATES_SYSTEM,
    KAIT_MODULES,
}

/**
 * Pre-allocated geometry cache for the Laras mascot so zero [Path] or [Stroke] objects
 * are allocated during 120Hz frame draws.
 */
class CachedMascotGeometry(
    val center: Offset,
    val radius: Float,
    val headPath: Path,
    val mouthPath: Path,
    val headStroke: Stroke,
    val mouthStroke: Stroke,
    val headphoneStroke: Stroke,
)

fun buildCachedMascotGeometry(center: Offset, radius: Float): CachedMascotGeometry {
    val strokeW = radius * 0.13f
    val headPath = Path().apply {
        moveTo(center.x - radius * 0.72f, center.y - radius * 0.15f)
        lineTo(center.x - radius * 0.82f, center.y - radius * 0.88f)
        lineTo(center.x - radius * 0.28f, center.y - radius * 0.58f)
        quadraticTo(center.x, center.y - radius * 0.68f, center.x + radius * 0.28f, center.y - radius * 0.58f)
        lineTo(center.x + radius * 0.82f, center.y - radius * 0.88f)
        lineTo(center.x + radius * 0.72f, center.y - radius * 0.15f)
        quadraticTo(center.x + radius * 0.88f, center.y + radius * 0.62f, center.x, center.y + radius * 0.74f)
        quadraticTo(center.x - radius * 0.88f, center.y + radius * 0.62f, center.x - radius * 0.72f, center.y - radius * 0.15f)
        close()
    }
    val mouthPath = Path().apply {
        moveTo(center.x - radius * 0.16f, center.y + radius * 0.30f)
        quadraticTo(center.x - radius * 0.08f, center.y + radius * 0.40f, center.x, center.y + radius * 0.31f)
        quadraticTo(center.x + radius * 0.08f, center.y + radius * 0.40f, center.x + radius * 0.16f, center.y + radius * 0.30f)
    }
    return CachedMascotGeometry(
        center = center,
        radius = radius,
        headPath = headPath,
        mouthPath = mouthPath,
        headStroke = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
        mouthStroke = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round),
        headphoneStroke = Stroke(width = strokeW * 0.95f, cap = StrokeCap.Round),
    )
}

fun DrawScope.drawCachedMascot(
    geom: CachedMascotGeometry,
    color: Color,
    alpha: Float = 1f,
    withHeadphones: Boolean = false,
) {
    val c = geom.center
    val r = geom.radius
    val paintColor = color.copy(alpha = alpha)

    drawPath(geom.headPath, color = color.copy(alpha = alpha * 0.18f))
    drawPath(geom.headPath, color = paintColor, style = geom.headStroke)

    drawCircle(
        color = paintColor,
        radius = r * 0.13f,
        center = Offset(c.x - r * 0.28f, c.y + r * 0.04f),
    )
    drawCircle(
        color = paintColor,
        radius = r * 0.13f,
        center = Offset(c.x + r * 0.28f, c.y + r * 0.04f),
    )
    drawPath(geom.mouthPath, color = paintColor, style = geom.mouthStroke)

    if (withHeadphones) {
        drawArc(
            color = paintColor,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = false,
            topLeft = Offset(c.x - r * 0.98f, c.y - r * 0.96f),
            size = Size(r * 1.96f, r * 1.96f),
            style = geom.headphoneStroke,
        )
        drawRoundRect(
            color = paintColor,
            topLeft = Offset(c.x - r * 1.06f, c.y - r * 0.16f),
            size = Size(r * 0.22f, r * 0.48f),
            cornerRadius = CornerRadius(r * 0.1f),
        )
        drawRoundRect(
            color = paintColor,
            topLeft = Offset(c.x + r * 0.84f, c.y - r * 0.16f),
            size = Size(r * 0.22f, r * 0.48f),
            cornerRadius = CornerRadius(r * 0.1f),
        )
    }
}

/**
 * Standalone Laras mascot badge icon using [drawWithCache] so geometry is built only once.
 */
@Composable
fun LarasMascotIcon(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    withHeadphones: Boolean = false,
) {
    Spacer(
        modifier.drawWithCache {
            val r = minOf(size.width, size.height) * 0.42f
            val geom = buildCachedMascotGeometry(
                center = Offset(size.width / 2f, size.height / 2f + r * 0.06f),
                radius = r,
            )
            onDrawBehind {
                drawCachedMascot(
                    geom = geom,
                    color = color,
                    alpha = 1f,
                    withHeadphones = withHeadphones,
                )
            }
        },
    )
}

/**
 * Ultra-lightweight animated Hero Card background watermark:
 * - Defers [phaseState] reads strictly inside `onDrawBehind` so Compose skips Composition and Layout phases (0 recompositions/sec);
 * - Pre-allocates [CachedMascotGeometry], [Path], and [Stroke] instances inside `drawWithCache` and reuses a single [wavePath] via `rewind()` (0 heap allocations/frame);
 * - Respects system Battery Saver / `ValueAnimator.areAnimatorsEnabled()`.
 */
@Composable
fun LarasHeroBackdrop(
    kind: HeroBackdropKind,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    alpha: Float = 0.20f,
    animated: Boolean = true,
) {
    val animEnabled = animated && remember { ValueAnimator.areAnimatorsEnabled() }
    val phaseState = if (animEnabled) {
        val infinite = rememberInfiniteTransition(label = "heroBackdrop")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "wavePhase",
        )
    } else {
        null
    }

    Spacer(
        modifier.drawWithCache {
            val w = size.width
            val h = size.height
            val mascotCenter = Offset(w - h * 0.52f, h * 0.50f)
            val mascotRadius = (minOf(w * 0.26f, h * 0.36f)).coerceAtLeast(24f)
            val mascotGeom = buildCachedMascotGeometry(mascotCenter, mascotRadius)

            // Reusable path and pre-built strokes for zero GC pressure during animation
            val reusableWavePath = Path()
            val waveStrokes = arrayOf(
                Stroke(width = 3.0f, cap = StrokeCap.Round),
                Stroke(width = 2.4f, cap = StrokeCap.Round),
                Stroke(width = 1.8f, cap = StrokeCap.Round),
            )
            val ringStroke = Stroke(width = 2.2f)
            val crescentStroke = Stroke(width = 3.0f, cap = StrokeCap.Round)
            val boxStroke = Stroke(width = 2.0f)
            val stepPx = 10.dp.toPx().coerceAtLeast(12f)

            onDrawBehind {
                // Reading phaseState?.value ONLY inside onDrawBehind avoids all recompositions!
                val t = phaseState?.value ?: 0f

                drawCachedMascot(
                    geom = mascotGeom,
                    color = color,
                    alpha = alpha * 0.85f,
                    withHeadphones = kind == HeroBackdropKind.NADA_AUDIO,
                )

                when (kind) {
                    HeroBackdropKind.NADA_AUDIO -> {
                        val startX = w * 0.34f
                        val spanX = (w - startX).coerceAtLeast(1f)
                        for (waveIdx in 0..2) {
                            reusableWavePath.rewind()
                            val amp = h * (0.11f + waveIdx * 0.045f)
                            val freq = (1.5f + waveIdx * 0.45f) * PI.toFloat()
                            val phaseShift = t + waveIdx * 1.1f
                            var x = startX
                            var first = true
                            while (x <= w) {
                                val norm = (x - startX) / spanX
                                val y = h * 0.54f + sin(norm * freq + phaseShift) * amp * norm
                                if (first) reusableWavePath.moveTo(x, y) else reusableWavePath.lineTo(x, y)
                                first = false
                                x += stepPx
                            }
                            drawPath(
                                path = reusableWavePath,
                                color = color.copy(alpha = alpha * (0.88f - waveIdx * 0.22f)),
                                style = waveStrokes[waveIdx],
                            )
                        }
                    }
                    HeroBackdropKind.SARING_SHIELD -> {
                        val breathe = 1f + 0.04f * sin(t)
                        for (ring in 1..3) {
                            val r = mascotRadius * (1.15f + ring * 0.36f) * breathe
                            drawCircle(
                                color = color.copy(alpha = alpha * (0.72f - ring * 0.17f)),
                                radius = r,
                                center = mascotCenter,
                                style = ringStroke,
                            )
                        }
                        for (i in 0..3) {
                            val angle = i * 1.57f + t * 0.25f
                            val dist = mascotRadius * (1.35f + (i and 1) * 0.38f)
                            drawCircle(
                                color = color.copy(alpha = alpha * 0.7f),
                                radius = 4f,
                                center = Offset(mascotCenter.x + cos(angle) * dist, mascotCenter.y + sin(angle) * dist),
                            )
                        }
                    }
                    HeroBackdropKind.LELAP_SLEEP -> {
                        drawArc(
                            color = color.copy(alpha = alpha * 0.7f),
                            startAngle = -55f,
                            sweepAngle = 210f,
                            useCenter = false,
                            topLeft = Offset(mascotCenter.x - mascotRadius * 1.45f, mascotCenter.y - mascotRadius * 1.45f),
                            size = Size(mascotRadius * 2.9f, mascotRadius * 2.9f),
                            style = crescentStroke,
                        )
                        for (idx in 0..2) {
                            val relX = when (idx) { 0 -> -1.55f; 1 -> -1.15f; else -> 1.18f }
                            val relY = when (idx) { 0 -> -0.75f; 1 -> 0.65f; else -> -0.82f }
                            val pulse = 0.75f + 0.25f * sin(t + idx * 1.8f)
                            drawCircle(
                                color = color.copy(alpha = alpha * 0.85f * pulse),
                                radius = 4.2f * pulse,
                                center = Offset(mascotCenter.x + relX * mascotRadius, mascotCenter.y + relY * mascotRadius),
                            )
                        }
                    }
                    HeroBackdropKind.PADAT_STORAGE -> {
                        val pulse = 1f + 0.03f * sin(t)
                        for (i in 0..2) {
                            val boxSize = (mascotRadius * 2.3f - i * 22f) * pulse
                            if (boxSize > 12f) {
                                drawRoundRect(
                                    color = color.copy(alpha = alpha * (0.65f - i * 0.15f)),
                                    topLeft = Offset(mascotCenter.x - boxSize / 2f, mascotCenter.y - boxSize / 2f),
                                    size = Size(boxSize, boxSize),
                                    cornerRadius = CornerRadius(14f),
                                    style = boxStroke,
                                )
                            }
                        }
                    }
                    HeroBackdropKind.INTEGRITY_KEYBOX,
                    HeroBackdropKind.UPDATES_SYSTEM,
                    HeroBackdropKind.KAIT_MODULES -> {
                        val breathe = 1f + 0.035f * sin(t)
                        for (ring in 1..2) {
                            drawCircle(
                                color = color.copy(alpha = alpha * (0.68f - ring * 0.2f)),
                                radius = mascotRadius * (1.24f + ring * 0.40f) * breathe,
                                center = mascotCenter,
                                style = ringStroke,
                            )
                        }
                    }
                }
            }
        },
    )
}
