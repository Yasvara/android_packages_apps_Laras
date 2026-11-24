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
import org.json.JSONObject

const val GBOARD_PACKAGE = "com.google.android.inputmethod.latin"
const val KETIK_HOOK_PACKAGE = "org.yasvara.ketik.hook"
private const val SERVICE = "yasvara_ketik"

/** Mirrors YasvaraKetikService.DENIED so raw flag editing cannot touch debug/log/metrics keys. */
private val DENIED_FLAG = Regex(
    "(^|_)(debug|logcat|log|logging|metrics|dry_run|training|federated|clearcut|primes)(_|$)"
)
private val VALID_FLAG_KEY = Regex("[a-z][a-z0-9_.]{0,127}")

/** What Gboard reports for a flag, from the service's filtered read of its dump. */
data class FlagState(
    val name: String,
    val type: String,
    val value: String,
    val sources: Set<String>,
) {
    val fromUs: Boolean get() = "MANAGED_CONFIG" in sources
    val editable: Boolean get() = type in EDITABLE_TYPES && !isDeniedFlag(name)

    companion object {
        private val EDITABLE_TYPES = setOf("Boolean", "Long", "Integer", "String")
    }
}

/** Safe runtime health snapshot of Gboard's live state (never contains keyboard history). */
data class KetikRuntimeHealth(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val running: Boolean = false,
    val hasRestrictions: Boolean = false,
    val totalFlags: Int = 0,
    val tags: Set<String> = emptySet(),
    val handlers: Set<String> = emptySet(),
    val modules: Set<String> = emptySet(),
)

fun isDeniedFlag(name: String): Boolean =
    !VALID_FLAG_KEY.matches(name) || DENIED_FLAG.containsMatchIn(name)

/** The flag service in system_server. Every call is false, null or empty when it isn't there. */
class KetikRepository(private val context: Context) {
    private fun service(): IYasvaraKetik? = try {
        ServiceManager.getService(SERVICE)?.let(IYasvaraKetik.Stub::asInterface)
    } catch (_: RuntimeException) {
        null
    }

    fun gboardInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(GBOARD_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun gboardVersion(): Pair<String, Long> = try {
        val pi = context.packageManager.getPackageInfo(GBOARD_PACKAGE, 0)
        (pi.versionName.orEmpty()) to pi.longVersionCode
    } catch (_: PackageManager.NameNotFoundException) {
        "" to 0L
    }

    fun catalog(): List<KetikFeatureDef> = KetikCatalog.load(context)

    suspend fun overrides(): Bundle? = call { getOverrides() }

    /**
     * Updates the stored overrides so [feature] is [enabled], preserving unrelated overrides and
     * resolving flag aliases against [liveKeys]. Also cleans up mutually exclusive features
     * (e.g. AI Writing Tools vs Proofread-only) and removes stale legacy flags.
     */
    suspend fun setFeatureEnabled(
        feature: KetikFeatureDef,
        enabled: Boolean,
        catalog: List<KetikFeatureDef>,
        liveKeys: Set<String>,
    ): Boolean = call {
        val bundle = getOverrides()
        // Drop dead legacy flag left over from earlier builds.
        bundle.remove("writing_helper_enable_text_stylization_internal")

        // Remove keys belonging to this feature (and any mutually exclusive peer when enabling).
        val targetsToClear = buildList {
            add(feature)
            if (enabled && feature.exclusiveGroup != null) {
                addAll(catalog.filter { it.id != feature.id && it.exclusiveGroup == feature.exclusiveGroup })
            }
        }
        val stillNeededByOthers = catalog
            .filter { f -> f !in targetsToClear && f.isOn(bundle) }
            .flatMapTo(HashSet()) { it.allKnownFlagKeys() }

        for (target in targetsToClear) {
            for (k in target.allKnownFlagKeys()) {
                if (k !in stillNeededByOthers) bundle.remove(k)
            }
        }

        if (enabled) {
            for ((k, v) in feature.resolvedFlags(liveKeys)) {
                bundle.putValue(k, v)
            }
        }
        setOverrides(bundle)
        true
    } ?: false

    suspend fun setSingleOverride(key: String, value: Any): Boolean = call {
        val bundle = getOverrides()
        bundle.putValue(key, value)
        setOverrides(bundle)
        true
    } ?: false

    suspend fun removeSingleOverride(key: String): Boolean = call {
        val bundle = getOverrides()
        bundle.remove(key)
        setOverrides(bundle)
        true
    } ?: false

    suspend fun clearAllOverrides(): Boolean = call {
        setOverrides(Bundle())
        true
    } ?: false

    suspend fun exportOverridesJson(): String? = call {
        val bundle = getOverrides()
        val root = JSONObject()
        for (k in bundle.keySet().sorted()) {
            val v = bundle.get(k) ?: continue
            root.put(k, v)
        }
        root.toString(2)
    }

    suspend fun importOverridesJson(json: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val svc = service() ?: error("Ketik service unavailable")
            val obj = JSONObject(json)
            val bundle = Bundle()
            val keys = obj.keys()
            var count = 0
            while (keys.hasNext()) {
                val k = keys.next()
                if (isDeniedFlag(k)) continue
                val v = obj.get(k)
                when (v) {
                    is Boolean -> { bundle.putBoolean(k, v); count++ }
                    is Int -> { bundle.putInt(k, v); count++ }
                    is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) {
                        bundle.putInt(k, v.toInt()); count++
                    }
                    is String -> { bundle.putString(k, v); count++ }
                }
            }
            svc.setOverrides(bundle)
            count
        }
    }

    /** Empty when Gboard's keyboard service is not running, which says nothing about the flags. */
    suspend fun flagStates(names: Collection<String>): Map<String, FlagState> = call {
        readFlagStates(names.toTypedArray()).mapNotNull(::parseFlagRow).associateBy { it.name }
    }.orEmpty()

    /** Full live flag catalog reported by Gboard (~2,000 flags). */
    suspend fun allFlagStates(): List<FlagState> = call {
        readFlagStates(null).mapNotNull(::parseFlagRow).sortedBy { it.name }
    }.orEmpty()

    /** Safe live health snapshot (version, active GenAI handlers, whitelisted readiness tags). */
    suspend fun runtimeHealth(): KetikRuntimeHealth = call {
        val b = try {
            readRuntimeHealth()
        } catch (_: Throwable) {
            // Backwards compatibility if Laras runs against an older system_server before reboot.
            null
        }
        if (b == null) {
            val (ver, code) = gboardVersion()
            return@call KetikRuntimeHealth(versionName = ver, versionCode = code)
        }
        KetikRuntimeHealth(
            versionName = b.getString("versionName").orEmpty(),
            versionCode = b.getLong("versionCode", 0L),
            running = b.getBoolean("running", false),
            hasRestrictions = b.getBoolean("hasRestrictions", false),
            totalFlags = b.getInt("totalFlags", 0),
            tags = b.getStringArrayList("tags")?.toSet().orEmpty(),
            handlers = b.getStringArrayList("handlers")?.toSet().orEmpty(),
            modules = b.getStringArrayList("modules")?.toSet().orEmpty(),
        )
    } ?: KetikRuntimeHealth()

    suspend fun lastRevert(): List<String> = call { getLastRevert() }.orEmpty()

    suspend fun clearLastRevert() {
        call { clearLastRevert() }
    }

    private fun parseFlagRow(row: String): FlagState? {
        val c = row.split('\t')
        if (c.size < 4) return null
        val sources = if (c[3].isBlank()) emptySet() else c[3].split(',').toSet()
        return FlagState(name = c[0], type = c[1], value = c[2], sources = sources)
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
            is Long -> putInt(key, value.toInt())
            else -> putString(key, value.toString())
        }
    }
}
