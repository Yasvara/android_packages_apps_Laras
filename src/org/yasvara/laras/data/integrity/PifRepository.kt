/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import android.util.Log
import java.io.File

enum class PifSource(val id: String) {
    CUSTOM("custom"), GOOGLE("google"), YASVARA("yasvara"), COMMUNITY("community"),
    IMPORTED("imported"), BUILT_IN("built-in");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** Update channel: stable release prints (DEVICE verdict) or beta/canary (STRONG only). */
enum class PifChannel(val id: String) {
    STABLE("stable"), BETA("beta"), CANARY("canary");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: STABLE
    }
}

/** The profile in effect and where it came from. */
class StoredPif(val profile: PifProfile, val source: PifSource, val fetchedAt: Long)

class SourceFailure(val source: PifSource, val failure: FetchFailure)

sealed interface PifUpdate {
    /** [changed] is false when the source served the profile already in effect. */
    class Updated(val stored: StoredPif, val changed: Boolean) : PifUpdate

    /** Every source failed; the profile in effect is untouched. [attempts] is in try-order. */
    class Failed(val attempts: List<SourceFailure>) : PifUpdate
}

/**
 * Loads, updates and imports the spoofed device profile. Updates fetch from the selected
 * channel and merge every valid profile into the on-device library; the fetched (or
 * chosen) profile becomes active. A failed update never disturbs the active profile.
 */
class PifRepository(private val context: Context, private val store: SecureStore) {
    var customUrl: String
        get() = store.getString(IntegrityKeys.PIF_CUSTOM_URL)
        set(value) = store.putString(IntegrityKeys.PIF_CUSTOM_URL, value.trim())

    var channel: PifChannel
        get() = PifChannel.of(store.getString(IntegrityKeys.PIF_CHANNEL))
        set(value) = store.putString(IntegrityKeys.PIF_CHANNEL, value.id)

    /** The Google device codename profiles are fetched for ("" = first known). */
    var device: String
        get() = store.getString(IntegrityKeys.PIF_DEVICE)
        set(value) = store.putString(IntegrityKeys.PIF_DEVICE, value.trim())

    /** The profile in effect: the stored one, else the ROM's built-in fallback. */
    fun current(): StoredPif? {
        PifProfile.parse(store.getString(IntegrityKeys.PIF_CONFIG))?.let {
            val source = PifSource.of(store.getString(IntegrityKeys.PIF_SOURCE)) ?: PifSource.IMPORTED
            return StoredPif(it, source, store.getLong(IntegrityKeys.PIF_LAST_FETCH))
        }
        return builtIn()?.let { StoredPif(it, PifSource.BUILT_IN, 0L) }
    }

    /** Every known profile, newest patch first. The active one is in [current]. */
    fun library(): List<PifProfile> {
        val raw = store.getString(IntegrityKeys.PIF_LIBRARY)
        if (raw.isEmpty()) return emptyList()
        return try {
            val root = org.json.JSONObject(raw)
            buildList {
                for (key in root.keys()) {
                    PifProfile.parse(root.optString(key))?.let { add(it) }
                }
            }.sortedByDescending { it.securityPatch }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun update(): PifUpdate = update(channel)

    suspend fun update(channel: PifChannel): PifUpdate {
        val failures = mutableListOf<SourceFailure>()
        // 1. Custom feed first, when configured.
        customUrl.takeIf { it.isNotBlank() }?.let { url ->
            try {
                val profiles = parseFeed(Http.get(context, url, MAX_BYTES))
                if (profiles.isEmpty()) throw FetchException(FetchFailure(FailureKind.OTHER, "no profiles"))
                return activateFetched(profiles, PifSource.CUSTOM)
            } catch (e: FetchException) {
                failures += SourceFailure(PifSource.CUSTOM, e.failure)
            }
        }
        // 2. Straight from Google: device list, then builds for the chosen device.
        try {
            val devices = GoogleFeed.devices(context, store)
            if (devices.isEmpty()) throw FetchException(FetchFailure(FailureKind.OTHER, "no devices"))
            val target = devices.firstOrNull { it.id == device } ?: devices.first()
            val profiles = GoogleFeed.builds(context, store, target, channel)
            if (profiles.isEmpty()) {
                throw FetchException(FetchFailure(FailureKind.OTHER, "no ${channel.id} builds"))
            }
            if (device != target.id) device = target.id
            return activateFetched(profiles, PifSource.GOOGLE)
        } catch (e: FetchException) {
            failures += SourceFailure(PifSource.GOOGLE, e.failure)
        }
        return PifUpdate.Failed(failures)
    }

    /** Re-scrapes Google's Pixel device list (cached 7 days otherwise). */
    suspend fun refreshGoogleDevices(force: Boolean = false): List<GoogleDevice> =
        GoogleFeed.devices(context, store, force)

    private fun activateFetched(profiles: List<PifProfile>, source: PifSource): PifUpdate {
        mergeLibrary(profiles)
        val before = current()?.takeIf { it.source != PifSource.BUILT_IN }?.profile
        save(profiles.first(), source)
        return PifUpdate.Updated(current()!!, changed = before != profiles.first())
    }

    /** Imports pif.json / pif.prop text, adds it to the library and activates it. */
    fun import(text: String): StoredPif? {
        val profile = PifProfile.parse(text) ?: return null
        mergeLibrary(listOf(profile))
        save(profile, PifSource.IMPORTED)
        return current()
    }

    /** Activates a library profile by fingerprint. Returns null when unknown. */
    fun activate(fingerprint: String): StoredPif? {
        val profile = library().firstOrNull { it.fingerprint == fingerprint } ?: return null
        save(profile, PifSource.of(store.getString(IntegrityKeys.PIF_SOURCE)) ?: PifSource.IMPORTED)
        return current()
    }

    /** Drops the stored profile so the engine falls back to the built-in one. */
    fun resetToBuiltIn() {
        store.putString(IntegrityKeys.PIF_CONFIG, "")
        store.putString(IntegrityKeys.PIF_SOURCE, "")
        store.putLong(IntegrityKeys.PIF_LAST_FETCH, 0L)
    }

    private fun save(profile: PifProfile, source: PifSource) {
        store.putString(IntegrityKeys.PIF_CONFIG, profile.toJson())
        store.putString(IntegrityKeys.PIF_SOURCE, source.id)
        store.putLong(IntegrityKeys.PIF_LAST_FETCH, System.currentTimeMillis())
        profile.securityPatch.takeIf { it.isNotEmpty() }
            ?.let { store.putString(IntegrityKeys.TS_PATCH, it) }
    }

    /** Adds profiles to the library, keyed by fingerprint (dedupe); trims to 24 newest. */
    private fun mergeLibrary(profiles: List<PifProfile>) {
        try {
            val root = try {
                org.json.JSONObject(store.getString(IntegrityKeys.PIF_LIBRARY))
            } catch (_: Exception) { org.json.JSONObject() }
            for (profile in profiles) {
                if (profile.fingerprint.isNotEmpty()) root.put(profile.fingerprint, profile.toJson())
            }
            // Bound the library so Settings never bloats: keep the 24 newest patches.
            val sorted = buildList {
                for (key in root.keys()) {
                    PifProfile.parse(root.optString(key))?.let { add(it) }
                }
            }.sortedByDescending { it.securityPatch }.take(24)
            val trimmed = org.json.JSONObject()
            for (profile in sorted) trimmed.put(profile.fingerprint, profile.toJson())
            store.putString(IntegrityKeys.PIF_LIBRARY, trimmed.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the profile library", e)
        }
    }

    /**
     * Reads a feed: either a `{"devices": [...]}` list (stable/canary collections) or a
     * single pif.json / pif.prop profile. Invalid entries are skipped, never fatal.
     */
    private fun parseFeed(text: String): List<PifProfile> {
        val raw = text.trim()
        if (raw.startsWith("{")) {
            try {
                val root = org.json.JSONObject(raw)
                val devices = root.optJSONArray("devices")
                if (devices != null) {
                    return buildList {
                        for (i in 0 until devices.length()) {
                            PifProfile.parse(devices.opt(i)?.toString().orEmpty())?.let { add(it) }
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        return listOfNotNull(PifProfile.parse(raw))
    }

    private fun builtIn(): PifProfile? = try {
        PifProfile.parse(File(BUILT_IN_PATH).readText())
    } catch (_: Exception) { null }

    companion object {
        private const val MAX_BYTES = 256 * 1024
        private const val BUILT_IN_PATH = "/product/etc/yasvara/pif_fallback.json"
    }
}

private const val TAG = "PifRepository"
