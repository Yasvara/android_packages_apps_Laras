/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.root

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.yasvara.laras.R

/**
 * A quiet notice when an app that is not enrolled tried to use root.
 * It has no "Allow" button on purpose: enrolling happens in Akar, behind the device
 * credential, so a notification can never be the place root is handed out. The service
 * rate-limits (one per app per ten minutes, from the daemon's own limit).
 */
object RootNotifier {
    private const val CHANNEL = "root_denied"
    private const val ACTION_OPEN = "org.yasvara.laras.action.AKAR"
    const val EXTRA_PACKAGE = "package"

    fun denied(context: Context, uid: Int, pkg: String, reason: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.root_channel), NotificationManager.IMPORTANCE_LOW))
        val label = if (pkg.isEmpty()) "uid $uid" else RootRepository(context).labelOf(pkg)
        val open = PendingIntent.getActivity(context, uid,
            Intent(ACTION_OPEN).setPackage(context.packageName).putExtra(EXTRA_PACKAGE, pkg),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val text = when (reason) {
            "frozen" -> context.getString(R.string.root_notif_frozen)
            "locked" -> context.getString(R.string.root_notif_locked)
            "expired" -> context.getString(R.string.root_notif_expired)
            else -> context.getString(R.string.root_notif_text)
        }
        nm.notify("root", uid, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_laras)
            .setContentTitle(context.getString(R.string.root_notif_title, label))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build())
    }
}

/** Sent by YasvaraRootService (which holds MANAGE_ROOT) when the daemon refused an app. */
class RootDeniedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RootNotifier.denied(
            context,
            intent.getIntExtra("uid", -1),
            intent.getStringExtra("package").orEmpty(),
            intent.getStringExtra("reason").orEmpty(),
        )
    }
}

/**
 * "Root freeze" in Quick Settings: while on, the daemon refuses every request (an instant,
 * zero-cost kill switch). The tile only changes state when the owner taps it, so it costs
 * nothing at rest.
 */
class RootFreezeTile : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        scope.launch {
            val repo = RootRepository(applicationContext)
            val s = repo.state() ?: return@launch
            // Unfreezing gives root back, so it needs the device unlocked; freezing never does.
            if (s.frozen) unlockAndRun { scope.launch { repo.setFrozen(false); refresh() } }
            else { repo.setFrozen(true); refresh() }
        }
    }

    private fun refresh() {
        scope.launch {
            val s = RootRepository(applicationContext).state()
            qsTile?.apply {
                state = when {
                    s == null || !s.daemonInstalled -> Tile.STATE_UNAVAILABLE
                    s.frozen -> Tile.STATE_ACTIVE
                    else -> Tile.STATE_INACTIVE
                }
                label = getString(R.string.root_tile)
                subtitle = when {
                    s == null || !s.daemonInstalled -> getString(R.string.root_tile_unavailable)
                    !s.enabled -> getString(R.string.root_tile_off)
                    s.frozen -> getString(R.string.root_tile_frozen)
                    else -> getString(R.string.root_tile_ready)
                }
                icon = Icon.createWithResource(applicationContext, R.drawable.ic_laras)
                updateTile()
            }
        }
    }
}
