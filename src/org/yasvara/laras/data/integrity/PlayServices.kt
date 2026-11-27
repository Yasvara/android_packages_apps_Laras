/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.app.ActivityManager
import android.content.Context
import android.security.yasvara.YasvaraSpoofEngine
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
        YasvaraSpoofEngine.PKG_GMS,
        YasvaraSpoofEngine.PKG_VENDING,
        YasvaraSpoofEngine.PKG_GSF,
    )

    private val _restartPending = MutableStateFlow(false)
    val restartPending: StateFlow<Boolean> = _restartPending.asStateFlow()

    /**
     * Marks a Play Services restart as pending. When [autoHandledByService] is true, skips the
     * banner if [AttestationService] is reachable because YasvaraAttestationService already
     * SIGKILLs GMS / Play Store automatically on setting/keybox changes.
     */
    fun markRestartNeeded(autoHandledByService: Boolean = false) {
        if (autoHandledByService && AttestationService.get() != null) return
        _restartPending.value = true
    }

    fun restart(context: Context) {
        if (AttestationService.purgeIntegrityCache() < 0) {
            kill(context, PACKAGES)
        }
        _restartPending.value = false
    }

    fun kill(context: Context, packages: List<String>) {
        if (packages.isEmpty()) return
        val targets = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (targets.isEmpty()) return

        // Delegate to YasvaraAttestationService in system_server (CAP_KILL), which SIGKILLs
        // every running process owned by the target packages (including DroidGuard
        // com.google.android.gms.unstable, com.google.process.gapps, and bound/foreground
        // GMS/Play Store processes) without setting FLAG_STOPPED.
        val killedByService = try {
            AttestationService.get()?.restartPackages(targets.toTypedArray()) ?: -1
        } catch (e: Throwable) {
            Log.w(TAG, "AttestationService.restartPackages failed for $targets", e)
            -1
        }

        // Also request background process cleanup via ActivityManager as a belt-and-suspenders
        // fallback (or when the attestation service is unavailable).
        try {
            val am = context.getSystemService(ActivityManager::class.java)
            if (am != null) {
                for (pkg in targets) {
                    val basePkg = pkg.removeSuffix(".unstable").substringBefore(':')
                    am.killBackgroundProcesses(basePkg)
                }
            }
        } catch (e: Exception) {
            if (killedByService < 0) {
                Log.w(TAG, "Could not restart $targets", e)
            }
        }
    }
}

private const val TAG = "PlayServices"
