/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.color

import android.content.Context
import com.google.ux.material.libmonet.hct.Hct
import org.yasvara.laras.R
import kotlin.math.roundToInt

/**
 * Colour names for screen readers (SPEC-0002 §5.8): "Primary, blue, tone 40". Hue buckets follow
 * HCT hue, so a name matches what the engine sees; low chroma reads as grey.
 */
object ColorNames {
    // HCT hue at the start of each bucket, and its name.
    private val HUES = listOf(
        0.0 to R.string.color_name_pink,
        20.0 to R.string.color_name_red,
        45.0 to R.string.color_name_orange,
        70.0 to R.string.color_name_amber,
        95.0 to R.string.color_name_yellow,
        115.0 to R.string.color_name_lime,
        135.0 to R.string.color_name_green,
        165.0 to R.string.color_name_teal,
        195.0 to R.string.color_name_cyan,
        225.0 to R.string.color_name_sky,
        250.0 to R.string.color_name_blue,
        275.0 to R.string.color_name_indigo,
        300.0 to R.string.color_name_purple,
        330.0 to R.string.color_name_magenta,
        350.0 to R.string.color_name_pink,
    )
    private const val GREY_CHROMA = 8.0

    fun describe(context: Context, role: String, argb: Int): String {
        val hct = Hct.fromInt(argb)
        val name = if (hct.chroma < GREY_CHROMA) R.string.color_name_grey
            else HUES.last { hct.hue >= it.first }.second
        return context.getString(
            R.string.color_swatch_description, role, context.getString(name), hct.tone.roundToInt(),
        )
    }
}
