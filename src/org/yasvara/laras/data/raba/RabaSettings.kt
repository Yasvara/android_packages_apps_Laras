/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.raba

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import org.json.JSONException
import org.json.JSONObject
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption

/*
 * Raba UI haptics, as system_server reads them (com.android.server.vibrator.RabaProfile): keys,
 * profiles, categories and the HyperOS defaults below mirror it.
 */

const val RABA_SETTING = "yasvara_raba"
const val MASTER_MAX = 1.5f
const val STRENGTH_MIN = 0.05f

enum class RabaProfileKind(val key: String, override val label: Int) : ChoiceOption {
    HYPEROS("hyperos", R.string.raba_profile_hyperos),
    PIXEL("pixel", R.string.raba_profile_pixel),
    CUSTOM("custom", R.string.raba_profile_custom);

    override val value: Int get() = ordinal
}

/** The chip RAM bank's waves by feel; wave 5 is a copy of 4, so it isn't offered. */
enum class Texture(val wave: Int, override val label: Int) : ChoiceOption {
    CRISP(1, R.string.raba_texture_crisp),
    DOUBLE(2, R.string.raba_texture_double),
    FIRM(3, R.string.raba_texture_firm),
    DEEP(4, R.string.raba_texture_deep),
    TAP(6, R.string.raba_texture_tap),
    POP(7, R.string.raba_texture_pop),
    LIGHT(8, R.string.raba_texture_light),
    SHARP(9, R.string.raba_texture_sharp),
    SNAP(10, R.string.raba_texture_snap),
    SOFT(11, R.string.raba_texture_soft);

    override val value: Int get() = wave

    companion object {
        /** Wave 5 plays like 4 and is named so. */
        fun of(wave: Int): Texture = entries.firstOrNull { it.wave == wave } ?: DEEP
    }
}

/** A group Laras edits as one; [lead] is the constant it is previewed with. */
enum class HapticCategory(
    val key: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val lead: Int,
    val wave: Int,
    val strength: Float,
) {
    TAPS("taps", R.string.raba_cat_taps, R.string.raba_cat_taps_summary,
        HapticFeedbackConstants.VIRTUAL_KEY, 6, 1f),
    KEYBOARD("keyboard", R.string.raba_cat_keyboard, R.string.raba_cat_keyboard_summary,
        HapticFeedbackConstants.KEYBOARD_TAP, 8, 1f),
    SWITCHES("switches", R.string.raba_cat_switches, R.string.raba_cat_switches_summary,
        HapticFeedbackConstants.TOGGLE_ON, 10, 1f),
    TICKS("ticks", R.string.raba_cat_ticks, R.string.raba_cat_ticks_summary,
        HapticFeedbackConstants.SEGMENT_TICK, 6, 0.625f),
    EDGES("edges", R.string.raba_cat_edges, R.string.raba_cat_edges_summary,
        HapticFeedbackConstants.SCROLL_LIMIT, 8, 0.375f),
    PRESS("press", R.string.raba_cat_press, R.string.raba_cat_press_summary,
        HapticFeedbackConstants.LONG_PRESS, 1, 0.625f),
    GESTURES("gestures", R.string.raba_cat_gestures, R.string.raba_cat_gestures_summary,
        HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE, 3, 0.375f),
    CONFIRM("confirm", R.string.raba_cat_confirm, R.string.raba_cat_confirm_summary,
        HapticFeedbackConstants.CONFIRM, 7, 1f);

    val hyperOs: CategoryTone get() = CategoryTone(true, wave, strength)
}

data class CategoryTone(val on: Boolean, val wave: Int, val strength: Float)

data class RabaSettings(
    val profile: RabaProfileKind = RabaProfileKind.HYPEROS,
    val master: Float = 1f,
    val reskin: Boolean = true,
    /** Custom's edits; kept while another profile is chosen, so Custom comes back as it was. */
    val custom: Map<HapticCategory, CategoryTone> = emptyMap(),
) {
    /** What a category plays now. */
    fun tone(category: HapticCategory): CategoryTone =
        if (profile == RabaProfileKind.CUSTOM) custom[category] ?: category.hyperOs
        else category.hyperOs

    fun isEdited(category: HapticCategory) =
        profile == RabaProfileKind.CUSTOM && category in custom

    /** Editing switches to Custom; from another profile it starts over from HyperOS's table. */
    fun edit(category: HapticCategory, tone: CategoryTone): RabaSettings {
        val base = if (profile == RabaProfileKind.CUSTOM) custom else emptyMap()
        return copy(profile = RabaProfileKind.CUSTOM, custom = base + (category to tone))
    }

    fun reset(category: HapticCategory) = copy(custom = custom - category)

    /** Null for the defaults, so the setting is removed. */
    fun toJson(): String? {
        if (this == RabaSettings()) return null
        val cats = JSONObject()
        for ((c, t) in custom) {
            cats.put(c.key, JSONObject().put("on", t.on).put("wave", t.wave)
                .put("strength", t.strength.toDouble()))
        }
        return JSONObject()
            .put("profile", profile.key)
            .put("master", master.toDouble())
            .put("reskin", reskin)
            .put("categories", cats)
            .toString()
    }

    companion object {
        fun parse(json: String?): RabaSettings {
            if (json.isNullOrEmpty()) return RabaSettings()
            return try {
                val o = JSONObject(json)
                val cats = o.optJSONObject("categories")
                val custom = buildMap {
                    for (c in HapticCategory.entries) {
                        val t = cats?.optJSONObject(c.key) ?: continue
                        val wave = t.optInt("wave", 0)
                        if (wave !in 1..11) continue
                        val strength = t.optDouble("strength", 1.0).toFloat()
                        put(c, CategoryTone(t.optBoolean("on", true), wave,
                            if (strength.isFinite()) strength.coerceIn(STRENGTH_MIN, 1f) else 1f))
                    }
                }
                val master = o.optDouble("master", 1.0).toFloat()
                RabaSettings(
                    profile = RabaProfileKind.entries.firstOrNull { it.key == o.optString("profile") }
                        ?: RabaProfileKind.HYPEROS,
                    master = if (master.isFinite()) master.coerceIn(0f, MASTER_MAX) else 1f,
                    reskin = o.optBoolean("reskin", true),
                    custom = custom,
                )
            } catch (e: JSONException) {
                RabaSettings()
            }
        }
    }
}
