/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.samar

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.yasvara.laras.R
import org.yasvara.laras.data.samar.AppMode
import org.yasvara.laras.data.samar.Detail
import org.yasvara.laras.data.samar.Layer
import org.yasvara.laras.data.samar.OfflineArea
import org.yasvara.laras.data.samar.SamarApp
import org.yasvara.laras.data.samar.SamarMaps
import org.yasvara.laras.data.samar.SamarPlace
import org.yasvara.laras.data.samar.SamarRepository
import org.yasvara.laras.data.samar.Speed
import org.yasvara.laras.samar.icon
import org.yasvara.laras.samar.label
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasModalSheet
import org.yasvara.laras.ui.components.LarasNavRow
import org.yasvara.laras.ui.components.LarasRadioRow
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasStatBar
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.LarasTabBar
import org.yasvara.laras.ui.components.StatFigure
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

enum class SamarTab(val label: Int) {
    LOCATION(R.string.samar_tab_location),
    APPS(R.string.samar_tab_apps),
    PRIVACY(R.string.samar_tab_privacy),
    MAPS(R.string.samar_tab_maps),
}

private enum class AppFilter(val label: Int, val icon: ImageVector, val test: (SamarApp) -> Boolean) {
    ALL(R.string.lelap_filter_all, Icons.Rounded.Apps, { true }),
    CUSTOM(R.string.samar_filter_custom, Icons.Rounded.Tune, { it.custom }),
    USER(R.string.lelap_filter_user, Icons.Rounded.Person, { !it.system }),
    SYSTEM(R.string.lelap_filter_system, Icons.Rounded.Android, { it.system }),
}

private val Layer.title: Int
    get() = when (this) {
        Layer.RADIO -> R.string.samar_layer_radio
        Layer.BLE -> R.string.samar_layer_ble
        Layer.MEDIA -> R.string.samar_layer_media
        Layer.LAN -> R.string.samar_layer_lan
    }

private val Layer.summary: Int
    get() = when (this) {
        Layer.RADIO -> R.string.samar_layer_radio_summary
        Layer.BLE -> R.string.samar_layer_ble_summary
        Layer.MEDIA -> R.string.samar_layer_media_summary
        Layer.LAN -> R.string.samar_layer_lan_summary
    }

private val AppMode.title: Int
    get() = when (this) {
        AppMode.FOLLOW -> R.string.samar_mode_follow
        AppMode.REAL -> R.string.samar_mode_real
        AppMode.OWN -> R.string.samar_mode_own
    }

private val AppMode.summary: Int
    get() = when (this) {
        AppMode.FOLLOW -> R.string.samar_mode_follow_summary
        AppMode.REAL -> R.string.samar_mode_real_summary
        AppMode.OWN -> R.string.samar_mode_own_summary
    }

/** Laras › Samar: the location apps see, per-app choices, location privacy and offline maps. */
@Composable
fun SamarScreen(
    onBack: () -> Unit,
    initialTab: SamarTab = SamarTab.LOCATION,
    viewModel: SamarViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val mapState = remember { SamarMapState() }
    var tab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var openApp by rememberSaveable { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<LatLng?>(null) }
    var editAltitude by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf<LatLngBounds?>(null) }
    var deleting by remember { mutableStateOf<OfflineArea?>(null) }
    var onMap by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        viewModel.watch(true)
        onPauseOrDispose { viewModel.watch(false) }
    }
    LaunchedEffect(Unit) {
        viewModel.focus.collect { focus ->
            val map = mapState.map
            if (focus.bounds != null && map != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(focus.bounds, 64), 700)
            } else {
                mapState.moveTo(focus.point.latitude, focus.point.longitude, zoom = 15.0)
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.notices.collect { notice ->
            when (notice) {
                is SamarNotice.Moved -> {
                    val text = if (notice.metres > 0) {
                        context.getString(R.string.samar_notice_moved, SamarRepository.formatDistance(notice.metres))
                    } else {
                        context.getString(R.string.samar_notice_set)
                    }
                    if (notice.wasOff) {
                        val result = snackbar.showSnackbar(
                            context.getString(R.string.samar_notice_set_off),
                            actionLabel = context.getString(R.string.samar_turn_on),
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.setOn(true)
                    } else {
                        snackbar.showSnackbar(text)
                    }
                }
                SamarNotice.NotUnderstood -> snackbar.showSnackbar(context.getString(R.string.samar_notice_not_understood))
                SamarNotice.NeedsPlace -> snackbar.showSnackbar(context.getString(R.string.samar_notice_needs_place))
                is SamarNotice.Saved -> snackbar.showSnackbar(context.getString(R.string.samar_notice_saved, notice.name))
                is SamarNotice.DownloadFailed ->
                    snackbar.showSnackbar(context.getString(R.string.samar_notice_download_failed, notice.reason))
                SamarNotice.CacheCleared -> snackbar.showSnackbar(context.getString(R.string.samar_notice_cache_cleared))
            }
        }
    }

    val shownApps = remember(ui.apps, query, filter) {
        ui.apps.filter {
            filter.test(it) && (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
        }
    }

    Page(
        title = stringResource(R.string.samar_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        userScrollEnabled = !onMap,
        topContent = {
            LarasTabBar(
                tabs = SamarTab.entries,
                selected = tab,
                label = { stringResource(it.label) },
                onSelect = { tab = it },
            )
        },
    ) {
        if (!ui.available) {
            item(key = "unavailable") { LarasEmptyState(title = stringResource(R.string.samar_unavailable)) }
            return@Page
        }
        when (tab) {
            SamarTab.LOCATION -> {
                item(key = "find") {
                    FindField(
                        searching = ui.searching,
                        onQuery = { viewModel.query(it, mapState.camera?.target) },
                        onFind = { viewModel.find(it, mapState.camera?.target) },
                        onClear = viewModel::clearResults,
                    )
                }
                ui.results.forEachIndexed { i, place ->
                    item(key = "result_${i}_${place.name}_${place.lat}") {
                        LarasNavRow(
                            title = place.name,
                            summary = place.detail.ifEmpty { null },
                            leading = { Icon(Icons.Rounded.Place, null) },
                            shape = groupedShape(i, ui.results.size),
                            showChevron = false,
                            onClick = { viewModel.choose(place) },
                        )
                    }
                }
                item(key = "map") {
                    Spacer(Modifier.height(8.dp))
                    LocationMap(
                        ui = ui,
                        areas = areas,
                        mapState = mapState,
                        onPick = viewModel::pick,
                        onMove = viewModel::moveTo,
                        onSave = { naming = it },
                        onSaveArea = { downloading = it },
                        onTouch = { onMap = it },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item(key = "master") {
                    val p = ui.position
                    LarasSwitchRow(
                        title = stringResource(R.string.samar_master),
                        summary = when {
                            p == null || !p.hasPlace -> stringResource(R.string.samar_master_no_place)
                            ui.on -> stringResource(
                                R.string.samar_master_on,
                                ui.placeName ?: SamarRepository.format(p.lat, p.lng),
                            )
                            else -> stringResource(R.string.samar_master_off)
                        },
                        checked = ui.on,
                        onCheckedChange = viewModel::setOn,
                    )
                }
                item(key = "move_hdr") { LarasSectionHeader(stringResource(R.string.samar_movement)) }
                item(key = "speed") {
                    Surface(shape = groupedShape(0, 4), color = LarasSurfaces.card) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                            Text(stringResource(R.string.samar_speed), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.samar_speed_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            LarasFilterRow(
                                options = Speed.entries,
                                selected = ui.position?.speedPreset ?: Speed.WALK,
                                label = { stringResource(it.label) },
                                leadingIcon = { it.icon },
                                onSelect = viewModel::setSpeed,
                            )
                        }
                    }
                }
                item(key = "panel") {
                    LarasNavRow(
                        title = stringResource(R.string.samar_panel),
                        summary = stringResource(R.string.samar_panel_summary),
                        leading = { Icon(Icons.Rounded.Gamepad, null) },
                        shape = groupedShape(1, 4),
                        onClick = viewModel::showPanel,
                    )
                }
                item(key = "panel_tile") {
                    LarasSwitchRow(
                        title = stringResource(R.string.samar_panel_tile),
                        summary = stringResource(R.string.samar_panel_tile_summary),
                        checked = ui.panelFromTile,
                        shape = groupedShape(2, 4),
                        onCheckedChange = viewModel::setPanelFromTile,
                    )
                }
                item(key = "altitude") {
                    LarasNavRow(
                        title = stringResource(R.string.samar_altitude),
                        summary = stringResource(R.string.samar_altitude_value, ui.position?.altitude ?: 0.0),
                        shape = groupedShape(3, 4),
                        onClick = { editAltitude = true },
                    )
                }
                item(key = "places_hdr") {
                    val p = ui.position
                    LarasSectionHeader(
                        title = stringResource(R.string.samar_places),
                        summary = if (ui.places.isEmpty()) stringResource(R.string.samar_places_empty) else null,
                        actionLabel = if (p?.hasPlace == true) stringResource(R.string.samar_places_save_current) else null,
                        onAction = { p?.let { naming = LatLng(it.lat, it.lng) } },
                    )
                }
                ui.places.forEachIndexed { i, place ->
                    item(key = "place_${place.index}_${place.name}") {
                        PlaceRow(
                            place = place,
                            from = ui.position,
                            shape = groupedShape(i, ui.places.size),
                            onClick = {
                                viewModel.pick(LatLng(place.lat, place.lng))
                                viewModel.focusOn(place.lat, place.lng)
                            },
                            onDelete = { viewModel.removePlace(place) },
                        )
                    }
                }
                item(key = "note") { Footnote(stringResource(R.string.samar_note_emergency)) }
            }

            SamarTab.APPS -> {
                item(key = "apps_note") { Footnote(stringResource(R.string.samar_apps_note)) }
                item(key = "apps_search") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LarasSearchBar(query, { query = it }, stringResource(R.string.samar_apps_search))
                        LarasFilterRow(
                            options = AppFilter.entries,
                            selected = filter,
                            label = { stringResource(it.label) },
                            leadingIcon = { it.icon },
                            count = { f -> ui.apps.count(f.test) },
                            onSelect = { filter = it },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
                if (shownApps.isEmpty() && !ui.loading) {
                    item(key = "apps_empty") { LarasEmptyState(title = stringResource(R.string.samar_apps_empty)) }
                }
                shownApps.forEachIndexed { i, app ->
                    item(key = "app_${app.packageName}") {
                        LarasNavRow(
                            title = app.label,
                            summary = appSummary(app),
                            summaryColor = if (app.custom) MaterialTheme.colorScheme.primary else null,
                            leading = { AppIcon(app.packageName) },
                            shape = groupedShape(i, shownApps.size),
                            onClick = { openApp = app.packageName },
                        )
                    }
                }
            }

            SamarTab.PRIVACY -> {
                item(key = "privacy_note") { Footnote(stringResource(R.string.samar_privacy_note)) }
                Layer.entries.forEachIndexed { i, layer ->
                    item(key = "layer_${layer.name}") {
                        LarasSwitchRow(
                            title = stringResource(layer.title),
                            summary = stringResource(layer.summary),
                            checked = ui.layers and layer.bit != 0,
                            shape = groupedShape(i, Layer.entries.size),
                            onCheckedChange = { viewModel.setLayer(layer, it) },
                        )
                    }
                }
                item(key = "privacy_apps") {
                    Spacer(Modifier.height(8.dp))
                    LarasNavRow(
                        title = stringResource(R.string.samar_privacy_per_app),
                        summary = stringResource(R.string.samar_privacy_per_app_summary),
                        onClick = { tab = SamarTab.APPS },
                    )
                }
            }

            SamarTab.MAPS -> {
                item(key = "maps_stats") {
                    LarasStatBar(
                        listOf(
                            StatFigure(areas.size.toString(), stringResource(R.string.samar_maps_areas)),
                            StatFigure(
                                Formatter.formatShortFileSize(context, areas.sumOf { it.bytes }),
                                stringResource(R.string.samar_maps_saved),
                            ),
                            StatFigure(
                                Formatter.formatShortFileSize(context, ui.databaseBytes),
                                stringResource(R.string.samar_maps_storage),
                            ),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item(key = "maps_map") {
                    Box(Modifier.fillMaxWidth().height(260.dp).clip(LarasShapes.Hero)) {
                        SamarMap(
                            modifier = Modifier.matchParentSize(),
                            state = mapState,
                            position = ui.position?.takeIf { it.hasPlace }?.let { LatLng(it.lat, it.lng) },
                            initialZoom = 11.0,
                            onTouch = { onMap = it },
                        )
                        Button(
                            onClick = { mapState.visibleBounds?.let { downloading = it } },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                        ) {
                            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.samar_maps_download_here))
                        }
                    }
                }
                item(key = "maps_hdr") {
                    LarasSectionHeader(
                        title = stringResource(R.string.samar_maps_saved_areas),
                        summary = if (areas.isEmpty()) stringResource(R.string.samar_maps_none) else null,
                    )
                }
                areas.forEachIndexed { i, area ->
                    item(key = "area_${area.id}") {
                        AreaRow(
                            area = area,
                            shape = groupedShape(i, areas.size),
                            onShow = { mapState.map?.animateCamera(CameraUpdateFactory.newLatLngBounds(area.bounds, 48)) },
                            onUpdate = { viewModel.updateArea(area.id) },
                            onPause = { viewModel.pauseArea(area.id) },
                            onResume = { viewModel.resumeArea(area.id) },
                            onDelete = { deleting = area },
                        )
                    }
                }
                item(key = "cache") {
                    Spacer(Modifier.height(8.dp))
                    LarasNavRow(
                        title = stringResource(R.string.samar_maps_cache),
                        summary = stringResource(R.string.samar_maps_cache_summary),
                        trailing = {
                            TextButton(onClick = viewModel::clearCache) { Text(stringResource(R.string.samar_maps_cache_clear)) }
                        },
                        onClick = viewModel::clearCache,
                    )
                }
                item(key = "attribution") { Footnote(stringResource(R.string.samar_maps_attribution)) }
            }
        }
    }

    openApp?.let { pkg ->
        ui.apps.firstOrNull { it.packageName == pkg }?.let { app ->
            AppSheet(app = app, ui = ui, viewModel = viewModel, onDismiss = { openApp = null })
        }
    }
    naming?.let { point ->
        NameDialog(
            initial = "",
            onDismiss = { naming = null },
            onSave = {
                viewModel.savePlace(it, point)
                naming = null
            },
        )
    }
    if (editAltitude) {
        AltitudeDialog(
            initial = ui.position?.altitude ?: 0.0,
            onDismiss = { editAltitude = false },
            onSave = {
                viewModel.setAltitude(it)
                editAltitude = false
            },
        )
    }
    downloading?.let { bounds ->
        DownloadSheet(
            bounds = bounds,
            onDismiss = { downloading = null },
            onDownload = { name, detail ->
                viewModel.download(name, bounds, detail)
                downloading = null
                tab = SamarTab.MAPS
            },
        )
    }
    deleting?.let { area ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.samar_maps_delete_title, area.name)) },
            text = {
                Text(
                    stringResource(
                        R.string.samar_maps_delete_body,
                        Formatter.formatShortFileSize(context, area.bytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteArea(area.id)
                    deleting = null
                }) { Text(stringResource(R.string.samar_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun FindField(searching: Boolean, onQuery: (String) -> Unit, onFind: (String) -> Unit, onClear: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onQuery(it)
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = LarasShapes.Pill,
        placeholder = { Text(stringResource(R.string.samar_find_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                text.isNotEmpty() -> IconButton(onClick = {
                    text = ""
                    onClear()
                }) { Icon(Icons.Rounded.Close, stringResource(R.string.samar_clear)) }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            if (text.isNotBlank()) {
                onFind(text)
                focus.clearFocus()
            }
        }),
    )
}

@Composable
private fun LocationMap(
    ui: SamarUi,
    areas: List<OfflineArea>,
    mapState: SamarMapState,
    onPick: (LatLng?) -> Unit,
    onMove: (LatLng) -> Unit,
    onSave: (LatLng) -> Unit,
    onSaveArea: (LatLngBounds) -> Unit,
    onTouch: (Boolean) -> Unit,
) {
    val position = ui.position?.takeIf { it.hasPlace }?.let { LatLng(it.lat, it.lng) }
    var offlineHint by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(340.dp).clip(LarasShapes.Hero)) {
        SamarMap(
            modifier = Modifier.matchParentSize(),
            state = mapState,
            position = position,
            pick = ui.pick,
            onTap = { onPick(it) },
            onLongPress = { onPick(it) },
            onTouch = onTouch,
            onCameraIdle = {
                // Offer to keep an area offline when the owner looks at one that is not.
                val camera = mapState.camera
                offlineHint = camera != null && camera.zoom >= 11 && camera.target != null &&
                    areas.none { it.covers(camera.target!!) }
            },
        )
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (position != null) {
                SmallFloatingActionButton(onClick = { mapState.moveTo(position.latitude, position.longitude) }) {
                    Icon(Icons.Rounded.MyLocation, stringResource(R.string.samar_panel_recenter))
                }
            }
        }
        AnimatedVisibility(
            offlineHint && ui.pick == null,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            FilledTonalButton(onClick = { mapState.visibleBounds?.let(onSaveArea) }) {
                Icon(Icons.Rounded.Download, null, Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.samar_maps_save_offline), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (position == null && ui.pick == null) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                shape = LarasShapes.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    stringResource(R.string.samar_map_hint),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        AnimatedVisibility(
            ui.pick != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val pick = ui.pick ?: return@AnimatedVisibility
            Surface(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                shape = LarasShapes.Card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
            ) {
                Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                SamarRepository.format(pick.latitude, pick.longitude),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            position?.let {
                                Text(
                                    stringResource(
                                        R.string.samar_link_away,
                                        SamarRepository.formatDistance(
                                            SamarRepository.distance(it.latitude, it.longitude, pick.latitude, pick.longitude),
                                        ),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { onPick(null) }) { Icon(Icons.Rounded.Close, stringResource(R.string.samar_clear)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onMove(pick) }) { Text(stringResource(R.string.samar_move_here)) }
                        TextButton(onClick = { onSave(pick) }) { Text(stringResource(R.string.samar_save_place)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: SamarPlace,
    from: org.yasvara.laras.data.samar.SamarPosition?,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val away = from?.takeIf { it.hasPlace }?.let { SamarRepository.distance(it.lat, it.lng, place.lat, place.lng) }
    val here = away != null && away < 40
    LarasNavRow(
        title = place.name,
        summary = when {
            here -> stringResource(R.string.samar_place_here)
            away != null -> stringResource(R.string.samar_link_away, SamarRepository.formatDistance(away))
            else -> SamarRepository.format(place.lat, place.lng)
        },
        summaryColor = if (here) MaterialTheme.colorScheme.primary else null,
        shape = shape,
        trailing = {
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.samar_delete)) }
        },
        onClick = onClick,
    )
}

@Composable
private fun AreaRow(
    area: OfflineArea,
    shape: androidx.compose.ui.graphics.Shape,
    onShow: () -> Unit,
    onUpdate: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, area.bytes)
    val detail = stringResource(if (area.detail == Detail.STREETS) R.string.samar_detail_streets else R.string.samar_detail_overview)
    val summary = when {
        area.error != null -> stringResource(R.string.samar_maps_error, area.error)
        area.downloading -> stringResource(R.string.samar_maps_progress, (area.progress * 100).toInt(), size)
        !area.complete -> stringResource(R.string.samar_maps_paused, (area.progress * 100).toInt(), size)
        else -> stringResource(
            R.string.samar_maps_area_summary,
            size,
            detail,
            DateUtils.getRelativeTimeSpanString(area.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        )
    }
    Surface(shape = shape, color = LarasSurfaces.card) {
        Column {
            LarasNavRow(
                title = area.name,
                summary = summary,
                summaryColor = if (area.error != null) MaterialTheme.colorScheme.error else null,
                containerColor = LarasSurfaces.card,
                trailing = {
                    Row {
                        when {
                            area.downloading -> IconButton(onClick = onPause) {
                                Icon(Icons.Rounded.Pause, stringResource(R.string.samar_maps_pause))
                            }
                            !area.complete -> IconButton(onClick = onResume) {
                                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.samar_maps_resume))
                            }
                            else -> IconButton(onClick = onUpdate) {
                                Icon(Icons.Rounded.Refresh, stringResource(R.string.samar_maps_update))
                            }
                        }
                        IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.samar_delete)) }
                    }
                },
                onClick = onShow,
            )
            if (area.downloading) {
                LinearProgressIndicator(
                    progress = { area.progress },
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun appSummary(app: SamarApp): String {
    val mode = stringResource(app.mode.title)
    val overridden = Layer.entries.filter { app.layers.of(it) != null }
    return if (overridden.isEmpty()) {
        mode
    } else {
        stringResource(R.string.samar_app_summary_layers, mode, overridden.size)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppSheet(app: SamarApp, ui: SamarUi, viewModel: SamarViewModel, onDismiss: () -> Unit) {
    val own = ui.appPlaces[app.packageName]
    var text by remember { mutableStateOf("") }
    LarasModalSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(app.packageName, size = 48.dp)
            Column {
                Text(app.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppMode.entries.forEachIndexed { i, mode ->
                LarasRadioRow(
                    title = stringResource(mode.title),
                    summary = stringResource(mode.summary),
                    selected = app.mode == mode,
                    shape = groupedShape(i, AppMode.entries.size),
                    containerColor = LarasSurfaces.sheetCard,
                    onClick = { viewModel.setAppMode(app.packageName, mode) },
                )
            }
        }
        if (app.mode == AppMode.OWN) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.samar_app_location), style = MaterialTheme.typography.titleSmall)
                Text(
                    own?.takeIf { it.hasPlace }?.let { SamarRepository.format(it.lat, it.lng) }
                        ?: stringResource(R.string.samar_master_no_place),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.samar_find_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (text.isNotBlank()) viewModel.findForApp(app.packageName, text)
                        text = ""
                    }),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.position?.takeIf { it.hasPlace }?.let { g ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.setAppPlace(app.packageName, LatLng(g.lat, g.lng))
                            },
                            label = { Text(stringResource(R.string.samar_app_use_global)) },
                        )
                    }
                    ui.places.forEach { place ->
                        FilterChip(
                            selected = own?.let { SamarRepository.distance(it.lat, it.lng, place.lat, place.lng) < 40 } == true,
                            onClick = {
                                viewModel.setAppPlace(app.packageName, LatLng(place.lat, place.lng))
                            },
                            label = { Text(place.name) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.samar_app_panel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.samar_tab_privacy), style = MaterialTheme.typography.titleSmall)
            Layer.entries.forEach { layer ->
                val current = app.layers.of(layer)
                val global = ui.layers and layer.bit != 0
                Column {
                    Text(stringResource(layer.title), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = current == null,
                            onClick = { viewModel.setAppLayer(app, layer, null) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.samar_layer_default,
                                        stringResource(if (global) R.string.samar_layer_hidden else R.string.samar_layer_shown),
                                    ),
                                )
                            },
                        )
                        FilterChip(
                            selected = current == true,
                            onClick = { viewModel.setAppLayer(app, layer, true) },
                            label = { Text(stringResource(R.string.samar_layer_hidden)) },
                        )
                        FilterChip(
                            selected = current == false,
                            onClick = { viewModel.setAppLayer(app, layer, false) },
                            label = { Text(stringResource(R.string.samar_layer_shown)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSheet(bounds: LatLngBounds, onDismiss: () -> Unit, onDownload: (String, Detail) -> Unit) {
    val context = LocalContext.current
    val center = bounds.center
    var name by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf(Detail.STREETS) }
    val tiles = remember(detail) { SamarMaps.tileCount(bounds, detail) }
    val tooBig = tiles > SamarMaps.MAX_TILES
    LarasModalSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.samar_maps_download_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.samar_place_name)) },
            placeholder = { Text(SamarRepository.format(center.latitude, center.longitude)) },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Detail.entries.forEachIndexed { i, d ->
                LarasRadioRow(
                    title = stringResource(if (d == Detail.STREETS) R.string.samar_detail_streets else R.string.samar_detail_overview),
                    summary = stringResource(
                        R.string.samar_maps_estimate,
                        Formatter.formatShortFileSize(context, SamarMaps.estimateBytes(bounds, d)),
                    ),
                    selected = detail == d,
                    shape = groupedShape(i, Detail.entries.size),
                    containerColor = LarasSurfaces.sheetCard,
                    onClick = { detail = d },
                )
            }
        }
        if (tooBig) {
            Text(
                stringResource(R.string.samar_maps_too_big),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(
            enabled = !tooBig,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onDownload(name.trim().ifEmpty { SamarRepository.format(center.latitude, center.longitude) }, detail) },
        ) { Text(stringResource(R.string.samar_maps_download)) }
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.samar_save_place)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.samar_place_name)) },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.samar_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun AltitudeDialog(initial: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf("%.0f".format(initial)) }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in -500.0..9000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.samar_altitude)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                isError = value == null,
                suffix = { Text("m") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(stringResource(R.string.samar_altitude_hint)) },
            )
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = { value?.let(onSave) }) { Text(stringResource(R.string.samar_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
