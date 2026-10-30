/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * EdgeTAM's prompt encoder, in plain Kotlin. The exported encoder computes this too, but its
 * 8-bit build quantizes point positions so tightly that where a point lies is lost; computing it
 * here, in float, from the float model's own weights keeps every tap exact, and lets each tap
 * run only the small mask decoder instead of the whole image encoder.
 *
 * As the float graph does it: a point p (model input pixels) becomes u = p / 1024, then
 * n = a·u + b (the pixel-centre shift and the move to -1..1 folded into a and b), then
 * [sin, cos] of W·n (W holds 2π times SAM 2's Gaussian matrix), plus the point embedding.
 * The padding token that ends the prompt is the pad embedding alone. This export has no
 * separate negative label: taking away is done by the selection, not by the prompt.
 */
class PromptEncoder(input: InputStream) {
    /** W as two rows of 128: the x and y weights. */
    private val weights: FloatArray
    private val scale: Float
    private val offset: Float
    private val point: FloatArray
    private val pad: FloatArray

    init {
        val bytes = input.use { it.readBytes() }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(buffer.int == MAGIC) { "Not a prompt encoder file" }
        weights = FloatArray(2 * HALF) { buffer.float }
        scale = buffer.float
        offset = buffer.float
        point = FloatArray(DIM) { buffer.float }
        pad = FloatArray(DIM) { buffer.float }
    }

    /** A point of the prompt, in model input pixels (0..1024). */
    data class Point(val x: Float, val y: Float)

    /** Embeds up to [TOKENS] - 1 points, padded, as [1, TOKENS, 256] floats. */
    fun encode(points: List<Point>): FloatArray {
        require(points.size < TOKENS) { "At most ${TOKENS - 1} points" }
        val out = FloatArray(TOKENS * DIM)
        for (t in 0 until TOKENS) {
            val base = t * DIM
            val p = points.getOrNull(t)
            if (p == null) {
                pad.copyInto(out, base)
                continue
            }
            val nx = scale * (p.x / INPUT) + offset
            val ny = scale * (p.y / INPUT) + offset
            for (i in 0 until HALF) {
                val angle = nx * weights[i] + ny * weights[HALF + i]
                out[base + i] = sin(angle) + point[i]
                out[base + HALF + i] = cos(angle) + point[HALF + i]
            }
        }
        return out
    }

    companion object {
        const val INPUT = 1024f
        const val TOKENS = 3
        const val DIM = 256
        private const val HALF = DIM / 2
        /** "YPE2" */
        private const val MAGIC = 0x32455059
    }
}
