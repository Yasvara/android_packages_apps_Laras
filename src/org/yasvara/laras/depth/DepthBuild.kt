/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * What the depth build found for a picture (docs/specs/0013 §4), kept in the picture's library
 * folder as `build/` so the Studio opens on it at once:
 *
 * - [alpha]: the subject's alpha after S1 and S2, 0..255, [width] x [height] over the whole picture
 *   (the work size: the lock screen's crop at 1.25 picture pixels per display pixel);
 * - [parts]: S3, one cell per [PARTS]² of the stretched picture: 0 background, 1 the character,
 *   n + 1 the nth prop (1-based), so a prop can be dropped with one tap.
 */
class DepthBuild(
    val width: Int,
    val height: Int,
    val alpha: ByteArray,
    val parts: ByteArray,
    /** How many props [parts] holds. */
    val props: Int,
    /** Where S2 looked closer, in work size pixels: left, top, right, bottom per tile. */
    val tiles: List<IntArray>,
    /** How long the build took, for the log and the spec. */
    val millis: Long,
) {
    /** [alpha] as coverage 0..1. */
    fun coverage(): FloatArray = FloatArray(alpha.size) { (alpha[it].toInt() and 0xFF) / 255f }

    /** The cells of the [n]th prop (1-based), 1 or 0, on the [PARTS]² grid. */
    fun prop(n: Int): FloatArray {
        val value = (n + 1).toByte()
        return FloatArray(parts.size) { if (parts[it] == value) 1f else 0f }
    }

    /** The share of the picture the [n]th prop covers, and its bounds on the grid (l, t, r, b). */
    fun propBounds(n: Int): IntArray {
        val value = (n + 1).toByte()
        var l = PARTS
        var t = PARTS
        var r = -1
        var b = -1
        for (y in 0 until PARTS) for (x in 0 until PARTS) {
            if (parts[y * PARTS + x] != value) continue
            l = minOf(l, x)
            t = minOf(t, y)
            r = maxOf(r, x)
            b = maxOf(b, y)
        }
        return intArrayOf(l, t, r + 1, b + 1)
    }

    fun write(dir: File) {
        dir.mkdirs()
        writeGrid(File(dir, ALPHA), width, height, alpha)
        writeGrid(File(dir, PARTS_FILE), PARTS, PARTS, parts)
        val json = JSONObject()
            .put(KEY_FORMAT, FORMAT)
            .put(KEY_WIDTH, width)
            .put(KEY_HEIGHT, height)
            .put(KEY_PROPS, props)
            .put(KEY_TILES, JSONArray().apply { tiles.forEach { put(JSONArray(it.toList())) } })
            .put(KEY_MILLIS, millis)
        // Written last: a folder without it is an unfinished build.
        File(dir, META).writeText(json.toString())
    }

    companion object {
        /** Bumped when the build changes enough that old results should be made again. */
        const val FORMAT = 1
        const val PARTS = 256

        private const val ALPHA = "alpha.grid"
        private const val PARTS_FILE = "parts.grid"
        private const val META = "build.json"
        private const val MAGIC = 0x59475244 // "YGRD"
        private const val KEY_FORMAT = "format"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_PROPS = "props"
        private const val KEY_TILES = "tiles"
        private const val KEY_MILLIS = "millis"

        /** The build in [dir], or null if there is none, it is unfinished, or from another format. */
        fun read(dir: File): DepthBuild? = try {
            val meta = File(dir, META)
            if (!meta.isFile) null else {
                val json = JSONObject(meta.readText())
                if (json.getInt(KEY_FORMAT) != FORMAT) null else {
                    val w = json.getInt(KEY_WIDTH)
                    val h = json.getInt(KEY_HEIGHT)
                    val tiles = json.getJSONArray(KEY_TILES).let { list ->
                        (0 until list.length()).map { i ->
                            val t = list.getJSONArray(i)
                            IntArray(t.length()) { t.getInt(it) }
                        }
                    }
                    val alpha = readGrid(File(dir, ALPHA), w, h)
                    val parts = readGrid(File(dir, PARTS_FILE), PARTS, PARTS)
                    if (alpha == null || parts == null) null
                    else DepthBuild(w, h, alpha, parts, json.getInt(KEY_PROPS), tiles, json.getLong(KEY_MILLIS))
                }
            }
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            null
        }

        private fun writeGrid(file: File, w: Int, h: Int, bytes: ByteArray) {
            val tmp = File(file.path + ".new")
            DataOutputStream(GZIPOutputStream(tmp.outputStream().buffered())).use {
                it.writeInt(MAGIC)
                it.writeInt(w)
                it.writeInt(h)
                it.write(bytes)
            }
            if (!tmp.renameTo(file)) throw IOException("Could not write $file")
        }

        private fun readGrid(file: File, w: Int, h: Int): ByteArray? {
            if (!file.isFile) return null
            DataInputStream(GZIPInputStream(file.inputStream().buffered())).use {
                if (it.readInt() != MAGIC || it.readInt() != w || it.readInt() != h) return null
                return ByteArray(w * h).also { bytes -> it.readFully(bytes) }
            }
        }
    }
}
