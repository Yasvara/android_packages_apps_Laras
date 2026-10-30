/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * S4 (docs/specs/0013 §4): the subject's own colour where it is see-through. A strand of hair at
 * alpha 0.4 carries 60 % of the sea behind it; over the clock that sea shows as a halo. Blur
 * fusion (Forte & Pitié, "Approximate Fast Foreground Colour Estimation", ICIP 2021) estimates
 * the foreground F and background B from box-blurred neighbours, twice, the second time from the
 * first's estimates: wide (radius [R1]) then narrow ([R2]).
 *
 * The wide pass is smooth, so it runs at 1/[LOW] size; the narrow one runs in strips at full
 * size. The result differs from full-size fusion by 0.0006 on average in the band
 * (`lab/depth/tools/m2_s4.py`), and memory stays a few strips, not a dozen full planes.
 */
internal object Foreground {
    private const val R1 = 90
    private const val R2 = 6
    private const val LOW = 4
    private const val STRIP = 256
    private const val EPS = 1e-5f

    /**
     * The cut-out subject: [pixels] ([w] x [h], ARGB) with [alpha] as alpha and its colour fixed
     * where 0 < alpha < 1. Unpremultiplied ARGB, transparent where alpha is 0.
     */
    fun subject(pixels: IntArray, alpha: FloatArray, w: Int, h: Int): IntArray {
        val lw = (w + LOW - 1) / LOW
        val lh = (h + LOW - 1) / LOW
        val rl = max(1, R1 / LOW)
        val al = Raster.area(alpha, w, h, lw, lh)
        val il = Raster.resizeRgb(pixels, w, h, lw, lh).onEach { p -> for (i in p.indices) p[i] /= 255f }
        val ba = Raster.boxBlur(al, lw, lh, rl)
        val bf = Array(3) { c ->
            val f = Raster.boxBlur(FloatArray(al.size) { il[c][it] * al[it] }, lw, lh, rl)
            FloatArray(al.size) { f[it] / (ba[it] + EPS) }
        }
        val bb = Array(3) { c ->
            val b = Raster.boxBlur(FloatArray(al.size) { il[c][it] * (1 - al[it]) }, lw, lh, rl)
            FloatArray(al.size) { b[it] / ((1 - ba[it]) + EPS) }
        }

        val out = IntArray(w * h)
        var y0 = 0
        while (y0 < h) {
            val y1 = min(h, y0 + STRIP)
            strip(pixels, alpha, w, h, y0, y1, lw, lh, bf, bb, out)
            y0 = y1
        }
        return out
    }

    /** Rows [y0, y1) of the result, computed over those rows plus [R2] either side. */
    private fun strip(
        pixels: IntArray, alpha: FloatArray, w: Int, h: Int, y0: Int, y1: Int,
        lw: Int, lh: Int, bf: Array<FloatArray>, bb: Array<FloatArray>, out: IntArray,
    ) {
        // Nothing see-through here: the picture's own colour, or nothing.
        var soft = false
        for (i in y0 * w until y1 * w) if (alpha[i] > 0f && alpha[i] < 1f) {
            soft = true
            break
        }
        if (!soft) {
            for (i in y0 * w until y1 * w) out[i] = if (alpha[i] > 0f) pixels[i] else 0
            return
        }
        val top = max(0, y0 - R2)
        val bottom = min(h, y1 + R2)
        val rows = bottom - top
        val n = rows * w
        val a = FloatArray(n) { alpha[top * w + it] }
        val img = Array(3) { c -> FloatArray(n) { ((pixels[top * w + it] shr (16 - 8 * c)) and 0xFF) / 255f } }
        // Pass 1 at full size from the wide estimates, upsampled as cv2.INTER_LINEAR does.
        val f1 = Array(3) { FloatArray(n) }
        val b1 = Array(3) { FloatArray(n) }
        val sx = lw / w.toFloat()
        val sy = lh / h.toFloat()
        for (r in 0 until rows) {
            val my = ((top + r + 0.5f) * sy - 0.5f).coerceIn(0f, lh - 1f)
            val ly0 = my.toInt()
            val ly1 = min(ly0 + 1, lh - 1)
            val fy = my - ly0
            for (x in 0 until w) {
                val mx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, lw - 1f)
                val lx0 = mx.toInt()
                val lx1 = min(lx0 + 1, lw - 1)
                val fx = mx - lx0
                val i = r * w + x
                val ai = a[i]
                for (c in 0..2) {
                    val f = lerp2(bf[c], lw, lx0, lx1, ly0, ly1, fx, fy)
                    val b = lerp2(bb[c], lw, lx0, lx1, ly0, ly1, fx, fy)
                    f1[c][i] = (f + ai * (img[c][i] - ai * f - (1 - ai) * b)).coerceIn(0f, 1f)
                    b1[c][i] = b
                }
            }
        }
        // Pass 2: narrow, from pass 1's estimates.
        val ba2 = Raster.boxBlur(a, w, rows, R2)
        for (c in 0..2) {
            val fa = Raster.boxBlur(FloatArray(n) { f1[c][it] * a[it] }, w, rows, R2)
            val bfa = Raster.boxBlur(FloatArray(n) { b1[c][it] * (1 - a[it]) }, w, rows, R2)
            for (i in 0 until n) {
                val f = fa[i] / (ba2[i] + EPS)
                val b = bfa[i] / ((1 - ba2[i]) + EPS)
                f1[c][i] = (f + a[i] * (img[c][i] - a[i] * f - (1 - a[i]) * b)).coerceIn(0f, 1f)
            }
        }
        for (y in y0 until y1) for (x in 0 until w) {
            val g = y * w + x
            val av = alpha[g]
            out[g] = when {
                av <= 0f -> 0
                av >= 1f -> pixels[g]
                else -> {
                    val i = (y - top) * w + x
                    val r = (f1[0][i] * 255f).roundToInt()
                    val gr = (f1[1][i] * 255f).roundToInt()
                    val b = (f1[2][i] * 255f).roundToInt()
                    ((av * 255f).roundToInt() shl 24) or (r shl 16) or (gr shl 8) or b
                }
            }
        }
    }

    private fun lerp2(p: FloatArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int, fx: Float, fy: Float): Float {
        val t = p[y0 * w + x0] + (p[y0 * w + x1] - p[y0 * w + x0]) * fx
        val b = p[y1 * w + x0] + (p[y1 * w + x1] - p[y1 * w + x0]) * fx
        return t + (b - t) * fy
    }
}
