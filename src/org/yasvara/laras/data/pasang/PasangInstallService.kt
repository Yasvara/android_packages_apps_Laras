/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.pasang

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.yasvara.laras.LarasActivity
import org.yasvara.laras.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ephemeral foreground service that mirrors `pasangd` Live CoW OTA progress into an ongoing
 * notification with real-time partition, speed (MB/s), and ETA — plus Cancel / Restart actions.
 *
 * Strictly zero idle RAM: starts only while an OTA is active and calls `stopSelf()` immediately
 * after posting the completion/error notification.
 */
class PasangInstallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }
    private val repo by lazy { PasangRepository(this) }
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                scope.launch { repo.cancelOta() }
                return START_NOT_STICKY
            }
            ACTION_REBOOT -> {
                notifications.cancel(NOTIF_RESULT_ID)
                scope.launch {
                    if (!repo.rebootToNewSlot()) {
                        Log.w(TAG, "rebootToNewSlot rejected from notification")
                    }
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Start foreground immediately to satisfy Android FGS timing budget
        val initial = buildProgressNotification(PasangStatus(phase = "preflight", busy = true))
        startForeground(
            NOTIF_PROGRESS_ID,
            initial,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (monitorJob?.isActive != true) {
            monitorJob = scope.launch { monitorLoop() }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun CoroutineScope.monitorLoop() {
        var missedTicks = 0
        var sawBusy = false
        while (isActive) {
            val st = repo.fetchStatus()
            if (st == null) {
                missedTicks++
                if (missedTicks >= 6) break
                delay(500L)
                continue
            }
            missedTicks = 0

            if (st.busy) {
                sawBusy = true
                notifications.notify(NOTIF_PROGRESS_ID, buildProgressNotification(st))
                delay(400L)
                continue
            }

            // Terminal or idle state reached
            stopForeground(STOP_FOREGROUND_REMOVE)
            when (st.phase) {
                "done" -> postResultNotification(
                    title = getString(R.string.pasang_notif_done_title),
                    body = getString(
                        R.string.pasang_notif_done_body,
                        if (st.targetSlot == 0) "A" else "B",
                    ),
                    showRebootAction = st.rebootRequired,
                )
                "error" -> postResultNotification(
                    title = getString(R.string.pasang_phase_error),
                    body = st.error.ifBlank { getString(R.string.pasang_phase_error) },
                    showRebootAction = false,
                )
                "cancelled" -> postResultNotification(
                    title = getString(R.string.pasang_phase_cancelled),
                    body = getString(R.string.pasang_notif_cancelled_body),
                    showRebootAction = false,
                )
                else -> {
                    // If daemon already finished before first poll and reported idle, just exit quietly
                    if (!sawBusy) break
                }
            }
            break
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun ensureChannels() {
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            getString(R.string.pasang_notif_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        val resultChannel = NotificationChannel(
            CHANNEL_RESULT,
            getString(R.string.pasang_notif_channel_result),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        notifications.createNotificationChannel(progressChannel)
        notifications.createNotificationChannel(resultChannel)
    }

    private fun openPasangPendingIntent(): PendingIntent {
        val intent = Intent(this, LarasActivity::class.java).apply {
            action = "org.yasvara.laras.action.PASANG"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun serviceActionIntent(action: String, reqCode: Int): PendingIntent {
        val intent = Intent(this, PasangInstallService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildProgressNotification(st: PasangStatus): Notification {
        val phaseTitle = when (st.phase) {
            "preflight" -> getString(R.string.pasang_phase_preflight)
            "indexing" -> if (st.currentPartition.isNotBlank()) {
                getString(R.string.pasang_notif_indexing_part, st.currentPartition)
            } else {
                getString(R.string.pasang_phase_indexing)
            }
            "planning" -> getString(R.string.pasang_phase_planning)
            "flashing" -> getString(
                R.string.pasang_phase_flashing,
                st.currentPartition.ifBlank { "super" },
            )
            "verifying" -> if (st.currentPartition.isNotBlank()) {
                getString(R.string.pasang_notif_verifying_part, st.currentPartition)
            } else {
                getString(R.string.pasang_phase_verifying)
            }
            "committing" -> getString(R.string.pasang_phase_committing)
            "cleaning_overlay" -> getString(R.string.pasang_phase_cleaning_overlay)
            else -> getString(R.string.pasang_title)
        }

        val indeterminate = st.phase != "flashing" || st.bytesToWrite <= 0L
        val pctInt = st.progressPct.roundToInt().coerceIn(0, 100)

        val detail = if (st.phase == "flashing" && st.bytesToWrite > 0L) {
            val written = Formatter.formatShortFileSize(this, st.bytesWritten)
            val total = Formatter.formatShortFileSize(this, st.bytesToWrite)
            val speed = if (st.speedBps > 0L) {
                "${Formatter.formatShortFileSize(this, st.speedBps)}/s"
            } else {
                "—"
            }
            val eta = formatEta(st.etaSeconds)
            getString(R.string.pasang_telemetry_line, written, total, speed, eta)
        } else {
            val sec = (st.elapsedMs / 1000L).coerceAtLeast(0L)
            val aliasNote = if (st.aliasPct > 0f) {
                " · " + getString(
                    R.string.pasang_alias_badge,
                    String.format(Locale.US, "%.1f", st.aliasPct),
                )
            } else {
                ""
            }
            getString(R.string.pasang_notif_elapsed, formatEta(sec)) + aliasNote
        }

        val cancelAction = Notification.Action.Builder(
            null,
            getString(R.string.pasang_cancel_action),
            serviceActionIntent(ACTION_CANCEL, 101),
        ).build()

        return Notification.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_laras)
            .setContentTitle(phaseTitle)
            .setContentText(detail)
            .setSubText(if (!indeterminate) "$pctInt%" else getString(R.string.pasang_title))
            .setProgress(100, pctInt, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openPasangPendingIntent())
            .addAction(cancelAction)
            .build()
    }

    private fun postResultNotification(
        title: String,
        body: String,
        showRebootAction: Boolean,
    ) {
        val builder = Notification.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_laras)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openPasangPendingIntent())

        if (showRebootAction) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.pasang_reboot_action),
                    serviceActionIntent(ACTION_REBOOT, 102),
                ).build(),
            )
        }
        notifications.notify(NOTIF_RESULT_ID, builder.build())
    }

    private fun formatEta(seconds: Long): String {
        if (seconds < 0L) return "…"
        val m = seconds / 60L
        val s = seconds % 60L
        return if (m > 0L) "${m}m ${s}s" else "${s}s"
    }

    companion object {
        private const val TAG = "PasangInstallSvc"
        private const val CHANNEL_PROGRESS = "pasang_ota_progress"
        private const val CHANNEL_RESULT = "pasang_ota_result"
        private const val NOTIF_PROGRESS_ID = 4201
        private const val NOTIF_RESULT_ID = 4202
        private const val ACTION_MONITOR = "org.yasvara.laras.pasang.action.MONITOR"
        private const val ACTION_CANCEL = "org.yasvara.laras.pasang.action.CANCEL"
        private const val ACTION_REBOOT = "org.yasvara.laras.pasang.action.REBOOT"

        fun startMonitoring(context: Context) {
            runCatching {
                val intent = Intent(context, PasangInstallService::class.java).setAction(ACTION_MONITOR)
                context.startForegroundService(intent)
            }.onFailure {
                Log.w(TAG, "Failed to start PasangInstallService: ${it.message}")
            }
        }
    }
}
