/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.yasvara.laras.R

/**
 * Makes depth without anyone waiting for it (docs/specs/0013 §4, §9 Q3), as a foreground service
 * with a notification, one picture at a time:
 *
 * - SystemUI asks ([BuildRequestReceiver]) when the lock screen shows a picture with no depth;
 *   the build is saved and handed to the lock screen as it is, then "Depth is on your lock screen"
 *   offers a review;
 * - the Studio asks when it opens on a picture with no build, and shows the progress itself.
 *
 * ToonOut needs about 3 GB for a minute or two, so a build waits while memory is short (a game),
 * the phone is hot, or Battery Saver is on. Stopping a requested build remembers the picture, so
 * SystemUI's next request for it is ignored; the Studio can always start one.
 */
class DepthBuildService : Service() {

    sealed interface State {
        /** The picture it is about. */
        val sha: String?

        data object Idle : State {
            override val sha: String? get() = null
        }

        data class Waiting(override val sha: String) : State
        data class Running(
            override val sha: String,
            val stage: DepthBuilder.Stage,
            val step: Int,
            val steps: Int,
            val tile: Int,
            val tiles: Int,
            /** S2's tiles once placed (left, top, right, bottom) on the [workWidth] x [workHeight] picture. */
            val rects: List<IntArray> = emptyList(),
            val workWidth: Int = 0,
            val workHeight: Int = 0,
            /** When the build started (elapsed realtime), for an estimate of what is left. */
            val started: Long = 0,
        ) : State
        data class Done(override val sha: String) : State
        data class Failed(override val sha: String) : State
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val queued = LinkedHashSet<String>()
    private val cancel = AtomicBoolean(false)
    private val library by lazy { DepthLibrary(this) }
    private val notifications by lazy { getSystemService(NotificationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.depth_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_PROGRESS, progress(null),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
        )
        when (intent?.action) {
            ACTION_STOP -> {
                cancel.set(true)
                synchronized(queued) { queued.clear() }
            }
            ACTION_BUILD -> {
                val sha = intent.getStringExtra(DepthStudioActivity.EXTRA_FINGERPRINT)
                val studio = intent.getBooleanExtra(EXTRA_STUDIO, false)
                if (sha != null && Fingerprint.isSha(sha) && synchronized(queued) { queued.add(sha) }) {
                    worker.execute { run(sha, studio) }
                }
            }
        }
        if (synchronized(queued) { queued.isEmpty() } && _state.value !is State.Running) finish()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Media processing may run 6 hours a day; a build that is still waiting gives up.
        cancel.set(true)
        finish()
    }

    override fun onDestroy() {
        cancel.set(true)
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun run(sha: String, studio: Boolean) {
        cancel.set(false)
        try {
            build(sha, studio)
        } catch (e: DepthBuilder.Cancelled) {
            stopped(sha, studio)
        } catch (e: InterruptedException) {
            // Stopped while it waited for room.
            stopped(sha, studio)
        } catch (e: Exception) {
            Log.e(TAG, "Build of ${sha.take(12)} failed", e)
            _state.value = State.Failed(sha)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Build of ${sha.take(12)} ran out of memory", e)
            _state.value = State.Failed(sha)
        } finally {
            synchronized(queued) { queued.remove(sha) }
            if (synchronized(queued) { queued.isEmpty() }) finish()
        }
    }

    private fun stopped(sha: String, studio: Boolean) {
        Log.i(TAG, "Build of ${sha.take(12)} stopped")
        if (!studio) declined(this).edit().putBoolean(sha, true).apply()
        _state.value = State.Idle
    }

    private fun build(sha: String, studio: Boolean) {
        val picture = picture(sha) ?: run {
            Log.i(TAG, "Picture ${sha.take(12)} is gone; nothing to build")
            return
        }
        val lean = waitForRoom(sha, studio)
        val started = SystemClock.elapsedRealtime()
        var rects = emptyList<IntArray>()
        var size = 0 to 0
        val builder = DepthBuilder(this, lean)
        builder.onTiles = { tiles, w, h ->
            rects = tiles
            size = w to h
        }
        val result = builder.build(
            picture,
            { stage, step, steps, tile, tiles ->
                _state.value = State.Running(sha, stage, step, steps, tile, tiles, rects, size.first, size.second, started)
                notifications.notify(NOTIFICATION_PROGRESS, progress(_state.value))
            },
            { cancel.get() },
        )
        library.saveBuild(sha, result, picture.bytes)
        if (!studio && library.load(sha) == null) apply(sha, picture, result)
        _state.value = State.Done(sha)
    }

    /**
     * Gives the lock screen the build as it is, as the start of a project the Studio can open
     * (SPEC-0013 §9 Q3), and says so.
     */
    private fun apply(sha: String, picture: WallpaperPicture, build: DepthBuild) {
        val (w, h) = DepthStudioActivity.workingSize(picture.bitmap.width, picture.bitmap.height)
        val selection = Selection(w, h).apply { push(SelectionOp.Auto(build)) }
        val project = DepthSave.save(this, library, selection, picture, null) ?: return
        val review = PendingIntent.getActivity(
            this, 0,
            Intent(this, DepthStudioActivity::class.java)
                .putExtra(DepthStudioActivity.EXTRA_FINGERPRINT, sha)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val off = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, TurnOffReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val thumb = BitmapFactory.decodeFile(library.thumb(project.fingerprint.sha).path)
        val text = if (build.props == 0) getString(R.string.depth_ready_subject)
        else resources.getQuantityString(R.plurals.depth_ready_text, build.props, build.props)
        notifications.notify(
            NOTIFICATION_READY,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_laras)
                .setContentTitle(getString(R.string.depth_ready_title))
                .setContentText(text)
                .setStyle(thumb?.let { Notification.BigPictureStyle().bigPicture(it) })
                .setContentIntent(review)
                .addAction(Notification.Action.Builder(null, getString(R.string.depth_review), review).build())
                .addAction(Notification.Action.Builder(null, getString(R.string.depth_turn_off), off).build())
                .setAutoCancel(true)
                .build(),
        )
    }

    /** The picture [sha] names: the lock screen's, or a saved one's original file. */
    private fun picture(sha: String): WallpaperPicture? =
        library.picture(sha).takeIf { it.isFile }?.readBytes()?.let(WallpaperPicture::decode)
            ?: WallpaperPicture.current(this)?.takeIf { it.fingerprint.sha == sha }

    /**
     * Waits until the phone has room for a build; returns whether it must run lean. The Studio
     * runs fast when memory allows, since someone is watching.
     */
    private fun waitForRoom(sha: String, studio: Boolean): Boolean {
        val activity = getSystemService(ActivityManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        var told = false
        while (true) {
            if (cancel.get()) throw DepthBuilder.Cancelled()
            val memory = ActivityManager.MemoryInfo().also { activity.getMemoryInfo(it) }.availMem
            val busy = power.isPowerSaveMode || power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
            if (!busy && memory >= FAST_BYTES && studio) return false
            if (!busy && memory >= LEAN_BYTES) return true
            if (!told) {
                Log.i(TAG, "Build of ${sha.take(12)} waits: ${memory shr 20} MB free, busy=$busy")
                _state.value = State.Waiting(sha)
                notifications.notify(NOTIFICATION_PROGRESS, progress(_state.value))
                told = true
            }
            Thread.sleep(WAIT_MS)
        }
    }

    private fun progress(state: State?): Notification {
        val stop = PendingIntent.getService(
            this, 0, Intent(this, DepthBuildService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_laras)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.depth_stop), stop).build())
        when (state) {
            is State.Waiting -> builder
                .setContentTitle(getString(R.string.depth_waiting_title))
                .setStyle(Notification.BigTextStyle().bigText(getString(R.string.depth_waiting_text)))
            is State.Running -> builder
                .setContentTitle(getString(R.string.depth_building_title))
                .setContentText(stageText(this, state))
                .setProgress(state.steps, state.step, false)
            else -> builder
                .setContentTitle(getString(R.string.depth_building_title))
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** "Turn off" on the ready notification: the lock screen's depth effect goes off. */
    class TurnOffReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Settings.Secure.putInt(context.contentResolver, KEY_ENABLED, 0)
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_READY)
        }
    }

    /** SystemUI's request for a picture's depth (DepthRepository, STATUS_BAR_SERVICE only). */
    class BuildRequestReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sha = intent.getStringExtra(DepthStudioActivity.EXTRA_FINGERPRINT) ?: return
            if (!Fingerprint.isSha(sha) || !DepthBuilder.available) return
            if (declined(context).getBoolean(sha, false)) return
            val library = DepthLibrary(context)
            // Already made: the lock screen only lost it (SystemUI's data cleared); give it back.
            library.load(sha)?.let { project ->
                Thread { DepthSync.push(context, project, library) }.start()
                return
            }
            if (library.build(sha) != null) return
            request(context, sha, studio = false)
        }
    }

    companion object {
        private const val TAG = "DepthBuildService"
        private const val CHANNEL = "depth_build"
        private const val NOTIFICATION_PROGRESS = 0x6465
        private const val NOTIFICATION_READY = 0x6466
        private const val ACTION_BUILD = "org.yasvara.laras.depth.action.BUILD"
        private const val ACTION_STOP = "org.yasvara.laras.depth.action.STOP"
        private const val EXTRA_STUDIO = "studio"
        private const val KEY_ENABLED = "yasvara_depth_enabled"
        private const val WAIT_MS = 30_000L

        /** ToonOut's peak in lean (3.2 GB) and fast (6.4 GB) mode on onyx, plus room for the rest. */
        private const val LEAN_BYTES = 4_200L shl 20
        private const val FAST_BYTES = 8_000L shl 20

        private val _state = MutableStateFlow<State>(State.Idle)
        val state: StateFlow<State> get() = _state

        /** Builds [sha]'s depth; from the Studio ([studio]) the result is left for it to show. */
        fun request(context: Context, sha: String, studio: Boolean) {
            context.startForegroundService(
                Intent(context, DepthBuildService::class.java)
                    .setAction(ACTION_BUILD)
                    .putExtra(DepthStudioActivity.EXTRA_FINGERPRINT, sha)
                    .putExtra(EXTRA_STUDIO, studio),
            )
        }

        fun stageText(context: Context, state: State.Running): String = when (state.stage) {
            DepthBuilder.Stage.CHARACTER -> context.getString(R.string.depth_stage_character)
            DepthBuilder.Stage.HAIR -> context.getString(R.string.depth_stage_hair, maxOf(1, state.tile), state.tiles)
            DepthBuilder.Stage.PARTS -> context.getString(R.string.depth_stage_props)
        }

        private fun declined(context: Context) =
            context.getSharedPreferences("depth_declined", Context.MODE_PRIVATE)
    }
}
