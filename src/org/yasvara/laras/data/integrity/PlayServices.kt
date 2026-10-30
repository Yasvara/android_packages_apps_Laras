/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Spoof settings are read when a process starts, so a change only takes effect after the
 * affected processes restart. Edits mark a restart as pending and the user applies them at once.
 */
object PlayServices {
    private val PACKAGES = listOf(
        "com.google.android.gms", "com.google.android.gms.unstable",
        "com.android.vending", "com.google.android.gsf",
    )

    private val _restartPending = MutableStateFlow(false)
    val restartPending: StateFlow<Boolean> = _restartPending.asStateFlow()

    fun markRestartNeeded() {
        _restartPending.value = true
    }

    fun restart(context: Context) {
        kill(context, PACKAGES)
        _restartPending.value = false
    }

    fun kill(context: Context, packages: List<String>) {
        try {
            val am = context.getSystemService(ActivityManager::class.java)
            packages.forEach { am.killBackgroundProcesses(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restart $packages", e)
        }
    }
}

private const val TAG = "PlayServices"
