/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import android.util.Log
import java.io.File
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

/**
 * Every depth wallpaper Laras has made (docs/specs/0013 §6), in credential-encrypted storage, one
 * folder per picture named by its [Fingerprint.sha]:
 *
 * - `project.json`: the [DepthProject], written last, so a folder without it is ignored;
 * - `op-<n>.grid`: the grid of the project's nth operation when it is a region or a matte;
 * - `matte.png`: the matte the lock screen was given, at the picture's editing size;
 * - `picture`: the original wallpaper file, so a picture that is no longer the wallpaper can
 *   still be re-opened and edited;
 * - `thumb.webp`: the picture, small, its subject bright and the rest dimmed, for lists;
 * - `subject.png`: the subject cut out with its colour fixed (S4), at the matte's size;
 * - `build/`: what the depth build found ([DepthBuild]), which `auto` and `part` steps refer to.
 */
class DepthLibrary(context: Context) {
    private val root = File(context.filesDir, "depth")

    /** What a list shows of a saved project, read without its grids. */
    class Summary(
        val fingerprint: Fingerprint,
        val pictureWidth: Int,
        val pictureHeight: Int,
        val steps: Int,
        val modified: Long,
        val thumb: File,
    )

    fun load(sha: String): DepthProject? {
        if (!Fingerprint.isSha(sha)) return null
        val dir = File(root, sha)
        val file = File(dir, PROJECT).takeIf { it.isFile } ?: return null
        val built by lazy { build(sha) }
        return try {
            val json = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
            DepthProject.fromJson(json, { built }) { name, width, height ->
                File(dir, name).takeIf { GRID_NAME.matches(name) && it.isFile }
                    ?.readBytes()
                    ?.let { DepthProject.decodeGrid(it, width, height) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Unreadable depth project $sha", e)
            null
        } catch (e: JSONException) {
            Log.w(TAG, "Unreadable depth project $sha", e)
            null
        }
    }

    /** Saved projects, most recently edited first, without their grids. */
    fun summaries(): List<Summary> =
        root.listFiles { f -> f.isDirectory && Fingerprint.isSha(f.name) }
            .orEmpty()
            .mapNotNull { summary(it) }
            .sortedByDescending { it.modified }

    private fun summary(dir: File): Summary? = try {
        val json = JSONObject(AtomicFile(File(dir, PROJECT)).readFully().toString(Charsets.UTF_8))
        val picture = json.getJSONObject("picture")
        Summary(
            Fingerprint(dir.name, Fingerprint.parseHex(json.getString("dhash"))),
            picture.getInt("width"),
            picture.getInt("height"),
            json.getJSONArray("ops").length(),
            json.getLong("modified"),
            File(dir, THUMB),
        )
    } catch (e: IOException) {
        null
    } catch (e: JSONException) {
        null
    } catch (e: NumberFormatException) {
        null
    }

    /**
     * A saved project for a picture that looks like [fingerprint]'s but is not the same file (a
     * re-encoded or resized copy): same proportions, and a difference hash within
     * [Fingerprint.SIMILAR_BITS]. The closest wins.
     */
    fun similar(fingerprint: Fingerprint, aspect: Float): DepthProject? =
        summaries()
            .filter { it.fingerprint.sha != fingerprint.sha }
            .filter { kotlin.math.abs(it.pictureWidth / it.pictureHeight.toFloat() - aspect) < 0.01f * aspect }
            .map { it to Fingerprint.distance(it.fingerprint.dhash, fingerprint.dhash) }
            .filter { it.second <= Fingerprint.SIMILAR_BITS }
            .minByOrNull { it.second }
            ?.let { load(it.first.fingerprint.sha) }

    fun matte(sha: String) = File(File(root, sha), MATTE)

    fun picture(sha: String) = File(File(root, sha), PICTURE)

    fun thumb(sha: String) = File(File(root, sha), THUMB)

    fun subject(sha: String) = File(File(root, sha), SUBJECT)

    /** The picture's depth build, or null if it has none (or an unfinished or outdated one). */
    fun build(sha: String): DepthBuild? =
        if (!Fingerprint.isSha(sha)) null else DepthBuild.read(File(File(root, sha), BUILD))

    /**
     * Keeps a finished build for [sha], with the original file's [picture] bytes so the
     * picture can be re-opened once it is no longer the wallpaper.
     */
    fun saveBuild(sha: String, build: DepthBuild, picture: ByteArray) {
        require(Fingerprint.isSha(sha))
        val dir = File(root, sha).apply { mkdirs() }
        val target = File(dir, BUILD)
        val staging = File(dir, ".$BUILD.new").apply { deleteRecursively() }
        build.write(staging)
        target.deleteRecursively()
        if (!staging.renameTo(target)) throw IOException("Could not keep the build of $sha")
        val original = File(dir, PICTURE)
        if (!original.isFile) writeAtomically(original) { it.write(picture) }
    }

    /**
     * Saves [project] with the [matte] it produced, its [subject] cut out, and a [thumb].
     * [picture], the original file's bytes, is written only the first time: it never changes for
     * a fingerprint.
     */
    fun save(project: DepthProject, matte: Bitmap, subject: Bitmap?, picture: ByteArray, thumb: Bitmap) {
        val dir = File(root, project.fingerprint.sha).apply { mkdirs() }
        val grids = HashSet<String>()
        project.ops.forEachIndexed { n, op ->
            val coverage = when (op) {
                is SelectionOp.Region -> op.coverage
                is SelectionOp.Matte -> op.coverage
                is SelectionOp.Brush, is SelectionOp.Auto, is SelectionOp.Part -> return@forEachIndexed
            }
            val name = gridName(n)
            writeAtomically(File(dir, name)) { it.write(DepthProject.encodeGrid(coverage)) }
            grids += name
        }
        writeAtomically(File(dir, MATTE)) { matte.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (subject != null) {
            writeAtomically(File(dir, SUBJECT)) { subject.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            File(dir, SUBJECT).delete()
        }
        writeAtomically(File(dir, THUMB)) { thumb.compress(Bitmap.CompressFormat.WEBP_LOSSY, THUMB_QUALITY, it) }
        val original = File(dir, PICTURE)
        if (!original.isFile) writeAtomically(original) { it.write(picture) }
        val json = project.toJson(::gridName).toString().toByteArray()
        writeAtomically(File(dir, PROJECT)) { it.write(json) }
        // Grids of operations the project no longer has.
        dir.listFiles { f -> GRID_NAME.matches(f.name) && f.name !in grids }?.forEach { it.delete() }
    }

    fun delete(sha: String) {
        if (Fingerprint.isSha(sha)) File(root, sha).deleteRecursively()
    }

    private fun gridName(n: Int) = "op-$n.grid"

    private fun writeAtomically(file: File, write: (java.io.OutputStream) -> Unit) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            write(out)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    companion object {
        private const val TAG = "DepthLibrary"
        private const val PROJECT = "project.json"
        private const val MATTE = "matte.png"
        private const val PICTURE = "picture"
        private const val THUMB = "thumb.webp"
        private const val SUBJECT = "subject.png"
        private const val BUILD = "build"
        private const val THUMB_QUALITY = 80
        private val GRID_NAME = Regex("op-[0-9]+\\.grid")
    }
}
