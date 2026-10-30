/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import org.json.JSONObject

/** One Pixel device as listed on Google's factory images page. */
data class GoogleDevice(val id: String, val model: String)

/**
 * Fetches device profiles straight from Google, the same flow as the Yasvara web tool:
 * developer.android.com versions → latest version page → factory images page (device
 * list) → flash.android.com (API key) → Flashstation builds API per product.
 *
 * Channels map to Flashstation products: stable uses `<id>`, beta/canary use
 * `<id>_beta` filtered by `previewMetadata.active` / `canary`. Device list and API key
 * are cached in Settings; builds are always live.
 */
object GoogleFeed {
    private const val VERSIONS_URL = "https://developer.android.com/about/versions"
    private const val FLASH_URL = "https://flash.android.com/"
    private const val BUILDS_URL = "https://content-flashstation-pa.googleapis.com/v1/builds"
    private const val REFERER = "https://flash.android.com"

    private const val DEVICE_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

    private val VERSION_LINK = Regex("""href="(/about/versions/\d+)"""")
    private val DOWNLOAD_LINK = Regex("""href="([^"]+download[^"]*)"""")
    private val DEVICE_ROW = Regex("""<tr[^>]*id="([^"]+)"[^>]*>[\s\S]*?<td[^>]*>([^<]+)</td>""")
    private val API_KEY = Regex("""AIza[a-zA-Z0-9_\-]+""")
    private val PATCH_DATE = Regex("""\.(\d{2})(\d{2})\d{2}\.""")

    /** Cached Pixel device list, re-scraped when older than 7 days. */
    suspend fun devices(context: Context, store: SecureStore, force: Boolean = false): List<GoogleDevice> {
        if (!force) {
            cachedDevices(store)?.let { return it }
        }
        val versionsHtml = Http.get(context, VERSIONS_URL)
        var latestUrl = ""
        var maxVersion = -1
        var major = ""
        for (match in VERSION_LINK.findAll(versionsHtml)) {
            val url = match.groupValues[1]
            val num = url.substringAfterLast('/').toIntOrNull() ?: continue
            if (num > maxVersion) {
                maxVersion = num
                latestUrl = "https://developer.android.com$url"
                major = num.toString()
            }
        }
        if (latestUrl.isEmpty()) throw FetchException(FetchFailure(FailureKind.OTHER, "no version page"))
        val versionHtml = Http.get(context, latestUrl)
        var downloadUrl = ""
        for (match in DOWNLOAD_LINK.findAll(versionHtml)) {
            val url = match.groupValues[1]
            if ("qpr" in url) {
                downloadUrl = url
                break
            }
            if (downloadUrl.isEmpty()) downloadUrl = url
        }
        if (!downloadUrl.startsWith("http")) downloadUrl = "https://developer.android.com$downloadUrl"
        val factoryHtml = Http.get(context, downloadUrl)
        val seen = LinkedHashMap<String, String>()
        for (match in DEVICE_ROW.findAll(factoryHtml)) {
            val id = match.groupValues[1].trim()
            val model = match.groupValues[2].trim()
            if (id.isNotEmpty() && model.isNotEmpty() && ' ' !in id && id !in seen) {
                seen[id] = model
            }
        }
        if (seen.isEmpty()) throw FetchException(FetchFailure(FailureKind.OTHER, "no devices"))
        val devices = seen.map { (id, model) -> GoogleDevice(id, model) }
        store.putString(
            IntegrityKeys.PIF_GOOGLE_DEVICES,
            JSONObject(mapOf("major" to major, "devices" to devices.map { mapOf("id" to it.id, "model" to it.model) })).toString(),
        )
        store.putLong(IntegrityKeys.PIF_GOOGLE_DEVICES_AT, System.currentTimeMillis())
        return devices
    }

    /** Latest Android major from the cached scrape ("" when unknown). */
    fun cachedMajor(store: SecureStore): String = try {
        JSONObject(store.getString(IntegrityKeys.PIF_GOOGLE_DEVICES)).optString("major")
    } catch (_: Exception) { "" }

    private fun cachedDevices(store: SecureStore): List<GoogleDevice>? {
        val at = store.getLong(IntegrityKeys.PIF_GOOGLE_DEVICES_AT)
        if (at <= 0L || System.currentTimeMillis() - at > DEVICE_CACHE_TTL_MS) return null
        return try {
            val devices = JSONObject(store.getString(IntegrityKeys.PIF_GOOGLE_DEVICES))
                .optJSONArray("devices") ?: return null
            buildList {
                for (i in 0 until devices.length()) {
                    val o = devices.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val model = o.optString("model")
                    if (id.isNotEmpty() && model.isNotEmpty()) add(GoogleDevice(id, model))
                }
            }.ifEmpty { null }
        } catch (_: Exception) { null }
    }

    /**
     * Builds for one device + channel, newest first, as PIF profiles. Usually a single
     * profile wins; the list form keeps the door open for multi-build choice.
     */
    suspend fun builds(
        context: Context,
        store: SecureStore,
        device: GoogleDevice,
        channel: PifChannel,
    ): List<PifProfile> {
        val key = apiKey(context, store)
        val headers = mapOf("Referer" to REFERER)
        val major = cachedMajor(store)
        return when (channel) {
            PifChannel.STABLE -> {
                val builds = query(context, device.id, key, headers)
                val newest = builds.maxByOrNull { it.optString("buildId") } ?: return emptyList()
                listOfNotNull(profileFor(device, newest, channel, major))
            }
            PifChannel.BETA, PifChannel.CANARY -> {
                val builds = query(context, "${device.id}_beta", key, headers)
                val wantCanary = channel == PifChannel.CANARY
                val target = builds.reversed().firstOrNull { build ->
                    val meta = build.optJSONObject("previewMetadata") ?: return@firstOrNull false
                    meta.optBoolean("active") && meta.optBoolean("canary") == wantCanary
                } ?: return emptyList()
                listOfNotNull(profileFor(device, target, channel, major, beta = true))
            }
        }
    }

    private suspend fun apiKey(context: Context, store: SecureStore): String {
        store.getString(IntegrityKeys.PIF_GOOGLE_KEY).takeIf { it.isNotEmpty() }?.let { return it }
        val html = Http.get(context, FLASH_URL)
        val key = API_KEY.find(html)?.value
            ?: throw FetchException(FetchFailure(FailureKind.OTHER, "no api key"))
        store.putString(IntegrityKeys.PIF_GOOGLE_KEY, key)
        return key
    }

    private suspend fun query(
        context: Context,
        product: String,
        key: String,
        headers: Map<String, String>,
    ): List<JSONObject> {
        val body = Http.get(context, "$BUILDS_URL?product=$product&key=$key", headers = headers)
        val builds = try {
            JSONObject(body).optJSONArray("flashstationBuild")
        } catch (_: Exception) { null } ?: return emptyList()
        return buildList {
            for (i in 0 until builds.length()) {
                builds.optJSONObject(i)?.let { add(it) }
            }
        }
    }

    private fun profileFor(
        device: GoogleDevice,
        build: JSONObject,
        channel: PifChannel,
        major: String,
        beta: Boolean = false,
    ): PifProfile? {
        val releaseId = build.optString("releaseCandidateName").trim()
        val incremental = build.optString("buildId").trim()
        if (releaseId.isEmpty() || incremental.isEmpty()) return null
        val product = if (beta) "${device.id}_beta" else device.id
        val release = when (channel) {
            PifChannel.STABLE -> major.ifEmpty { "16" }
            else -> "CANARY"
        }
        val fingerprint =
            "google/$product/${device.id}:$release/$releaseId/$incremental:user/release-keys"
        val patch = PATCH_DATE.find(releaseId)?.let { "20${it.groupValues[1]}-${it.groupValues[2]}-05" }
            ?: releaseId.substringBefore('.').takeIf { it.length == 4 }?.let { "$it-05" }
            ?: return null
        val json = JSONObject()
            .put("CHANNEL", channel.id.replaceFirstChar { it.uppercase() })
            .put("MANUFACTURER", "Google")
            .put("MODEL", device.model)
            .put("FINGERPRINT", fingerprint)
            .put("BRAND", "google")
            .put("PRODUCT", product)
            .put("DEVICE", device.id)
            .put("RELEASE", release)
            .put("ID", releaseId)
            .put("INCREMENTAL", incremental)
            .put("TYPE", "user")
            .put("TAGS", "release-keys")
            .put("SECURITY_PATCH", patch)
            .put("DEVICE_INITIAL_SDK_INT", "32")
            .toString()
        return PifProfile.parse(json)
    }
}
