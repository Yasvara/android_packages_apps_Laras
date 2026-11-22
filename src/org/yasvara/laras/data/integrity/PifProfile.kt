/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.security.yasvara.YasvaraSpoofEngine
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A spoofed device identity, in the field names the engine reads. */
class PifProfile private constructor(private val fields: Map<String, String>) {
    val model get() = fields["MODEL"].orEmpty()
    val manufacturer get() = fields["MANUFACTURER"].orEmpty()
    val brand get() = fields["BRAND"].orEmpty()
    val device get() = fields["DEVICE"].orEmpty()
    val product get() = fields["PRODUCT"].orEmpty()
    val fingerprint get() = fields["FINGERPRINT"].orEmpty()
    val id get() = fields["ID"].orEmpty()
    val incremental get() = fields["INCREMENTAL"].orEmpty()
    val securityPatch get() = fields["SECURITY_PATCH"].orEmpty()
    val release get() = fields["RELEASE"].orEmpty()
    val initialSdk get() = fields["DEVICE_INITIAL_SDK_INT"].orEmpty()

    fun toJson(): String = JSONObject(fields).toString()
    fun toPrettyJson(): String = JSONObject(fields).toString(2)

    /**
     * True when [raw] differs semantically from this normalized profile (e.g. a legacy Canary
     * entry with stale `"RELEASE":"16"` or missing derived fields), ignoring JSON key order,
     * whitespace, and slash escaping so healthy configs never trigger spurious writes.
     */
    fun differsSemanticallyFrom(raw: String): Boolean {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return true
        val rawMap = parseJson(trimmed) ?: return true
        val rawKnown = KEYS.mapNotNull { k ->
            rawMap[k]?.takeIf { it.isNotBlank() }?.let { k to it.trim() }
        }.toMap()
        return rawKnown != fields
    }

    /** Days since [securityPatch], or null when it is missing or malformed. */
    fun patchAgeDays(today: LocalDate = LocalDate.now()): Long? = try {
        ChronoUnit.DAYS.between(LocalDate.parse(securityPatch), today)
    } catch (_: Exception) { null }

    override fun equals(other: Any?) = other is PifProfile && other.fields == fields
    override fun hashCode() = fields.hashCode()

    companion object {
        private val KEYS = listOf(
            "BRAND", "DEVICE", "FINGERPRINT", "ID", "MANUFACTURER", "MODEL", "PRODUCT",
            "SECURITY_PATCH", "DEVICE_INITIAL_SDK_INT", "FIRST_API_LEVEL",
            "RELEASE", "INCREMENTAL", "TYPE", "TAGS", "BOARD_API_LEVEL", "API_LEVEL",
        )

        fun isValidFingerprint(fp: String) =
            fp.count { it == '/' } >= 4 && fp.contains(':') && fp.endsWith("release-keys")

        fun fromJson(text: String): PifProfile? = parse(text)

        /** Builds and normalizes a profile from manually entered fields; null if fingerprint invalid. */
        fun fromFields(entries: Map<String, String>): PifProfile? {
            val cleaned = entries.mapNotNull { (k, v) ->
                val tv = v.trim()
                if (tv.isEmpty()) null else k to tv
            }.toMap()
            return parse(JSONObject(cleaned).toString())
        }

        /**
         * Reads a profile from pif.json, a `{"pif": {…}}` wrapper, or the key=value pif.prop
         * format. Missing Build/VERSION fields are derived from the fingerprint when possible.
         * Returns null unless the result has a valid fingerprint.
         */
        fun parse(text: String): PifProfile? {
            val raw = text.trim()
            val map = (if (raw.startsWith("{")) parseJson(raw) else parseProp(raw)) ?: return null
            val fields = KEYS.mapNotNull { k -> map[k]?.takeIf { it.isNotBlank() }?.let { k to it.trim() } }
                .toMap().toMutableMap()
            val fp = fields["FINGERPRINT"] ?: return null
            if (!isValidFingerprint(fp)) return null
            if (!fields.containsKey("DEVICE_INITIAL_SDK_INT")) {
                fields["FIRST_API_LEVEL"]?.let { fields["DEVICE_INITIAL_SDK_INT"] = it }
            }
            // google/oriole_beta/oriole:16/BP22.250325.012/13467521:user/release-keys
            val slash = fp.split('/')
            if (slash.size >= 4) fields.getOrPut("ID") { slash[3] }
            var fpRel = ""
            if (slash.size >= 3) {
                fields.getOrPut("BRAND") { slash[0] }
                fields.getOrPut("PRODUCT") { slash[1] }
                fields.getOrPut("DEVICE") { slash[2].substringBefore(':') }
                fpRel = slash[2].substringAfter(':', "").trim()
                if (fpRel.isNotEmpty()) fields.getOrPut("RELEASE") { fpRel }
            }
            val currentRel = fields["RELEASE"].orEmpty()
            if (fpRel.isNotEmpty() && !isNumericRelease(fpRel)) {
                val inferred = GoogleFeed.inferReleaseForCodename(fpRel, fields["ID"].orEmpty())
                if (!isNumericRelease(currentRel) ||
                    currentRel.substringBefore('.') != inferred.substringBefore('.')
                ) {
                    fields["RELEASE"] = inferred
                }
            } else if (!isNumericRelease(currentRel)) {
                inferReleaseFromBuildId(fields["ID"].orEmpty())?.let { fields["RELEASE"] = it }
            }
            if (slash.size >= 6) {
                val inc = slash[4].substringBefore(':')
                val type = slash[4].substringAfter(':', "")
                if (inc.isNotEmpty()) fields.getOrPut("INCREMENTAL") { inc }
                if (type.isNotEmpty()) fields.getOrPut("TYPE") { type }
                if (slash[5].isNotEmpty()) fields.getOrPut("TAGS") { slash[5] }
            }
            return PifProfile(fields)
        }

        private fun isNumericRelease(rel: String): Boolean {
            val trimmed = rel.trim()
            if (trimmed.isEmpty()) return false
            return trimmed.split('.').all { part -> part.isNotEmpty() && part.all { it.isDigit() } }
        }

        internal fun inferReleaseFromBuildId(buildId: String): String? {
            val ver = YasvaraSpoofEngine.inferVersionFromBuildId(buildId) ?: return null
            val major = ver / 10000
            return if (major > 0) major.toString() else null
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
