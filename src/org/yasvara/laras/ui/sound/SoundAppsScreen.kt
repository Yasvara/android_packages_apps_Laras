/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.sound

import android.app.yasvara.ISwaraListener
import android.app.yasvara.Swara
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private const val TAG = "LarasSwara"

/** One app on the page: what Swara knows about it, or the defaults. */
private data class SoundApp(
    val pkg: String,
    val label: String,
    val playing: Boolean,
    val recording: Boolean,
    val rules: Bundle,
) {
    val hasRule get() = rules.getBoolean(Swara.KEY_HAS_RULE)
}

/**
 * Sound › Apps (SPEC-0010 §5.2): every launchable app, searchable, with the full Swara rule.
 * Apps that play, record or have a rule come first.
 */
@Composable
fun SoundAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val service = remember { Swara.get() }
    // Laras runs as the user it serves; getCurrentUser() needs INTERACT_ACROSS_USERS.
    val userId = remember { context.userId }
    var generation by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<SoundApp>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(service) {
        val listener = object : ISwaraListener.Stub() {
            override fun onAppsChanged(user: Int) {
                generation++
            }
        }
        runCatching { service?.registerListener(listener) }
        onDispose { runCatching { service?.unregisterListener(listener) } }
    }
    LaunchedEffect(generation) { apps = withContext(Dispatchers.IO) { load(context, userId) } }

    val shown = apps.filter {
        query.isBlank() || it.label.contains(query, ignoreCase = true) ||
            it.pkg.contains(query, ignoreCase = true)
    }
    Page(
        stringResource(R.string.swara_title),
        onBack,
        actions = {
            if (apps.any { it.hasRule }) {
                TextButton(onClick = {
                    runCatching { service?.resetAll(userId) }
                }) { Text(stringResource(R.string.swara_reset_all)) }
            }
        },
    ) {
        item(key = "intro") {
            Text(
                stringResource(R.string.swara_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.swara_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
        if (service == null) {
            item(key = "missing") { Text(stringResource(R.string.swara_unavailable)) }
        }
        items(shown.size, key = { shown[it].pkg }) { i ->
            val app = shown[i]
            AppCard(app, open == app.pkg, groupedShape(i, shown.size), userId) {
                open = if (open == app.pkg) null else app.pkg
            }
        }
    }
}

@Composable
private fun AppCard(
    app: SoundApp,
    expanded: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    userId: Int,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val r = app.rules
    var volume by remember(app.pkg, r) { mutableFloatStateOf(r.getFloat(Swara.KEY_VOLUME, 1f)) }
    var micGain by remember(app.pkg, r) { mutableFloatStateOf(r.getFloat(Swara.KEY_MIC_GAIN, 1f)) }
    var outputMenu by remember { mutableStateOf(false) }

    fun send(build: Bundle.() -> Unit) {
        runCatching { Swara.get()?.setRules(app.pkg, userId, Bundle().apply(build)) }
            .onFailure { Log.w(TAG, "setRules ${app.pkg}", it) }
    }

    val muted = r.getBoolean(Swara.KEY_MUTED)
    val status = buildList {
        if (app.playing) add(stringResource(R.string.swara_playing))
        if (app.recording) add(stringResource(R.string.swara_recording))
        if (muted) add(stringResource(R.string.swara_muted))
        else if (volume < 1f) add("${(volume * 100).roundToInt()}%")
        if (r.getBoolean(Swara.KEY_MIC_MUTED)) add(stringResource(R.string.swara_mic_muted))
        if (r.getInt(Swara.KEY_OUTPUT_TYPE) != 0) add(outputName(context, r.getInt(Swara.KEY_OUTPUT_TYPE)))
    }.joinToString(" · ")

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
    ) {
        Column(Modifier.animateContentSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.pkg)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    if (status.isNotEmpty()) {
                        Text(status, style = MaterialTheme.typography.bodyMedium,
                            color = if (app.hasRule) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!expanded) return@Column
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.swara_volume, (volume * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = { send { putFloat(Swara.KEY_VOLUME, volume) } },
                )
                SwitchRow(stringResource(R.string.swara_mute), muted, { v ->
                    send { putBoolean(Swara.KEY_MUTED, v) }
                })
                SwitchRow(stringResource(R.string.swara_mic_mute), r.getBoolean(Swara.KEY_MIC_MUTED), { v ->
                    send { putBoolean(Swara.KEY_MIC_MUTED, v) }
                }, summary = stringResource(R.string.swara_mic_mute_summary))
                Text(stringResource(R.string.swara_mic_gain, (micGain * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = micGain,
                    onValueChange = { micGain = it },
                    onValueChangeFinished = { send { putFloat(Swara.KEY_MIC_GAIN, micGain) } },
                    valueRange = 0f..Swara.MAX_MIC_GAIN,
                )
                SwitchRow(stringResource(R.string.swara_share_mic), r.getBoolean(Swara.KEY_SHARE_MIC), { v ->
                    send { putBoolean(Swara.KEY_SHARE_MIC, v) }
                }, summary = stringResource(R.string.swara_share_mic_warning))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.swara_output), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            outputName(context, r.getInt(Swara.KEY_OUTPUT_TYPE)) +
                                if (!r.getBoolean(Swara.KEY_OUTPUT_AVAILABLE, true))
                                    " · " + stringResource(R.string.swara_output_unavailable) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { outputMenu = true }) { Text(stringResource(R.string.swara_change)) }
                    DropdownMenu(expanded = outputMenu, onDismissRequest = { outputMenu = false }) {
                        for ((type, address) in outputs(context)) {
                            DropdownMenuItem(
                                text = { Text(outputName(context, type)) },
                                onClick = {
                                    outputMenu = false
                                    send {
                                        putInt(Swara.KEY_OUTPUT_TYPE, type)
                                        putString(Swara.KEY_OUTPUT_ADDRESS, address)
                                    }
                                },
                            )
                        }
                    }
                }
                SwitchRow(stringResource(R.string.swara_reset_on_close), r.getBoolean(Swara.KEY_RESET_ON_CLOSE), { v ->
                    send { putBoolean(Swara.KEY_RESET_ON_CLOSE, v) }
                }, summary = stringResource(R.string.swara_reset_on_close_summary))
                if (app.hasRule) {
                    TextButton(onClick = {
                        runCatching { Swara.get()?.resetRules(app.pkg, userId) }
                    }) { Text(stringResource(R.string.swara_reset)) }
                }
            }
        }
    }
}

private val ROUTABLE = setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_USB_HEADSET,
)

private fun outputs(context: Context): List<Pair<Int, String>> {
    val list = mutableListOf(0 to "")
    val am = context.getSystemService(AudioManager::class.java) ?: return list
    for (d in am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
        if (d.type in ROUTABLE && list.none { it.first == d.type }) list.add(d.type to d.address)
    }
    return list
}

private fun outputName(context: Context, type: Int): String = context.getString(
    when (type) {
        0 -> R.string.swara_output_system
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.swara_output_speaker
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> R.string.swara_output_bt
        AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.swara_output_usb
        else -> R.string.swara_output_wired
    },
)

private fun load(context: Context, userId: Int): List<SoundApp> {
    val service = Swara.get() ?: return emptyList()
    val pm = context.packageManager
    val live = runCatching { service.getApps(userId) }.getOrNull().orEmpty()
        .associateBy { it.getString(Swara.KEY_PACKAGE).orEmpty() }
    val launchable = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
    ).map { it.activityInfo.packageName }.toSet()
    return (launchable + live.keys).filter { it.isNotEmpty() }.map { pkg ->
        val b = live[pkg]
        val rules = b ?: runCatching { service.getRules(pkg, userId) }.getOrNull() ?: Bundle()
        SoundApp(
            pkg = pkg,
            label = runCatching { pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString() }
                .getOrDefault(pkg),
            playing = b?.getBoolean(Swara.KEY_PLAYING) == true,
            recording = b?.getBoolean(Swara.KEY_RECORDING) == true,
            rules = rules,
        )
    }.sortedWith(
        compareBy<SoundApp>(
            { if (it.playing) 0 else if (it.recording) 1 else if (it.hasRule) 2 else 3 },
            { it.label.lowercase() },
        ),
    )
}
