/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.sound

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.SectionCard
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private const val TAG = "LarasNada"

/**
 * A starting point for a profile. [routes] limits it to the device classes it was made for;
 * [source] says where its numbers come from.
 */
private class Preset(val name: String, val spec: String, val routes: Set<String>, val source: String)

// Speaker presets come from stepped-sine sweeps of onyx's speaker through the mixer, mic presets
// 1/5/10 (they agree within 1 dB; preset 9 does not), three runs each (lab/nada/measure/2026-11-12/fit).
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
    // Traced from a shared TOZO-app EQ screenshot (lab/nada/measure/2026-11-12/tozo), fitted with
    // lab/nada/tools/eq_match.py.
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
 * Processor effects, ViPER-style: one row per effect with its switch; its controls open while
 * it is on. Everything is drawn from the engine's schema. [app] edits that app's own profile.
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
        // Edits go out at most every 60 ms, so a dragged slider is heard while it moves.
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

    Page(title = stringResource(R.string.nada_title), onBack = onBack) {
        val s = schema
        if (nada == null || s == null) {
            item { Text(stringResource(R.string.nada_unavailable), Modifier.padding(20.dp)) }
            return@Page
        }
        val master = (values["on"]?.toFloatOrNull() ?: 0f) != 0f
        if (app == null) {
            item(key = "scope") {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Nada.ROUTES.forEach { r ->
                        FilterChip(selected = scope == r, onClick = { scope = r },
                            label = { Text(routeLabel(r) + if (r == route) " •" else "") })
                    }
                }
            }
        }
        item(key = "master") {
            MasterCard(s, values, state, scope.takeIf { it in Nada.ROUTES } ?: route, ::set, ::reset)
        }
        item(key = "presets") {
            val engine = engineKeys(values)
            val shown = BUILT_IN.filter { it.routes.isEmpty() || scope !in Nada.ROUTES || scope in it.routes }
            SectionCard(title = stringResource(R.string.nada_presets), modifier = Modifier.padding(vertical = 12.dp),
                subtitle = stringResource(R.string.nada_presets_summary)) {
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
                Text(stringResource(R.string.nada_presets_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val effects = s.groups.filter { it.id != "main" }
        effects.forEachIndexed { i, group ->
            item(key = "g_" + group.id) {
                // Dolby runs on the DSP chip and does not follow the engine's master switch.
                val live = master || group.kind == "dsp"
                EffectCard(group, values, impulses, groupedShape(i, effects.size), live,
                    onInfo = { info = group.title to group.summary }, onImport = { pick.launch(arrayOf("audio/*")) },
                    set = ::set, reset = ::reset)
                Spacer(Modifier.height(2.dp))
            }
        }
        item(key = "diag") {
            SectionCard(title = stringResource(R.string.nada_diag_title), modifier = Modifier.padding(top = 12.dp),
                subtitle = stringResource(R.string.nada_diag_summary)) {
                diagnostics(state).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Text(name, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** The engine's switch, headroom, and what it is doing to the sound right now. */
@Composable
private fun MasterCard(
    s: Nada.Schema,
    values: Map<String, String>,
    state: String,
    route: String,
    set: (String, String) -> Unit,
    reset: (String) -> Unit,
) {
    val on = (values["on"]?.toFloatOrNull() ?: 0f) != 0f
    Surface(shape = RoundedCornerShape(28.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.nada_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Surface(onClick = { set("on", if (on) "0" else "1") }, color = androidx.compose.ui.graphics.Color.Transparent) {
                    CheckSwitch(on)
                }
            }
            val (tone, text) = fidelity(s, values)
            StatusLine(if (tone == Tone.GOOD) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, tone, text)
            pathLine(state, route)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            s.groups.firstOrNull { it.id == "main" }?.params?.filter { it.key != "on" }?.forEach { p ->
                val auto = values["pre.auto"]?.toFloatOrNull() ?: s.params["pre.auto"]?.def ?: 1f
                if (p.key == "pre.gain" && auto != 0f) return@forEach
                ParamControl(p, values, emptyList(), {}, set, reset)
            }
        }
    }
}

/** What the current settings do to fidelity, judged from which effects are on. */
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

/** Delay and headroom of this device class's mix points, from the engine's own report. */
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
        if (lim > 0f) "limiter caught up to $lim dB" else "limiter idle",
        mine.mapNotNull { it["skipped"]?.takeIf { s -> s != "-" } }.firstOrNull()?.let { "not on games: $it" },
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

/** One effect: icon, name, kind and switch; its controls while it is on. */
@Composable
private fun EffectCard(
    group: Nada.Group,
    values: Map<String, String>,
    impulses: List<String>,
    shape: androidx.compose.ui.graphics.Shape,
    live: Boolean,
    onInfo: () -> Unit,
    onImport: () -> Unit,
    set: (String, String) -> Unit,
    reset: (String) -> Unit,
) {
    val sw = group.switchParam()
    val on = sw == null || (values[sw.key]?.toFloatOrNull() ?: sw.def) != 0f
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, modifier = Modifier.alpha(if (live) 1f else 0.55f)) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(groupIcon(group.id), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleSmall)
                    badge(group.kind)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onInfo) { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (sw != null) {
                    Surface(onClick = { set(sw.key, if (on) "0" else "1") }, color = androidx.compose.ui.graphics.Color.Transparent) {
                        CheckSwitch(on)
                    }
                }
            }
            AnimatedVisibility(on) {
                Column(Modifier.padding(top = 4.dp, bottom = 8.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.params.filter { it !== sw }.forEach { p -> ParamControl(p, values, impulses, onImport, set, reset) }
                }
            }
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
            Text(p.title, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                p.options.forEachIndexed { i, name ->
                    FilterChip(selected = cur == i, onClick = { set(p.key, i.toString()) }, label = { Text(name) })
                }
            }
        }
        Nada.Param.BANDS -> EqEditor(values[p.key].orEmpty()) { set(p.key, it) }
        else -> Column {
            Text(p.title, style = MaterialTheme.typography.bodyLarge)
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

/** A slider with its value, and a reset to the default once it has moved. */
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
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(trim(value) + (if (unit.isEmpty()) "" else " $unit"), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (value != def) {
                IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Restore, stringResource(R.string.nada_reset_to, trim(def) + " " + unit), Modifier.size(18.dp))
                }
            } else {
                Spacer(Modifier.width(36.dp))
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

/** Ten octave bands, 31 Hz to 16 kHz; graphic mode writes these as peaking sections. */
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

/** Response of one section at [f] Hz, cookbook forms at 48 kHz (display only). */
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

/** The curve of all bands, graphic mode with draggable points, parametric mode for the rest. */
@Composable
private fun EqEditor(spec: String, onChange: (String) -> Unit) {
    val bands = parseBands(spec)
    val gains = graphicGains(bands)
    var parametric by remember { mutableStateOf(bands.isNotEmpty() && gains == null) }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !parametric, onClick = { parametric = false }, label = { Text(stringResource(R.string.nada_eq_graphic)) })
            FilterChip(selected = parametric, onClick = { parametric = true }, label = { Text(stringResource(R.string.nada_eq_parametric)) })
        }
        // x: 31 Hz … 16 kHz on a log scale (nine octaves); y: ±12 dB.
        fun x(f: Double, w: Float) = (log2(f / 31.25) / 9.0 * w).toFloat()
        fun y(db: Double, h: Float) = (h / 2 - db / 12.0 * (h / 2 - 8)).toFloat()
        val g = gains ?: FloatArray(GRAPHIC.size)
        // The gesture outlives the edits it makes, so it reads the latest state.
        val latest by rememberUpdatedState(g)
        val emit by rememberUpdatedState(onChange)
        Canvas(
            Modifier.fillMaxWidth().height(170.dp).pointerInput(parametric) {
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
            BandEditor(spec, onChange)
        }
    }
}

private val BAND_TYPES = listOf("pk", "ls", "hs", "hp", "lp")

/** Parametric bands: type, frequency on a log scale, gain and Q. */
@Composable
private fun BandEditor(spec: String, onChange: (String) -> Unit) {
    val bands = parseBands(spec)
    fun put(i: Int, b: Band) = onChange(formatBands(bands.toMutableList().also { it[i] = b }))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        bands.forEachIndexed { i, b ->
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BAND_TYPES.forEach { t ->
                    FilterChip(selected = b.type == t, onClick = { put(i, b.copy(type = t)) }, label = { Text(bandLabel(t)) })
                }
                TextButton(onClick = { onChange(formatBands(bands.filterIndexed { n, _ -> n != i })) }) {
                    Text(stringResource(R.string.nada_band_remove))
                }
            }
            // 20 Hz to 20 kHz on a log scale, snapped to 1/24 octave.
            val pos = (ln(b.f / 20f) / ln(1000f)).coerceIn(0f, 1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.nada_band_freq), style = MaterialTheme.typography.bodyLarge)
                Text("${b.f.roundToInt()} Hz", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Slider(value = pos, onValueChange = { put(i, b.copy(f = (20f * 1000f.pow((it * 240).roundToInt() / 240f)).roundToInt().toFloat())) })
            if (b.type != "hp" && b.type != "lp") {
                NumberRow(stringResource(R.string.nada_band_gain), b.gain, 0f, -12f, 12f, 0.5f, "dB", { put(i, b.copy(gain = it)) }, { put(i, b.copy(gain = 0f)) })
            }
            NumberRow(stringResource(R.string.nada_band_q), b.q, 0.71f, 0.3f, 8f, 0.1f, "", { put(i, b.copy(q = it)) }, { put(i, b.copy(q = 0.71f)) })
        }
        if (bands.size < 32) {
            TextButton(onClick = { onChange(formatBands(bands + Band("pk", 1000f, 0f, 1f))) }) {
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

// One line per mix point that is processing: what the engine measured on the phone.
private fun diagnostics(state: String): List<String> = dump(state).mapNotNull { f ->
    if (f["stages"] == "-" || f["calls"] == "0") return@mapNotNull null
    fun us(k: String) = "%.1f".format(java.util.Locale.ROOT, (f[k]?.toLongOrNull() ?: 0) / 1000f)
    "${f["kind"]} ${f["route"]} · ${f["stages"]} · +${f["added_latency_frames"]} frames · " +
        "avg ${us("avg_ns")} µs · p99 ≤ ${us("p99_ns")} µs · max ${us("max_ns")} µs" +
        (if (f["overload"] == "1") " · overload" else "")
}.ifEmpty { listOf("—") }

// WAV (PCM 16/24/32 or float32) to float32, channel after channel, handed over on a pipe.
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
                // Planar output: all of channel 0, then channel 1.
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
