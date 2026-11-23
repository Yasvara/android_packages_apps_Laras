/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.sound

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.server.yasvara.nada.INadaManager
import com.android.server.yasvara.nada.Nada
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.data.sound.NadaShortcuts
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.HeroBackdropKind
import org.yasvara.laras.ui.components.LarasCollapsibleCard
import org.yasvara.laras.ui.components.LarasHeroBackdrop
import org.yasvara.laras.ui.components.LarasMascotIcon
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.larasStickyControls
import org.yasvara.laras.ui.screens.Page

private const val TAG = "LarasNada"

private class Preset(val name: String, val spec: String, val routes: Set<String>, val source: String)

private val BUILT_IN = listOf(
    Preset("Reference", "on:0", emptySet(), "Bit-exact: the engine does not touch the audio."),
    Preset("Speaker · balanced",
        "on:1|eq.on:1|eq.bands:pk/566/4.7/3.63,pk/4398/-11/1.64,pk/8930/5.2/1.74,pk/12000/3.9/1.66|" +
            "bass.on:1|bass.gain:0|bass.cut:350|bass.harm:100",
        setOf("spk"), "Measured on this phone: deviation 600 Hz–10.5 kHz 5.6 → 2.2 dB (the +9.5 dB peak at " +
            "4.4 kHz cut, the top octave lifted); bass below 350 Hz replaced by its overtones. 5.6 dB quieter."),
    Preset("Speaker · loud",
        "on:1|eq.on:1|eq.bands:pk/4398/-8/1.64|bass.on:1|bass.gain:0|bass.cut:350|bass.harm:100",
        setOf("spk"), "Measured on this phone: no boosts, same level as off (−0.5 dB); deviation 5.6 → 4.0 dB, " +
            "distortion unchanged; bass below 350 Hz replaced by its overtones."),
    Preset("TOZO AeroSound6 Pro",
        "on:1|eq.on:1|eq.bands:ls/29/-3.9/0.54,pk/458/1.1/0.77,pk/2041/-0.8/1.08,pk/9420/-5.1/1.26",
        setOf("bt"), "A listener's TOZO-app tune for these earbuds, matched within 0.13 dB rms from 20 Hz " +
            "to 12.8 kHz: sub-bass −3 dB, +1 dB around 450 Hz, −5 dB at 9.4 kHz. Not measured; set the " +
            "TOZO app's own EQ flat so it is not applied twice."),
    Preset("Crossfeed", "on:1|xfeed.on:1", setOf("wired", "usb", "bt", "other"),
        "Classic 700 Hz / 4.5 dB crossfeed for headphones. Not measured on your headphones."),
    Preset("Night", "on:1|comp.on:1|comp.threshold:-30|comp.ratio:4|comp.makeup:6", emptySet(),
        "Narrower loudness range for quiet listening. Changes the dynamics on purpose."),
)

private fun engineKeys(v: Map<String, String>) = v.filterKeys { !it.startsWith(Nada.DOLBY) }

/**
 * Processor effects with:
 * - Animated Mascot + Sine-Wave Hero Card showing live "Active now" effect chips;
 * - Sticky Animated Bento Route Cards (Speaker, Wired, USB, Bluetooth, Other);
 * - Collapsible accordion effect cards and collapsible Advanced parametric band controls.
 */
@Composable
fun NadaScreen(onBack: () -> Unit, app: String? = null) {
    val context = LocalContext.current
    val nada = remember { Nada.get() }
    var schema by remember { mutableStateOf<Nada.Schema?>(null) }
    var scope by remember { mutableStateOf(app?.let { Nada.SCOPE_APP + it } ?: "spk") }
    var route by remember { mutableStateOf("") }
    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var impulses by remember { mutableStateOf<List<String>>(emptyList()) }
    var state by remember { mutableStateOf("") }
    var info by remember { mutableStateOf<Pair<String, String>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var expandedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var presetsOpen by rememberSaveable { mutableStateOf(false) }
    var diagOpen by rememberSaveable { mutableStateOf(false) }
    var drawerEnabled by remember { mutableStateOf(NadaShortcuts.isDrawerEnabled(context)) }
    val store = remember { context.getSharedPreferences("nada_presets", Context.MODE_PRIVATE) }
    var saved by remember { mutableStateOf(store.all.mapValues { it.value.toString() }.toSortedMap()) }

    LaunchedEffect(nada) {
        if (nada == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            runCatching {
                schema = Nada.Schema.parse(nada.schema)
                route = nada.route
                impulses = nada.listImpulses().toList()
                if (app == null) scope = route
            }.onFailure { Log.w(TAG, "service", it) }
        }
    }
    LaunchedEffect(nada, scope) {
        if (nada == null) return@LaunchedEffect
        values = withContext(Dispatchers.IO) { runCatching { Nada.decode(nada.getProfile(scope)) }.getOrDefault(emptyMap()) }
        snapshotFlow { values }.drop(1).collectLatest { v ->
            delay(60)
            withContext(Dispatchers.IO) { runCatching { nada.setProfile(scope, Nada.encode(v)) }.onFailure { Log.w(TAG, "set", it) } }
        }
    }
    LaunchedEffect(nada) {
        while (nada != null) {
            state = withContext(Dispatchers.IO) { runCatching { nada.state }.getOrDefault("") }
            delay(1000)
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && nada != null) {
            runCatching { importImpulse(context, nada, uri) }
                .onSuccess { name ->
                    impulses = nada.listImpulses().toList()
                    values = values + ("conv.ir" to name)
                }
                .onFailure { Log.w(TAG, "import", it) }
        }
    }
    fun set(k: String, v: String) { values = values + (k to v) }
    fun reset(k: String) { values = values - k }
    fun apply(spec: String) { values = values.filterKeys { it.startsWith(Nada.DOLBY) } + Nada.decode(spec) }

    Page(
        title = stringResource(R.string.nada_title),
        onBack = onBack,
        actions = {
            if (NadaShortcuts.isPinSupported(context)) {
                IconButton(
                    onClick = {
                        val ok = NadaShortcuts.requestPin(context)
                        Toast.makeText(
                            context,
                            if (ok) R.string.kait_pin_shortcut_done else R.string.kait_pin_unsupported,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.kait_pin_shortcut))
                }
            }
            IconButton(
                onClick = {
                    val next = !drawerEnabled
                    NadaShortcuts.setDrawerEnabled(context, next)
                    drawerEnabled = next
                    Toast.makeText(
                        context,
                        context.getString(R.string.kait_drawer_icon) + ": " +
                            context.getString(if (next) R.string.akar_freeze_on else R.string.akar_freeze_off),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Icon(
                    Icons.Rounded.Apps,
                    contentDescription = stringResource(R.string.kait_drawer_icon),
                    tint = if (drawerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        val s = schema
        if (nada == null || s == null) {
            item { Text(stringResource(R.string.nada_unavailable), Modifier.padding(20.dp)) }
            return@Page
        }
        val master = (values["on"]?.toFloatOrNull() ?: 0f) != 0f
        item(key = "master") {
            MasterCard(
                s = s,
                values = values,
                state = state,
                route = scope.takeIf { it in Nada.ROUTES } ?: route,
                onSelectEffect = { groupId -> expandedGroup = if (expandedGroup == groupId) null else groupId },
                set = ::set,
                reset = ::reset,
            )
            Spacer(Modifier.height(6.dp))
        }
        if (app == null) {
            larasStickyControls(key = "nada_sticky_bento_routes") {
                NadaRouteBentoRow(
                    routes = Nada.ROUTES.toList(),
                    selected = scope,
                    liveRoute = route,
                    onSelect = { scope = it },
                )
            }
        }
        item(key = "presets") {
            val engine = engineKeys(values)
            val shown = BUILT_IN.filter { it.routes.isEmpty() || scope !in Nada.ROUTES || scope in it.routes }
            val activePresetName = shown.firstOrNull { engine == Nada.decode(it.spec) }?.name
                ?: saved.entries.firstOrNull { engine == Nada.decode(it.value) }?.key
                ?: stringResource(R.string.nada_presets_summary)
            LarasCollapsibleCard(
                title = stringResource(R.string.nada_presets),
                summary = activePresetName,
                leadingIcon = Icons.Rounded.Tune,
                expanded = presetsOpen,
                onToggleExpand = { presetsOpen = !presetsOpen },
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { p ->
                        PresetChip(p.name, engine == Nada.decode(p.spec), onClick = { apply(p.spec) },
                            onLongClick = { info = p.name to p.source })
                    }
                    saved.forEach { (name, spec) ->
                        PresetChip(name, engine == Nada.decode(spec), onClick = { apply(spec) },
                            onLongClick = {
                                store.edit().remove(name).apply()
                                saved = store.all.mapValues { it.value.toString() }.toSortedMap()
                            })
                    }
                    TextButton(onClick = { saving = true }) { Text(stringResource(R.string.nada_preset_save)) }
                }
                Text(
                    stringResource(R.string.nada_presets_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val effects = s.groups.filter { it.id != "main" }
        effects.forEachIndexed { i, group ->
            item(key = "g_" + group.id) {
                val live = master || group.kind == "dsp"
                EffectCard(
                    group = group,
                    values = values,
                    impulses = impulses,
                    shape = groupedShape(i, effects.size),
                    live = live,
                    expanded = expandedGroup == group.id,
                    onToggleExpand = { expandedGroup = if (expandedGroup == group.id) null else group.id },
                    onInfo = { info = group.title to group.summary },
                    onImport = { pick.launch(arrayOf("audio/*")) },
                    set = ::set,
                    reset = ::reset,
                )
            }
        }
        item(key = "diag") {
            val diagLines = diagnostics(state)
            LarasCollapsibleCard(
                title = stringResource(R.string.nada_diag_title),
                summary = diagLines.firstOrNull() ?: stringResource(R.string.nada_diag_summary),
                leadingIcon = Icons.Rounded.Speed,
                expanded = diagOpen,
                onToggleExpand = { diagOpen = !diagOpen },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    stringResource(R.string.nada_diag_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                diagLines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    info?.let { (title, text) ->
        AlertDialog(onDismissRequest = { info = null }, title = { Text(title) }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { info = null }) { Text(stringResource(android.R.string.ok)) } })
    }
    if (saving) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { saving = false }, title = { Text(stringResource(R.string.nada_preset_save)) },
            text = { OutlinedTextField(name, { name = it.take(32) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    store.edit().putString(name.trim(), Nada.encode(engineKeys(values))).apply()
                    saved = store.all.mapValues { it.value.toString() }.toSortedMap()
                    saving = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(android.R.string.cancel)) } })
    }
}

/**
 * Animated Bento Route Cards for Speaker, Wired, USB, Bluetooth, and Other.
 * Each card draws a custom animated Canvas glyph that pulses smoothly when active/selected.
 */
@Composable
private fun NadaRouteBentoRow(
    routes: List<String>,
    selected: String,
    liveRoute: String,
    onSelect: (String) -> Unit,
) {
    val animEnabled = remember { android.animation.ValueAnimator.areAnimatorsEnabled() }
    val pulseState = if (animEnabled) {
        val infinite = rememberInfiniteTransition(label = "routeBento")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bentoPulse",
        )
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        routes.forEach { r ->
            val isSelected = r == selected
            val isLive = r == liveRoute
            val scheme = MaterialTheme.colorScheme
            val bg by animateColorAsState(
                if (isSelected) scheme.primaryContainer else LarasSurfaces.card,
                label = "routeBg",
            )
            val fg = if (isSelected) scheme.onPrimaryContainer else scheme.onSurface
            val border = if (isSelected) BorderStroke(1.5.dp, scheme.primary) else null

            Surface(
                onClick = { onSelect(r) },
                shape = RoundedCornerShape(18.dp),
                color = bg,
                contentColor = fg,
                border = border,
                modifier = Modifier.width(84.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Canvas(Modifier.size(34.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val rad = size.minDimension / 2f
                        val anim = if (isSelected || isLive) (pulseState?.value ?: 0.5f) else 0.25f
                        val stroke = Stroke(width = 2.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        when (r) {
                            "spk" -> {
                                // Speaker cone + radiating acoustic rings
                                drawCircle(fg.copy(alpha = 0.15f + 0.20f * anim), rad * (0.72f + 0.24f * anim), c)
                                drawCircle(fg, rad * 0.56f, c, style = stroke)
                                drawCircle(fg, rad * (0.20f + 0.08f * anim), c)
                            }
                            "wired" -> {
                                // Headphone arc + animated earcups
                                drawArc(
                                    color = fg,
                                    startAngle = 180f,
                                    sweepAngle = 180f,
                                    useCenter = false,
                                    topLeft = Offset(c.x - rad * 0.68f, c.y - rad * 0.68f),
                                    size = Size(rad * 1.36f, rad * 1.36f),
                                    style = stroke,
                                )
                                val cupH = rad * (0.52f + 0.10f * anim)
                                drawRoundRect(fg, Offset(c.x - rad * 0.78f, c.y - rad * 0.1f), Size(rad * 0.26f, cupH), CornerRadius(6f))
                                drawRoundRect(fg, Offset(c.x + rad * 0.52f, c.y - rad * 0.1f), Size(rad * 0.26f, cupH), CornerRadius(6f))
                            }
                            "usb" -> {
                                // USB-C DAC plug + pulsing audio cable wave
                                drawRoundRect(
                                    color = fg,
                                    topLeft = Offset(c.x - rad * 0.45f, c.y - rad * 0.58f),
                                    size = Size(rad * 0.90f, rad * 0.65f),
                                    cornerRadius = CornerRadius(8f),
                                    style = stroke,
                                )
                                drawLine(fg, Offset(c.x - rad * 0.22f, c.y - rad * 0.82f), Offset(c.x - rad * 0.22f, c.y - rad * 0.58f), stroke.width)
                                drawLine(fg, Offset(c.x + rad * 0.22f, c.y - rad * 0.82f), Offset(c.x + rad * 0.22f, c.y - rad * 0.58f), stroke.width)
                                drawLine(fg, Offset(c.x, c.y + rad * 0.08f), Offset(c.x, c.y + rad * (0.65f + 0.18f * anim)), stroke.width, StrokeCap.Round)
                            }
                            "bt" -> {
                                // Bluetooth rune + animated wireless signal arcs
                                val btPath = Path().apply {
                                    moveTo(c.x - rad * 0.35f, c.y - rad * 0.38f)
                                    lineTo(c.x + rad * 0.25f, c.y + rad * 0.35f)
                                    lineTo(c.x - rad * 0.05f, c.y + rad * 0.68f)
                                    lineTo(c.x - rad * 0.05f, c.y - rad * 0.68f)
                                    lineTo(c.x + rad * 0.25f, c.y - rad * 0.35f)
                                    lineTo(c.x - rad * 0.35f, c.y + rad * 0.38f)
                                }
                                drawPath(btPath, fg, style = stroke)
                                drawArc(
                                    color = fg.copy(alpha = 0.4f + 0.5f * anim),
                                    startAngle = -40f,
                                    sweepAngle = 80f,
                                    useCenter = false,
                                    topLeft = Offset(c.x - rad * 0.2f, c.y - rad * 0.55f),
                                    size = Size(rad * (0.85f + 0.15f * anim), rad * 1.1f),
                                    style = stroke,
                                )
                            }
                            else -> {
                                // 3 animated studio mixer faders
                                val xs = listOf(-0.48f, 0f, 0.48f)
                                val knobs = listOf(0.25f - 0.2f * anim, -0.25f + 0.2f * anim, 0.15f)
                                xs.forEachIndexed { idx, rx ->
                                    val x = c.x + rx * rad
                                    drawLine(fg.copy(alpha = 0.45f), Offset(x, c.y - rad * 0.65f), Offset(x, c.y + rad * 0.65f), stroke.width, StrokeCap.Round)
                                    drawCircle(fg, rad * 0.18f, Offset(x, c.y + knobs[idx] * rad))
                                }
                            }
                        }
                    }
                    Text(
                        text = routeLabel(r) + if (isLive) " •" else "",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Text(name, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The engine's Hero Card with animated Mascot + Sine-Wave background art, master switch,
 * headroom summary, and live "Active now" effect chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MasterCard(
    s: Nada.Schema,
    values: Map<String, String>,
    state: String,
    route: String,
    onSelectEffect: (String) -> Unit,
    set: (String, String) -> Unit,
    reset: (String) -> Unit,
) {
    fun num(k: String) = values[k]?.toFloatOrNull() ?: s.params[k]?.def ?: 0f
    val on = num("on") != 0f
    var showPreamp by rememberSaveable { mutableStateOf(false) }
    val turn by animateFloatAsState(if (showPreamp) 180f else 0f, label = "preampTurn")
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (on) scheme.primaryContainer else LarasSurfaces.card,
        label = "nadaHeroBg",
    )
    val content = if (on) scheme.onPrimaryContainer else scheme.onSurface

    val activeGroups = remember(s, values) {
        s.groups.filter { g ->
            g.id != "main" && (
                (on && g.kind != "dsp" && g.switchParam()?.let { num(it.key) != 0f } == true) ||
                    (g.kind == "dsp" && g.switchParam()?.let { num(it.key) != 0f } == true)
                )
        }
    }

    Surface(
        shape = LarasShapes.Hero,
        color = container,
        contentColor = content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth()) {
            LarasHeroBackdrop(
                kind = HeroBackdropKind.NADA_AUDIO,
                modifier = Modifier.matchParentSize(),
                color = content,
                alpha = if (on) 0.22f else 0.10f,
                animated = on,
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { showPreamp = !showPreamp },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LarasMascotIcon(
                        modifier = Modifier.size(36.dp),
                        color = if (on) content else scheme.primary,
                        withHeadphones = true,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.nada_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = pathLine(state, route) ?: routeLabel(route),
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.80f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).rotate(turn),
                        tint = content.copy(alpha = 0.75f),
                    )
                    Surface(onClick = { set("on", if (on) "0" else "1") }, color = androidx.compose.ui.graphics.Color.Transparent) {
                        CheckSwitch(on)
                    }
                }

                // "ACTIVE NOW" section showing which processor effects are currently running
                if (activeGroups.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "ACTIVE NOW (${activeGroups.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = content.copy(alpha = 0.75f),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            activeGroups.forEach { g ->
                                val detail = when (g.id) {
                                    "eq" -> parseBands(values["eq.bands"].orEmpty()).size.takeIf { it > 0 }?.let { " ($it bands)" }.orEmpty()
                                    "bass" -> values["bass.cut"]?.let { " (${it} Hz)" }.orEmpty()
                                    else -> ""
                                }
                                Surface(
                                    onClick = { onSelectEffect(g.id) },
                                    shape = LarasShapes.Pill,
                                    color = scheme.surface.copy(alpha = 0.24f),
                                    contentColor = content,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(groupIcon(g.id), null, Modifier.size(14.dp))
                                        Text(
                                            text = g.title + detail,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val (tone, text) = fidelity(s, values)
                    StatusLine(if (tone == Tone.GOOD) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, tone, text)
                }

                AnimatedVisibility(visible = showPreamp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val (tone, text) = fidelity(s, values)
                        if (activeGroups.isNotEmpty()) {
                            StatusLine(if (tone == Tone.GOOD) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, tone, text)
                        }
                        s.groups.firstOrNull { it.id == "main" }?.params?.filter { it.key != "on" }?.forEach { p ->
                            val auto = values["pre.auto"]?.toFloatOrNull() ?: s.params["pre.auto"]?.def ?: 1f
                            if (p.key == "pre.gain" && auto != 0f) return@forEach
                            ParamControl(p, values, emptyList(), {}, set, reset)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun fidelity(s: Nada.Schema, v: Map<String, String>): Pair<Tone, String> {
    fun num(k: String) = v[k]?.toFloatOrNull() ?: s.params[k]?.def ?: 0f
    if (num("on") == 0f) return Tone.GOOD to stringResource(R.string.nada_fid_reference)
    val active = s.groups.filter { g -> g.kind != "dsp" && g.switchParam()?.let { num(it.key) != 0f } == true }
    if (active.isEmpty()) return Tone.GOOD to stringResource(R.string.nada_fid_reference)
    val colour = active.filter { it.kind == "colour" }.map { it.title } +
        (if (active.any { it.id == "bass" } && num("bass.harm") > 0f) listOf("Psychoacoustic bass") else emptyList())
    val dyn = active.filter { it.kind == "dynamics" }.map { it.title }
    return when {
        colour.isNotEmpty() -> Tone.WARN to stringResource(R.string.nada_fid_colour, colour.joinToString())
        dyn.isNotEmpty() -> Tone.NEUTRAL to stringResource(R.string.nada_fid_dynamics, dyn.joinToString())
        else -> Tone.GOOD to stringResource(R.string.nada_fid_linear, active.joinToString { it.title })
    }
}

private fun dump(state: String) = state.lines().filter { it.startsWith("insert ") }.map { line ->
    line.split(' ').mapNotNull { val kv = it.split('=', limit = 2); if (kv.size == 2) kv[0] to kv[1] else null }.toMap()
}

private fun pathLine(state: String, route: String): String? {
    val mine = dump(state).filter { it["route"] == route && it["stages"] != "-" }
    if (mine.isEmpty()) return null
    fun ms(f: Map<String, String>): String {
        val frames = f["added_latency_frames"]?.toFloatOrNull() ?: 0f
        val rate = f["rate"]?.toFloatOrNull()?.takeIf { it > 0 } ?: 48000f
        return "%.1f ms".format(java.util.Locale.ROOT, frames * 1000 / rate)
    }
    val music = mine.firstOrNull { it["kind"] == "mixer" }
    val games = mine.firstOrNull { it["kind"] == "fast" }
    val any = music ?: games!!
    val lim = mine.maxOf { it["lim_max_db"]?.toFloatOrNull() ?: 0f }
    return listOfNotNull(
        music?.let { "Music +" + ms(it) },
        games?.let { "Games +" + ms(it) },
        "headroom ${any["pre_db"]} dB",
        if (lim > 0f) "limiter $lim dB" else null,
    ).joinToString(" · ")
}

private fun groupIcon(id: String): ImageVector = when (id) {
    "eq" -> Icons.Rounded.GraphicEq
    "dyneq" -> Icons.Rounded.Tune
    "bass" -> Icons.Rounded.Speaker
    "exc" -> Icons.Rounded.AutoAwesome
    "tube" -> Icons.Rounded.LocalFireDepartment
    "xfeed" -> Icons.Rounded.Headphones
    "width" -> Icons.Rounded.SwapHoriz
    "conv" -> Icons.Rounded.Waves
    "comp" -> Icons.Rounded.Compress
    "mbc" -> Icons.Rounded.Equalizer
    "loud" -> Icons.Rounded.Speed
    "dolby" -> Icons.Rounded.SurroundSound
    else -> Icons.Rounded.VolumeUp
}

@Composable
private fun badge(kind: String): String? = when (kind) {
    "tone" -> stringResource(R.string.nada_kind_tone)
    "dynamics" -> stringResource(R.string.nada_kind_dynamics)
    "colour" -> stringResource(R.string.nada_kind_colour)
    "spatial" -> stringResource(R.string.nada_kind_spatial)
    "dsp" -> stringResource(R.string.nada_kind_dsp)
    else -> null
}

@Composable
private fun EffectCard(
    group: Nada.Group,
    values: Map<String, String>,
    impulses: List<String>,
    shape: androidx.compose.ui.graphics.Shape,
    live: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onInfo: () -> Unit,
    onImport: () -> Unit,
    set: (String, String) -> Unit,
    reset: (String) -> Unit,
) {
    val sw = group.switchParam()
    val on = sw == null || (values[sw.key]?.toFloatOrNull() ?: sw.def) != 0f
    val kindBadge = badge(group.kind)
    val summaryText = buildList {
        if (!kindBadge.isNullOrBlank()) add(kindBadge)
        if (group.id == "eq" && on) {
            val bandCount = parseBands(values["eq.bands"].orEmpty()).size
            if (bandCount > 0) add("$bandCount bands active")
        } else if (on && live) {
            add("Active")
        }
    }.joinToString(" · ")

    LarasCollapsibleCard(
        title = group.title,
        summary = summaryText.ifEmpty { null },
        summaryColor = if (on && live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        leadingIcon = groupIcon(group.id),
        expanded = expanded,
        onToggleExpand = onToggleExpand,
        checked = if (sw != null) on else null,
        onCheckedChange = if (sw != null) ({ isChecked -> set(sw.key, if (isChecked) "1" else "0") }) else null,
        trailingActions = {
            IconButton(onClick = onInfo, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = shape,
        modifier = Modifier.alpha(if (live) 1f else 0.55f),
    ) {
        group.params.filter { it !== sw }.forEach { p ->
            ParamControl(p, values, impulses, onImport, set, reset)
        }
    }
}

@Composable
private fun ParamControl(
    p: Nada.Param,
    values: Map<String, String>,
    impulses: List<String>,
    onImport: () -> Unit,
    set: (String, String) -> Unit,
    reset: (String) -> Unit,
) {
    when (p.type) {
        Nada.Param.BOOL -> SwitchRow(p.title, (values[p.key]?.toFloatOrNull() ?: p.def) != 0f,
            onChange = { set(p.key, if (it) "1" else "0") })
        Nada.Param.NUM -> {
            val v = values[p.key]?.toFloatOrNull() ?: p.def
            NumberRow(p.title, v, p.def, p.min, p.max, p.step, p.unit, { set(p.key, trim(it)) }, { reset(p.key) })
        }
        Nada.Param.ENUM -> Column {
            val cur = (values[p.key]?.toFloatOrNull() ?: p.def).roundToInt()
            Text(p.title, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.options.forEachIndexed { i, name ->
                    FilterChip(selected = cur == i, onClick = { set(p.key, i.toString()) }, label = { Text(name) })
                }
            }
        }
        Nada.Param.BANDS -> EqEditor(values[p.key].orEmpty()) { set(p.key, it) }
        else -> Column {
            Text(p.title, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                impulses.forEach { name ->
                    FilterChip(selected = values[p.key] == name, onClick = { set(p.key, name) }, label = { Text(name) })
                }
                TextButton(onClick = onImport) { Text(stringResource(R.string.nada_import)) }
            }
        }
    }
}

private fun trim(v: Float): String = if (v == v.roundToInt().toFloat()) v.roundToInt().toString() else "%.2f".format(java.util.Locale.ROOT, v)

@Composable
private fun NumberRow(
    title: String,
    value: Float,
    def: Float,
    min: Float,
    max: Float,
    step: Float,
    unit: String,
    onChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(trim(value) + (if (unit.isEmpty()) "" else " $unit"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value != def) {
                IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Restore, stringResource(R.string.nada_reset_to, trim(def) + " " + unit), Modifier.size(16.dp))
                }
            } else {
                Spacer(Modifier.width(32.dp))
            }
        }
        Slider(value = value.coerceIn(min, max), valueRange = min..max,
            onValueChange = { onChange(((it - min) / step).roundToInt() * step + min) })
    }
}

private data class Band(val type: String, val f: Float, val gain: Float, val q: Float)

private fun parseBands(s: String): List<Band> = s.split(',').mapNotNull {
    val f = it.split('/')
    if (f.size != 4) null else Band(f[0], f[1].toFloatOrNull() ?: 1000f, f[2].toFloatOrNull() ?: 0f, f[3].toFloatOrNull() ?: 1f)
}

private fun formatBands(b: List<Band>) = b.joinToString(",") { "${it.type}/${trim(it.f)}/${trim(it.gain)}/${trim(it.q)}" }

private val GRAPHIC = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
private const val GRAPHIC_Q = 1.41f

private fun graphic(gains: FloatArray) = formatBands(GRAPHIC.mapIndexed { i, f -> Band("pk", f, gains[i], GRAPHIC_Q) })

private fun graphicGains(bands: List<Band>): FloatArray? =
    if (bands.size == GRAPHIC.size && bands.withIndex().all { (i, b) -> b.type == "pk" && b.f == GRAPHIC[i] && b.q == GRAPHIC_Q })
        FloatArray(GRAPHIC.size) { bands[it].gain } else null

private val GRAPHIC_PRESETS = listOf(
    "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Warm" to floatArrayOf(2f, 2f, 1.5f, 0.5f, 0f, 0f, -0.5f, -1f, -1.5f, -2f),
    "Vocal" to floatArrayOf(-2f, -1.5f, -1f, 0f, 1.5f, 2f, 2f, 1f, 0f, -1f),
    "Bright" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 1f, 2f, 3f, 3f),
    "V-shape" to floatArrayOf(4f, 3f, 1.5f, 0f, -1.5f, -1.5f, 0f, 1.5f, 3f, 3.5f),
)

private fun bandDb(b: Band, f: Double): Double {
    val fs = 48000.0
    if (b.f >= fs / 2) return 0.0
    val w0 = 2 * PI * b.f / fs
    val a = 10.0.pow(b.gain / 40.0)
    val al = sin(w0) / (2 * b.q)
    val c = cos(w0)
    val sa = 2 * sqrt(a) * al
    val k = when (b.type) {
        "pk" -> doubleArrayOf(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
        "ls" -> doubleArrayOf(a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa)
        "hs" -> doubleArrayOf(a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa)
        "lp" -> doubleArrayOf((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
        "hp" -> doubleArrayOf((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al)
        else -> return 0.0
    }
    val w = 2 * PI * f / fs
    fun mag(p: Double, q: Double, r: Double) = hypot(p + q * cos(w) + r * cos(2 * w), q * sin(w) + r * sin(2 * w))
    return 20 * log10(mag(k[0], k[1], k[2]) / mag(k[3], k[4], k[5]))
}

@Composable
private fun EqEditor(spec: String, onChange: (String) -> Unit) {
    val bands = parseBands(spec)
    val gains = graphicGains(bands)
    var parametric by remember { mutableStateOf(bands.isNotEmpty() && gains == null) }
    var showAdvancedBands by rememberSaveable { mutableStateOf(false) }
    val advTurn by animateFloatAsState(if (showAdvancedBands) 180f else 0f, label = "advBandsTurn")
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !parametric,
                onClick = { parametric = false },
                label = { Text(stringResource(R.string.nada_eq_graphic)) },
                leadingIcon = { Icon(Icons.Rounded.GraphicEq, null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
            FilterChip(
                selected = parametric,
                onClick = { parametric = true },
                label = { Text(stringResource(R.string.nada_eq_parametric)) },
                leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
        fun x(f: Double, w: Float) = (log2(f / 31.25) / 9.0 * w).toFloat()
        fun y(db: Double, h: Float) = (h / 2 - db / 12.0 * (h / 2 - 8)).toFloat()
        val g = gains ?: FloatArray(GRAPHIC.size)
        val latest by rememberUpdatedState(g)
        val emit by rememberUpdatedState(onChange)
        Canvas(
            Modifier.fillMaxWidth().height(140.dp).pointerInput(parametric) {
                if (parametric) return@pointerInput
                fun nearest(px: Float) = (px / size.width * 9).roundToInt().coerceIn(0, 9)
                fun gainAt(py: Float) = (((size.height / 2 - py) / (size.height / 2 - 8) * 12) * 2).roundToInt() / 2f
                var idx = 0
                var cur = g.copyOf()
                detectDragGestures(onDragStart = { o -> idx = nearest(o.x); cur = latest.copyOf() }) { change, _ ->
                    cur[idx] = gainAt(change.position.y).coerceIn(-12f, 12f)
                    emit(graphic(cur))
                }
            }.pointerInput(parametric) {
                if (parametric) return@pointerInput
                detectTapGestures { o ->
                    val i = (o.x / size.width * 9).roundToInt().coerceIn(0, 9)
                    val cur = latest.copyOf()
                    cur[i] = ((((size.height / 2 - o.y) / (size.height / 2 - 8) * 12) * 2).roundToInt() / 2f).coerceIn(-12f, 12f)
                    emit(graphic(cur))
                }
            }
        ) {
            val w = size.width
            val h = size.height
            for (db in listOf(-12.0, -6.0, 0.0, 6.0, 12.0)) drawLine(grid, Offset(0f, y(db, h)), Offset(w, y(db, h)), if (db == 0.0) 2f else 1f)
            val path = Path()
            var f = 31.25
            var first = true
            while (f <= 16000.0) {
                val db = bands.sumOf { bandDb(it, f) }.coerceIn(-14.0, 14.0)
                if (first) path.moveTo(x(f, w), y(db, h)) else path.lineTo(x(f, w), y(db, h))
                first = false
                f *= 1.03
            }
            drawPath(path, primary, style = Stroke(width = 5f))
            if (!parametric) g.forEachIndexed { i, gain -> drawCircle(primary, 12f, Offset(x(GRAPHIC[i].toDouble() * 1.0079, w), y(gain.toDouble(), h))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!parametric) {
            if (bands.isNotEmpty() && gains == null) {
                Text(stringResource(R.string.nada_eq_replace), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GRAPHIC_PRESETS.forEach { (name, preset) ->
                    FilterChip(selected = gains?.contentEquals(preset) == true, onClick = { onChange(graphic(preset)) }, label = { Text(name) })
                }
            }
        } else {
            Surface(
                onClick = { showAdvancedBands = !showAdvancedBands },
                shape = LarasShapes.Inset,
                color = LarasSurfaces.nestedCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "${stringResource(R.string.adv_title)} (${bands.size} bands · ${stringResource(R.string.nada_band_freq)}, ${stringResource(R.string.nada_band_gain)}, ${stringResource(R.string.nada_band_q)})",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Rounded.ExpandMore, null, Modifier.size(18.dp).rotate(advTurn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(visible = showAdvancedBands) {
                BandEditor(spec, onChange)
            }
        }
    }
}

private val BAND_TYPES = listOf("pk", "ls", "hs", "hp", "lp")

@Composable
private fun BandEditor(spec: String, onChange: (String) -> Unit) {
    val bands = parseBands(spec)
    var activeBandIdx by rememberSaveable { mutableStateOf(0) }
    val safeIdx = activeBandIdx.coerceIn(0, (bands.size - 1).coerceAtLeast(0))
    fun put(i: Int, b: Band) = onChange(formatBands(bands.toMutableList().also { it[i] = b }))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (bands.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                bands.forEachIndexed { i, b ->
                    FilterChip(
                        selected = i == safeIdx,
                        onClick = { activeBandIdx = i },
                        label = { Text("#${i + 1} ${b.f.roundToInt()} Hz") },
                    )
                }
                if (bands.size < 32) {
                    TextButton(onClick = {
                        onChange(formatBands(bands + Band("pk", 1000f, 0f, 1f)))
                        activeBandIdx = bands.size
                    }) {
                        Text(stringResource(R.string.nada_band_add))
                    }
                }
            }

            val b = bands[safeIdx]
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BAND_TYPES.forEach { t ->
                    FilterChip(selected = b.type == t, onClick = { put(safeIdx, b.copy(type = t)) }, label = { Text(bandLabel(t)) })
                }
                TextButton(onClick = {
                    onChange(formatBands(bands.filterIndexed { n, _ -> n != safeIdx }))
                    activeBandIdx = (safeIdx - 1).coerceAtLeast(0)
                }) {
                    Text(stringResource(R.string.nada_band_remove))
                }
            }
            val pos = (ln(b.f / 20f) / ln(1000f)).coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.nada_band_freq), style = MaterialTheme.typography.bodyMedium)
                Text("${b.f.roundToInt()} Hz", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(value = pos, onValueChange = { put(safeIdx, b.copy(f = (20f * 1000f.pow((it * 240).roundToInt() / 240f)).roundToInt().toFloat())) })
            if (b.type != "hp" && b.type != "lp") {
                NumberRow(stringResource(R.string.nada_band_gain), b.gain, 0f, -12f, 12f, 0.5f, "dB", { put(safeIdx, b.copy(gain = it)) }, { put(safeIdx, b.copy(gain = 0f)) })
            }
            NumberRow(stringResource(R.string.nada_band_q), b.q, 0.71f, 0.3f, 8f, 0.1f, "", { put(safeIdx, b.copy(q = it)) }, { put(safeIdx, b.copy(q = 0.71f)) })
        } else {
            TextButton(onClick = { onChange(formatBands(listOf(Band("pk", 1000f, 0f, 1f)))) }) {
                Text(stringResource(R.string.nada_band_add))
            }
        }
    }
}

@Composable
private fun bandLabel(t: String) = stringResource(when (t) {
    "ls" -> R.string.nada_band_ls
    "hs" -> R.string.nada_band_hs
    "hp" -> R.string.nada_band_hp
    "lp" -> R.string.nada_band_lp
    else -> R.string.nada_band_pk
})

@Composable
private fun routeLabel(route: String) = stringResource(when (route) {
    "spk" -> R.string.nada_route_spk
    "wired" -> R.string.nada_route_wired
    "usb" -> R.string.nada_route_usb
    "bt" -> R.string.nada_route_bt
    else -> R.string.nada_route_other
})

private fun diagnostics(state: String): List<String> = dump(state).mapNotNull { f ->
    if (f["stages"] == "-" || f["calls"] == "0") return@mapNotNull null
    fun us(k: String) = "%.1f".format(java.util.Locale.ROOT, (f[k]?.toLongOrNull() ?: 0) / 1000f)
    "${f["kind"]} ${f["route"]} · ${f["stages"]} · +${f["added_latency_frames"]} frames · " +
        "avg ${us("avg_ns")} µs · p99 ≤ ${us("p99_ns")} µs · max ${us("max_ns")} µs" +
        (if (f["overload"] == "1") " · overload" else "")
}.ifEmpty { listOf("—") }

private fun importImpulse(context: android.content.Context, nada: INadaManager, uri: Uri): String {
    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    require(bytes.size > 44 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "not a WAV file" }
    var pos = 12
    var format = 0; var channels = 0; var rate = 0; var bits = 0
    var data: FloatArray? = null
    while (pos + 8 <= bytes.size) {
        val id = String(bytes, pos, 4)
        val size = b.getInt(pos + 4)
        val at = pos + 8
        if (id == "fmt ") {
            format = b.getShort(at).toInt() and 0xffff
            channels = b.getShort(at + 2).toInt()
            rate = b.getInt(at + 4)
            bits = b.getShort(at + 14).toInt()
            if (format == 0xfffe && size >= 26) format = b.getShort(at + 24).toInt() and 0xffff
        } else if (id == "data") {
            require(channels in intArrayOf(1, 2, 4) && (format == 1 || format == 3)) { "unsupported WAV" }
            val step = bits / 8
            val frames = minOf(size, bytes.size - at) / step / channels
            data = FloatArray(frames * channels) { n ->
                val o = at + ((n % frames) * channels + n / frames) * step
                when {
                    format == 3 -> b.getFloat(o)
                    step == 2 -> b.getShort(o) / 32768f
                    step == 3 -> (((bytes[o + 2].toInt() shl 16) or ((bytes[o + 1].toInt() and 0xff) shl 8) or (bytes[o].toInt() and 0xff))) / 8388608f
                    else -> b.getInt(o) / 2147483648f
                }
            }
            break
        }
        pos = at + size + (size and 1)
    }
    val samples = requireNotNull(data) { "no audio data" }
    val name = (uri.lastPathSegment ?: "impulse").substringAfterLast('/').substringAfterLast(':').substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifEmpty { "impulse" }
    val pipe = ParcelFileDescriptor.createPipe()
    Thread {
        ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { out ->
            val buf = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.asFloatBuffer().put(samples)
            out.write(buf.array())
        }
    }.start()
    pipe[0].use { nada.putImpulse(name, rate, channels, it) }
    return name
}
