/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.yasvara.laras.debug.ColorDebug
import org.yasvara.laras.debug.SeamSpike
import org.yasvara.laras.ui.LarasApp
import org.yasvara.laras.ui.Route
import org.yasvara.laras.ui.theme.LarasTheme

class LarasActivity : ComponentActivity() {
    private var startRoute by mutableStateOf<Route>(Route.Home)
    private var launchSeq by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        SeamSpike.handle(this, intent)
        ColorDebug.handle(this, intent)
        startRoute = routeFromIntent(intent)
        setContent { LarasTheme { LarasApp(start = startRoute, launchSeq = launchSeq) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        startRoute = routeFromIntent(intent)
        launchSeq++
    }

    private fun routeFromIntent(intent: Intent?): Route = when (intent?.action) {
        ACTION_APP_UPDATES -> Route.AppUpdates(intent.getStringExtra(EXTRA_PACKAGE))
        // A refused root request's notification opens Akar on that app.
        ACTION_AKAR -> Route.Akar(intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() })
        ACTION_KAIT -> Route.Kait(intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() })
        // The Volume Panel's "Manage" line.
        ACTION_SOUND_APPS -> Route.SoundApps
        ACTION_WIFI_REGION -> Route.WifiRegion
        ACTION_PADAT -> Route.Padat(intent.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() })
        else -> Route.Home
    }

    private companion object {
        const val ACTION_APP_UPDATES = "org.yasvara.laras.action.APP_UPDATES"
        const val ACTION_AKAR = "org.yasvara.laras.action.AKAR"
        const val ACTION_KAIT = "org.yasvara.laras.action.KAIT"
        const val ACTION_SOUND_APPS = "org.yasvara.laras.action.SOUND_APPS"
        const val ACTION_WIFI_REGION = "org.yasvara.laras.action.WIFI_REGION"
        const val ACTION_PADAT = "org.yasvara.laras.action.PADAT"
        const val EXTRA_PACKAGE = "package"
    }
}
