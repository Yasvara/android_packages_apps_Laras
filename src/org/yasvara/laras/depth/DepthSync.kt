/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.os.bundleOf
import org.json.JSONObject

/**
 * Hands depth to SystemUI's DepthAssetsProvider (docs/specs/0013 §6), which keeps it in device
 * storage by the picture's fingerprint for the lock screen. Files go as descriptors, not bytes.
 */
object DepthSync {
    private const val TAG = "DepthSync"
    private val ASSETS: Uri = Uri.parse("content://com.android.systemui.yasvara.assets")

    /** Sends [project]'s matte and subject from [library]; true once SystemUI has validated and kept them. */
    fun push(context: Context, project: DepthProject, library: DepthLibrary): Boolean = push(
        context, project.fingerprint.sha, project.modified, project.pictureWidth, project.pictureHeight, library,
    )

    /** Sends a saved picture's matte and subject; true once SystemUI has validated and kept them. */
    fun push(context: Context, summary: DepthLibrary.Summary, library: DepthLibrary): Boolean = push(
        context, summary.fingerprint.sha, summary.modified, summary.pictureWidth, summary.pictureHeight, library,
    )

    private fun push(
        context: Context, sha: String, modified: Long, width: Int, height: Int, library: DepthLibrary,
    ): Boolean = runCatching {
        val subject = library.subject(sha).takeIf { it.isFile }
        ParcelFileDescriptor.open(library.matte(sha), ParcelFileDescriptor.MODE_READ_ONLY).use { matte ->
            val cut = subject?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            try {
                val meta = JSONObject()
                    .put("format", DepthProject.FORMAT)
                    .put("modified", modified)
                    .put("pictureWidth", width)
                    .put("pictureHeight", height)
                val extras = bundleOf("matte" to matte, "meta" to meta.toString())
                if (cut != null) extras.putParcelable("subject", cut)
                context.contentResolver.call(ASSETS, "put_depth", sha, extras)?.getBoolean("ok") == true
            } finally {
                cut?.close()
            }
        }
    }.onFailure { Log.w(TAG, "Could not hand depth to SystemUI", it) }.getOrDefault(false)

    /** Fingerprints SystemUI has depth for, most recently shown first. */
    fun list(context: Context): List<String> = runCatching {
        context.contentResolver.call(ASSETS, "list_depth", null, null)
            ?.getStringArray("fingerprints")
            ?.toList()
            .orEmpty()
    }.onFailure { Log.w(TAG, "Could not list SystemUI depth", it) }.getOrDefault(emptyList())

    /**
     * The matte SystemUI keeps for [sha] as a [SelectionOp.Matte], or null if it has none: the
     * start of a project when Laras has no project of its own for that picture.
     */
    fun fetch(context: Context, sha: String): SelectionOp.Matte? = runCatching {
        val uri = ASSETS.buildUpon().appendPath("depth").appendPath(sha).appendPath("matte.png").build()
        val png = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor)
        } ?: return null
        val pixels = IntArray(png.width * png.height)
        png.getPixels(pixels, 0, png.width, 0, 0, png.width, png.height)
        png.recycle()
        SelectionOp.Matte(true, png.width, png.height, FloatArray(pixels.size) { (pixels[it] ushr 24) / 255f })
    }.onFailure { Log.i(TAG, "No SystemUI depth to start from", it) }.getOrNull()

    fun remove(context: Context, sha: String) {
        runCatching { context.contentResolver.call(ASSETS, "remove_depth", sha, null) }
            .onFailure { Log.w(TAG, "Could not remove SystemUI depth", it) }
    }
}
