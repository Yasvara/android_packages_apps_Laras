/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A spoofed device identity, in the field names the engine reads. */
class PifProfile private constructor(private val fields: Map<String, String>) {
    val model get() = fields["MODEL"].orEmpty()
    val fingerprint get() = fields["FINGERPRINT"].orEmpty()
    val securityPatch get() = fields["SECURITY_PATCH"].orEmpty()

    fun toJson(): String = JSONObject(fields).toString()

    /** Days since [securityPatch], or null when it is missing or malformed. */
    fun patchAgeDays(today: LocalDate = LocalDate.now()): Long? = try {
        ChronoUnit.DAYS.between(LocalDate.parse(securityPatch), today)
    } catch (_: Exception) { null }

    override fun equals(other: Any?) = other is PifProfile && other.fields == fields
    override fun hashCode() = fields.hashCode()

    companion object {
        private val KEYS = listOf(
            "BRAND", "DEVICE", "FINGERPRINT", "ID", "MANUFACTURER", "MODEL", "PRODUCT",
            "SECURITY_PATCH", "DEVICE_INITIAL_SDK_INT",
        )

        fun isValidFingerprint(fp: String) =
            fp.count { it == '/' } >= 4 && fp.contains(':') && fp.endsWith("release-keys")

        /**
         * Reads a profile from pif.json, a `{"pif": {…}}` wrapper, or the key=value pif.prop
         * format. BRAND/DEVICE/PRODUCT/ID are derived from the fingerprint when absent.
         * Returns null unless the result has a valid fingerprint.
         */
        fun parse(text: String): PifProfile? {
            val raw = text.trim()
            val map = (if (raw.startsWith("{")) parseJson(raw) else parseProp(raw)) ?: return null
            val fields = KEYS.mapNotNull { k -> map[k]?.takeIf { it.isNotBlank() }?.let { k to it } }
                .toMap().toMutableMap()
            val fp = fields["FINGERPRINT"] ?: return null
            if (!isValidFingerprint(fp)) return null
            // google/oriole_beta/oriole:16/BP22.250325.012/13467521:user/release-keys
            val slash = fp.split('/')
            fields.getOrPut("BRAND") { slash[0] }
            fields.getOrPut("PRODUCT") { slash[1] }
            fields.getOrPut("DEVICE") { slash[2].substringBefore(':') }
            fields.getOrPut("ID") { slash[3] }
            return PifProfile(fields)
        }

        private fun parseJson(raw: String): Map<String, String>? = try {
            val root = JSONObject(raw)
            val o = root.optJSONObject("pif") ?: root
            o.keys().asSequence().associateWith { o.optString(it) }
        } catch (_: Exception) { null }

        private fun parseProp(raw: String): Map<String, String> = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }
}
