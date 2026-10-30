/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.updates

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.security.yasvara.YasvaraUpdatePolicy
import org.yasvara.laras.R

/**
 * Owner notices for App updates (SPEC-0005 §4.6). YasvaraUpdatePolicyService decides and rate
 * limits; Laras only words them and handles the actions. One low-importance channel, one
 * notification per app, grouped under a summary.
 */
object UpdateNotifier {
    private const val CHANNEL = "app_updates"
    private const val GROUP = "org.yasvara.laras.APP_UPDATES"
    private const val SUMMARY_ID = 0x5005

    const val ACTION_ALLOW_ONCE = "org.yasvara.laras.action.UPDATE_ALLOW_ONCE"
    const val ACTION_KEEP_VERSION = "org.yasvara.laras.action.UPDATE_KEEP_VERSION"
    const val ACTION_DISMISS = "org.yasvara.laras.action.UPDATE_DISMISS"
    const val ACTION_CLEAR_DATA = "org.yasvara.laras.action.UPDATE_CLEAR_DATA"
    const val ACTION_RESUME = "org.yasvara.laras.action.UPDATE_RESUME"
    const val ACTION_SKIP = "org.yasvara.laras.action.UPDATE_SKIP"
    private const val ACTION_APP_UPDATES = "org.yasvara.laras.action.APP_UPDATES"

    fun post(context: Context, pkg: String, versionCode: Long, installer: String?, decision: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL,
            context.getString(R.string.updates_channel), NotificationManager.IMPORTANCE_LOW))
        val label = labelOf(context, pkg)
        val id = pkg.hashCode()
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_laras)
            .setGroup(GROUP)
            .setAutoCancel(true)
            .setContentIntent(storePage(context, pkg, installer, id))
        if (decision == "reverted") {
            // A core app crash-looped while detached (SPEC-0005 §4.8); tapping opens its sheet.
            builder.setContentTitle(context.getString(R.string.updates_notif_reverted_title, label))
                .bigText(context.getString(R.string.updates_notif_reverted_text))
                .setContentIntent(sheet(context, pkg, id))
        } else if (decision == "rollback_crash") {
            // Crashed right after a rollback: likely newer data in the older version (§4.4).
            builder.setContentTitle(context.getString(R.string.updates_notif_rollback_crash_title, label))
                .bigText(context.getString(R.string.updates_notif_rollback_crash_text))
                .setContentIntent(sheet(context, pkg, id))
                .addAction(Notification.Action.Builder(null,
                    context.getString(R.string.updates_notif_clear_data),
                    action(context, ACTION_CLEAR_DATA, pkg, versionCode, installer, id)).build())
        } else if (decision == "stale") {
            // Paused for 90 days: once, so security fixes are not missed silently (§9).
            builder.setContentTitle(context.getString(R.string.updates_notif_stale_title, label))
                .bigText(context.getString(R.string.updates_notif_stale_text,
                    YasvaraUpdatePolicy.STALE_REMINDER_DAYS))
                .setContentIntent(sheet(context, pkg, id))
                .addAction(Notification.Action.Builder(null,
                    context.getString(R.string.updates_notif_resume),
                    action(context, ACTION_RESUME, pkg, versionCode, installer, id)).build())
        } else if (decision == "ask") {
            builder.setContentTitle(context.getString(R.string.updates_notif_ask_title, label))
                .setContentText(context.getString(R.string.updates_notif_ask_text, versionCode))
                .addAction(Notification.Action.Builder(null,
                    context.getString(R.string.updates_notif_keep_version),
                    action(context, ACTION_KEEP_VERSION, pkg, versionCode, installer, id)).build())
        } else {
            builder.setContentTitle(context.getString(R.string.updates_notif_blocked_title, label))
                .setContentText(context.getString(R.string.updates_notif_blocked_text,
                    labelOf(context, installer) ?: context.getString(R.string.updates_installer_unknown),
                    versionCode))
                .addAction(Notification.Action.Builder(null,
                    context.getString(R.string.updates_notif_allow_once),
                    allowOnce(context, pkg, versionCode, installer, id)).build())
                .addAction(Notification.Action.Builder(null,
                    context.getString(R.string.updates_notif_skip),
                    action(context, ACTION_SKIP, pkg, versionCode, installer, id)).build())
                .addAction(Notification.Action.Builder(null,
                    context.getString(R.string.updates_notif_keep_blocked),
                    action(context, ACTION_DISMISS, pkg, versionCode, installer, id)).build())
        }
        nm.notify(pkg, id, builder.build())
        nm.notify(SUMMARY_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_laras)
            .setContentTitle(context.getString(R.string.updates_title))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build())
    }

    fun cancel(context: Context, pkg: String) {
        context.getSystemService(NotificationManager::class.java).cancel(pkg, pkg.hashCode())
    }

    /** The app's page in the store that installed it, so the owner can retry the update there. */
    fun storeIntent(context: Context, pkg: String, installer: String?): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (installer != null && intent.setPackage(installer)
                .resolveActivity(context.packageManager) != null) {
            return intent
        }
        return intent.setPackage(null)
    }

    private fun Notification.Builder.bigText(text: String) =
        setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))

    /** The app's sheet in Laras › App updates. */
    private fun sheet(context: Context, pkg: String, id: Int) =
        PendingIntent.getActivity(context, id,
            Intent(ACTION_APP_UPDATES).setPackage(context.packageName)
                .putExtra(YasvaraUpdatePolicy.EXTRA_PACKAGE, pkg),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun storePage(context: Context, pkg: String, installer: String?, id: Int) =
        PendingIntent.getActivity(context, id, storeIntent(context, pkg, installer),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    // An activity, not a receiver: it opens the store, and notification trampolines are blocked.
    private fun allowOnce(context: Context, pkg: String, versionCode: Long, installer: String?,
            id: Int): PendingIntent =
        PendingIntent.getActivity(context, id * 31 + ACTION_ALLOW_ONCE.hashCode(),
            Intent(ACTION_ALLOW_ONCE).setClass(context, AllowOnceActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                .putExtra(YasvaraUpdatePolicy.EXTRA_PACKAGE, pkg)
                .putExtra(YasvaraUpdatePolicy.EXTRA_VERSION_CODE, versionCode)
                .putExtra(YasvaraUpdatePolicy.EXTRA_INSTALLER, installer),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun action(context: Context, action: String, pkg: String, versionCode: Long,
            installer: String?, id: Int): PendingIntent =
        PendingIntent.getBroadcast(context, id * 31 + action.hashCode(),
            Intent(action).setClass(context, UpdateActionReceiver::class.java)
                .putExtra(YasvaraUpdatePolicy.EXTRA_PACKAGE, pkg)
                .putExtra(YasvaraUpdatePolicy.EXTRA_VERSION_CODE, versionCode)
                .putExtra(YasvaraUpdatePolicy.EXTRA_INSTALLER, installer),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun labelOf(context: Context, pkg: String?): String? = pkg?.let {
        runCatching {
            context.packageManager.getApplicationInfo(it, 0).loadLabel(context.packageManager).toString()
        }.getOrNull()
    } ?: pkg
}

/** system_server reports a blocked or to-be-reviewed store update (sent with MANAGE_UPDATE_POLICY). */
class UpdateEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != YasvaraUpdatePolicy.ACTION_UPDATE_EVENT) return
        val pkg = intent.getStringExtra(YasvaraUpdatePolicy.EXTRA_PACKAGE) ?: return
        UpdateNotifier.post(context, pkg,
            intent.getLongExtra(YasvaraUpdatePolicy.EXTRA_VERSION_CODE, 0),
            intent.getStringExtra(YasvaraUpdatePolicy.EXTRA_INSTALLER),
            intent.getStringExtra(YasvaraUpdatePolicy.EXTRA_DECISION) ?: "blocked")
    }
}

/** The notification buttons. */
class UpdateActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.getStringExtra(YasvaraUpdatePolicy.EXTRA_PACKAGE) ?: return
        UpdateNotifier.cancel(context, pkg)
        val pending = goAsync()
        Thread {
            try {
                val repository = UpdatePolicyRepository(context)
                when (intent.action) {
                    UpdateNotifier.ACTION_KEEP_VERSION -> repository.set(pkg, UpdatePolicy.NEVER, 0)
                    UpdateNotifier.ACTION_RESUME -> repository.set(pkg, UpdatePolicy.AUTO, 0)
                    UpdateNotifier.ACTION_SKIP -> intent.getLongExtra(YasvaraUpdatePolicy.EXTRA_VERSION_CODE, 0)
                        .takeIf { it > 0 }?.let { repository.skipVersion(pkg, it) }
                    UpdateNotifier.ACTION_CLEAR_DATA -> repository.clearData(pkg)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** "Allow once": lets the next update through for a day, then opens the store page to retry. */
class AllowOnceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(YasvaraUpdatePolicy.EXTRA_PACKAGE)
        if (pkg != null) {
            UpdateNotifier.cancel(this, pkg)
            UpdatePolicyRepository(this).allowOnce(pkg)
            runCatching {
                startActivity(UpdateNotifier.storeIntent(this, pkg,
                    intent.getStringExtra(YasvaraUpdatePolicy.EXTRA_INSTALLER)))
            }
        }
        finish()
    }
}
