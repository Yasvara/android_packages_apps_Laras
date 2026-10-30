/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * The lock screen clock face, as SystemUI stores it in
 * Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE: which face, an optional seed colour, and the
 * face's style axes. [id] null is the system default.
 */
data class ClockFace(
    val id: String? = null,
    val seedColor: Int? = null,
    val axes: Map<String, Float> = emptyMap(),
) {
    fun with(axis: String, value: Float) = copy(axes = axes + (axis to value))

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_ID, id ?: JSONObject.NULL)
        put(KEY_SEED, seedColor ?: JSONObject.NULL)
        put(KEY_METADATA, JSONObject())
        put(KEY_AXES, JSONArray().apply {
            axes.forEach { (key, value) ->
                put(JSONObject().put(KEY_AXIS_KEY, key).put(KEY_AXIS_VALUE, value.toDouble()))
            }
        })
    }

    companion object {
        private const val KEY_ID = "clockId"
        private const val KEY_SEED = "seedColor"
        private const val KEY_METADATA = "metadata"
        private const val KEY_AXES = "axes"
        private const val KEY_AXIS_KEY = "key"
        private const val KEY_AXIS_VALUE = "value"

        val DEFAULT = ClockFace()

        fun fromJson(json: JSONObject): ClockFace {
            val axes = mutableMapOf<String, Float>()
            json.optJSONArray(KEY_AXES)?.let { array ->
                for (i in 0 until array.length()) {
                    val axis = array.optJSONObject(i) ?: continue
                    val key = axis.optString(KEY_AXIS_KEY).takeIf { it.isNotEmpty() } ?: continue
                    val value = axis.optDouble(KEY_AXIS_VALUE).takeIf { !it.isNaN() } ?: continue
                    axes[key] = value.toFloat()
                }
            }
            return ClockFace(
                id = if (json.isNull(KEY_ID)) null else json.optString(KEY_ID),
                seedColor = if (json.isNull(KEY_SEED)) null else json.optInt(KEY_SEED),
                axes = axes,
            )
        }
    }
}
