/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.data.theme

import org.json.JSONException
import org.json.JSONObject
import org.yasvara.laras.data.Namespace

/**
 * A setting a theme apply writes, in the order it is written. The colour spec
 * goes first: every SystemUI observer the later keys wake re-reads it, so each evaluation already
 * sees the final colours and the overlays change once. Pure black and the AOSP mirror follow;
 * the pack record goes last, because it only describes what the others already say.
 */
enum class ThemeKey(val namespace: Namespace, val key: String) {
    COLOR(Namespace.SECURE, "yasvara_color"),
    PURE_BLACK(Namespace.LINEAGE_SECURE, "berry_black_theme"),
    OVERLAYS(Namespace.SECURE, "theme_customization_overlay_packages"),
    THEME(Namespace.SECURE, "yasvara_theme"),
    BATTERY_STYLE(Namespace.LINEAGE_SYSTEM, "status_bar_battery_style"),
    PACK(Namespace.SECURE, "yasvara_theme_pack"),
}

/** Where [ThemeCommitter] reads and writes; the settings providers in the app. */
interface ThemeStore {
    fun read(key: ThemeKey): String?

    /** Writes [value], or deletes the setting when it is null. */
    fun write(key: ThemeKey, value: String?)

    /** The one-step undo snapshot, `Settings.Secure.yasvara_theme_undo`. */
    var undo: String?
}

/**
 * Files a theme brings along (fonts, charger art), kept apart until the settings that use them
 * are written. The default has none.
 */
interface ThemeAssets {
    /** Puts the files in place without using them; throws if they cannot be. */
    fun stage() {}

    /** Makes the staged files the live ones. */
    fun promote() {}

    /** Drops the staged files. */
    fun discard() {}

    companion object {
        val NONE = object : ThemeAssets {}
    }
}

/** The new values of a theme apply: a key mapped to null is deleted, a key left out is kept. */
class ThemeChange(val values: Map<ThemeKey, String?>) {
    val isEmpty: Boolean
        get() = values.isEmpty()
}

sealed interface CommitResult {
    data object Applied : CommitResult

    /** Nothing was changed: the assets could not be staged, or there was nothing to undo. */
    data class Refused(val reason: String) : CommitResult

    /** A write failed after [key]; everything written before it was put back. */
    data class RolledBack(val key: ThemeKey, val cause: Exception) : CommitResult
}

/**
 * Applies several theme settings as one change, with a single step of undo.
 *
 * Before writing, it records the current value of every [ThemeKey] as the undo snapshot. It then
 * writes the changed keys in [ThemeKey] order. If any write fails, the keys already written get
 * their old values back in the same order, the staged assets are dropped and the previous undo
 * snapshot is restored, so a failed apply leaves no trace. [undo] applies the snapshot and
 * clears it: undo is one step, and undoing an undo is not possible.
 */
class ThemeCommitter(private val store: ThemeStore) {

    fun canUndo(): Boolean = Snapshot.parse(store.undo) != null

    fun commit(change: ThemeChange, assets: ThemeAssets = ThemeAssets.NONE): CommitResult {
        if (change.isEmpty) return CommitResult.Refused("empty change")
        try {
            assets.stage()
        } catch (e: Exception) {
            assets.discard()
            return CommitResult.Refused("assets: ${e.message}")
        }
        val previousUndo = store.undo
        val before = Snapshot(ThemeKey.entries.associateWith { store.read(it) })
        try {
            store.undo = before.toJson()
        } catch (e: Exception) {
            assets.discard()
            return CommitResult.Refused("undo: ${e.message}")
        }
        val failure = write(change, before)
        if (failure != null) {
            assets.discard()
            runCatching { store.undo = previousUndo }
            return failure
        }
        assets.promote()
        return CommitResult.Applied
    }

    fun undo(): CommitResult {
        val snapshot = Snapshot.parse(store.undo) ?: return CommitResult.Refused("nothing to undo")
        val before = Snapshot(ThemeKey.entries.associateWith { store.read(it) })
        val change = ThemeChange(snapshot.values.filter { (key, value) -> before.values[key] != value })
        write(change, before)?.let { return it }
        store.undo = null
        return CommitResult.Applied
    }

    /** Writes [change] in order; on failure restores [before] and returns what went wrong. */
    private fun write(change: ThemeChange, before: Snapshot): CommitResult.RolledBack? {
        val written = mutableListOf<ThemeKey>()
        for (key in ThemeKey.entries) {
            if (key !in change.values) continue
            try {
                store.write(key, change.values[key])
                written += key
            } catch (e: Exception) {
                // The failed key may have been half written; put it back too.
                (written + key).forEach { runCatching { store.write(it, before.values[it]) } }
                return CommitResult.RolledBack(key, e)
            }
        }
        return null
    }

    /** The values of every [ThemeKey] at one moment, as stored in the undo setting. */
    internal class Snapshot(val values: Map<ThemeKey, String?>) {
        fun toJson(): String =
            JSONObject()
                .put("v", 1)
                .put(
                    "values",
                    JSONObject().apply {
                        values.forEach { (key, value) -> put(key.key, value ?: JSONObject.NULL) }
                    },
                )
                .toString()

        companion object {
            /** Reads a snapshot, or null for a missing or unreadable one. */
            fun parse(json: String?): Snapshot? {
                if (json.isNullOrBlank()) return null
                return try {
                    val values = JSONObject(json).getJSONObject("values")
                    // A key the snapshot does not list was not owned when it was taken: keep it.
                    Snapshot(
                        ThemeKey.entries
                            .filter { values.has(it.key) }
                            .associateWith { key ->
                                if (values.isNull(key.key)) null else values.getString(key.key)
                            }
                    )
                } catch (e: JSONException) {
                    null
                }
            }
        }
    }
}
