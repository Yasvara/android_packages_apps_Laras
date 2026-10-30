/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A picture to make depth for, read from its original file: the bytes SystemUI fingerprints and
 * cuts the subject from, so the matte lines up with what the lock screen shows.
 *
 * [bitmap] is the picture as ImageDecoder shows it (EXIF orientation applied, as the platform
 * crops it), at most [MAX_SIDE] long; [width] x [height] is its full size.
 */
class WallpaperPicture(
    val bitmap: Bitmap,
    val bytes: ByteArray,
    val fingerprint: Fingerprint,
    val width: Int,
    val height: Int,
) {
    val aspect get() = width / height.toFloat()

    companion object {
        private const val TAG = "WallpaperPicture"

        /** The editing size cap: the matte is made at this size and scaled by the lock screen. */
        const val MAX_SIDE = 4096

        /** The lock screen's picture: the lock wallpaper, or the home one when they are shared. */
        fun current(context: Context): WallpaperPicture? = currentBytes(context)?.let(::decode)

        /** The fingerprint of the lock screen's picture, without decoding it. */
        fun currentSha(context: Context): String? = currentBytes(context)?.let(Fingerprint::sha256)

        /** The lock screen's picture, small (about [side] px on its short side), for a thumbnail. */
        fun currentPreview(context: Context, side: Int): Bitmap? = currentBytes(context)?.let { bytes ->
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(bytes)) { decoder, info, _ ->
                    val k = side / minOf(info.size.width, info.size.height).toFloat()
                    if (k < 1f) {
                        decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
                    }
                }
            }.getOrNull()
        }

        private fun currentBytes(context: Context): ByteArray? = runCatching {
            val manager = WallpaperManager.getInstance(context)
            val which = if (manager.getWallpaperId(WallpaperManager.FLAG_LOCK) >= 0) {
                WallpaperManager.FLAG_LOCK
            } else {
                WallpaperManager.FLAG_SYSTEM
            }
            val file = manager.getWallpaperFile(which, /* getCropped= */ false) ?: return null
            ParcelFileDescriptor.AutoCloseInputStream(file).use { it.readBytes() }
        }.onFailure { Log.w(TAG, "No wallpaper", it) }.getOrNull()

        fun decode(bytes: ByteArray): WallpaperPicture? = runCatching { decodeOrThrow(bytes) }
            .onFailure { Log.w(TAG, "Undecodable picture", it) }.getOrNull()

        private fun decodeOrThrow(bytes: ByteArray): WallpaperPicture? {
            var full = android.util.Size(0, 0)
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(bytes)) { decoder, info, _ ->
                full = info.size
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val long = max(info.size.width, info.size.height)
                if (long > MAX_SIDE) {
                    val k = MAX_SIDE / long.toFloat()
                    decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
                }
            }
            val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) decoded else {
                decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
            }
            if (full.width <= 0 || full.height <= 0) return null
            return WallpaperPicture(
                bitmap,
                bytes,
                Fingerprint(Fingerprint.sha256(bytes), Fingerprint.dhash(lumaGrid(bitmap))),
                full.width,
                full.height,
            )
        }

        /** [bitmap]'s mean brightness over a [Fingerprint.DHASH_WIDTH] x [Fingerprint.DHASH_HEIGHT] grid. */
        private fun lumaGrid(bitmap: Bitmap): FloatArray {
            val w = Fingerprint.DHASH_WIDTH
            val h = Fingerprint.DHASH_HEIGHT
            val sum = DoubleArray(w * h)
            val count = IntArray(w * h)
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                val cy = y * h / bitmap.height
                for (x in 0 until bitmap.width) {
                    val p = row[x]
                    val i = cy * w + x * w / bitmap.width
                    sum[i] += 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)
                    count[i]++
                }
            }
            return FloatArray(w * h) { (sum[it] / count[it].coerceAtLeast(1)).toFloat() }
        }
    }
}
