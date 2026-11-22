/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.ketik

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.yasvara.laras.R

/** Gboard kept crashing after a change, so the service undid it. The owner hears why. */
class KetikRevertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val flags = intent.getStringArrayListExtra("flags").orEmpty()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.ketik_title), NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_laras)
            .setContentTitle(context.getString(R.string.ketik_reverted_title))
            .setContentText(context.getString(R.string.ketik_reverted_text))
            .setStyle(Notification.BigTextStyle().bigText(
                context.getString(R.string.ketik_reverted_text) + "\n" + flags.joinToString()))
            .setAutoCancel(true)
            .build())
    }

    private companion object {
        const val CHANNEL = "ketik"
        const val ID = 0x5022
    }
}
