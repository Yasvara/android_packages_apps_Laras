/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Saving a depth (docs/specs/0013 §6), for the Studio and the background build alike: the
 * selection drawn at the picture's editing size, the subject cut out with its colour fixed (S4),
 * both kept in the [DepthLibrary] with a thumbnail and handed to SystemUI.
 */
internal object DepthSave {
    private const val TAG = "DepthSave"
    private const val THUMB_WIDTH = 360

    /** How bright the background stays in a thumbnail. */
    private const val THUMB_DIM = 0.32f

    /** The saved project, or null if it could not be kept or SystemUI refused it. */
    fun save(
        context: Context,
        library: DepthLibrary,
        selection: Selection,
        picture: WallpaperPicture,
        created: Long?,
    ): DepthProject? {
        val w = picture.bitmap.width
        val h = picture.bitmap.height
        val coverage = selection.render(w, h)
        val matte = Selection.toBitmap(coverage, w, h)
        val pixels = IntArray(w * h).also { picture.bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val subject = Bitmap.createBitmap(Foreground.subject(pixels, coverage, w, h), w, h, Bitmap.Config.ARGB_8888)
        val thumb = highlighted(picture.bitmap, matte)
        val now = System.currentTimeMillis()
        val project = DepthProject(
            picture.fingerprint, picture.width, picture.height, selection.operations,
            w, h, created ?: now, now,
        )
        try {
            library.save(project, matte, subject, picture.bytes, thumb)
        } catch (e: IOException) {
            Log.w(TAG, "Could not keep the depth project", e)
            return null
        } finally {
            matte.recycle()
            subject.recycle()
            thumb.recycle()
        }
        return project.takeIf { DepthSync.push(context, it, library) }
    }

    /** The picture small, its subject bright and the rest dimmed: what lists show of a depth. */
    private fun highlighted(picture: Bitmap, matte: Bitmap): Bitmap {
        val w = minOf(THUMB_WIDTH, picture.width)
        val h = (w * picture.height / picture.width.toFloat()).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(picture, w, h, true)
        val alpha = Bitmap.createScaledBitmap(matte, w, h, true)
        val pixels = IntArray(w * h)
        val cover = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        alpha.getPixels(cover, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val k = THUMB_DIM + (1f - THUMB_DIM) * ((cover[i] ushr 24) / 255f)
            val p = pixels[i]
            pixels[i] = (0xFF shl 24) or
                (((p shr 16) and 0xFF) * k).toInt().shl(16) or
                (((p shr 8) and 0xFF) * k).toInt().shl(8) or
                ((p and 0xFF) * k).toInt()
        }
        if (small !== picture) small.recycle()
        alpha.recycle()
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
