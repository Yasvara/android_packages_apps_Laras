/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import java.util.IdentityHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The subject selection, kept as a list of operations rather than pixels, like a layer stack:
 * undo drops the last operation, and nothing is lost by adding or taking away.
 *
 * Coordinates are fractions of the picture (0..1). Model masks are stretched squares, as the
 * picture was when encoded, so they map back to the picture by the same fractions.
 *
 * The model sees the picture at a quarter of its working size, so each region it finds is
 * snapped to the picture's own edges ([guide], its brightness on the same grid) with a guided
 * filter. Operations then apply strictly in order, so taking away after adding (or the reverse)
 * does what it says whatever kind each step is. A [SelectionOp.Matte] is taken as is.
 */
class Selection(val width: Int, val height: Int, private val guide: FloatArray? = null) {

    private val ops = ArrayList<SelectionOp>()
    private val undone = ArrayList<SelectionOp>()

    /** Regions and mattes resampled onto the grid (regions edge-snapped), made once per step. */
    private val layers = IdentityHashMap<SelectionOp, FloatArray>()

    /** Builds' alphas on the grid. */
    private val autos = IdentityHashMap<DepthBuild, FloatArray>()

    val canUndo get() = ops.isNotEmpty()
    val canRedo get() = undone.isNotEmpty()
    val isEmpty get() = ops.none { it.add }

    /** The operations in effect, oldest first: what a saved project keeps. */
    val operations: List<SelectionOp> get() = ops.toList()

    fun push(op: SelectionOp) {
        ops += op
        undone.clear()
        forget()
    }

    /** Starts over from a saved project's [operations]. */
    fun restore(operations: List<SelectionOp>) {
        clear()
        ops += operations
    }

    /** Redo keeps its steps' layers; anything else dropped from history frees them. */
    private fun forget() = layers.keys.retainAll((ops + undone).toSet())

    fun undo() {
        if (ops.isNotEmpty()) undone += ops.removeAt(ops.lastIndex)
    }

    fun redo() {
        if (undone.isNotEmpty()) ops += undone.removeAt(undone.lastIndex)
    }

    fun clear() {
        ops.clear()
        undone.clear()
        layers.clear()
        autos.clear()
    }

    /** The selection's coverage, 0..1 per pixel of a [width] x [height] grid. */
    fun render(): FloatArray = render(width, height)

    /**
     * The selection at [w] x [h], for saving at the picture's size: the editing grid's layers are
     * scaled up, but a build's alpha, a matte and the brush are drawn at that size themselves, so
     * the build's fine edges reach the lock screen.
     */
    fun render(w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (op in ops) when (op) {
            is SelectionOp.Region -> combine(out, op.add, scaled(layerOf(op), w, h))
            is SelectionOp.Matte -> combine(
                out, op.add,
                if (w == width && h == height) layerOf(op) else Raster.bilinear(op.coverage, op.width, op.height, w, h),
            )
            is SelectionOp.Auto -> combine(out, true, autoAt(op.build, w, h))
            is SelectionOp.Part -> {
                val gate = scaled(layerOf(op), w, h)
                if (op.add) {
                    val auto = autoAt(op.build, w, h)
                    for (i in out.indices) out[i] = max(out[i], auto[i] * gate[i])
                } else {
                    combine(out, false, gate)
                }
            }
            is SelectionOp.Brush -> applyBrush(out, w, h, op)
        }
        return out
    }

    /** The props of the build this selection starts from, as dropped (true) or kept, by index. */
    fun droppedProps(): Map<Int, Boolean> {
        val state = HashMap<Int, Boolean>()
        for (op in ops) if (op is SelectionOp.Part) state[op.index] = !op.add
        return state
    }

    /** The build's alpha on a [w] x [h] grid; the editing grid's copy is kept. */
    private fun autoAt(build: DepthBuild, w: Int, h: Int): FloatArray =
        if (w == width && h == height) {
            autos.getOrPut(build) { Raster.area(build.coverage(), build.width, build.height, w, h) }
        } else {
            Raster.area(build.coverage(), build.width, build.height, w, h)
        }

    /** A layer of the editing grid on a [w] x [h] grid. */
    private fun scaled(layer: FloatArray, w: Int, h: Int): FloatArray =
        if (w == width && h == height) layer else Raster.bilinear(layer, width, height, w, h)

    private fun layerOf(op: SelectionOp): FloatArray = layers.getOrPut(op) {
        when (op) {
            is SelectionOp.Region -> resample(op.coverage, SegmentationEngine.MASK, SegmentationEngine.MASK)
                .also { if (guide != null) refine(it, guide) }
            is SelectionOp.Matte -> resample(op.coverage, op.width, op.height)
            // A prop's cells, snapped to the picture's edges where it meets the character.
            is SelectionOp.Part -> resample(op.build.prop(op.index), DepthBuild.PARTS, DepthBuild.PARTS)
                .also { if (guide != null) refine(it, guide) }
            is SelectionOp.Auto, is SelectionOp.Brush -> error("No layer for $op")
        }
    }

    private fun combine(out: FloatArray, add: Boolean, layer: FloatArray) {
        for (i in out.indices) out[i] = if (add) max(out[i], layer[i]) else out[i] * (1f - layer[i])
    }

    /**
     * Guided filter (He et al.): fits the coverage, window by window, as a linear function of
     * the picture's brightness, so its edges follow the picture's; then a steep curve around
     * one half turns the soft result into a clean edge a pixel or two wide.
     */
    private fun refine(coverage: FloatArray, image: FloatArray) {
        val r = maxOf(2, width / GUIDE_WINDOW_DIVISOR)
        val n = coverage.size
        val meanI = boxBlur(image, r)
        val meanP = boxBlur(coverage, r)
        val ip = boxBlur(FloatArray(n) { image[it] * coverage[it] }, r)
        val ii = boxBlur(FloatArray(n) { image[it] * image[it] }, r)
        val a = FloatArray(n)
        val b = FloatArray(n)
        for (i in 0 until n) {
            val variance = ii[i] - meanI[i] * meanI[i]
            a[i] = (ip[i] - meanI[i] * meanP[i]) / (variance + GUIDE_EPSILON)
            b[i] = meanP[i] - a[i] * meanI[i]
        }
        val meanA = boxBlur(a, r)
        val meanB = boxBlur(b, r)
        for (i in 0 until n) {
            val q = meanA[i] * image[i] + meanB[i]
            coverage[i] = ((q - 0.5f) * EDGE_STEEPNESS + 0.5f).coerceIn(0f, 1f)
        }
    }

    /** The mean over a (2[r]+1)² window, edges clamped, by running sums across then down. */
    private fun boxBlur(src: FloatArray, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val k = 2 * r + 1
        for (y in 0 until height) {
            val row = y * width
            var sum = 0f
            for (x in -r..r) sum += src[row + x.coerceIn(0, width - 1)]
            for (x in 0 until width) {
                tmp[row + x] = sum / k
                sum += src[row + (x + r + 1).coerceAtMost(width - 1)] - src[row + (x - r).coerceAtLeast(0)]
            }
        }
        for (x in 0 until width) {
            var sum = 0f
            for (y in -r..r) sum += tmp[y.coerceIn(0, height - 1) * width + x]
            for (y in 0 until height) {
                out[y * width + x] = sum / k
                sum += tmp[(y + r + 1).coerceAtMost(height - 1) * width + x] -
                    tmp[(y - r).coerceAtLeast(0) * width + x]
            }
        }
        return out
    }

    /** A [gridWidth] x [gridHeight] grid over the whole picture, sampled onto this grid. */
    private fun resample(grid: FloatArray, gridWidth: Int, gridHeight: Int): FloatArray {
        val out = FloatArray(width * height)
        for (y in 0 until height) {
            val my = (y + 0.5f) / height * gridHeight - 0.5f
            for (x in 0 until width) {
                val mx = (x + 0.5f) / width * gridWidth - 0.5f
                out[y * width + x] = bilinear(grid, gridWidth, gridHeight, mx, my)
            }
        }
        return out
    }

    private fun applyBrush(out: FloatArray, width: Int, height: Int, op: SelectionOp.Brush) {
        val r = op.radius * width
        // Densify the stroke so fast drags leave no gaps.
        val dabs = ArrayList<PointF>()
        for (i in op.points.indices) {
            val p = op.points[i]
            if (i == 0) {
                dabs += p
                continue
            }
            val q = op.points[i - 1]
            val dx = (p.x - q.x) * width
            val dy = (p.y - q.y) * height
            val steps = max(1, (sqrt(dx * dx + dy * dy) / (r * 0.3f)).toInt())
            for (s in 1..steps) {
                val t = s / steps.toFloat()
                dabs += PointF(q.x + (p.x - q.x) * t, q.y + (p.y - q.y) * t)
            }
        }
        for (d in dabs) {
            val cx = d.x * width
            val cy = d.y * height
            val x0 = max(0, (cx - r).toInt())
            val x1 = min(width - 1, (cx + r).toInt())
            val y0 = max(0, (cy - r).toInt())
            val y1 = min(height - 1, (cy + r).toInt())
            for (y in y0..y1) for (x in x0..x1) {
                val dist = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / r
                if (dist >= 1f) continue
                // Hard core, soft rim.
                val v = if (dist < 0.7f) 1f else (1f - dist) / 0.3f
                val i = y * width + x
                out[i] = if (op.add) max(out[i], v) else out[i] * (1f - v)
            }
        }
    }

    companion object {
        /** The guided filter's window is the working width over this, about 9 px at 432. */
        private const val GUIDE_WINDOW_DIVISOR = 100
        private const val GUIDE_EPSILON = 1e-3f
        private const val EDGE_STEEPNESS = 4f

        /** [picture]'s brightness, 0..1, on a [width] x [height] grid: the guide for [render]. */
        fun guideOf(picture: Bitmap, width: Int, height: Int): FloatArray {
            val small = Bitmap.createScaledBitmap(picture, width, height, true)
            val pixels = IntArray(width * height)
            small.getPixels(pixels, 0, width, 0, 0, width, height)
            if (small !== picture) small.recycle()
            return FloatArray(pixels.size) {
                val p = pixels[it]
                (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) +
                    0.114f * (p and 0xFF)) / 255f
            }
        }

        fun bilinear(grid: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
            val cx = x.coerceIn(0f, w - 1f)
            val cy = y.coerceIn(0f, h - 1f)
            val x0 = cx.toInt()
            val y0 = cy.toInt()
            val x1 = min(x0 + 1, w - 1)
            val y1 = min(y0 + 1, h - 1)
            val fx = cx - x0
            val fy = cy - y0
            val top = grid[y0 * w + x0] * (1 - fx) + grid[y0 * w + x1] * fx
            val bottom = grid[y1 * w + x0] * (1 - fx) + grid[y1 * w + x1] * fx
            return top * (1 - fy) + bottom * fy
        }

        /** [coverage] as an image: white, with the coverage in its alpha. */
        fun toBitmap(coverage: FloatArray, width: Int, height: Int): Bitmap {
            val pixels = IntArray(width * height) {
                val a = (coverage[it].coerceIn(0f, 1f) * 255).toInt()
                (a shl 24) or 0xFFFFFF
            }
            return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }

        /** A box prompt: the object at the box's centre, as model input pixels. */
        fun boxCentre(box: RectF): List<PromptEncoder.Point> {
            val s = PromptEncoder.INPUT
            return listOf(PromptEncoder.Point(box.centerX() * s, box.centerY() * s))
        }

        /** Keeps only the part of a model mask inside [box] (fractions of the picture). */
        fun clipTo(coverage: FloatArray, box: RectF): FloatArray {
            val side = SegmentationEngine.MASK
            val out = coverage.copyOf()
            for (y in 0 until side) for (x in 0 until side) {
                val fx = (x + 0.5f) / side
                val fy = (y + 0.5f) / side
                if (!box.contains(fx, fy)) out[y * side + x] = 0f
            }
            return out
        }
    }
}
