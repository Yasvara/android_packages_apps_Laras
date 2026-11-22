/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.ketik

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
import com.android.server.yasvara.ketik.IYasvaraKetik
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yasvara.laras.R

const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
private const val SERVICE = "yasvara_ketik"
private const val SERVER_MODEL = "202504090000_writing_tools_config"

/** A Gboard feature behind flags. It is on when every value below is in the stored overrides. */
enum class KetikFeature(
    val title: Int,
    val summary: Int,
    val flags: Map<String, Any>,
    /** Typed text leaves the device, so turning it on asks first. */
    val cloud: Boolean = false,
) {
    WRITING_TOOLS(
        R.string.ketik_writing_tools, R.string.ketik_writing_tools_summary,
        mapOf(
            "writing_helper" to true,
            "writing_tools" to true,
            "writing_helper_on_selected_text" to true,
            "enable_writing_tools_cooperative_mode" to true,
            "writing_helper_enable_text_stylization_internal" to true,
            // 1 is Gboard's server. The others need AICore or attested Private Compute.
            "writing_tools_v2_backend_type" to 1,
            "writing_helper_supported_language_tags" to "*",
            "writing_helper_model_version" to SERVER_MODEL,
            "writing_helper_voice_input_model_version" to SERVER_MODEL,
            "writing_helper_text_stylization_model_version" to SERVER_MODEL,
        ),
        cloud = true,
    ),
    KEY_SHAPE(R.string.ketik_key_shape, R.string.ketik_key_shape_summary, mapOf("more_pill_keys" to true)),
    QUICK_INSERT(
        R.string.ketik_quick_insert, R.string.ketik_quick_insert_summary,
        mapOf("super_insert" to true, "super_insert_vk" to true),
    ),
    TRACKPAD(
        R.string.ketik_trackpad, R.string.ketik_trackpad_summary,
        mapOf("free_cursor" to true, "free_cursor_lock_mode" to true),
    ),
    BLUETOOTH_MIC(
        R.string.ketik_bluetooth_mic, R.string.ketik_bluetooth_mic_summary,
        mapOf("enable_use_bluetooth_setting" to true),
    ),
    DISMISS_PROACTIVE(
        R.string.ketik_dismiss_proactive, R.string.ketik_dismiss_proactive_summary,
        mapOf("enable_close_proactive_suggestions_access_point" to true),
    ),
    SMART_COMPOSE(
        R.string.ketik_smart_compose, R.string.ketik_smart_compose_summary,
        mapOf("enable_inline_suggestions_on_client_side" to true),
    ),
    ACCESSIBILITY_LAYOUT(
        R.string.ketik_accessibility, R.string.ketik_accessibility_summary,
        mapOf("enable_pk_simulator" to true),
    ),
    CLIPBOARD_LIMIT(
        R.string.ketik_clipboard_limit, R.string.ketik_clipboard_limit_summary,
        mapOf("text_clip_item_char_limit" to 100000),
    ),
    SCAN_TEXT(R.string.ketik_scan_text, R.string.ketik_scan_text_summary, mapOf("enable_ocr" to true)),
}

/** What Gboard reports for a flag, from the service's filtered read of its dump. */
data class FlagState(val name: String, val value: String, val sources: Set<String>) {
    val fromUs get() = "MANAGED_CONFIG" in sources
}

/** The flag service in system_server. Every call is false, null or empty when it isn't there. */
class KetikRepository(private val context: Context) {
    private fun service(): IYasvaraKetik? = try {
        ServiceManager.getService(SERVICE)?.let(IYasvaraKetik.Stub::asInterface)
    } catch (e: RuntimeException) {
        null
    }

    fun gboardInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(GBOARD_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    suspend fun overrides(): Bundle? = call { getOverrides() }

    /** Rewrites the stored bundle so exactly [features] are on, keeping unrelated overrides. */
    suspend fun setFeatures(features: Set<KetikFeature>): Boolean = call {
        val bundle = getOverrides()
        KetikFeature.entries.forEach { f -> f.flags.keys.forEach(bundle::remove) }
        features.forEach { f -> f.flags.forEach { (k, v) -> bundle.putValue(k, v) } }
        setOverrides(bundle)
        true
    } ?: false

    /** Empty when Gboard's keyboard service is not running, which says nothing about the flags. */
    suspend fun flagStates(names: Collection<String>): Map<String, FlagState> = call {
        readFlagStates(names.toTypedArray()).mapNotNull { row ->
            val c = row.split('\t')
            if (c.size < 4) return@mapNotNull null
            c[0] to FlagState(c[0], c[2], c[3].split(',').toSet())
        }.toMap()
    }.orEmpty()

    suspend fun lastRevert(): List<String> = call { getLastRevert() }.orEmpty()

    suspend fun clearLastRevert() {
        call { clearLastRevert() }
    }

    private suspend fun <T> call(block: IYasvaraKetik.() -> T): T? = withContext(Dispatchers.IO) {
        try {
            service()?.block()
        } catch (e: Exception) {
            Log.w("Ketik", "Flag service call failed", e)
            null
        }
    }

    private fun Bundle.putValue(key: String, value: Any) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            else -> putString(key, value.toString())
        }
    }
}

/** True when every value of this feature is in [overrides]. */
fun KetikFeature.isOn(overrides: Bundle): Boolean = flags.all { (k, v) -> overrides.get(k) == v }
