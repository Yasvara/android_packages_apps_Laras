/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.content.Context
import android.util.Log
import android.view.WindowManager
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finds a picture's subject the way docs/specs/0013 §4 lays out, without anyone touching it:
 *
 * - **S1** ToonOut (BiRefNet fine-tuned on anime) over the whole picture at 1024²: which pixels
 *   are the character, hair included;
 * - **S2** up to [MAX_TILES] more ToonOut passes on 1024 px tiles of the picture at the size the
 *   lock screen shows it (×1.25), where S1 is unsure near the character, so strands come out at
 *   the resolution they are seen at. S1 decides what is subject: a tile may sharpen it, not add to it;
 * - **S3** parts: EdgeTAM splits S1's foreground into object parts, and those ISNet-anime (trained
 *   on characters only) does not believe in are props, offered one tap away.
 *
 * ISNet also steers S2's tiles to the character, not to props. Models load one at a time and are
 * closed when done. Mirrors `lab/depth/tools/m2_build.py`, `m2_parts.py` and `m2_parts3.py`.
 */
class DepthBuilder internal constructor(private val context: Context, private val lean: Boolean) {

    enum class Stage { CHARACTER, HAIR, PARTS }

    /** [step] of [steps] of the whole build is done, now in [stage]; in S2, at [tile] of [tiles]. */
    fun interface Listener {
        fun progress(stage: Stage, step: Int, steps: Int, tile: Int, tiles: Int)
    }

    class Cancelled : Exception()

    /** Told where S2's tiles go (left, top, right, bottom on a [width] x [height] work size). */
    var onTiles: ((tiles: List<IntArray>, width: Int, height: Int) -> Unit)? = null

    private lateinit var listener: Listener
    private lateinit var cancelled: () -> Boolean
    private var step = 0
    private var steps = 0

    private fun advance(stage: Stage, tile: Int = 0, tiles: Int = 0) {
        if (cancelled()) throw Cancelled()
        listener.progress(stage, step++, steps, tile, tiles)
    }

    fun build(picture: WallpaperPicture, listener: Listener, cancelled: () -> Boolean): DepthBuild {
        this.listener = listener
        this.cancelled = cancelled
        val start = System.currentTimeMillis()
        val bitmap = picture.bitmap
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        // ISNet, S1, up to four tiles, parts.
        steps = 3 + MAX_TILES
        step = 0

        advance(Stage.CHARACTER)
        val k = workScale(w, h)
        val ww = max(1, (w * k).roundToInt())
        val wh = max(1, (h * k).roundToInt())
        val character = OnnxModel(ISNET, lean = true).use { isnet(it, pixels, w, h, ww, wh) }

        // ToonOut is closed before EdgeTAM loads, so the two never hold memory at once.
        val (s1, s1Side, refined) = OnnxModel(TOONOUT, lean).use { toonout ->
            val s1 = toonout.run(normalize(Raster.resizeRgb(pixels, w, h, toonout.side, toonout.side)))
            advance(Stage.HAIR, 0, MAX_TILES)
            val work = if (k < 1f) Raster.pack(Raster.resizeRgb(pixels, w, h, ww, wh)) else pixels
            Triple(s1, toonout.side, refine(toonout, s1, work, ww, wh, character))
        }
        val (alpha, tiles) = refined
        steps -= MAX_TILES - tiles.size
        advance(Stage.PARTS)
        val parts = parts(picture, s1, s1Side, character, ww, wh)
        val props = parts.maxOf { it.toInt() and 0xFF }.let { max(0, it - 1) }
        val millis = System.currentTimeMillis() - start
        Log.i(TAG, "Built ${picture.fingerprint.sha.take(12)}: ${ww}x$wh, ${tiles.size} tiles, $props props, $millis ms")
        return DepthBuild(
            ww, wh,
            ByteArray(alpha.size) { (alpha[it].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte() },
            parts, props, tiles, millis,
        )
    }

    /**
     * The lock screen's default crop (the largest rectangle of the display's proportions, centred)
     * shown at [WORK_SCALE] picture pixels per display pixel; never larger than the picture.
     */
    private fun workScale(w: Int, h: Int): Float {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val dw = min(bounds.width(), bounds.height())
        val dh = max(bounds.width(), bounds.height())
        val cropH = min(h.toFloat(), w * dh / dw.toFloat())
        return min(1f, WORK_SCALE * dh / cropH)
    }

    /** ISNet-anime's character alpha, letterboxed into its square input, at the work size. */
    private fun isnet(model: OnnxModel, pixels: IntArray, w: Int, h: Int, ww: Int, wh: Int): FloatArray {
        val s = model.side
        val k = s / max(w, h).toFloat()
        val nw = (w * k).roundToInt()
        val nh = (h * k).roundToInt()
        val pw = (s - nw) / 2
        val ph = (s - nh) / 2
        val small = Raster.resizeRgb(pixels, w, h, nw, nh)
        val planes = Array(3) { c ->
            FloatArray(s * s).also { plane ->
                for (y in 0 until nh) for (x in 0 until nw) {
                    plane[(y + ph) * s + x + pw] = small[c][y * nw + x].roundToInt().coerceIn(0, 255) / 255f
                }
            }
        }
        val out = model.run(planes)
        val crop = FloatArray(nw * nh) { out[(it / nw + ph) * s + it % nw + pw].coerceIn(0f, 1f) }
        return Raster.bilinear(crop, nw, nh, ww, wh)
    }

    /** ImageNet normalization, from 0..255 planes, rounded to 8 bits first as the picture is. */
    private fun normalize(rgb: Array<FloatArray>): Array<FloatArray> = Array(3) { c ->
        FloatArray(rgb[c].size) { (rgb[c][it].roundToInt().coerceIn(0, 255) / 255f - MEAN[c]) / STD[c] }
    }

    private fun normalize(work: IntArray, ww: Int, x0: Int, y0: Int, side: Int, wh: Int): Array<FloatArray> =
        Array(3) { c ->
            val shift = 16 - 8 * c
            FloatArray(side * side) {
                // Past the picture's edge (a picture smaller than a tile): repeat the edge.
                val x = min(x0 + it % side, ww - 1)
                val y = min(y0 + it / side, wh - 1)
                (((work[y * ww + x] shr shift) and 0xFF) / 255f - MEAN[c]) / STD[c]
            }
        }

    /** S2: tiles placed and run, blended over S1. Returns the alpha at the work size and the tiles. */
    private fun refine(
        model: OnnxModel,
        s1: FloatArray,
        work: IntArray,
        ww: Int,
        wh: Int,
        character: FloatArray,
    ): Pair<FloatArray, List<IntArray>> {
        val side = model.side
        val g = Raster.bilinear(s1, side, side, ww, wh)
        val n = ww * wh
        val unsure = BooleanArray(n) { g[it] > 0.02f && g[it] < 0.98f }
        val edge = Raster.gradient3(BooleanArray(n) { g[it] > 0.5f }, ww, wh)
        val band = Raster.dilate(BooleanArray(n) { unsure[it] || edge[it] }, ww, wh, BAND_PX)
        val near = Raster.dilate(BooleanArray(n) { g[it] > 0.05f }, ww, wh, NEAR_PX)
        for (i in 0 until n) band[i] = band[i] && near[i]
        val onCharacter = Raster.dilate(BooleanArray(n) { character[it] > 0.5f }, ww, wh, CHARACTER_PX)
        // Integer weights: band 1, band on the character ON_CHARACTER_WEIGHT.
        val must = BooleanArray(n) { band[it] && onCharacter[it] }
        val weight = IntArray(n) { if (must[it]) ON_CHARACTER_WEIGHT else if (band[it]) 1 else 0 }
        val tiles = placeTiles(weight, must, ww, wh, side)
        onTiles?.invoke(tiles, ww, wh)

        val acc = FloatArray(n)
        val fsum = FloatArray(n)
        for ((i, t) in tiles.withIndex()) {
            advance(Stage.HAIR, i + 1, tiles.size)
            val x0 = t[0]
            val y0 = t[1]
            val a = model.run(normalize(work, ww, x0, y0, side, wh))
            val tw = t[2] - x0
            val th = t[3] - y0
            for (y in 0 until th) {
                val ry = min(y, th - 1 - y)
                for (x in 0 until tw) {
                    val rx = min(x, tw - 1 - x)
                    val f = (min(rx, ry) / FEATHER.toFloat()).coerceIn(0f, 1f)
                    val i = (y0 + y) * ww + x0 + x
                    acc[i] += a[y * side + x] * f
                    fsum[i] += f
                }
            }
        }
        val local = FloatArray(n) {
            val rest = max(0f, 1f - fsum[it])
            (acc[it] + rest * g[it]) / (fsum[it] + rest)
        }
        // Seen at S1's own scale, a tile may sharpen what S1 found but not add area to it: a
        // tile alone has no context and takes sea or railing between hair strands for subject.
        val shrunk = Raster.area(local, ww, wh, side, side)
        val gate1 = FloatArray(side * side) {
            (1f - (max(0f, shrunk[it] - s1[it]) - GAIN_FREE) / GAIN_SPAN).coerceIn(0f, 1f)
        }
        val gate = Raster.bilinear(gate1, side, side, ww, wh)
        val use = Raster.gaussian(FloatArray(n) { if (band[it]) 1f else 0f }, ww, wh, BLEND_SIGMA)
        val out = FloatArray(n) {
            if (!near[it]) 0f else {
                val l = if (local[it] > g[it]) g[it] + (local[it] - g[it]) * gate[it] else local[it]
                (use[it] * l + (1 - use[it]) * g[it]).coerceIn(0f, 1f)
            }
        }
        return out to tiles
    }

    /**
     * Greedy: each tile where it covers the most band weight not yet covered, as long as it covers
     * [MIN_GAIN] of the band on the character ([must]), so none is spent on props alone.
     */
    private fun placeTiles(weight: IntArray, must: BooleanArray, w: Int, h: Int, side: Int): List<IntArray> {
        val wt = weight.copyOf()
        val mu = IntArray(must.size) { if (must[it]) 1 else 0 }
        val total = mu.sum()
        val tiles = ArrayList<IntArray>()
        if (total == 0) return tiles
        val ys = (0..max(0, h - side) step STRIDE).toMutableSet().apply { add(max(0, h - side)) }.sorted()
        val xs = (0..max(0, w - side) step STRIDE).toMutableSet().apply { add(max(0, w - side)) }.sorted()
        repeat(MAX_TILES) {
            val iw = integral(wt, w, h)
            val im = integral(mu, w, h)
            var best = 0L
            var at: IntArray? = null
            for (y in ys) for (x in xs) {
                val x1 = min(w, x + side)
                val y1 = min(h, y + side)
                if (boxSum(im, w, x, y, x1, y1) < MIN_GAIN * total) continue
                val s = boxSum(iw, w, x, y, x1, y1)
                if (s > best) {
                    best = s
                    at = intArrayOf(x, y, x1, y1)
                }
            }
            val t = at ?: return tiles
            tiles += t
            for (y in t[1] until t[3]) for (x in t[0] until t[2]) {
                wt[y * w + x] = 0
                mu[y * w + x] = 0
            }
        }
        return tiles
    }

    private fun integral(v: IntArray, w: Int, h: Int): LongArray {
        val ii = LongArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += v[y * w + x]
                ii[(y + 1) * (w + 1) + x + 1] = ii[y * (w + 1) + x + 1] + row
            }
        }
        return ii
    }

    private fun boxSum(ii: LongArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int): Long {
        val s = w + 1
        return ii[y1 * s + x1] - ii[y0 * s + x1] - ii[y1 * s + x0] + ii[y0 * s + x0]
    }

    /**
     * S3: object parts from EdgeTAM, one positive point each on a grid over S1's sure foreground;
     * masks that agree are one object, an object inside a bigger one is part of it. A part
     * ISNet mostly disagrees with, or where S1 - ISNet is large, is a prop; touching prop cells
     * are one prop. Cells: 0 background, 1 character, n + 1 the nth prop.
     */
    private fun parts(
        picture: WallpaperPicture,
        s1: FloatArray,
        s1Side: Int,
        character: FloatArray,
        ww: Int,
        wh: Int,
    ): ByteArray {
        val side = DepthBuild.PARTS
        val n = side * side
        val a = Raster.area(s1, s1Side, s1Side, side, side)
        val c = Raster.area(character, ww, wh, side, side)
        val fg = BooleanArray(n) { a[it] > 0.5f }
        val out = ByteArray(n) { if (fg[it]) 1 else 0 }
        val label = runCatching { objects(picture, a, fg) }
            .onFailure { Log.w(TAG, "No parts: EdgeTAM unavailable", it) }
            .getOrNull() ?: return out

        val prop = BooleanArray(n) { a[it] - c[it] > CORE }
        val count = label.maxOrNull() ?: -1
        for (k in 0..count) {
            var sum = 0f
            var cells = 0
            for (i in 0 until n) if (label[i] == k && a[i] > 0.9f) {
                sum += c[i]
                cells++
            }
            if (cells > 0 && sum / cells < PROP_PART) for (i in 0 until n) if (label[i] == k) prop[i] = true
        }
        for (i in 0 until n) prop[i] = prop[i] && fg[i]
        val (comp, sizes) = Raster.components(Raster.open3(prop, side, side), side, side)
        val kept = sizes.indices.filter { sizes[it] >= MIN_PROP * n }.sortedByDescending { sizes[it] }
        kept.forEachIndexed { rank, id ->
            if (rank + 2 > Byte.MAX_VALUE) return@forEachIndexed
            for (i in 0 until n) if (comp[i] == id) out[i] = (rank + 2).toByte()
        }
        return out
    }

    /** EdgeTAM's object parts over S1's foreground: a label per cell, -1 outside. */
    private fun objects(picture: WallpaperPicture, a: FloatArray, fg: BooleanArray): IntArray {
        val side = DepthBuild.PARTS
        val n = side * side
        val sure = Raster.erode(BooleanArray(n) { a[it] > SURE }, side, side, 2)
        val masks = ArrayList<BooleanArray>()
        val scale = PromptEncoder.INPUT / side
        SegmentationEngine(context).use { engine ->
            engine.encode(picture.bitmap)
            val step = side / GRID
            for (gy in 0 until GRID) for (gx in 0 until GRID) {
                val x = ((gx + 0.5f) * step).toInt()
                val y = ((gy + 0.5f) * step).toInt()
                if (!sure[y * side + x]) continue
                if (cancelled()) throw Cancelled()
                val cov = engine.decode(listOf(PromptEncoder.Point(x * scale, y * scale))).coverage
                val m = BooleanArray(n) { cov[it] > 0.5f && fg[it] }
                if (m.count { it } < 16 || !m[y * side + x]) continue
                masks += m
            }
        }
        val label = IntArray(n) { -1 }
        if (masks.isEmpty()) return label
        // As bit sets, so the pairwise overlaps stay cheap with a few hundred masks.
        val bits = masks.map { m ->
            LongArray(n / 64).also { b -> for (p in 0 until n) if (m[p]) b[p shr 6] = b[p shr 6] or (1L shl (p and 63)) }
        }
        fun overlap(x: LongArray, y: LongArray): Int {
            var count = 0
            for (i in x.indices) count += java.lang.Long.bitCount(x[i] and y[i])
            return count
        }
        val area = IntArray(masks.size) { i -> masks[i].count { it } }
        // Same object: IoU above SAME, joined with union-find.
        val parent = IntArray(masks.size) { it }
        fun find(i: Int): Int {
            var j = i
            while (parent[j] != j) {
                parent[j] = parent[parent[j]]
                j = parent[j]
            }
            return j
        }
        for (i in masks.indices) for (j in i + 1 until masks.size) {
            val inter = overlap(bits[i], bits[j])
            if (inter.toFloat() / (area[i] + area[j] - inter) > SAME) parent[find(i)] = find(j)
        }
        val groups = masks.indices.groupBy { find(it) }.values
        val objs = groups.map { members ->
            val votes = IntArray(n)
            for (m in members) for (p in 0 until n) if (masks[m][p]) votes[p]++
            BooleanArray(n) { votes[it] * 2 > members.size }
        }.map { it to it.count { v -> v } }.sortedByDescending { it.second }
        // A part of a bigger object folds into it.
        val top = ArrayList<Pair<BooleanArray, Int>>()
        val topBits = ArrayList<LongArray>()
        for (o in objs) {
            val ob = LongArray(n / 64).also { b -> for (p in 0 until n) if (o.first[p]) b[p shr 6] = b[p shr 6] or (1L shl (p and 63)) }
            if (topBits.none { overlap(ob, it) >= PART * o.second }) {
                top += o
                topBits += ob
            }
        }
        // Larger first, so where objects overlap the smaller (more specific) one wins.
        for ((k, t) in top.withIndex()) for (p in 0 until n) if (t.first[p]) label[p] = k
        // Foreground no object covers: the nearest object's.
        if ((0 until n).any { fg[it] && label[it] < 0 }) {
            val dist = FloatArray(n) { Float.MAX_VALUE }
            val nearest = IntArray(n) { -1 }
            for (k in top.indices) {
                val d = Raster.distance(BooleanArray(n) { label[it] == k }, side, side)
                for (p in 0 until n) if (d[p] < dist[p]) {
                    dist[p] = d[p]
                    nearest[p] = k
                }
            }
            for (p in 0 until n) if (fg[p] && label[p] < 0) label[p] = nearest[p]
        }
        for (p in 0 until n) if (!fg[p]) label[p] = -1
        // Tiny objects join the neighbour they touch most.
        for (k in top.indices) {
            val cells = (0 until n).filter { label[it] == k }
            if (cells.isEmpty() || cells.size >= MIN_AREA * n) continue
            val mine = BooleanArray(n).also { m -> cells.forEach { m[it] = true } }
            val ring = Raster.dilate(mine, side, side, 1)
            val votes = HashMap<Int, Int>()
            for (p in 0 until n) if (ring[p] && !mine[p] && label[p] >= 0) votes.merge(label[p], 1, Int::plus)
            votes.maxByOrNull { it.value }?.let { best -> cells.forEach { label[it] = best.key } }
        }
        return label
    }

    companion object {
        private const val TAG = "DepthBuilder"

        /** The ROM's models (vendor/yasvara/prebuilts/depth), read in place. */
        private val DIR = File("/system_ext/etc/yasvara/depth")
        val TOONOUT = File(DIR, "toonout.onnx")
        val ISNET = File(DIR, "isnet.onnx")

        /** Whether this device ships the build's models (onyx does; smaller phones do not). */
        val available get() = TOONOUT.isFile && ISNET.isFile

        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        const val MAX_TILES = 4
        private const val WORK_SCALE = 1.25f
        private const val STRIDE = 32
        private const val MIN_GAIN = 0.03f
        private const val BAND_PX = 12
        private const val NEAR_PX = 16
        private const val CHARACTER_PX = 24
        private const val ON_CHARACTER_WEIGHT = 5
        private const val FEATHER = 64
        private const val GAIN_FREE = 0.1f
        private const val GAIN_SPAN = 0.2f
        private const val BLEND_SIGMA = 3f

        // S3, as m2_parts.py / m2_parts3.py.
        private const val GRID = 16
        private const val SURE = 0.9f
        private const val SAME = 0.7f
        private const val PART = 0.85f
        private const val MIN_AREA = 0.004f
        private const val CORE = 0.6f
        private const val PROP_PART = 0.8f
        private const val MIN_PROP = 0.003f
    }
}
