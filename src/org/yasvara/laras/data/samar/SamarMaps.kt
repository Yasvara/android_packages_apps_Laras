/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.samar

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import org.maplibre.android.storage.FileSource
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** How much detail an offline area keeps. The vector tiles stop at zoom 14 and scale up from there. */
enum class Detail(val maxZoom: Double) { OVERVIEW(11.0), STREETS(14.0) }

data class OfflineArea(
    val id: Long,
    val name: String,
    val bounds: LatLngBounds,
    val detail: Detail,
    val created: Long,
    val updated: Long,
    val bytes: Long = 0,
    val done: Long = 0,
    val required: Long = 0,
    val complete: Boolean = false,
    val downloading: Boolean = false,
    val error: String? = null,
) {
    val progress: Float get() = if (required <= 0) 0f else (done.toFloat() / required).coerceIn(0f, 1f)

    fun covers(point: LatLng): Boolean = bounds.contains(point)
}

/**
 * MapLibre with OpenFreeMap's vector style. Tiles a map shows are kept in a bounded cache; areas
 * the owner saves are kept whole until deleted.
 */
object SamarMaps {
    private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"
    private const val AMBIENT_CACHE_BYTES = 128L * 1024 * 1024
    private const val MIN_ZOOM = 3.0
    /** Rough mean size of one compressed vector tile, for estimates before a download. */
    private const val TILE_BYTES = 38_000L
    const val MAX_TILES = 60_000L

    @Volatile private var ready = false

    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            MapLibre.getInstance(context.applicationContext)
            OfflineManager.getInstance(context.applicationContext)
                .setMaximumAmbientCacheSize(AMBIENT_CACHE_BYTES, Ignore)
            ready = true
        }
    }

    fun style(context: Context): String {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        return if (night) STYLE_DARK else STYLE_LIGHT
    }

    /** Tiles a download of [bounds] at [detail] needs. */
    fun tileCount(bounds: LatLngBounds, detail: Detail): Long {
        var total = 0L
        var z = MIN_ZOOM.toInt()
        while (z <= detail.maxZoom.toInt()) {
            val x0 = lonToTile(bounds.longitudeWest, z)
            val x1 = lonToTile(bounds.longitudeEast, z)
            val y0 = latToTile(bounds.latitudeNorth, z)
            val y1 = latToTile(bounds.latitudeSouth, z)
            total += (x1 - x0 + 1).coerceAtLeast(1) * (y1 - y0 + 1).coerceAtLeast(1)
            z++
        }
        return total
    }

    fun estimateBytes(bounds: LatLngBounds, detail: Detail): Long = tileCount(bounds, detail) * TILE_BYTES

    private fun lonToTile(lon: Double, z: Int): Long = floor((lon + 180) / 360 * (1 shl z)).toLong()

    private fun latToTile(lat: Double, z: Int): Long {
        val r = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        return floor((1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * (1 shl z)).toLong()
    }

    /** Size of the whole map database: saved areas and the cache together. */
    fun databaseBytes(context: Context): Long =
        File(FileSource.getResourcesCachePath(context), "mbgl-offline.db").length()

    private val regions = HashMap<Long, OfflineRegion>()
    private val _areas = MutableStateFlow<List<OfflineArea>>(emptyList())
    val areas: StateFlow<List<OfflineArea>> = _areas.asStateFlow()

    suspend fun refresh(context: Context) {
        init(context)
        val list = suspendCancellableCoroutine { cont ->
            OfflineManager.getInstance(context.applicationContext).listOfflineRegions(
                object : OfflineManager.ListOfflineRegionsCallback {
                    override fun onList(offlineRegions: Array<OfflineRegion>?) = cont.resume(offlineRegions.orEmpty().toList())
                    override fun onError(error: String) = cont.resume(emptyList())
                },
            )
        }
        val known = _areas.value.associateBy { it.id }
        val areas = list.mapNotNull { region ->
            regions[region.id] = region
            val area = toArea(region) ?: return@mapNotNull null
            val status = status(region)
            val previous = known[region.id]
            (status?.let { area.withStatus(it) } ?: area).copy(downloading = previous?.downloading == true)
        }
        _areas.value = areas.sortedByDescending { it.created }
    }

    fun download(context: Context, name: String, bounds: LatLngBounds, detail: Detail, onError: (String) -> Unit) {
        init(context)
        val definition = OfflineTilePyramidRegionDefinition(
            style(context),
            bounds,
            MIN_ZOOM,
            detail.maxZoom,
            context.resources.displayMetrics.density,
        )
        val now = System.currentTimeMillis()
        val meta = JSONObject()
            .put("name", name)
            .put("detail", detail.name)
            .put("created", now)
            .put("updated", now)
            .toString()
            .toByteArray()
        OfflineManager.getInstance(context.applicationContext).createOfflineRegion(
            definition,
            meta,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    regions[offlineRegion.id] = offlineRegion
                    val area = toArea(offlineRegion) ?: return
                    _areas.update { listOf(area.copy(downloading = true)) + it }
                    start(offlineRegion)
                }

                override fun onError(error: String) = onError(error)
            },
        )
    }

    /** Fetches every tile of the area again, keeping it usable while it does. */
    fun update(context: Context, id: Long) {
        val region = regions[id] ?: return
        region.invalidate(object : OfflineRegion.OfflineRegionInvalidateCallback {
            override fun onInvalidate() {
                val meta = runCatching { JSONObject(String(region.metadata)) }.getOrNull() ?: JSONObject()
                meta.put("updated", System.currentTimeMillis())
                region.updateMetadata(meta.toString().toByteArray(), object : OfflineRegion.OfflineRegionUpdateMetadataCallback {
                    override fun onUpdate(metadata: ByteArray) = Unit
                    override fun onError(error: String) = Unit
                })
                setArea(id) { it.copy(updated = System.currentTimeMillis(), downloading = true, complete = false, done = 0) }
                start(region)
            }

            override fun onError(error: String) = setArea(id) { it.copy(error = error) }
        })
    }

    fun pause(id: Long) {
        regions[id]?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        setArea(id) { it.copy(downloading = false) }
    }

    fun resume(id: Long) {
        regions[id]?.let(::start)
    }

    fun delete(id: Long, done: () -> Unit) {
        val region = regions[id] ?: return
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                regions.remove(id)
                _areas.update { list -> list.filterNot { it.id == id } }
                done()
            }

            override fun onError(error: String) = setArea(id) { it.copy(error = error) }
        })
    }

    fun clearCache(context: Context, done: () -> Unit) {
        init(context)
        OfflineManager.getInstance(context.applicationContext).clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() = done()
            override fun onError(message: String) = done()
        })
    }

    private fun start(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                setArea(region.id) { it.withStatus(status).copy(downloading = !status.isComplete, error = null) }
                if (status.isComplete) region.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }

            override fun onError(error: OfflineRegionError) {
                // Connection errors retry by themselves; only report what will not.
                if (error.reason != OfflineRegionError.REASON_CONNECTION) {
                    setArea(region.id) { it.copy(error = error.message) }
                }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                setArea(region.id) { it.copy(downloading = false, error = "limit $limit") }
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        setArea(region.id) { it.copy(downloading = true) }
    }

    private fun setArea(id: Long, change: (OfflineArea) -> OfflineArea) {
        _areas.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    private suspend fun status(region: OfflineRegion): OfflineRegionStatus? = suspendCancellableCoroutine { cont ->
        region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) = cont.resume(status)
            override fun onError(error: String?) = cont.resume(null)
        })
    }

    private fun OfflineArea.withStatus(s: OfflineRegionStatus) = copy(
        bytes = s.completedResourceSize,
        done = s.completedResourceCount,
        required = s.requiredResourceCount,
        complete = s.isComplete,
    )

    private fun toArea(region: OfflineRegion): OfflineArea? {
        val definition = region.definition as? OfflineTilePyramidRegionDefinition ?: return null
        val bounds = definition.bounds ?: return null
        val meta = runCatching { JSONObject(String(region.metadata)) }.getOrNull() ?: JSONObject()
        return OfflineArea(
            id = region.id,
            name = meta.optString("name").ifEmpty { SamarRepository.format(bounds.center.latitude, bounds.center.longitude) },
            bounds = bounds,
            detail = runCatching { Detail.valueOf(meta.optString("detail")) }.getOrDefault(Detail.STREETS),
            created = meta.optLong("created"),
            updated = meta.optLong("updated"),
        )
    }

    private object Ignore : OfflineManager.FileSourceCallback {
        override fun onSuccess() = Unit
        override fun onError(message: String) = Unit
    }
}
