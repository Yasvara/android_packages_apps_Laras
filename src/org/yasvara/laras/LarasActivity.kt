/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.yasvara.laras.debug.ColorDebug
import org.yasvara.laras.debug.SeamSpike
import org.yasvara.laras.ui.LarasApp
import org.yasvara.laras.ui.Route
import org.yasvara.laras.ui.theme.LarasTheme

class LarasActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        SeamSpike.handle(this, intent)
        ColorDebug.handle(this, intent)
        // App info › Updates (SPEC-0005 §4.9) opens straight on that app's update sheet.
        val start = when (intent?.action) {
            ACTION_APP_UPDATES -> Route.AppUpdates(intent.getStringExtra(EXTRA_PACKAGE))
            // A refused root request's notification (SPEC-0006) opens Akar on that app.
            ACTION_AKAR -> Route.Akar(intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() })
            // The Volume Panel's "Manage" line (SPEC-0010 §5.1).
            ACTION_SOUND_APPS -> Route.SoundApps
            ACTION_WIFI_REGION -> Route.WifiRegion
            else -> Route.Home
        }
        setContent { LarasTheme { LarasApp(start) } }
    }

    private companion object {
        const val ACTION_APP_UPDATES = "org.yasvara.laras.action.APP_UPDATES"
        const val ACTION_AKAR = "org.yasvara.laras.action.AKAR"
        const val ACTION_SOUND_APPS = "org.yasvara.laras.action.SOUND_APPS"
        const val ACTION_WIFI_REGION = "org.yasvara.laras.action.WIFI_REGION"
        const val EXTRA_PACKAGE = "package"
    }
}
