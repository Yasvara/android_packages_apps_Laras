/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.samar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A thumb stick. Reports x east and y north, each in [-1, 1], while held; (0, 0) on release.
 * The knob jumps to the touch, as in games, and springs back when let go.
 */
@Composable
fun Joystick(
    onMove: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    description: String,
) {
    val colors = MaterialTheme.colorScheme
    val knob = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val move by rememberUpdatedState(onMove)

    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                val radius = min(this.size.width, this.size.height) / 2f
                val center = Offset(this.size.width / 2f, this.size.height / 2f)
                val travel = radius * 0.62f
                fun report(p: Offset) {
                    var d = p - center
                    val len = sqrt(d.x * d.x + d.y * d.y)
                    if (len > travel) d = d * (travel / len)
                    scope.launch { knob.snapTo(d) }
                    // Small dead zone so a resting thumb does not creep.
                    val nx = d.x / travel
                    val ny = -d.y / travel
                    if (sqrt(nx * nx + ny * ny) < 0.08f) move(0f, 0f) else move(nx, ny)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    report(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.positionChange() != Offset.Zero) report(change.position)
                        change.consume()
                    }
                    move(0f, 0f)
                    scope.launch {
                        knob.animateTo(Offset.Zero, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                    }
                }
            },
    ) {
        val r = min(this.size.width, this.size.height) / 2f
        drawCircle(colors.surfaceContainerHighest, radius = r)
        drawCircle(colors.outlineVariant, radius = r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
        drawCircle(colors.outlineVariant.copy(alpha = 0.5f), radius = r * 0.62f, style = Stroke(1.dp.toPx()))
        val c = center + knob.value
        drawCircle(colors.primary.copy(alpha = 0.22f), radius = r * 0.44f, center = c)
        drawCircle(colors.primary, radius = r * 0.32f, center = c)
    }
}
