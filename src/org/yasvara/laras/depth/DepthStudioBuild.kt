/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.yasvara.laras.R

/*
 * The Studio's view of the depth build (docs/specs/0013 §5, design/laras-depth-build): its
 * progress in place of the tools, S2's tiles over the picture, and the props it found, each one
 * tap away.
 */

/** The build's progress, in place of the tools while it runs. */
@Composable
internal fun BuildProgress(state: DepthBuildService.State, modifier: Modifier = Modifier) {
    val running = state as? DepthBuildService.State.Running
    val waiting = state is DepthBuildService.State.Waiting
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (waiting) R.string.depth_waiting_title else R.string.depth_finding),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                running?.let { eta(it) }?.let {
                    Text(
                        stringResource(R.string.depth_eta, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (running != null) {
                LinearProgressIndicator(
                    progress = { running.step / running.steps.coerceAtLeast(1).toFloat() },
                    Modifier.fillMaxWidth(),
                )
            } else if (!waiting) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!waiting) {
                val stage = running?.stage ?: DepthBuilder.Stage.CHARACTER
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StageRow(stringResource(R.string.depth_stage_character), stage, DepthBuilder.Stage.CHARACTER, null)
                    StageRow(
                        stringResource(R.string.depth_stage_hair_short), stage, DepthBuilder.Stage.HAIR,
                        running?.takeIf { it.stage == DepthBuilder.Stage.HAIR && it.tile > 0 }
                            ?.let { stringResource(R.string.depth_progress_count, it.tile, it.tiles) },
                    )
                    StageRow(stringResource(R.string.depth_stage_props), stage, DepthBuilder.Stage.PARTS, null)
                }
            }
            Text(
                stringResource(if (waiting) R.string.depth_waiting_text else R.string.depth_leave_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Minutes left, from the time each step has taken so far; null until that means something. */
private fun eta(state: DepthBuildService.State.Running): Int? {
    if (state.step < 2 || state.started == 0L) return null
    val perStep = (SystemClock.elapsedRealtime() - state.started) / state.step.toFloat()
    return max(1, ceil(perStep * (state.steps - state.step) / 60_000f).toInt())
}

@Composable
private fun StageRow(label: String, now: DepthBuilder.Stage, row: DepthBuilder.Stage, detail: String?) {
    val done = now > row
    val current = now == row
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outline
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            when {
                done -> Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = primary)
                current -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.5.dp)
                else -> Canvas(Modifier.size(16.dp)) {
                    drawCircle(muted, radius = size.minDimension / 2 - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        Text(
            label,
            Modifier.weight(1f),
            style = if (current) MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            else MaterialTheme.typography.bodyLarge,
            color = if (done || current) MaterialTheme.colorScheme.onSurface else muted,
        )
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * S2's tiles over the picture drawn at [origin], [shown] big: done ones solid, the one at work
 * filled in [primary], those to come dashed.
 */
internal fun DrawScope.drawTiles(state: DepthBuildService.State.Running, origin: Offset, shown: Size, primary: Color) {
    if (state.rects.isEmpty() || state.workWidth <= 0) return
    val kx = shown.width / state.workWidth
    val ky = shown.height / state.workHeight
    val corner = CornerRadius(6.dp.toPx())
    val working = if (state.stage == DepthBuilder.Stage.HAIR) state.tile else if (state.stage > DepthBuilder.Stage.HAIR) Int.MAX_VALUE else 0
    state.rects.forEachIndexed { i, r ->
        val topLeft = Offset(origin.x + r[0] * kx, origin.y + r[1] * ky)
        val size = Size((r[2] - r[0]) * kx, (r[3] - r[1]) * ky)
        when {
            i + 1 < working -> drawRoundRect(Color.White.copy(alpha = 0.7f), topLeft, size, corner, Stroke(1.5.dp.toPx()))
            i + 1 == working -> {
                drawRoundRect(primary.copy(alpha = 0.14f), topLeft, size, corner)
                drawRoundRect(primary, topLeft, size, corner, Stroke(2.dp.toPx()))
            }
            else -> drawRoundRect(
                Color.White.copy(alpha = 0.4f), topLeft, size, corner,
                Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
        }
    }
}

/**
 * "Also in front of the clock": the props the build found, as cut-outs. A kept one carries a
 * check; one left out is grey in a dashed outline. A tap takes it out or brings it back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PropsRow(
    thumbs: List<ImageBitmap>,
    dropped: Map<Int, Boolean>,
    enabled: Boolean,
    onToggle: (index: Int, drop: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.depth_props),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val outline = MaterialTheme.colorScheme.outline
            thumbs.forEachIndexed { i, thumb ->
                val index = i + 1
                val kept = dropped[index] != true
                val label = stringResource(if (kept) R.string.depth_prop_kept else R.string.depth_prop_dropped, index)
                Box(
                    Modifier.size(64.dp)
                        .semantics { contentDescription = label }
                        .toggleable(kept, enabled = enabled, role = Role.Switch) { onToggle(index, !it) },
                ) {
                    Box(
                        Modifier.size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .then(
                                if (kept) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                                else Modifier.drawBehind {
                                    drawRoundRect(
                                        outline, cornerRadius = CornerRadius(18.dp.toPx()),
                                        style = Stroke(
                                            1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                                        ),
                                    )
                                },
                            )
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            thumb, null,
                            Modifier.size(52.dp),
                            contentScale = ContentScale.Fit,
                            alpha = if (kept) 1f else 0.35f,
                            colorFilter = if (kept) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                        )
                    }
                    if (kept) {
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(top = 0.dp).size(20.dp)
                                .clip(MaterialShapes.SoftBurst.toShape())
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Each prop of [build] cut out of [picture], at most [side] px long: the build's alpha inside the
 * prop's cells, so it reads as the object, not a box.
 */
internal fun propThumbs(picture: WallpaperPicture, build: DepthBuild, side: Int): List<Bitmap> {
    val bmp = picture.bitmap
    val alpha = build.coverage()
    val grid = DepthBuild.PARTS
    return (1..build.props).map { n ->
        val b = build.propBounds(n)
        val x0 = b[0] * bmp.width / grid
        val y0 = b[1] * bmp.height / grid
        val x1 = max(x0 + 1, b[2] * bmp.width / grid)
        val y1 = max(y0 + 1, b[3] * bmp.height / grid)
        val k = min(1f, side / max(x1 - x0, y1 - y0).toFloat())
        val w = max(1, ((x1 - x0) * k).roundToInt())
        val h = max(1, ((y1 - y0) * k).roundToInt())
        val crop = Bitmap.createBitmap(bmp, x0, y0, x1 - x0, y1 - y0)
        val small = Bitmap.createScaledBitmap(crop, w, h, true)
        if (crop !== small && crop !== bmp) crop.recycle()
        val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        if (small !== bmp) small.recycle()
        val mask = build.prop(n)
        for (y in 0 until h) for (x in 0 until w) {
            // Where this pixel is in the whole picture, as fractions.
            val fx = (x0 + (x + 0.5f) / k) / bmp.width
            val fy = (y0 + (y + 0.5f) / k) / bmp.height
            val a = Selection.bilinear(alpha, build.width, build.height, fx * build.width - 0.5f, fy * build.height - 0.5f) *
                Selection.bilinear(mask, grid, grid, fx * grid - 0.5f, fy * grid - 0.5f)
            val i = y * w + x
            px[i] = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (px[i] and 0xFFFFFF)
        }
        Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }
}
