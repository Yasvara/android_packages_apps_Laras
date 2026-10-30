/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.debug

import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import lineageos.providers.LineageSettings
import org.yasvara.monet.YasColorSpec
import org.yasvara.monet.YasSeedResolver
import org.yasvara.monet.YasvaraMonet

/**
 * Fingerprint parity (SPEC-0002 §3.3, §10), on debuggable builds only:
 * `am start -a org.yasvara.laras.action.OPEN --es yas_resolve '<spec json>'` logs the fingerprint
 * Laras resolves for the spec on this device, which `vendor/yasvara/tests/color/
 * fingerprint_parity.sh` compares with the one SystemUI reports after applying it.
 */
object ColorDebug {
    private const val TAG = "LarasColorDebug"
    private const val EXTRA = "yas_resolve"

    fun handle(context: Context, intent: Intent?) {
        if (!Build.IS_DEBUGGABLE) return
        val json = intent?.getStringExtra(EXTRA) ?: return
        val spec = YasColorSpec.parse(json)
        if (spec == null) {
            Log.w(TAG, "fp=unreadable")
            return
        }
        val wallpaper = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        val contrast = context.getSystemService(UiModeManager::class.java).contrast.toDouble()
        val pureBlack = LineageSettings.Secure.getInt(
            context.contentResolver, LineageSettings.Secure.BERRY_BLACK_THEME, 0,
        ) == 1
        val resolved = YasvaraMonet.resolve(
            spec, YasSeedResolver.seed(spec, wallpaper), contrast, pureBlack,
        )
        Log.i(TAG, "fp=${resolved.fingerprint}")
    }
}
