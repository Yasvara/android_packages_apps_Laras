/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.samar

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Place(
    val name: String,
    /** Street, city, region, country: whatever the result has beyond its name. */
    val detail: String,
    val lat: Double,
    val lng: Double,
    /** The place's extent, for zooming to a city or a country rather than a point. */
    val bounds: LatLngBounds? = null,
)

/**
 * Place search over OpenStreetMap data: Photon (search-as-you-type, typo tolerant), with Nominatim
 * when Photon fails. Only the query and, to rank nearby results first, the centre of the map on
 * screen are sent. Network; call off the main thread.
 */
object SamarSearch {
    private const val PHOTON = "https://photon.komoot.io/api/"
    private const val NOMINATIM = "https://nominatim.openstreetmap.org/search"
    private const val USER_AGENT = "Laras/1 (Yasvara)"
    private const val LIMIT = 7

    fun search(query: String, nearLat: Double? = null, nearLng: Double? = null): List<Place> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return try {
            photon(q, nearLat, nearLng)
        } catch (e: IOException) {
            null
        } catch (e: org.json.JSONException) {
            null
        }?.takeIf { it.isNotEmpty() } ?: try {
            nominatim(q)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun photon(q: String, lat: Double?, lng: Double?): List<Place> {
        val url = buildString {
            append(PHOTON).append("?q=").append(enc(q)).append("&limit=").append(LIMIT)
            if (lat != null && lng != null) {
                append("&lat=").append("%.3f".format(Locale.ROOT, lat))
                append("&lon=").append("%.3f".format(Locale.ROOT, lng))
            }
        }
        val features = JSONObject(get(url)).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val f = features.optJSONObject(i) ?: return@mapNotNull null
            val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val p = f.optJSONObject("properties") ?: JSONObject()
            val street = listOf(p.optString("street"), p.optString("housenumber")).filter { it.isNotEmpty() }.joinToString(" ")
            val name = p.optString("name").ifEmpty { street }.ifEmpty { return@mapNotNull null }
            val detail = listOf(
                street.takeIf { it != name },
                p.optString("district"),
                p.optString("city"),
                p.optString("county"),
                p.optString("state"),
                p.optString("country"),
            ).filterNotNull().filter { it.isNotEmpty() && it != name }.distinct().joinToString(", ")
            Place(name, detail, c.optDouble(1), c.optDouble(0), p.optJSONArray("extent")?.let(::photonBounds))
        }.distinctBy { it.name to it.detail }
    }

    /** Photon's extent is [minLon, maxLat, maxLon, minLat]. */
    private fun photonBounds(e: JSONArray): LatLngBounds? {
        if (e.length() < 4) return null
        val (west, north, east, south) = listOf(e.optDouble(0), e.optDouble(1), e.optDouble(2), e.optDouble(3))
        if (listOf(west, north, east, south).any { it.isNaN() } || north <= south || east <= west) return null
        return LatLngBounds.from(north, east, south, west)
    }

    private fun nominatim(q: String): List<Place> {
        val url = "$NOMINATIM?q=${enc(q)}&format=jsonv2&limit=$LIMIT&accept-language=${Locale.getDefault().language}"
        val results = JSONArray(get(url))
        return (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            val display = r.optString("display_name")
            val name = r.optString("name").ifEmpty { display.substringBefore(',') }
            val box = r.optJSONArray("boundingbox")?.let { b ->
                // [south, north, west, east] as strings.
                val v = (0 until 4).map { b.optString(it).toDoubleOrNull() ?: return@let null }
                if (v[1] > v[0] && v[3] > v[2]) LatLngBounds.from(v[1], v[3], v[0], v[2]) else null
            }
            Place(
                name = name,
                detail = display.removePrefix(name).removePrefix(", "),
                lat = r.optString("lat").toDoubleOrNull() ?: return@mapNotNull null,
                lng = r.optString("lon").toDoubleOrNull() ?: return@mapNotNull null,
                bounds = box,
            )
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
