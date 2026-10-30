/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import org.json.JSONObject

/** One engine outcome for a package, from [IntegrityKeys.TS_RUNG]. */
data class RungReport(val rung: String, val at: Long, val reason: String)

/**
 * Reads the ladder telemetry the engine writes after serving each target package.
 * Status only — the rung name, when it ran, and why it fell back, if it did.
 */
class RungStore(private val store: SecureStore) {
    fun report(pkg: String): RungReport? {
        val raw = store.getString(IntegrityKeys.TS_RUNG)
        if (raw.isEmpty()) return null
        return try {
            val entry = JSONObject(raw).optJSONObject(pkg) ?: return null
            RungReport(
                rung = entry.optString("rung"),
                at = entry.optLong("at"),
                reason = entry.optString("reason"),
            )
        } catch (_: Exception) { null }
    }

    fun all(): Map<String, RungReport> {
        val raw = store.getString(IntegrityKeys.TS_RUNG)
        if (raw.isEmpty()) return emptyMap()
        return try {
            val all = JSONObject(raw)
            buildMap {
                for (key in all.keys()) {
                    val entry = all.optJSONObject(key) ?: continue
                    put(key, RungReport(
                        rung = entry.optString("rung"),
                        at = entry.optLong("at"),
                        reason = entry.optString("reason"),
                    ))
                }
            }
        } catch (_: Exception) { emptyMap() }
    }
}
