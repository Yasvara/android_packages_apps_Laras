/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.samar

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.yasvara.laras.data.samar.AppMode
import org.yasvara.laras.data.samar.Detail
import org.yasvara.laras.data.samar.GLOBAL
import org.yasvara.laras.data.samar.Layer
import org.yasvara.laras.data.samar.Place
import org.yasvara.laras.data.samar.SamarApp
import org.yasvara.laras.data.samar.SamarMaps
import org.yasvara.laras.data.samar.SamarPlace
import org.yasvara.laras.data.samar.SamarPosition
import org.yasvara.laras.data.samar.SamarRepository
import org.yasvara.laras.data.samar.SamarSearch
import org.yasvara.laras.data.samar.Speed
import org.yasvara.laras.samar.SamarPanel
import org.yasvara.laras.samar.SamarPrefs
import org.yasvara.laras.samar.SamarTile

sealed interface SamarNotice {
    data object NotUnderstood : SamarNotice
    data class Moved(val metres: Double, val wasOff: Boolean) : SamarNotice
    data object NeedsPlace : SamarNotice
    data class Saved(val name: String) : SamarNotice
    data class DownloadFailed(val reason: String) : SamarNotice
    data object CacheCleared : SamarNotice
}

/** Where the map should go: a point, or a place's whole extent. */
data class MapFocus(val point: LatLng, val bounds: LatLngBounds? = null)

data class SamarUi(
    val available: Boolean = true,
    val loading: Boolean = true,
    val on: Boolean = false,
    val position: SamarPosition? = null,
    /** A point the owner picked on the map or typed, not yet applied. */
    val pick: LatLng? = null,
    val places: List<SamarPlace> = emptyList(),
    val layers: Int = 0,
    val apps: List<SamarApp> = emptyList(),
    /** Where each app with its own location stands. */
    val appPlaces: Map<String, SamarPosition> = emptyMap(),
    val panelFromTile: Boolean = true,
    val databaseBytes: Long = 0,
    /** Place search results for what is typed in the find field. */
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
) {
    /** The saved place the position sits on, if any. */
    val placeName: String?
        get() {
            val p = position ?: return null
            return places.firstOrNull { SamarRepository.distance(it.lat, it.lng, p.lat, p.lng) < 40 }?.name
        }
}

class SamarViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SamarRepository(app)
    private val _state = MutableStateFlow(SamarUi())
    val state: StateFlow<SamarUi> = _state.asStateFlow()
    private val _notices = MutableSharedFlow<SamarNotice>(extraBufferCapacity = 4)
    val notices: SharedFlow<SamarNotice> = _notices.asSharedFlow()
    /** Points the map should fly to. */
    private val _focus = MutableSharedFlow<MapFocus>(extraBufferCapacity = 2)
    val focus: SharedFlow<MapFocus> = _focus.asSharedFlow()
    private var search: Job? = null
    private val cache = object : LinkedHashMap<String, List<Place>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>) = size > 64
    }
    val areas = SamarMaps.areas
    private var polling: Job? = null

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            val ui = withContext(Dispatchers.IO) {
                val apps = repository.apps()
                _state.value.copy(
                    available = repository.available,
                    loading = false,
                    on = repository.isOn(),
                    position = repository.position(),
                    places = repository.places(),
                    layers = repository.layers().value,
                    apps = apps,
                    appPlaces = ownPlaces(apps),
                    panelFromTile = SamarPrefs.panelFromTile(getApplication()),
                    databaseBytes = SamarMaps.databaseBytes(getApplication()),
                )
            }
            _state.value = ui
            SamarMaps.refresh(getApplication())
        }
    }

    /** Follows the simulated position while the screen is visible; the joystick may be moving it. */
    fun watch(active: Boolean) {
        polling?.cancel()
        if (!active) return
        polling = viewModelScope.launch {
            while (isActive) {
                val p = withContext(Dispatchers.IO) { repository.position() }
                val on = withContext(Dispatchers.IO) { repository.isOn() }
                _state.update { it.copy(position = p, on = on) }
                delay(1000)
            }
        }
    }

    fun setOn(on: Boolean) = io {
        if (on && _state.value.position?.hasPlace != true) {
            _notices.tryEmit(SamarNotice.NeedsPlace)
            return@io
        }
        repository.setOn(on)
        _state.update { it.copy(on = on) }
        refreshTile()
    }

    fun pick(point: LatLng?) = _state.update { it.copy(pick = point) }

    /**
     * Search as you type: coordinates, links and plus codes resolve locally; anything else is a
     * place name. [near] is the centre of the map on screen, to rank nearby places first.
     */
    fun query(text: String, near: LatLng?) {
        search?.cancel()
        val q = text.trim()
        if (q.length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        search = viewModelScope.launch(Dispatchers.IO) {
            delay(250)
            repository.parse(q, _state.value.position)?.let { (lat, lng) ->
                _state.update {
                    it.copy(results = listOf(Place(SamarRepository.format(lat, lng), "", lat, lng)), searching = false)
                }
                return@launch
            }
            val key = q.lowercase()
            val found = synchronized(cache) { cache[key] } ?: run {
                _state.update { it.copy(searching = true) }
                SamarSearch.search(q, near?.latitude, near?.longitude).also { if (it.isNotEmpty()) synchronized(cache) { cache[key] = it } }
            }
            // The request does not stop when a newer keystroke cancels this one; drop its answer.
            ensureActive()
            _state.update { it.copy(results = found, searching = false) }
        }
    }

    /** The search key: takes the first result, or resolves a short map link. */
    fun find(text: String, near: LatLng?) = io {
        search?.cancel()
        val first = _state.value.results.firstOrNull()
            ?: repository.parseOrResolve(text.trim(), _state.value.position)?.let { Place("", "", it.first, it.second) }
            ?: SamarSearch.search(text, near?.latitude, near?.longitude).firstOrNull()
        if (first == null) {
            _state.update { it.copy(searching = false) }
            _notices.tryEmit(SamarNotice.NotUnderstood)
        } else {
            choose(first)
        }
    }

    fun choose(place: Place) {
        search?.cancel()
        val point = LatLng(place.lat, place.lng)
        _state.update { it.copy(pick = point, results = emptyList(), searching = false) }
        _focus.tryEmit(MapFocus(point, place.bounds))
    }

    fun clearResults() {
        search?.cancel()
        _state.update { it.copy(results = emptyList(), searching = false) }
    }

    /** Moves the global location to [point]. */
    fun moveTo(point: LatLng) = io {
        val before = _state.value.position
        if (repository.setPlace(GLOBAL, point.latitude, point.longitude).isNaN()) return@io
        val metres = before?.takeIf { it.hasPlace }
            ?.let { SamarRepository.distance(it.lat, it.lng, point.latitude, point.longitude) } ?: 0.0
        _state.update { it.copy(pick = null, position = repository.position()) }
        _notices.tryEmit(SamarNotice.Moved(metres, wasOff = !_state.value.on))
        refreshTile()
    }

    fun focusOn(lat: Double, lng: Double) {
        _focus.tryEmit(MapFocus(LatLng(lat, lng)))
    }

    fun setSpeed(speed: Speed) = io {
        repository.setSpeed(GLOBAL, speed)
        _state.update { s -> s.copy(position = s.position?.copy(speedPreset = speed)) }
    }

    fun setAltitude(metres: Double) = io {
        repository.setAltitude(GLOBAL, metres)
        _state.update { s -> s.copy(position = s.position?.copy(altitude = metres)) }
    }

    fun savePlace(name: String, point: LatLng) = io {
        if (repository.savePlace(name, point.latitude, point.longitude)) {
            _state.update { it.copy(places = repository.places()) }
            _notices.tryEmit(SamarNotice.Saved(name.trim()))
        }
    }

    fun removePlace(place: SamarPlace) = io {
        repository.removePlace(place.index)
        _state.update { it.copy(places = repository.places()) }
    }

    fun setLayer(layer: Layer, on: Boolean) = io {
        val value = if (on) _state.value.layers or layer.bit else _state.value.layers and layer.bit.inv()
        repository.setLayers(GLOBAL, 0, value)
        _state.update { it.copy(layers = value) }
    }

    fun setAppMode(pkg: String, mode: AppMode) = io {
        repository.setMode(pkg, mode)
        // A new own profile starts where the global one is; give it a place to stand.
        if (mode == AppMode.OWN && repository.position(pkg)?.hasPlace != true) {
            _state.value.position?.takeIf { it.hasPlace }?.let { repository.setPlace(pkg, it.lat, it.lng) }
        }
        reloadApps()
    }

    fun setAppPlace(pkg: String, point: LatLng) = io {
        repository.setPlace(pkg, point.latitude, point.longitude)
        reloadApps()
    }

    /** [on] null: the app follows the global setting for this layer. */
    fun setAppLayer(app: SamarApp, layer: Layer, on: Boolean?) = io {
        val current = app.layers
        val mask = if (on == null) current.mask and layer.bit.inv() else current.mask or layer.bit
        val value = when (on) {
            true -> current.value or layer.bit
            else -> current.value and layer.bit.inv()
        }
        repository.setLayers(app.packageName, mask, value)
        reloadApps()
    }

    /** Coordinates, a map link, a plus code or a place name, for one app's own location. */
    fun findForApp(pkg: String, text: String) = io {
        val point = repository.parseOrResolve(text.trim(), _state.value.position)
            ?: SamarSearch.search(text).firstOrNull()?.let { it.lat to it.lng }
        if (point == null) {
            _notices.tryEmit(SamarNotice.NotUnderstood)
        } else {
            repository.setPlace(pkg, point.first, point.second)
            reloadApps()
        }
    }

    fun setPanelFromTile(on: Boolean) {
        SamarPrefs.setPanelFromTile(getApplication(), on)
        _state.update { it.copy(panelFromTile = on) }
    }

    fun showPanel() = SamarPanel.show(getApplication())

    fun download(name: String, bounds: LatLngBounds, detail: Detail) {
        SamarMaps.download(getApplication(), name, bounds, detail) { _notices.tryEmit(SamarNotice.DownloadFailed(it)) }
    }

    fun updateArea(id: Long) = SamarMaps.update(getApplication(), id)

    fun pauseArea(id: Long) = SamarMaps.pause(id)

    fun resumeArea(id: Long) = SamarMaps.resume(id)

    fun deleteArea(id: Long) = SamarMaps.delete(id) { refreshDatabaseSize() }

    fun clearCache() = SamarMaps.clearCache(getApplication()) {
        refreshDatabaseSize()
        _notices.tryEmit(SamarNotice.CacheCleared)
    }

    fun refreshDatabaseSize() = io {
        _state.update { it.copy(databaseBytes = SamarMaps.databaseBytes(getApplication())) }
    }

    private suspend fun reloadApps() {
        val apps = repository.apps()
        _state.update { it.copy(apps = apps, appPlaces = ownPlaces(apps)) }
    }

    private fun ownPlaces(apps: List<SamarApp>): Map<String, SamarPosition> = apps
        .filter { it.mode == AppMode.OWN }
        .mapNotNull { app -> repository.position(app.packageName)?.let { app.packageName to it } }
        .toMap()

    private fun refreshTile() {
        val app = getApplication<Application>()
        TileService.requestListeningState(app, ComponentName(app, SamarTile::class.java))
    }

    private fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) { block() }
    }
}
