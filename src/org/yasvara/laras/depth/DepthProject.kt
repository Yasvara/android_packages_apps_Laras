/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.graphics.PointF
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A saved depth wallpaper as Laras keeps it (docs/specs/0013 §6): the picture it belongs to, the
 * selection as its list of operations, so re-opening it resumes editing exactly where it stopped,
 * and the size of the matte it produced. [DepthLibrary] stores it as `project.json`, with each
 * region's grid in a file of its own.
 */
class DepthProject(
    val fingerprint: Fingerprint,
    /** The picture's size as decoded, EXIF orientation applied. */
    val pictureWidth: Int,
    val pictureHeight: Int,
    val ops: List<SelectionOp>,
    val matteWidth: Int,
    val matteHeight: Int,
    val created: Long,
    val modified: Long,
) {
    /** [gridName] names the file the grid of the [n]th operation, a region or matte, is kept in. */
    fun toJson(gridName: (n: Int) -> String): JSONObject {
        val list = JSONArray()
        ops.forEachIndexed { n, op ->
            list.put(
                when (op) {
                    is SelectionOp.Region -> JSONObject()
                        .put(KEY_KIND, KIND_REGION)
                        .put(KEY_ADD, op.add)
                        .put(KEY_GRID, gridName(n))
                    is SelectionOp.Matte -> JSONObject()
                        .put(KEY_KIND, KIND_MATTE)
                        .put(KEY_ADD, op.add)
                        .put(KEY_GRID, gridName(n))
                        .put(KEY_WIDTH, op.width)
                        .put(KEY_HEIGHT, op.height)
                    is SelectionOp.Auto -> JSONObject()
                        .put(KEY_KIND, KIND_AUTO)
                        .put(KEY_ADD, true)
                    is SelectionOp.Part -> JSONObject()
                        .put(KEY_KIND, KIND_PART)
                        .put(KEY_ADD, op.add)
                        .put(KEY_INDEX, op.index)
                    is SelectionOp.Brush -> JSONObject()
                        .put(KEY_KIND, KIND_BRUSH)
                        .put(KEY_ADD, op.add)
                        .put(KEY_RADIUS, op.radius.toDouble())
                        .put(KEY_POINTS, JSONArray().apply {
                            op.points.forEach { put(round(it.x)); put(round(it.y)) }
                        })
                },
            )
        }
        return JSONObject()
            .put(KEY_FORMAT, FORMAT)
            .put(KEY_FINGERPRINT, fingerprint.sha)
            .put(KEY_DHASH, Fingerprint.hex(fingerprint.dhash))
            .put(KEY_PICTURE, size(pictureWidth, pictureHeight))
            .put(KEY_MATTE, size(matteWidth, matteHeight))
            .put(KEY_CREATED, created)
            .put(KEY_MODIFIED, modified)
            .put(KEY_OPS, list)
    }

    companion object {
        /** Bumped when the layout changes in a way older code cannot read. */
        const val FORMAT = 1

        private const val KEY_FORMAT = "format"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_DHASH = "dhash"
        private const val KEY_PICTURE = "picture"
        private const val KEY_MATTE = "matte"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_CREATED = "created"
        private const val KEY_MODIFIED = "modified"
        private const val KEY_OPS = "ops"
        private const val KEY_KIND = "kind"
        private const val KEY_ADD = "add"
        private const val KEY_GRID = "grid"
        private const val KEY_RADIUS = "radius"
        private const val KEY_POINTS = "points"
        private const val KIND_REGION = "region"
        private const val KIND_BRUSH = "brush"
        private const val KIND_MATTE = "matte"
        private const val KIND_AUTO = "auto"
        private const val KIND_PART = "part"
        private const val KEY_INDEX = "index"

        /**
         * Reads a project, with [grid] loading a grid of the given size by its file name and [build]
         * the picture's depth build; null if it is unreadable, from a newer format, or a grid or
         * the build it needs is missing.
         */
        fun fromJson(
            json: JSONObject,
            build: () -> DepthBuild? = { null },
            grid: (name: String, width: Int, height: Int) -> FloatArray?,
        ): DepthProject? = try {
            if (json.getInt(KEY_FORMAT) > FORMAT) null else {
                val sha = json.getString(KEY_FINGERPRINT)
                val ops = json.getJSONArray(KEY_OPS).let { list ->
                    (0 until list.length()).map { n ->
                        val op = list.getJSONObject(n)
                        val add = op.getBoolean(KEY_ADD)
                        when (op.getString(KEY_KIND)) {
                            KIND_REGION -> SelectionOp.Region(
                                add,
                                grid(op.getString(KEY_GRID), SelectionOp.SIDE, SelectionOp.SIDE) ?: return null,
                            )
                            KIND_MATTE -> {
                                val w = op.getInt(KEY_WIDTH)
                                val h = op.getInt(KEY_HEIGHT)
                                SelectionOp.Matte(add, w, h, grid(op.getString(KEY_GRID), w, h) ?: return null)
                            }
                            KIND_AUTO -> SelectionOp.Auto(build() ?: return null)
                            KIND_PART -> SelectionOp.Part(add, op.getInt(KEY_INDEX), build() ?: return null)
                            KIND_BRUSH -> {
                                val xy = op.getJSONArray(KEY_POINTS)
                                SelectionOp.Brush(
                                    add,
                                    (0 until xy.length() / 2).map {
                                        PointF(xy.getDouble(2 * it).toFloat(), xy.getDouble(2 * it + 1).toFloat())
                                    },
                                    op.getDouble(KEY_RADIUS).toFloat(),
                                )
                            }
                            else -> return null
                        }
                    }
                }
                val picture = json.getJSONObject(KEY_PICTURE)
                val matte = json.getJSONObject(KEY_MATTE)
                if (!Fingerprint.isSha(sha)) null else DepthProject(
                    Fingerprint(sha, Fingerprint.parseHex(json.getString(KEY_DHASH))),
                    picture.getInt(KEY_WIDTH),
                    picture.getInt(KEY_HEIGHT),
                    ops,
                    matte.getInt(KEY_WIDTH),
                    matte.getInt(KEY_HEIGHT),
                    json.getLong(KEY_CREATED),
                    json.getLong(KEY_MODIFIED),
                )
            }
        } catch (e: JSONException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

        /** A grid as bytes, 0..255 per cell. */
        fun encodeGrid(coverage: FloatArray): ByteArray =
            ByteArray(coverage.size) { (coverage[it].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte() }

        /** The inverse of [encodeGrid]; null unless [bytes] fill a [width] x [height] grid. */
        fun decodeGrid(bytes: ByteArray, width: Int, height: Int): FloatArray? =
            if (width <= 0 || height <= 0 || bytes.size != width * height) null
            else FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f }

        private fun size(width: Int, height: Int) = JSONObject().put(KEY_WIDTH, width).put(KEY_HEIGHT, height)

        /** Brush points to 1/100000 of the picture: well under a pixel, and short in JSON. */
        private fun round(v: Float) = Math.round(v * 100000.0) / 100000.0
    }
}
