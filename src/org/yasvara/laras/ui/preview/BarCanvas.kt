/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.yasvara.laras.ui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.view.RoundedCorner
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.ui.components.icon

/** A session with a SystemUI bar renderer: the surface it draws into, and where the parts are. */
interface BarSession {
    /** False when this build of SystemUI cannot render the bar; the canvas then says so. */
    val available: Boolean

    /** Where each part is drawn, by SystemUI's name for it, in the bar's own pixels. */
    val parts: Map<String, android.graphics.Rect>

    /** Embeds the bar into [view]; called once the view's surface exists. */
    fun attach(view: SurfaceView)
}

/** A part of a bar that can be tapped: SystemUI's name for it and the element it edits. */
class BarPart(val key: String, val element: Element)

/** Wallpaper shown under the bar, so a tap zone reaches below a bar thinner than a finger. */
private val STAGE_BELOW_BAR = 28.dp
private val MIN_TARGET = 48.dp
private val MIN_CORNER = 12.dp
private val CHIP_HEIGHT = 32.dp
private val CHIP_GAP = 8.dp
private val TAIL = 12.dp

/** A chip's width besides its label: padding, icon and the space between them. */
private val CHIP_CHROME = 12.dp + 18.dp + 6.dp + 16.dp

/**
 * A bar SystemUI renders live, shown the way it is used: at nearly its real size across the
 * width, over the strip of wallpaper it sits on and inside the display's own corners, instead of
 * a hairline across a shrunken phone. Under it, a callout chip names each part and points at it.
 * A part is picked by tapping it on the bar or its chip; both are at least 48 dp. Tapping the
 * bar anywhere else reports null.
 *
 * [barHeight] is the bar's height in display pixels. The bar sits at the top of the stage.
 */
@Composable
fun BarStage(
    session: BarSession,
    barHeight: Int,
    device: DeviceState,
    parts: List<BarPart>,
    focus: Element?,
    onSelect: (Element?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        val scale = width / device.displaySize.width
        val stageHeight = with(density) { (barHeight * scale).toDp() } + STAGE_BELOW_BAR
        // Each reported part, in stage pixels, in the order the parts are listed.
        val placed = parts.mapNotNull { part ->
            session.parts[part.key]?.let { r ->
                part to Rect(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale)
            }
        }
        Column {
            Stage(session, device, scale, stageHeight, placed, focus, onSelect)
            Callouts(placed, width, focus, onSelect)
        }
    }
}

@Composable
private fun Stage(
    session: BarSession,
    device: DeviceState,
    scale: Float,
    height: Dp,
    parts: List<Pair<BarPart, Rect>>,
    focus: Element?,
    onSelect: (Element?) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // The display's own corner, so the bar is clipped where the real screen clips it.
    val corner = max(
        remember(context) { displayCornerRadius(context) } * scale,
        with(density) { MIN_CORNER.toPx() },
    )
    val heightPx = with(density) { height.toPx() }
    Box(
        Modifier.fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(with(density) { corner.toDp() }))
            .clickable(interactionSource = null, indication = null) { onSelect(null) }
    ) {
        if (session.available) {
            // The surface keeps the display's pixel size, which SystemUI lays the bar out for,
            // and is scaled as a whole; its height is the stage's, in display pixels.
            val rows = (heightPx / scale).roundToInt()
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        clipChildren = false
                        addView(
                            BarSurfaceView(ctx, session),
                            FrameLayout.LayoutParams(device.displaySize.width, rows),
                        )
                    }
                },
                update = { frame ->
                    val surface = frame.getChildAt(0) as BarSurfaceView
                    if (surface.layoutParams.height != rows) {
                        surface.layoutParams = FrameLayout.LayoutParams(device.displaySize.width, rows)
                    }
                    surface.pivotX = 0f
                    surface.pivotY = 0f
                    surface.scaleX = scale
                    surface.scaleY = scale
                    // The corner is applied before the surface is scaled.
                    surface.setCornerRadius(corner / scale)
                    surface.displayHeight = device.displaySize.height
                    surface.wallpaper = device.homeWallpaper
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            device.homeWallpaper?.let {
                Image(
                    it.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
                )
            }
            Text(
                stringResource(R.string.status_bar_preview_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
            )
        }
        PartTargets(parts, heightPx, focus, onSelect)
    }
}

/**
 * The tap zones over the bar, and the marks that answer them: the picked part is outlined while
 * the rest of the bar steps back under a scrim (as on the lock screen canvas), and a pressed part
 * lights up under the finger.
 */
@Composable
private fun PartTargets(
    parts: List<Pair<BarPart, Rect>>,
    stageHeight: Float,
    focus: Element?,
    onSelect: (Element?) -> Unit,
) {
    val density = LocalDensity.current
    val motion = MaterialTheme.motionScheme
    val pad = with(density) { 4.dp.toPx() }
    val radius = with(density) { 8.dp.toPx() }
    val stroke = with(density) { 2.dp.toPx() }
    val focused = parts.firstOrNull { it.first.element == focus }?.second?.inflate(pad)
    val shown by animateFloatAsState(if (focused != null) 1f else 0f, motion.defaultEffectsSpec(), label = "shown")
    // The ring moves from part to part, appears in place, and stays put while it fades out.
    val ring = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val ringSpec = motion.defaultSpatialSpec<Rect>()
    LaunchedEffect(focused) {
        if (focused == null) return@LaunchedEffect
        if (shown == 0f) ring.snapTo(focused) else ring.animateTo(focused, ringSpec)
    }
    val interactions = remember(parts.size) { List(parts.size) { MutableInteractionSource() } }
    val pressedIndex = interactions.map { it.collectIsPressedAsState().value }.indexOfFirst { it }
    val press by animateFloatAsState(if (pressedIndex >= 0) 1f else 0f, motion.fastEffectsSpec(), label = "press")
    val lastPressed = remember { RectHolder() }
    parts.getOrNull(pressedIndex)?.let { lastPressed.rect = it.second.inflate(pad) }

    Canvas(Modifier.fillMaxSize()) {
        if (shown > 0f) {
            val r = ring.value
            val hole = Path().apply { addRoundRect(RoundRect(r, CornerRadius(radius))) }
            clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.45f * shown)) }
            drawRoundRect(
                Color.White.copy(alpha = 0.95f * shown), r.topLeft, r.size,
                CornerRadius(radius), style = Stroke(stroke),
            )
        }
        if (press > 0f) {
            val r = lastPressed.rect
            drawRoundRect(Color.White.copy(alpha = 0.2f * press), r.topLeft, r.size, CornerRadius(radius))
        }
    }

    val zones = zonesFor(parts.map { it.second }, with(density) { MIN_TARGET.toPx() })
    parts.forEachIndexed { i, (part, _) ->
        val zone = zones[i]
        val label = stringResource(part.element.title)
        val action = stringResource(R.string.bar_part_action, label)
        Box(
            Modifier.offset { IntOffset(zone.first.roundToInt(), 0) }
                .width(with(density) { (zone.second - zone.first).toDp() })
                .height(with(density) { stageHeight.toDp() })
                .semantics {
                    contentDescription = label
                    selected = part.element == focus
                }
                .clickable(interactions[i], indication = null, onClickLabel = action, role = Role.Button) {
                    onSelect(part.element)
                }
        )
    }
}

/** Names each part under the bar, with a line up to it; the chips never overlap. */
@Composable
private fun Callouts(
    parts: List<Pair<BarPart, Rect>>,
    width: Float,
    focus: Element?,
    onSelect: (Element?) -> Unit,
) {
    if (parts.isEmpty()) return
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val labels = parts.map { stringResource(it.first.element.title) }
    val widths = labels.map { label ->
        with(density) { CHIP_CHROME.toPx() } + measurer.measure(label, style).size.width
    }
    val anchors = parts.map { it.second.center.x }
    val lefts = spread(anchors, widths, width, with(density) { CHIP_GAP.toPx() })
    val line = MaterialTheme.colorScheme.outline

    // The chips' touch areas reach past them (48 dp); the visible chip starts where the line ends.
    val touchPad = (MIN_TARGET - CHIP_HEIGHT) / 2
    Box(Modifier.fillMaxWidth().height(TAIL + CHIP_HEIGHT + touchPad)) {
        Canvas(Modifier.fillMaxWidth().height(TAIL)) {
            val stroke = 1.5.dp.toPx()
            val inset = 16.dp.toPx()
            parts.indices.forEach { i ->
                // From under the part down to the chip, with an elbow when the chip was moved.
                val from = anchors[i]
                val to = from.coerceIn(lefts[i] + inset, lefts[i] + widths[i] - inset)
                val mid = size.height / 2
                drawCircle(line, 2.5.dp.toPx(), Offset(from, 0f))
                val path = Path().apply {
                    moveTo(from, 0f)
                    lineTo(from, mid)
                    lineTo(to, mid)
                    lineTo(to, size.height)
                }
                drawPath(path, line, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        parts.forEachIndexed { i, (part, _) ->
            CalloutChip(
                label = labels[i],
                element = part.element,
                selected = part.element == focus,
                onClick = { onSelect(part.element) },
                modifier = Modifier.offset {
                    IntOffset(lefts[i].roundToInt(), (TAIL - touchPad).roundToPx())
                },
            )
        }
    }
}

@Composable
private fun CalloutChip(
    label: String,
    element: Element,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainerHighest,
        motion.defaultEffectsSpec(), label = "chip",
    )
    val content by animateColorAsState(
        if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
        motion.defaultEffectsSpec(), label = "chipText",
    )
    Box(modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.TopStart) {
        Row(
            Modifier.height(CHIP_HEIGHT)
                .clip(CircleShape)
                .background(container)
                .selectable(selected, onClick = onClick, role = Role.Tab)
                .padding(start = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(element.icon, null, Modifier.size(18.dp), tint = content)
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/**
 * Tap zones along the bar, as (left, right): each part's span grown to [min], split halfway
 * where neighbours would overlap, so a zone never takes a neighbour's part.
 */
private fun zonesFor(rects: List<Rect>, min: Float): List<Pair<Float, Float>> {
    val spans = rects.map { r ->
        val grow = max(0f, (min - r.width) / 2)
        (r.left - grow) to (r.right + grow)
    }.toMutableList()
    val order = rects.indices.sortedBy { rects[it].center.x }
    for (k in 0 until order.size - 1) {
        val a = order[k]
        val b = order[k + 1]
        if (spans[a].second > spans[b].first) {
            val mid = (rects[a].right + rects[b].left) / 2
            spans[a] = spans[a].first to mid
            spans[b] = mid to spans[b].second
        }
    }
    return spans
}

/**
 * Chip left edges: each centred under its anchor, then pushed right until none overlap, then
 * back left until all fit in [width].
 */
private fun spread(anchors: List<Float>, widths: List<Float>, width: Float, gap: Float): List<Float> {
    val order = anchors.indices.sortedBy { anchors[it] }
    val left = FloatArray(anchors.size) { anchors[it] - widths[it] / 2 }
    var edge = 0f
    for (i in order) {
        left[i] = max(left[i], edge)
        edge = left[i] + widths[i] + gap
    }
    edge = width
    for (i in order.reversed()) {
        left[i] = min(left[i], edge - widths[i])
        edge = left[i] - gap
    }
    return left.toList()
}

private class RectHolder(var rect: Rect = Rect.Zero)

/** The radius of the display's top corners in pixels, or 0 when it is square. */
private fun displayCornerRadius(context: Context): Float = runCatching {
    context.display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius?.toFloat()
}.getOrNull() ?: 0f

/**
 * Paints the strip of wallpaper under a bar into its own surface, beneath the bar SystemUI
 * renders into it: a SurfaceView punches through the window, so whatever shows behind the bar
 * has to be in this surface. The view is the display's width and the stage's height, in display
 * pixels, and is scaled as a whole.
 */
internal class BarSurfaceView(
    context: Context,
    private val session: BarSession,
) : SurfaceView(context), SurfaceHolder.Callback {
    var displayHeight = 0
    var wallpaper: Bitmap? = null
        set(value) {
            if (field === value) return
            field = value
            draw()
        }

    init {
        holder.addCallback(this)
    }

    // SystemUI needs the host token, which exists only once the surface does.
    override fun surfaceCreated(holder: SurfaceHolder) = session.attach(this)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = draw()

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    private fun draw() {
        if (!holder.surface.isValid) return
        val canvas = holder.lockHardwareCanvas() ?: return
        try {
            canvas.drawColor(android.graphics.Color.BLACK)
            val bitmap = wallpaper ?: return
            if (displayHeight <= 0) return
            // As the window manager places it: the crop fills the display, centred; this
            // surface shows the display's top rows.
            val w = holder.surfaceFrame.width().toFloat()
            val h = displayHeight.toFloat()
            val s = max(w / bitmap.width, h / bitmap.height)
            val matrix = Matrix().apply {
                setScale(s, s)
                postTranslate((w - bitmap.width * s) / 2f, (h - bitmap.height * s) / 2f)
            }
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}
