/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The image maths of the depth build (docs/specs/0013 §4) on plain row-major arrays: planes of
 * floats, masks of booleans, pictures as packed ARGB ints. Each function matches the OpenCV call
 * the lab's reference build (`lab/depth/tools/m2_build.py`) uses, so the two can be compared.
 */
internal object Raster {

    /** One source pixel's share of one output pixel along a line, for [area]. */
    private class Taps(val start: IntArray, val count: IntArray, val weight: FloatArray, val offset: IntArray)

    /**
     * Weights from [from] samples to [to], as cv2.INTER_AREA: the average over each output
     * pixel's footprint when shrinking, linear interpolation when enlarging.
     */
    private fun taps(from: Int, to: Int): Taps {
        if (to > from) return linearTaps(from, to)
        val scale = from / to.toDouble()
        val start = IntArray(to)
        val count = IntArray(to)
        val offset = IntArray(to)
        val weights = ArrayList<Float>(to * (scale.toInt() + 2))
        for (o in 0 until to) {
            val a = o * scale
            val b = min(from.toDouble(), (o + 1) * scale)
            val first = floor(a).toInt()
            var j = first
            start[o] = first
            offset[o] = weights.size
            while (j < b) {
                val overlap = min(b, j + 1.0) - max(a, j.toDouble())
                if (overlap > 1e-9) weights += (overlap / (b - a)).toFloat()
                j++
            }
            count[o] = weights.size - offset[o]
        }
        return Taps(start, count, weights.toFloatArray(), offset)
    }

    private fun linearTaps(from: Int, to: Int): Taps {
        val start = IntArray(to)
        val count = IntArray(to) { 2 }
        val offset = IntArray(to) { 2 * it }
        val weight = FloatArray(2 * to)
        for (o in 0 until to) {
            val m = ((o + 0.5) * from / to - 0.5).coerceIn(0.0, from - 1.0)
            val i = min(m.toInt(), max(0, from - 2))
            val f = (m - i).toFloat()
            start[o] = i
            weight[2 * o] = 1 - f
            weight[2 * o + 1] = f
            if (from == 1) count[o] = 1
        }
        return Taps(start, count, weight, offset)
    }

    /** [src] ([w] x [h] packed ARGB) resized to [tw] x [th] (see [taps]), as planes R, G, B in 0..255. */
    fun resizeRgb(src: IntArray, w: Int, h: Int, tw: Int, th: Int): Array<FloatArray> {
        val tx = taps(w, tw)
        val ty = taps(h, th)
        val rows = Array(3) { FloatArray(tw * h) }
        for (y in 0 until h) {
            val line = y * w
            for (o in 0 until tw) {
                var r = 0f
                var g = 0f
                var b = 0f
                val at = tx.offset[o]
                for (k in 0 until tx.count[o]) {
                    val p = src[line + tx.start[o] + k]
                    val wt = tx.weight[at + k]
                    r += ((p shr 16) and 0xFF) * wt
                    g += ((p shr 8) and 0xFF) * wt
                    b += (p and 0xFF) * wt
                }
                rows[0][y * tw + o] = r
                rows[1][y * tw + o] = g
                rows[2][y * tw + o] = b
            }
        }
        return Array(3) { c -> columns(rows[c], tw, h, th, ty) }
    }

    /** A plane resized to [tw] x [th] as cv2.INTER_AREA does (see [taps]). */
    fun area(src: FloatArray, w: Int, h: Int, tw: Int, th: Int): FloatArray {
        val tx = taps(w, tw)
        val rows = FloatArray(tw * h)
        for (y in 0 until h) {
            for (o in 0 until tw) {
                var s = 0f
                val at = tx.offset[o]
                for (k in 0 until tx.count[o]) s += src[y * w + tx.start[o] + k] * tx.weight[at + k]
                rows[y * tw + o] = s
            }
        }
        return columns(rows, tw, h, th, taps(h, th))
    }

    private fun columns(rows: FloatArray, w: Int, h: Int, th: Int, ty: Taps): FloatArray {
        val out = FloatArray(w * th)
        for (o in 0 until th) {
            val at = ty.offset[o]
            for (k in 0 until ty.count[o]) {
                val wt = ty.weight[at + k]
                val line = (ty.start[o] + k) * w
                for (x in 0 until w) out[o * w + x] += rows[line + x] * wt
            }
        }
        return out
    }

    /** Planes R, G, B (0..255) packed back into opaque ARGB, rounded as an 8-bit image is. */
    fun pack(rgb: Array<FloatArray>): IntArray = IntArray(rgb[0].size) {
        val r = rgb[0][it].roundToInt().coerceIn(0, 255)
        val g = rgb[1][it].roundToInt().coerceIn(0, 255)
        val b = rgb[2][it].roundToInt().coerceIn(0, 255)
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Bilinear resample with pixel centres aligned and edges clamped (cv2.INTER_LINEAR). */
    fun bilinear(src: FloatArray, w: Int, h: Int, tw: Int, th: Int): FloatArray {
        val out = FloatArray(tw * th)
        val sx = w / tw.toFloat()
        val sy = h / th.toFloat()
        val x0 = IntArray(tw)
        val x1 = IntArray(tw)
        val fx = FloatArray(tw)
        for (x in 0 until tw) {
            val m = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, w - 1f)
            x0[x] = m.toInt()
            x1[x] = min(x0[x] + 1, w - 1)
            fx[x] = m - x0[x]
        }
        for (y in 0 until th) {
            val m = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, h - 1f)
            val y0 = m.toInt()
            val y1 = min(y0 + 1, h - 1)
            val fy = m - y0
            val a = y0 * w
            val b = y1 * w
            for (x in 0 until tw) {
                val top = src[a + x0[x]] + (src[a + x1[x]] - src[a + x0[x]]) * fx[x]
                val bottom = src[b + x0[x]] + (src[b + x1[x]] - src[b + x0[x]]) * fx[x]
                out[y * tw + x] = top + (bottom - top) * fy
            }
        }
        return out
    }

    /** The mean over a (2[r]+1)² window, edges replicated (cv2.blur, BORDER_REPLICATE). */
    fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val k = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0.0
            for (x in -r..r) sum += src[row + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = (sum / k).toFloat()
                sum += src[row + min(x + r + 1, w - 1)] - src[row + max(x - r, 0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0.0
            for (y in -r..r) sum += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = (sum / k).toFloat()
                sum += tmp[min(y + r + 1, h - 1) * w + x] - tmp[max(y - r, 0) * w + x]
            }
        }
        return out
    }

    /** Gaussian blur of [sigma], kernel 4 sigma each side as OpenCV's for floats, edges replicated. */
    fun gaussian(src: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
        val r = max(1, (4 * sigma).roundToInt())
        val kernel = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
        val total = kernel.sum()
        for (i in kernel.indices) kernel[i] /= total
        val tmp = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) s += src[y * w + (x + k).coerceIn(0, w - 1)] * kernel[k + r]
            tmp[y * w + x] = s
        }
        val out = FloatArray(src.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0f
            for (k in -r..r) s += tmp[(y + k).coerceIn(0, h - 1) * w + x] * kernel[k + r]
            out[y * w + x] = s
        }
        return out
    }

    /**
     * Euclidean distance from every cell to the nearest set cell of [mask] (Felzenszwalb &
     * Huttenlocher); cells of an empty mask are infinitely far.
     */
    fun distance(mask: BooleanArray, w: Int, h: Int): FloatArray {
        val inf = 1e20f
        val d = FloatArray(w * h) { if (mask[it]) 0f else inf }
        val n = max(w, h)
        val f = FloatArray(n)
        val out = FloatArray(n)
        val v = IntArray(n)
        val z = FloatArray(n + 1)
        for (x in 0 until w) {
            for (y in 0 until h) f[y] = d[y * w + x]
            line(f, h, out, v, z)
            for (y in 0 until h) d[y * w + x] = out[y]
        }
        for (y in 0 until h) {
            System.arraycopy(d, y * w, f, 0, w)
            line(f, w, out, v, z)
            for (x in 0 until w) d[y * w + x] = sqrt(out[x])
        }
        return d
    }

    /** One line of the squared distance transform: the lower envelope of parabolas. */
    private fun line(f: FloatArray, n: Int, d: FloatArray, v: IntArray, z: FloatArray) {
        fun cross(q: Int, p: Int) = ((f[q] + q.toFloat() * q) - (f[p] + p.toFloat() * p)) / (2f * (q - p))
        var k = 0
        v[0] = 0
        z[0] = -Float.MAX_VALUE
        z[1] = Float.MAX_VALUE
        for (q in 1 until n) {
            var s = cross(q, v[k])
            while (s <= z[k]) {
                k--
                s = cross(q, v[k])
            }
            k++
            v[k] = q
            z[k] = s
            z[k + 1] = Float.MAX_VALUE
        }
        k = 0
        for (q in 0 until n) {
            while (z[k + 1] < q) k++
            val p = v[k]
            d[q] = (q - p).toFloat() * (q - p) + f[p]
        }
    }

    /** [mask] grown by a disk of radius [r]. */
    fun dilate(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val d = distance(mask, w, h)
        return BooleanArray(d.size) { d[it] <= r }
    }

    /** [mask] shrunk by a disk of radius [r]. */
    fun erode(mask: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
        val d = distance(BooleanArray(mask.size) { !mask[it] }, w, h)
        return BooleanArray(d.size) { d[it] > r }
    }

    /** The 3x3 square neighbourhood: set where it holds both set and unset cells. */
    fun gradient3(mask: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until h) for (x in 0 until w) {
            var any = false
            var all = true
            for (dy in -1..1) for (dx in -1..1) {
                val v = mask[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                any = any || v
                all = all && v
            }
            out[y * w + x] = any && !all
        }
        return out
    }

    /** Opening by the 3x3 square: drops specks and one-cell bridges. */
    fun open3(mask: BooleanArray, w: Int, h: Int): BooleanArray = square3(square3(mask, w, h, true), w, h, false)

    private fun square3(mask: BooleanArray, w: Int, h: Int, erode: Boolean): BooleanArray {
        val out = BooleanArray(mask.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = erode
            for (dy in -1..1) for (dx in -1..1) {
                val yy = y + dy
                val xx = x + dx
                // Outside the grid never changes the result, as OpenCV's default morphology border.
                val m = if (yy in 0 until h && xx in 0 until w) mask[yy * w + xx] else erode
                v = if (erode) v && m else v || m
            }
            out[y * w + x] = v
        }
        return out
    }

    /**
     * 8-connected components of [mask]: a label per cell (-1 where unset, else 0 until count) and
     * each component's size.
     */
    fun components(mask: BooleanArray, w: Int, h: Int): Pair<IntArray, IntArray> {
        val label = IntArray(mask.size) { -1 }
        val sizes = ArrayList<Int>()
        val stack = IntArray(mask.size)
        for (start in mask.indices) {
            if (!mask[start] || label[start] >= 0) continue
            val id = sizes.size
            var top = 0
            var size = 0
            stack[top++] = start
            label[start] = id
            while (top > 0) {
                val i = stack[--top]
                size++
                val x = i % w
                val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val j = yy * w + xx
                    if (mask[j] && label[j] < 0) {
                        label[j] = id
                        stack[top++] = j
                    }
                }
            }
            sizes += size
        }
        return label to sizes.toIntArray()
    }
}
