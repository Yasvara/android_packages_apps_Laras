/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.preview

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the canvas needs from the device beyond what SystemUI renders: its screen and wallpaper. */
@Immutable
data class DeviceState(
    /** The display's real size in pixels, which the canvas mirrors. */
    val displaySize: IntSize,
    /** The lock screen's wallpaper. */
    val wallpaper: Bitmap?,
    /** The home screen's wallpaper, which the status bar sits on; the lock one when shared. */
    val homeWallpaper: Bitmap? = wallpaper,
) {
    val aspectRatio: Float get() = displaySize.width / displaySize.height.toFloat()
}

@Composable
fun rememberDeviceState(): DeviceState {
    val context = LocalContext.current
    val displaySize = remember(context) {
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        IntSize(bounds.width(), bounds.height())
    }
    val wallpapers by produceState(Wallpapers(), displaySize) {
        suspend fun load() {
            value = withContext(Dispatchers.IO) {
                val manager = WallpaperManager.getInstance(context)
                val home = loadWallpaper(context, displaySize, WallpaperManager.FLAG_SYSTEM)
                val separateLock = manager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0
                Wallpapers(
                    lock = if (separateLock) {
                        loadWallpaper(context, displaySize, WallpaperManager.FLAG_LOCK)
                    } else {
                        home
                    },
                    home = home,
                )
            }
        }
        load()
        // A new wallpaper, or a new crop of it, reports new colours; reload so the canvas follows.
        val manager = WallpaperManager.getInstance(context)
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> launch { load() } }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        awaitDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return DeviceState(displaySize, wallpapers.lock, wallpapers.home)
}

private data class Wallpapers(val lock: Bitmap? = null, val home: Bitmap? = null)

/**
 * A wallpaper ([which] is `FLAG_LOCK` or `FLAG_SYSTEM`) as the window manager shows it: only the
 * crop picked for this display (the picker's zoom and position), sized to fill the display width.
 */
private fun loadWallpaper(context: Context, display: IntSize, which: Int): Bitmap? = runCatching {
    val manager = WallpaperManager.getInstance(context)
    val drawable = manager.getDrawable(which) ?: return null
    val full = drawable.toBitmap()
    val bounds = Rect(0, 0, full.width, full.height)
    val crop = runCatching {
        manager.getBitmapCrops(listOf(Point(display.width, display.height)), which, false).firstOrNull()
    }.getOrNull()?.takeIf { it.intersect(bounds) && !it.isEmpty } ?: bounds
    val height = (display.width * crop.height().toFloat() / crop.width()).roundToInt().coerceAtLeast(1)
    val out = Bitmap.createBitmap(display.width, height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(full, crop, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
    out
}.onFailure { Log.w("Laras", "Wallpaper $which unavailable", it) }.getOrNull()
