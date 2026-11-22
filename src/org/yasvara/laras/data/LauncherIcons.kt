/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import org.yasvara.laras.ui.preview.Launcher

/**
 * The home screen's icons: the shapes the launcher offers, the installed icon
 * packs, and the launcher's themed icons. The shape and the pack live in `yasvara_theme`, which
 * the launcher and SystemUI follow; themed icons stay the launcher's own setting.
 */
class LauncherIcons(context: Context, private val settings: SettingsRepository) {
    private val context = context.applicationContext

    /** One shape, with the path the launcher draws it with (a 100 x 100 viewport). */
    data class Shape(val key: String, val title: String, val path: String, val current: Boolean)

    /** An installed icon pack. */
    data class Pack(val packageName: String, val label: String, val icon: Drawable?)

    /** What the theme picked: the shape and the pack, null for the launcher's and none. */
    data class Choice(val shape: String?, val pack: String?)

    fun choice(): Choice {
        val json = settings.readRaw(Namespace.SECURE, THEME)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        return Choice(
            shape = json?.optString(FIELD_SHAPE)?.takeIf { it.isNotEmpty() },
            pack = json?.optString(FIELD_PACK)?.takeIf { it.isNotEmpty() },
        )
    }

    /** The launcher's shapes, in its order; empty when it offers none. */
    fun shapes(): List<Shape> {
        val uri = uri("/shape_options") ?: return emptyList()
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val key = c.getColumnIndexOrThrow("shape_key")
                val title = c.getColumnIndexOrThrow("shape_title")
                val path = c.getColumnIndexOrThrow("path")
                val current = c.getColumnIndexOrThrow("is_default")
                buildList {
                    while (c.moveToNext()) {
                        add(Shape(c.getString(key), c.getString(title), c.getString(path), c.getInt(current) == 1))
                    }
                }
            }.orEmpty()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Launcher shapes unavailable", e)
            emptyList()
        }
    }

    /**
     * Picks [shape] for the launcher and, with the same path, for every adaptive icon in the
     * system; null goes back to the launcher's own shape and the stock mask. One write, so both
     * follow together.
     */
    fun setShape(shape: Shape?) {
        settings.mergeFields(
            Namespace.SECURE, THEME, mapOf(FIELD_SHAPE to shape?.key, FIELD_MASK to shape?.path),
        )
    }

    /** Installed icon packs, by label. */
    fun packs(): List<Pack> {
        val pm = context.packageManager
        return PACK_ACTIONS.flatMap { action ->
                pm.queryIntentActivities(Intent(action), PackageManager.ResolveInfoFlags.of(0))
            }
            .distinctBy { it.activityInfo.packageName }
            .map { Pack(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Picks [packageName]'s icon pack, or none. */
    fun setPack(packageName: String?) {
        settings.mergeFields(Namespace.SECURE, THEME, mapOf(FIELD_PACK to packageName))
    }

    /** Whether the launcher draws themed (monochrome) icons; null when it cannot say. */
    fun themedIcons(): Boolean? {
        val uri = uri("/get_icon_themed") ?: return null
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getInt(c.getColumnIndexOrThrow("boolean_value")) == 1 else null
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Launcher themed icons unavailable", e)
            null
        }
    }

    fun setThemedIcons(enabled: Boolean) {
        val uri = uri("/set_icon_themed") ?: return
        try {
            context.contentResolver.update(uri, ContentValues().apply { put("boolean_value", enabled) }, null, null)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not set the launcher's themed icons", e)
        }
    }

    private fun uri(path: String): Uri? = Launcher.authority(context)?.let { Uri.parse("content://$it$path") }

    companion object {
        private const val TAG = "LauncherIcons"
        const val THEME = "yasvara_theme"
        private const val FIELD_SHAPE = "iconShape"
        private const val FIELD_MASK = "iconMask"
        private const val FIELD_PACK = "iconPack"

        /** The de-facto intents icon packs answer to, as the launcher reads them. */
        private val PACK_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME")
    }
}
