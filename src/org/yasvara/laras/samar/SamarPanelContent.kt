/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.samar

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import org.yasvara.laras.R
import org.yasvara.laras.data.samar.AppMode
import org.yasvara.laras.data.samar.GLOBAL
import org.yasvara.laras.data.samar.SamarPosition
import org.yasvara.laras.data.samar.SamarRepository
import org.yasvara.laras.data.samar.Speed
import org.yasvara.laras.ui.samar.Joystick
import org.yasvara.laras.ui.samar.SamarMap
import org.yasvara.laras.ui.samar.SamarMapState

private data class Target(val key: String, val label: String)

val Speed.icon: ImageVector
    get() = when (this) {
        Speed.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
        Speed.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
        Speed.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
        Speed.CAR -> Icons.Rounded.DirectionsCar
    }

val Speed.label: Int
    get() = when (this) {
        Speed.WALK -> R.string.samar_speed_walk
        Speed.RUN -> R.string.samar_speed_run
        Speed.BIKE -> R.string.samar_speed_bike
        Speed.CAR -> R.string.samar_speed_car
    }

@Composable
internal fun SamarPanelContent(lifecycle: Lifecycle, onDrag: (Float, Float) -> Unit, onClose: () -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    AnimatedContent(
        targetState = expanded,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.85f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.85f))
        },
        label = "panel",
    ) { open ->
        if (open) {
            Panel(lifecycle, onDrag, onMinimize = { expanded = false }, onClose = onClose)
        } else {
            Bubble(onDrag, onOpen = { expanded = true })
        }
    }
}

@Composable
private fun Bubble(onDrag: (Float, Float) -> Unit, onOpen: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(8.dp)
            .size(56.dp)
            .shadow(6.dp, CircleShape)
            .windowDrag(onDrag, onTap = onOpen),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_samar), stringResource(R.string.samar_panel_open), Modifier.size(28.dp))
        }
    }
}

@Composable
private fun Panel(lifecycle: Lifecycle, onDrag: (Float, Float) -> Unit, onMinimize: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SamarRepository(context) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val mapState = remember { SamarMapState() }
    val global = stringResource(R.string.samar_target_global)
    var targets by remember { mutableStateOf(listOf(Target(GLOBAL, global))) }
    var target by remember { mutableStateOf(targets.first()) }
    var position by remember { mutableStateOf<SamarPosition?>(null) }
    var on by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(true) }
    var hint by remember { mutableStateOf(true) }
    val joystick = remember { Channel<Pair<Float, Float>>(Channel.CONFLATED) }

    LaunchedEffect(Unit) {
        val own = withContext(Dispatchers.IO) { repo.apps().filter { it.mode == AppMode.OWN } }
        targets = listOf(Target(GLOBAL, global)) + own.map { Target(it.packageName, it.label) }
        delay(5000)
        hint = false
    }
    LaunchedEffect(target) {
        while (true) {
            val p = withContext(Dispatchers.IO) { repo.position(target.key) }
            on = withContext(Dispatchers.IO) { repo.isOn() }
            position = p
            if (follow && p != null && p.hasPlace) mapState.moveTo(p.lat, p.lng, animate = true)
            delay(500)
        }
    }
    LaunchedEffect(target) {
        for ((x, y) in joystick) {
            withContext(Dispatchers.IO) { repo.setJoystick(target.key, x, y) }
            if (x != 0f || y != 0f) follow = true
            delay(70)
        }
    }

    Surface(
        modifier = Modifier.padding(8.dp).width(312.dp).shadow(10.dp, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Drag to move the panel; tap to switch between the global and per-app profiles.
                Row(
                    Modifier
                        .weight(1f)
                        .windowDrag(
                            onDrag,
                            onTap = if (targets.size > 1) {
                                {
                                    target = targets[(targets.indexOf(target) + 1) % targets.size]
                                    follow = true
                                }
                            } else {
                                null
                            },
                        )
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(10.dp).clip(CircleShape).background(
                            if (on || target.key != GLOBAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        ),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (targets.size > 1) "${target.label} ▾" else target.label,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            status(position, on || target.key != GLOBAL),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Rounded.Remove, stringResource(R.string.samar_panel_minimize))
                }
                IconButton(onClick = {
                    joystick.trySend(0f to 0f)
                    onClose()
                }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.samar_panel_close))
                }
            }

            PanelMap(
                mapState = mapState,
                position = position,
                lifecycle = lifecycle,
                hint = hint,
                follow = follow,
                onJump = { point ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        withContext(Dispatchers.IO) { repo.setPlace(target.key, point.latitude, point.longitude) }
                        follow = true
                    }
                },
                onUserMove = { follow = false },
                onRecenter = {
                    follow = true
                    position?.let { mapState.moveTo(it.lat, it.lng) }
                },
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Joystick(
                    onMove = { x, y -> joystick.trySend(x to y) },
                    size = 124.dp,
                    description = stringResource(R.string.samar_joystick),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Speed.entries.forEach { speed ->
                            FilledIconToggleButton(
                                checked = position?.speedPreset == speed,
                                onCheckedChange = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repo.setSpeed(target.key, speed) }
                                        position = position?.copy(speedPreset = speed)
                                    }
                                },
                                modifier = Modifier.size(38.dp),
                            ) {
                                Icon(speed.icon, stringResource(speed.label), Modifier.size(20.dp))
                            }
                        }
                    }
                    if (target.key == GLOBAL) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (on) R.string.samar_panel_on else R.string.samar_panel_off),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = on,
                                enabled = position?.hasPlace == true,
                                onCheckedChange = { value ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repo.setOn(value) }
                                        on = value
                                        SamarTile.requestRefresh(context)
                                    }
                                },
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.samar_panel_own),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun PanelMap(
    mapState: SamarMapState,
    position: SamarPosition?,
    lifecycle: Lifecycle,
    hint: Boolean,
    follow: Boolean,
    onJump: (LatLng) -> Unit,
    onUserMove: () -> Unit,
    onRecenter: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(196.dp).clip(RoundedCornerShape(20.dp))) {
        SamarMap(
            modifier = Modifier.matchParentSize(),
            state = mapState,
            position = position?.takeIf { it.hasPlace }?.let { LatLng(it.lat, it.lng) },
            initialZoom = 16.0,
            lifecycle = lifecycle,
            onLongPress = onJump,
            onUserMove = onUserMove,
        )
        AnimatedVisibility(hint, Modifier.align(Alignment.TopCenter).padding(8.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(
                stringResource(R.string.samar_panel_hint),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        AnimatedVisibility(!follow, Modifier.align(Alignment.BottomEnd).padding(8.dp), enter = scaleIn(), exit = scaleOut()) {
            SmallFloatingActionButton(onClick = onRecenter) {
                Icon(Icons.Rounded.MyLocation, stringResource(R.string.samar_panel_recenter))
            }
        }
    }
}

@Composable
private fun status(position: SamarPosition?, active: Boolean): String {
    if (position == null || !position.hasPlace) return stringResource(R.string.samar_tile_no_place)
    if (!active) return stringResource(R.string.samar_tile_off)
    return if (position.speed > 0.2) {
        stringResource(R.string.samar_panel_moving, position.speed * 3.6, compass(position.heading))
    } else {
        SamarRepository.format(position.lat, position.lng)
    }
}

private fun compass(heading: Double): String =
    listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((heading % 360) + 360 + 22.5) / 45).toInt() % 8]

private class DragState {
    var lastX = 0f
    var lastY = 0f
    var startX = 0f
    var startY = 0f
    var dragging = false
}

/**
 * Moves the overlay window by the finger's movement on screen. Raw coordinates are needed: the
 * window moves under the finger, so positions relative to it would cancel the drag out.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.windowDrag(onDrag: (Float, Float) -> Unit, onTap: (() -> Unit)? = null): Modifier {
    val s = remember { DragState() }
    val context = LocalContext.current
    val slop = remember { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val drag by rememberUpdatedState(onDrag)
    val tap by rememberUpdatedState(onTap)
    return pointerInteropFilter { e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                s.lastX = e.rawX
                s.lastY = e.rawY
                s.startX = e.rawX
                s.startY = e.rawY
                s.dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!s.dragging) s.dragging = kotlin.math.hypot(e.rawX - s.startX, e.rawY - s.startY) > slop
                if (s.dragging) {
                    drag(e.rawX - s.lastX, e.rawY - s.lastY)
                    s.lastX = e.rawX
                    s.lastY = e.rawY
                }
            }
            MotionEvent.ACTION_UP ->
                if (!s.dragging && e.eventTime - e.downTime < ViewConfiguration.getLongPressTimeout()) tap?.invoke()
        }
        true
    }
}
