/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.ketik

import android.content.Context
import android.os.Bundle
import android.util.Log
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.yasvara.laras.R

enum class KetikCategory { AI, CORE, LAB }

/** One flag rule in a feature, supporting alias fallback when Google renames flags across updates. */
data class KetikFlagSpec(
    val key: String,
    val aliases: List<String>,
    val value: Any,
) {
    /** Picks the first key present in Gboard's live catalog, falling back to the primary [key]. */
    fun resolveKey(liveKeys: Set<String>): String {
        if (liveKeys.isEmpty() || key in liveKeys) return key
        return aliases.firstOrNull { it in liveKeys } ?: key
    }

    fun allKeys(): List<String> = if (aliases.isEmpty()) listOf(key) else listOf(key) + aliases
}

/** A dynamic Gboard feature entry loaded from bundled assets or an optional user JSON override. */
data class KetikFeatureDef(
    val id: String,
    val category: KetikCategory,
    val title: String,
    val summary: String,
    val flags: List<KetikFlagSpec>,
    val cloud: Boolean = false,
    val requiresHook: Boolean = false,
    val exclusiveGroup: String? = null,
    val requiredTags: List<String> = emptyList(),
) {
    /** True when every flag spec of this feature matches [overrides] (on primary key or alias). */
    fun isOn(overrides: Bundle): Boolean = flags.all { spec ->
        spec.allKeys().any { k -> overrides.containsKey(k) && overrides.get(k) == spec.value }
    }

    fun resolvedFlags(liveKeys: Set<String>): Map<String, Any> =
        flags.associate { it.resolveKey(liveKeys) to it.value }

    fun allKnownFlagKeys(): Set<String> = flags.flatMapTo(LinkedHashSet()) { it.allKeys() }
}

/**
 * Loads the Ketik feature catalog from an optional override file in Laras' filesDir
 * (`ketik_catalog.json`) or falls back to the bundled `assets/ketik_catalog.json`.
 */
object KetikCatalog {
    private const val TAG = "KetikCatalog"
    private const val ASSET_NAME = "ketik_catalog.json"

    fun overrideFile(context: Context): File = File(context.filesDir, ASSET_NAME)

    fun load(context: Context): List<KetikFeatureDef> {
        val override = overrideFile(context)
        val raw = runCatching {
            if (override.isFile) override.readText()
            else context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.onFailure {
            Log.e(TAG, "Failed to read $ASSET_NAME", it)
        }.getOrNull() ?: return emptyList()
        return parse(context, raw)
    }

    fun parse(context: Context, jsonText: String): List<KetikFeatureDef> {
        val out = ArrayList<KetikFeatureDef>()
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return out
        val arr = root.optJSONArray("features") ?: return out
        val res = context.resources
        val pkg = context.packageName

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id").trim()
            if (id.isEmpty()) continue
            val cat = runCatching { KetikCategory.valueOf(obj.optString("category", "CORE")) }
                .getOrDefault(KetikCategory.CORE)
            val title = resolveString(res, pkg, obj.optString("titleKey"), obj.optString("title", id))
            val summary = resolveString(res, pkg, obj.optString("summaryKey"), obj.optString("summary", ""))
            val flags = parseFlags(obj.optJSONArray("flags"))
            if (flags.isEmpty()) continue
            val requiredTags = parseStringList(obj.optJSONArray("requiredTags"))
            out.add(
                KetikFeatureDef(
                    id = id,
                    category = cat,
                    title = title,
                    summary = summary,
                    flags = flags,
                    cloud = obj.optBoolean("cloud", false),
                    requiresHook = obj.optBoolean("requiresHook", false),
                    exclusiveGroup = obj.optString("exclusiveGroup").takeIf { it.isNotBlank() },
                    requiredTags = requiredTags,
                ),
            )
        }
        return out
    }

    private fun parseFlags(arr: JSONArray?): List<KetikFlagSpec> {
        if (arr == null) return emptyList()
        val out = ArrayList<KetikFlagSpec>(arr.length())
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val key = f.optString("key").trim()
            if (key.isEmpty()) continue
            val aliases = parseStringList(f.optJSONArray("aliases"))
            val value: Any = when (f.optString("type", "b")) {
                "b" -> f.optBoolean("value", false)
                "i" -> f.optInt("value", 0)
                else -> f.optString("value", "")
            }
            out.add(KetikFlagSpec(key, aliases, value))
        }
        return out
    }

    private fun parseStringList(arr: JSONArray?): List<String> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }

    private fun resolveString(
        res: android.content.res.Resources,
        pkg: String,
        key: String,
        fallback: String,
    ): String {
        val staticId = KNOWN_STRINGS[key]
        if (staticId != null) return res.getString(staticId)
        if (key.isNotBlank()) {
            val id = res.getIdentifier(key, "string", pkg)
            if (id != 0) return res.getString(id)
        }
        return fallback
    }

    /** Explicit R.string references so R8 shrink_resources never strips catalog titles/summaries. */
    private val KNOWN_STRINGS: Map<String, Int> = mapOf(
        "ketik_writing_tools" to R.string.ketik_writing_tools,
        "ketik_writing_tools_summary" to R.string.ketik_writing_tools_summary,
        "ketik_proofread_only" to R.string.ketik_proofread_only,
        "ketik_proofread_only_summary" to R.string.ketik_proofread_only_summary,
        "ketik_writing_tools_v2" to R.string.ketik_writing_tools_v2,
        "ketik_writing_tools_v2_summary" to R.string.ketik_writing_tools_v2_summary,
        "ketik_agentic_dictation" to R.string.ketik_agentic_dictation,
        "ketik_agentic_dictation_summary" to R.string.ketik_agentic_dictation_summary,
        "ketik_modeless_smartedit" to R.string.ketik_modeless_smartedit,
        "ketik_modeless_smartedit_summary" to R.string.ketik_modeless_smartedit_summary,
        "ketik_llm_grammar" to R.string.ketik_llm_grammar,
        "ketik_llm_grammar_summary" to R.string.ketik_llm_grammar_summary,
        "ketik_key_shape" to R.string.ketik_key_shape,
        "ketik_key_shape_summary" to R.string.ketik_key_shape_summary,
        "ketik_quick_insert" to R.string.ketik_quick_insert,
        "ketik_quick_insert_summary" to R.string.ketik_quick_insert_summary,
        "ketik_trackpad" to R.string.ketik_trackpad,
        "ketik_trackpad_summary" to R.string.ketik_trackpad_summary,
        "ketik_bluetooth_mic" to R.string.ketik_bluetooth_mic,
        "ketik_bluetooth_mic_summary" to R.string.ketik_bluetooth_mic_summary,
        "ketik_dismiss_proactive" to R.string.ketik_dismiss_proactive,
        "ketik_dismiss_proactive_summary" to R.string.ketik_dismiss_proactive_summary,
        "ketik_smart_compose" to R.string.ketik_smart_compose,
        "ketik_smart_compose_summary" to R.string.ketik_smart_compose_summary,
        "ketik_accessibility" to R.string.ketik_accessibility,
        "ketik_accessibility_summary" to R.string.ketik_accessibility_summary,
        "ketik_clipboard_limit" to R.string.ketik_clipboard_limit,
        "ketik_clipboard_limit_summary" to R.string.ketik_clipboard_limit_summary,
        "ketik_scan_text" to R.string.ketik_scan_text,
        "ketik_scan_text_summary" to R.string.ketik_scan_text_summary,
        "ketik_lab_gm3_expressive" to R.string.ketik_lab_gm3_expressive,
        "ketik_lab_gm3_expressive_summary" to R.string.ketik_lab_gm3_expressive_summary,
        "ketik_lab_top_rounded" to R.string.ketik_lab_top_rounded,
        "ketik_lab_top_rounded_summary" to R.string.ketik_lab_top_rounded_summary,
        "ketik_lab_render_blur" to R.string.ketik_lab_render_blur,
        "ketik_lab_render_blur_summary" to R.string.ketik_lab_render_blur_summary,
        "ketik_lab_floating_freeform" to R.string.ketik_lab_floating_freeform,
        "ketik_lab_floating_freeform_summary" to R.string.ketik_lab_floating_freeform_summary,
        "ketik_lab_floating_landscape" to R.string.ketik_lab_floating_landscape,
        "ketik_lab_floating_landscape_summary" to R.string.ketik_lab_floating_landscape_summary,
        "ketik_lab_clipboard_paste" to R.string.ketik_lab_clipboard_paste,
        "ketik_lab_clipboard_paste_summary" to R.string.ketik_lab_clipboard_paste_summary,
        "ketik_lab_emoji_ux" to R.string.ketik_lab_emoji_ux,
        "ketik_lab_emoji_ux_summary" to R.string.ketik_lab_emoji_ux_summary,
    )
}
