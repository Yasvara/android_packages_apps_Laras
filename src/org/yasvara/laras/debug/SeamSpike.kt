/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.debug

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View

/**
 * The M0 preview colour seam spike (SPEC-0002 §11 Q6), on debuggable builds only.
 *
 * `vendor/yasvara/tests/color/seam_spike.sh` drives it through the launch intent:
 * - `--es yas_seam capture` stores the system colours now applied (the reference palette);
 * - `--es yas_seam colorres|frro` makes the lock screen canvas ask SystemUI to preview the
 *   captured palette through that seam, while a different palette is applied;
 * - `--es yas_seam off` stops.
 * The script compares screenshots of the canvas against the reference.
 */
object SeamSpike {
    private const val TAG = "LarasSeamSpike"
    private const val EXTRA = "yas_seam"
    private const val PREFS = "seam_spike"
    private const val KEY_SEAM = "seam"
    private const val KEY_IDS = "ids"
    private const val KEY_VALUES = "values"

    fun handle(context: Context, intent: Intent?) {
        if (!Build.IS_DEBUGGABLE) return
        val command = intent?.getStringExtra(EXTRA) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (command) {
            "capture" -> {
                val ids = systemColorIds()
                prefs.edit()
                    .putString(KEY_IDS, ids.joinToString(","))
                    .putString(KEY_VALUES, ids.joinToString(",") { context.getColor(it).toString() })
                    .commit()
                Log.i(TAG, "Captured ${ids.size} system colours")
            }
            "off" -> prefs.edit().remove(KEY_SEAM).commit()
            else -> prefs.edit().putString(KEY_SEAM, command).commit()
        }
        Log.i(TAG, "seam=${prefs.getString(KEY_SEAM, "off")}")
    }

    /** Extra preview request keys for the active spike, or none. */
    fun previewExtras(context: Context): Bundle {
        if (!Build.IS_DEBUGGABLE) return Bundle.EMPTY
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seam = prefs.getString(KEY_SEAM, null) ?: return Bundle.EMPTY
        val ids = prefs.getString(KEY_IDS, null)?.split(",")?.map(String::toInt) ?: return Bundle.EMPTY
        val values = prefs.getString(KEY_VALUES, null)?.split(",")?.map(String::toInt)
            ?: return Bundle.EMPTY
        return Bundle().apply {
            putIntArray("yas_color_ids", ids.toIntArray())
            putIntArray("yas_color_values", values.toIntArray())
            putString("yas_color_seam", seam)
        }
    }

    /** Logs where [view] sits on screen, so the script can crop its screenshots to the canvas. */
    fun reportCanvas(view: View) {
        if (!Build.IS_DEBUGGABLE) return
        view.post {
            val bounds = Rect()
            if (view.getGlobalVisibleRect(bounds)) Log.i(TAG, "canvas=${bounds.flattenToString()}")
        }
    }

    private fun systemColorIds(): List<Int> =
        android.R.color::class.java.fields
            .filter { it.name.startsWith("system_") }
            .map { it.getInt(null) }
            .sorted()
}
