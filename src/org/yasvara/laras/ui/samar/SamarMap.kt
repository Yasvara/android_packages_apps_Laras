/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.samar

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.yasvara.laras.data.samar.SamarMaps

private const val SOURCE_POSITION = "samar-position"
private const val SOURCE_PICK = "samar-pick"

/** Drives a [SamarMap] from outside: camera moves the map would not make by itself. */
class SamarMapState {
    internal var map: MapLibreMap? = null

    /** Camera centre and zoom, or null before the map is ready. */
    val camera: CameraPosition? get() = map?.cameraPosition

    val visibleBounds get() = map?.projection?.visibleRegion?.latLngBounds

    fun moveTo(lat: Double, lng: Double, zoom: Double? = null, animate: Boolean = true) {
        val m = map ?: return
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom ?: m.cameraPosition.zoom.coerceAtLeast(13.0))
        if (animate) m.animateCamera(update, 600) else m.moveCamera(update)
    }
}

/**
 * MapLibre map with the simulated position as a dot and an optional picked point. Rendered to a
 * TextureView so it clips to rounded shapes and works inside an overlay window.
 */
@Composable
fun SamarMap(
    modifier: Modifier = Modifier,
    state: SamarMapState = remember { SamarMapState() },
    position: LatLng?,
    pick: LatLng? = null,
    initialZoom: Double = 15.0,
    onTap: ((LatLng) -> Unit)? = null,
    onLongPress: ((LatLng) -> Unit)? = null,
    onCameraIdle: (() -> Unit)? = null,
    onUserMove: (() -> Unit)? = null,
    /** A finger is down on the map (true) or lifted (false). */
    onTouch: ((Boolean) -> Unit)? = null,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
) {
    val context = LocalContext.current
    val dot = MaterialTheme.colorScheme.primary
    val pickColor = MaterialTheme.colorScheme.tertiary
    val ring = MaterialTheme.colorScheme.surface
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val idle by rememberUpdatedState(onCameraIdle)
    val userMove by rememberUpdatedState(onUserMove)
    var style by remember { mutableStateOf<Style?>(null) }
    val start = remember { position }

    val view = remember {
        SamarMaps.init(context)
        createMapView(context, start, initialZoom).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                state.map = map
                map.uiSettings.apply {
                    isRotateGesturesEnabled = false
                    isTiltGesturesEnabled = false
                    isCompassEnabled = false
                    isLogoEnabled = false
                    attributionGravity = Gravity.BOTTOM or Gravity.END
                }
                map.addOnMapClickListener { tap?.invoke(it); tap != null }
                map.addOnMapLongClickListener { longPress?.invoke(it); longPress != null }
                map.addOnCameraIdleListener { idle?.invoke() }
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) userMove?.invoke()
                }
                map.setStyle(SamarMaps.style(context)) { loaded ->
                    loaded.addSource(GeoJsonSource(SOURCE_PICK, FeatureCollection.fromFeatures(emptyList())))
                    loaded.addSource(GeoJsonSource(SOURCE_POSITION, FeatureCollection.fromFeatures(emptyList())))
                    loaded.addLayer(
                        CircleLayer("samar-pick-dot", SOURCE_PICK).withProperties(
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleColor(pickColor.toArgb()),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor(ring.toArgb()),
                        ),
                    )
                    loaded.addLayer(
                        CircleLayer("samar-halo", SOURCE_POSITION).withProperties(
                            PropertyFactory.circleRadius(22f),
                            PropertyFactory.circleColor(dot.toArgb()),
                            PropertyFactory.circleOpacity(0.18f),
                        ),
                    )
                    loaded.addLayer(
                        CircleLayer("samar-dot", SOURCE_POSITION).withProperties(
                            PropertyFactory.circleRadius(8f),
                            PropertyFactory.circleColor(dot.toArgb()),
                            PropertyFactory.circleStrokeWidth(3f),
                            PropertyFactory.circleStrokeColor(Color.White.toArgb()),
                        ),
                    )
                    style = loaded
                }
            }
        }
    }

    DisposableEffect(lifecycle, view) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.map = null
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop()
            view.onDestroy()
        }
    }

    // The position often arrives after the map is made; centre on it once when it does.
    var centred by remember { mutableStateOf(start != null) }
    LaunchedEffect(style, position) {
        if (!centred && style != null && position != null) {
            state.moveTo(position.latitude, position.longitude, zoom = initialZoom, animate = false)
            centred = true
        }
        val source = style?.getSourceAs<GeoJsonSource>(SOURCE_POSITION) ?: return@LaunchedEffect
        if (position == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(Point.fromLngLat(position.longitude, position.latitude))
        }
    }
    LaunchedEffect(style, pick) {
        val source = style?.getSourceAs<GeoJsonSource>(SOURCE_PICK) ?: return@LaunchedEffect
        if (pick == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        } else {
            source.setGeoJson(Point.fromLngLat(pick.longitude, pick.latitude))
        }
    }

    val touch by rememberUpdatedState(onTouch)
    // Watches without consuming: the map view still gets every event.
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touch?.invoke(true)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                touch?.invoke(false)
            }
        },
    ) {
        AndroidView(factory = { view }, modifier = Modifier.matchParentSize())
    }
}

/** Never takes focus: a focused child makes a scrolling parent bring it into view on every tap. */
private class TouchMapView(context: Context, options: MapLibreMapOptions) : MapView(context, options) {
    init {
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    }
}

private fun createMapView(context: Context, start: LatLng?, zoom: Double): MapView {
    val options = MapLibreMapOptions.createFromAttributes(context)
        .textureMode(true)
        .camera(
            CameraPosition.Builder()
                .target(start ?: LatLng(-2.5, 118.0))
                .zoom(if (start == null) 3.5 else zoom)
                .build(),
        )
    return TouchMapView(context, options)
}
