/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.brightness

import android.content.ContentResolver
import android.provider.Settings
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption

/*
 * Brightness tuning as system_server reads it (com.android.server.display.BrightnessTuning and
 * ThermalBrightnessGuard): keys, ranges and presets mirror it. An absent key is the build's tuning.
 */

const val KEY_PROFILE = "yasvara_brightness_profile"
const val KEY_SPEED_UP = "yasvara_brightness_speed_up"
const val KEY_SPEED_DOWN = "yasvara_brightness_speed_down"
const val KEY_DEBOUNCE_UP = "yasvara_brightness_debounce_up_ms"
const val KEY_DEBOUNCE_DOWN = "yasvara_brightness_debounce_down_ms"
const val KEY_SUN = "yasvara_brightness_sun"
const val KEY_SUN_LUX = "yasvara_brightness_sun_lux"
const val KEY_FLOOR = "yasvara_brightness_floor_nits"
const val KEY_CEILING = "yasvara_brightness_ceiling_nits"
const val KEY_THERMAL_GUARD = "yasvara_brightness_thermal_guard"

val BRIGHTNESS_KEYS = listOf(
    KEY_PROFILE, KEY_SPEED_UP, KEY_SPEED_DOWN, KEY_DEBOUNCE_UP, KEY_DEBOUNCE_DOWN, KEY_SUN,
    KEY_SUN_LUX, KEY_FLOOR, KEY_CEILING, KEY_THERMAL_GUARD,
)

const val SPEED_MIN = 0.5f
const val SPEED_MAX = 4f
const val SUN_LUX_MIN = 2000f
const val SUN_LUX_MAX = 20000f
/** The panel's HBM entry lux, which the framework uses while the key is unset. */
const val SUN_LUX_DEFAULT = 4475f
const val LIMIT_GAP_NITS = 50f
const val DEBOUNCE_MIN = 100
const val DEBOUNCE_MAX = 5000
/** The panel's auto-brightness debounce, which the framework uses while the keys are unset. */
const val DEBOUNCE_UP_DEFAULT = 500
const val DEBOUNCE_DOWN_DEFAULT = 2000

enum class BrightnessProfile(
    override val value: Int,
    override val label: Int,
    val speedUp: Float,
    val speedDown: Float,
    val debounceUp: Int?,
    val debounceDown: Int?,
) : ChoiceOption {
    GENTLE(1, R.string.pijar_profile_gentle, 0.6f, 0.6f, 1000, 3000),
    BALANCED(0, R.string.pijar_profile_balanced, 1f, 1f, null, null),
    RESPONSIVE(2, R.string.pijar_profile_responsive, 1.6f, 2f, 250, 1000),
    CUSTOM(3, R.string.pijar_profile_custom, 1f, 1f, null, null);

    companion object {
        val presets = listOf(GENTLE, BALANCED, RESPONSIVE)
        fun of(value: Int) = entries.firstOrNull { it.value == value } ?: BALANCED
    }
}

enum class SunMode(override val value: Int, override val label: Int) : ChoiceOption {
    CURVE(1, R.string.pijar_sun_curve),
    FIXED(2, R.string.pijar_sun_max),
}

data class BrightnessSettings(
    val profile: BrightnessProfile = BrightnessProfile.BALANCED,
    val speedUp: Float = 1f,
    val speedDown: Float = 1f,
    /** Null while the build's debounce applies. */
    val debounceUp: Int? = null,
    val debounceDown: Int? = null,
    val sunOn: Boolean = true,
    val sunMode: SunMode = SunMode.CURVE,
    /** Null while unset. */
    val sunLux: Float? = null,
    val floorNits: Float? = null,
    val ceilingNits: Float? = null,
    val thermalGuard: Boolean = false,
) {
    val isDefault: Boolean get() = this == BrightnessSettings()

    companion object {
        fun read(resolver: ContentResolver): BrightnessSettings {
            fun str(key: String) = Settings.Secure.getString(resolver, key)?.trim()
            fun int(key: String) = str(key)?.toIntOrNull()
            fun float(key: String) = str(key)?.toFloatOrNull()?.takeIf { it.isFinite() }

            val profile = BrightnessProfile.of(int(KEY_PROFILE) ?: 0)
            val custom = profile == BrightnessProfile.CUSTOM
            val sun = int(KEY_SUN)?.coerceIn(0, 2) ?: 1
            return BrightnessSettings(
                profile = profile,
                speedUp = if (custom) (float(KEY_SPEED_UP) ?: 1f).coerceIn(SPEED_MIN, SPEED_MAX)
                else profile.speedUp,
                speedDown = if (custom) (float(KEY_SPEED_DOWN) ?: 1f).coerceIn(SPEED_MIN, SPEED_MAX)
                else profile.speedDown,
                debounceUp = if (custom) int(KEY_DEBOUNCE_UP)?.takeIf { it >= 0 }
                    ?.coerceIn(DEBOUNCE_MIN, DEBOUNCE_MAX) else profile.debounceUp,
                debounceDown = if (custom) int(KEY_DEBOUNCE_DOWN)?.takeIf { it >= 0 }
                    ?.coerceIn(DEBOUNCE_MIN, DEBOUNCE_MAX) else profile.debounceDown,
                sunOn = sun != 0,
                sunMode = if (sun == 2) SunMode.FIXED else SunMode.CURVE,
                sunLux = float(KEY_SUN_LUX)?.coerceIn(SUN_LUX_MIN, SUN_LUX_MAX),
                floorNits = float(KEY_FLOOR),
                ceilingNits = float(KEY_CEILING),
                thermalGuard = (int(KEY_THERMAL_GUARD) ?: 0) != 0,
            )
        }
    }
}
