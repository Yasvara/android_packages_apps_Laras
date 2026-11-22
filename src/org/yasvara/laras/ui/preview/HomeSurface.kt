/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.preview

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.SurfaceView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.os.bundleOf
import kotlin.concurrent.thread
import org.yasvara.laras.catalog.Element

/**
 * A session with the launcher's own home screen preview: the launcher lays out
 * and draws its real workspace and dock into a surface Laras embeds, the way the stock wallpaper
 * picker does, so icon shapes and packs show exactly as they will. The launcher follows the
 * applied `yasvara_theme`, so a change shows in the preview as it lands.
 */
class HomeRenderer(context: Context) : PreviewSession {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var surfacePackage: SurfacePackage? = null
    private var callback: Messenger? = null
    private var host: WallpaperSurfaceView? = null
    private var requested = false
    private var closed = false

    /** False when no launcher offers a preview; the canvas then says so. */
    var available by mutableStateOf(true)
        private set

    override fun attach(view: WallpaperSurfaceView) {
        host = view
        val rendered = surfacePackage
        if (rendered != null) view.embed(rendered) else request(view)
    }

    override fun detach(view: WallpaperSurfaceView) {
        if (host === view) host = null
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
        val authority = Launcher.authority(context) ?: run {
            available = false
            return
        }
        requested = true
        val bounds = view.context.getSystemService(android.view.WindowManager::class.java)
            .maximumWindowMetrics.bounds
        val request = bundleOf(
            KEY_HOST_TOKEN to token,
            KEY_WIDTH to bounds.width(),
            KEY_HEIGHT to bounds.height(),
            KEY_DISPLAY_ID to view.display.displayId,
        )
        // The launcher builds the preview before answering; keep that wait off the UI thread.
        thread(name = "HomePreview") {
            val result = runCatching {
                context.contentResolver.call(Uri.parse("content://$authority"), METHOD_PREVIEW, null, request)
            }.onFailure { Log.w(TAG, "Launcher preview unavailable", it) }.getOrNull()
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
        available = rendered != null
        if (rendered != null) host?.embed(rendered)
    }

    private fun send(what: Int) {
        val messenger = callback ?: return
        runCatching { messenger.send(Message.obtain(null, what)) }
            .onFailure { Log.w(TAG, "Launcher preview went away", it) }
    }

    private companion object {
        const val TAG = "Laras"
        const val METHOD_PREVIEW = "get_preview"
        const val KEY_HOST_TOKEN = "host_token"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_DISPLAY_ID = "display_id"
        const val KEY_SURFACE_PACKAGE = "surface_package"
        const val KEY_CALLBACK = "callback"

        /** The launcher ends the preview on a message it does not know. */
        const val MESSAGE_DESTROY = 0
    }
}

/** The default launcher and the customization provider it declares. */
object Launcher {
    private const val META_GRID_CONTROL = "com.android.launcher3.grid.control"

    /** The provider authority of the default launcher, or null when it has none. */
    fun authority(context: Context): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(
            home, PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
        )?.activityInfo ?: return null
        return info.metaData?.getString(META_GRID_CONTROL)
    }
}

/**
 * The home screen canvas: the launcher's own preview over the home wallpaper. Tapping anywhere
 * picks the app icons, the one part of it Laras edits.
 */
@Composable
fun HomePreview(device: DeviceState, onSelect: (Element?) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val renderer = remember(context) { HomeRenderer(context) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    val corner = with(LocalDensity.current) { CanvasCorner.toPx() }
    val tap = remember { MutableInteractionSource() }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(CanvasCorner))) {
        val scale = constraints.maxWidth / device.displaySize.width.toFloat()
        KeyguardSurface(
            renderer = renderer,
            wallpaper = device.homeWallpaper,
            displaySize = device.displaySize,
            scale = scale,
            cornerRadius = corner,
            modifier = Modifier.fillMaxSize(),
        )
        // Over the surface, so a tap picks the icons instead of scrolling the launcher's pages.
        Box(Modifier.fillMaxSize().clickable(tap, indication = null) { onSelect(Element.HOME_ICONS) })
    }
}
