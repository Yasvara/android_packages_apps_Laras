/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.preview

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import kotlin.concurrent.thread
import org.yasvara.laras.debug.SeamSpike
import kotlin.math.max

/**
 * A session with SystemUI's lock screen preview: SystemUI lays out and draws its real keyguard
 * (status bar, clock, date, fingerprint sensor, shortcuts) into a surface that Laras embeds, so the canvas
 * shows exactly what the lock screen will, rendered by the same code.
 *
 * One session serves the whole window: each canvas embeds its own copy of the rendered surface,
 * so it can travel between screens without being rendered twice.
 */
class KeyguardRenderer(context: Context) : PreviewSession {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var surfacePackage: SurfacePackage? = null
    private var callback: Messenger? = null
    private var host: WallpaperSurfaceView? = null
    private var requested = false
    private var closed = false
    private var clockSize: String? = null

    /** Where SystemUI places notifications in the rendered lock screen, in display pixels. */
    var notificationTop by mutableStateOf<Int?>(null)
        private set

    /**
     * Where each lock screen element is drawn, by element name (see [ELEMENT_CLOCK]), in display
     * pixels. An element SystemUI has not reported yet is absent.
     */
    var elementBounds by mutableStateOf<Map<String, Rect>>(emptyMap())
        private set

    /**
     * Whether SystemUI can preview unsaved changes. Until it says so, and on a SystemUI that
     * never does, drafts are not sent: SystemUI ends a preview on any message it does not know.
     */
    var supportsDrafts by mutableStateOf(false)
        private set

    private var pendingDraft: Bundle? = null

    private val layoutReceiver = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MESSAGE_LAYOUT -> notificationTop = message.data.getInt(KEY_NOTIFICATION_TOP)
                MESSAGE_YAS_BOUNDS -> message.data.getBundle(KEY_YAS_BOUNDS)?.let { bundle ->
                    elementBounds = elementBounds + bundle.keySet().mapNotNull { key ->
                        bundle.getParcelable(key, Rect::class.java)?.let { key to it }
                    }
                }
            }
            true
        }
    )

    override fun attach(view: WallpaperSurfaceView) {
        host = view
        val rendered = surfacePackage
        if (rendered != null) view.embed(rendered) else request(view)
    }

    override fun detach(view: WallpaperSurfaceView) {
        if (host === view) host = null
    }

    /** Shows the large clock where the real lock screen would, or keeps it small. */
    fun setDynamicClock(dynamic: Boolean) {
        val size = if (dynamic) CLOCK_SIZE_DYNAMIC else CLOCK_SIZE_SMALL
        if (size == clockSize) return
        clockSize = size
        send(MESSAGE_CLOCK_SIZE, bundleOf(KEY_CLOCK_SIZE to size))
    }

    /**
     * Shows [changes] in the preview without saving them: keys follow the preview protocol
     * (such as [KEY_CLOCK_FACE]), and keys left out keep their current draft. Sent once SystemUI
     * is ready, and dropped if it cannot preview drafts.
     */
    fun draft(changes: Bundle) {
        if (callback == null) {
            pendingDraft = (pendingDraft ?: Bundle()).apply { putAll(changes) }
            return
        }
        if (supportsDrafts) send(MESSAGE_YAS_DRAFT, changes)
    }

    /** Goes back to previewing the saved settings. */
    fun clearDraft() {
        pendingDraft = null
        if (supportsDrafts) send(MESSAGE_YAS_DRAFT_CLEAR)
    }

    fun close() {
        closed = true
        send(MESSAGE_DESTROY)
        surfacePackage?.release()
        surfacePackage = null
        callback = null
    }

    private fun request(view: SurfaceView) {
        if (requested) return
        val token = view.hostToken ?: return
        requested = true
        val display = view.display
        val bounds = view.context.getSystemService(android.view.WindowManager::class.java)
            .maximumWindowMetrics.bounds
        val wallpaper = WallpaperManager.getInstance(context)
        val request = bundleOf(
            KEY_HOST_TOKEN to token,
            KEY_WIDTH to bounds.width(),
            KEY_HEIGHT to bounds.height(),
            KEY_DISPLAY_ID to display.displayId,
            KEY_COLORS to (wallpaper.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                ?: wallpaper.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)),
            KEY_FULL_LOCK_SCREEN to true,
            KEY_HOST_CALLBACK to layoutReceiver,
        )
        request.putAll(SeamSpike.previewExtras(context))
        // SystemUI builds the preview synchronously; keep that wait off the UI thread.
        thread(name = "KeyguardPreview") {
            val result = runCatching {
                context.contentResolver.call(AUTHORITY, METHOD_PREVIEW, null, request)
            }.onFailure { Log.w(TAG, "SystemUI preview unavailable", it) }.getOrNull()
            main.post { deliver(result) }
        }
    }

    private fun deliver(result: Bundle?) {
        val rendered = result?.getParcelable(KEY_SURFACE_PACKAGE, SurfacePackage::class.java)
        val messenger = result?.getParcelable(KEY_CALLBACK, Message::class.java)?.replyTo
        if (closed) {
            messenger?.let { runCatching { it.send(Message.obtain(null, MESSAGE_DESTROY)) } }
            rendered?.release()
            return
        }
        surfacePackage = rendered
        callback = messenger
        supportsDrafts = (result?.getInt(KEY_YAS_PROTOCOL) ?: 0) >= YAS_PROTOCOL_DRAFTS
        if (rendered != null) host?.embed(rendered)
        clockSize?.let { send(MESSAGE_CLOCK_SIZE, bundleOf(KEY_CLOCK_SIZE to it)) }
        pendingDraft?.let { draft(it) }
        pendingDraft = null
    }

    private fun send(what: Int, data: Bundle? = null) {
        val messenger = callback ?: return
        runCatching { messenger.send(Message.obtain(null, what).also { it.data = data }) }
            .onFailure { Log.w(TAG, "SystemUI preview went away", it) }
    }

    companion object {
        /** The clock face, as the bounds of whatever size of it is showing. */
        const val ELEMENT_CLOCK = "clock"

        /** A draft clock face, as JSON in the form of Settings.Secure.LOCK_SCREEN_CUSTOM_CLOCK_FACE. */
        const val KEY_CLOCK_FACE = "clock_face"

        private const val KEY_YAS_PROTOCOL = "yas_protocol"
        private const val YAS_PROTOCOL_DRAFTS = 2
        private const val MESSAGE_YAS_DRAFT = 0x59410001
        private const val MESSAGE_YAS_DRAFT_CLEAR = 0x59410002
        private const val MESSAGE_YAS_BOUNDS = 0x59410101
        private const val KEY_YAS_BOUNDS = "bounds"
        private const val TAG = "Laras"
        private val AUTHORITY: Uri = Uri.parse("content://com.android.systemui.customization")
        private const val METHOD_PREVIEW = "preview"
        private const val KEY_HOST_TOKEN = "host_token"
        private const val KEY_WIDTH = "width"
        private const val KEY_HEIGHT = "height"
        private const val KEY_DISPLAY_ID = "display_id"
        private const val KEY_COLORS = "wallpaper_colors"
        private const val KEY_FULL_LOCK_SCREEN = "full_lock_screen"
        private const val KEY_HOST_CALLBACK = "host_callback"
        private const val KEY_NOTIFICATION_TOP = "notification_top"
        private const val MESSAGE_LAYOUT = 1
        private const val KEY_SURFACE_PACKAGE = "surface_package"
        private const val KEY_CALLBACK = "callback"
        private const val KEY_CLOCK_SIZE = "clock_size"
        private const val CLOCK_SIZE_DYNAMIC = "clock_size_dynamic"
        private const val CLOCK_SIZE_SMALL = "clock_size_small"
        private const val MESSAGE_CLOCK_SIZE = 1119

        /** Any message SystemUI does not recognise ends the preview. */
        private const val MESSAGE_DESTROY = 0
    }
}

/** The window's keyguard session, shared by every canvas in it. */
val LocalKeyguardRenderer = staticCompositionLocalOf<KeyguardRenderer> {
    error("No KeyguardRenderer provided")
}

/** A [KeyguardRenderer] tied to the calling composition's lifetime. */
@Composable
fun rememberKeyguardRenderer(): KeyguardRenderer {
    val context = LocalContext.current
    val renderer = remember(context) { KeyguardRenderer(context) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    return renderer
}

/** A preview rendered by another process into a [WallpaperSurfaceView] it is attached to. */
interface PreviewSession {
    fun attach(view: WallpaperSurfaceView)

    fun detach(view: WallpaperSurfaceView)
}

/**
 * Hosts a rendered screen (the keyguard, or the launcher's home screen) over its wallpaper. The
 * surface keeps the display's full pixel size, which is what the renderer lays out for, and is
 * scaled down as a whole by [scale].
 */
@Composable
fun KeyguardSurface(
    renderer: PreviewSession,
    wallpaper: Bitmap?,
    displaySize: IntSize,
    scale: Float,
    cornerRadius: Float,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val surface = WallpaperSurfaceView(context, renderer)
            FrameLayout(context).apply {
                clipChildren = false
                addView(surface, FrameLayout.LayoutParams(displaySize.width, displaySize.height))
            }
        },
        update = { frame ->
            val surface = frame.getChildAt(0) as WallpaperSurfaceView
            surface.pivotX = 0f
            surface.pivotY = 0f
            surface.scaleX = scale
            surface.scaleY = scale
            // The corner is applied before the surface is scaled.
            surface.setCornerRadius(cornerRadius / scale)
            surface.wallpaper = wallpaper
        },
    )
}

/** Paints the wallpaper into its own surface, beneath the screen a [PreviewSession] renders. */
class WallpaperSurfaceView internal constructor(
    context: Context,
    private val renderer: PreviewSession,
) : SurfaceView(context), SurfaceHolder.Callback {
    var wallpaper: Bitmap? = null
        set(value) {
            if (field === value) return
            field = value
            draw()
        }

    init {
        holder.addCallback(this)
    }

    private var embedded = false

    /**
     * Embeds SystemUI's surface. A SurfaceView releases the package it holds when it goes
     * away, so every view gets its own copy; and it re-parents that copy by itself whenever its
     * surface is recreated, so it is embedded only once.
     */
    fun embed(rendered: SurfacePackage) {
        if (embedded) return
        embedded = true
        setChildSurfacePackage(SurfacePackage(rendered))
    }

    // SystemUI needs the host token, which exists only once the surface does.
    override fun surfaceCreated(holder: SurfaceHolder) = renderer.attach(this)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        draw()
        SeamSpike.reportCanvas(this)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = renderer.detach(this)

    private fun draw() {
        val surface = holder.surface
        if (!surface.isValid) return
        val canvas = holder.lockHardwareCanvas() ?: return
        try {
            canvas.drawColor(android.graphics.Color.BLACK)
            val bitmap = wallpaper ?: return
            // As the window manager places it: the crop fills the display, spare width to the
            // start and spare height split evenly, then zoomed around the centre as at rest.
            val w = holder.surfaceFrame.width().toFloat()
            val h = holder.surfaceFrame.height().toFloat()
            val s = max(w / bitmap.width, h / bitmap.height)
            val zoom = resources.getFloat(com.android.internal.R.dimen.config_wallpaperMaxScale)
            val matrix = Matrix().apply {
                setScale(s, s)
                postTranslate(0f, (h - bitmap.height * s) / 2f)
                postScale(zoom, zoom, w / 2f, h / 2f)
            }
            canvas.drawBitmap(bitmap, matrix, null)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}
