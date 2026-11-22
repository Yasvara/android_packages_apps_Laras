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
import org.yasvara.laras.ui.LarasApp
import org.yasvara.laras.ui.Route
import org.yasvara.laras.ui.theme.LarasTheme

/** Dedicated task entry point for Akar homescreen shortcuts and the optional app drawer alias. */
class AkarActivity : ComponentActivity() {
    private var startRoute by mutableStateOf<Route>(Route.Akar())
    private var launchSeq by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        applyIntent(intent)
        setContent { LarasTheme { LarasApp(start = startRoute, launchSeq = launchSeq) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
        launchSeq++
    }

    private fun applyIntent(intent: Intent?) {
        val pkg = intent?.getStringExtra(EXTRA_PACKAGE)?.takeIf { it.isNotEmpty() }
        startRoute = Route.Akar(pkg)
    }

    private companion object {
        const val EXTRA_PACKAGE = "package"
    }
}
