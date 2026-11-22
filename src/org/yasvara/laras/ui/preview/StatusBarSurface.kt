/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.yasvara.laras.ui.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import android.view.SurfaceControlViewHost.SurfacePackage
import android.view.SurfaceView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import com.android.internal.policy.SystemBarUtils
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.catalog.SampleKind
import org.yasvara.laras.ui.components.icon

/**
 * A session with SystemUI's status bar preview: SystemUI builds its real home
 * status bar into a surface Laras embeds, so the canvas shows the status bar SystemUI would.
 * On a SystemUI without the renderer, [available] turns false and the canvas says so.
 */
class StatusBarRenderer(context: Context, private val samples: StatusBarSamples?) : BarSession {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var surfacePackage: SurfacePackage? = null
    private var callback: Messenger? = null
    private var simulated: Bundle? = null
    private var host: SurfaceView? = null
    private var requested = false
    private var closed = false
    private var protocol = 0

    override var available by mutableStateOf(true)
        private set

    /** Where the status bar's parts are drawn, by name (`clock`, `battery`), in pixels. */
    override var parts by mutableStateOf<Map<String, Rect>>(emptyMap())
        private set

    private val receiver = Messenger(
        Handler(Looper.getMainLooper()) { message ->
            when (message.what) {
                MESSAGE_YAS_BOUNDS -> message.data.getBundle(KEY_YAS_BOUNDS)?.let { bundle ->
                    parts = bundle.keySet().mapNotNull { key ->
                        bundle.getParcelable(key, Rect::class.java)?.let { key to it }
                    }.toMap()
                }
                MESSAGE_YAS_SAMPLE_RESULT -> {
                    val data = message.data
                    val kind = data.getString(KEY_KIND)
                    val value = data.getString(KEY_VALUE)
                    val bitmap = data.getParcelable(KEY_BITMAP, Bitmap::class.java)
                    if (kind != null && value != null && bitmap != null) samples?.put(kind, value, bitmap)
                }
            }
            true
        }
    )

    override fun attach(view: SurfaceView) {
        host = view
        val rendered = surfacePackage
        if (rendered != null) view.setChildSurfacePackage(SurfacePackage(rendered)) else request(view)
    }

    /** Shows [sim] on the canvas instead of the phone's own state; never saved. */
    fun simulate(sim: PreviewSim) {
        val draft = bundleOf(KEY_SIM to sim.toJson())
        simulated = draft
        send(MESSAGE_YAS_DRAFT, draft)
    }

    /**
     * Asks SystemUI to draw every option of [kinds] in the canvas's current state, for the
     * pickers ([StatusBarSamples]). Older SystemUI builds cannot, and are not asked.
     */
    fun requestSamples(kinds: Collection<SampleKind>) {
        if (samples == null) return
        if (protocol < PROTOCOL_SAMPLES) {
            Log.i(TAG, "SystemUI preview protocol $protocol cannot draw samples")
            return
        }
        val messenger = callback ?: return
        for (kind in kinds) {
            val message = Message.obtain(null, MESSAGE_YAS_SAMPLE).also {
                it.data = bundleOf(KEY_KIND to kind.id, KEY_VALUES to kind.values.toTypedArray())
                it.replyTo = receiver
            }
            runCatching { messenger.send(message) }
                .onFailure { Log.w(TAG, "SystemUI status bar preview went away", it) }
        }
    }

    fun close() {
        closed = true
        send(MESSAGE_DESTROY)
        surfacePackage?.release()
        surfacePackage = null
        callback = null
    }

    private fun request(view: SurfaceView) {
        if (requested) return
        val token = view.hostToken ?: return
        requested = true
        val request = bundleOf(
            KEY_YAS_SURFACE to SURFACE_STATUS_BAR,
            KEY_YAS_PROTOCOL to YAS_PROTOCOL_VERSION,
            KEY_HOST_TOKEN to token,
            KEY_WIDTH to view.layoutParams.width,
            KEY_HEIGHT to view.layoutParams.height,
            KEY_DISPLAY_ID to view.display.displayId,
            KEY_HOST_CALLBACK to receiver,
        )
        thread(name = "StatusBarPreview") {
            val result = runCatching {
                context.contentResolver.call(AUTHORITY, METHOD_PREVIEW, null, request)
            }.onFailure { Log.w(TAG, "SystemUI status bar preview unavailable", it) }.getOrNull()
            main.post { deliver(result) }
        }
    }

    private fun deliver(result: Bundle?) {
        val rendered = result?.getParcelable(KEY_SURFACE_PACKAGE, SurfacePackage::class.java)
        val messenger = result?.getParcelable(KEY_CALLBACK, Message::class.java)?.replyTo
        if (closed) {
            messenger?.let { runCatching { it.send(Message.obtain(null, MESSAGE_DESTROY)) } }
            rendered?.release()
            return
        }
        surfacePackage = rendered
        callback = messenger
        protocol = result?.getInt(KEY_YAS_PROTOCOL) ?: 0
        available = rendered != null
        rendered?.let { host?.setChildSurfacePackage(SurfacePackage(it)) }
        // What was simulated before SystemUI answered, then the pickers' samples in that state.
        simulated?.let { send(MESSAGE_YAS_DRAFT, it) }
        requestSamples(SampleKind.entries)
    }

    private fun send(what: Int, data: Bundle? = null) {
        val messenger = callback ?: return
        runCatching { messenger.send(Message.obtain(null, what).also { it.data = data }) }
            .onFailure { Log.w(TAG, "SystemUI status bar preview went away", it) }
    }

    private companion object {
        const val TAG = "Laras"
        val AUTHORITY: Uri = Uri.parse("content://com.android.systemui.customization")
        const val METHOD_PREVIEW = "preview"
        const val KEY_YAS_SURFACE = "yas_surface"
        const val SURFACE_STATUS_BAR = "status_bar"
        const val KEY_YAS_PROTOCOL = "yas_protocol"
        const val YAS_PROTOCOL_VERSION = 3
        const val KEY_HOST_TOKEN = "host_token"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_DISPLAY_ID = "display_id"
        const val KEY_HOST_CALLBACK = "host_callback"
        const val KEY_SURFACE_PACKAGE = "surface_package"
        const val KEY_CALLBACK = "callback"
        const val KEY_YAS_BOUNDS = "bounds"
        const val KEY_SIM = "sim"
        const val MESSAGE_YAS_DRAFT = 0x59410001
        const val MESSAGE_YAS_SAMPLE = 0x59410003
        const val MESSAGE_YAS_SAMPLE_RESULT = 0x59410102
        const val PROTOCOL_SAMPLES = 3
        const val KEY_KIND = "kind"
        const val KEY_VALUES = "values"
        const val KEY_VALUE = "value"
        const val KEY_BITMAP = "bitmap"
        const val MESSAGE_YAS_BOUNDS = 0x59410101
        const val MESSAGE_DESTROY = 0
    }
}

private const val SAMPLE_SETTLE_MS = 250L

/** The parts with states to try on the canvas. */
private val SIMULATED = setOf(Element.STATUS_BAR_BATTERY, Element.STATUS_BAR_ICONS)

/** The status bar's parts that can be tapped, by SystemUI's name for them. */
private val STATUS_BAR_PARTS = listOf(
    BarPart("clock", Element.STATUS_BAR_CLOCK),
    BarPart("status_icons", Element.STATUS_BAR_ICONS),
    BarPart("battery", Element.STATUS_BAR_BATTERY),
)

/**
 * The notification shade canvas: SystemUI's own status bar across the width at nearly its real
 * size, its parts named by callouts, and Quick Settings under it, which follow
 * once SystemUI renders them (roadmap). While the battery or the icons are picked, a card under
 * the bar tries their states on the canvas alone; the pickers' samples follow those states.
 */
@Composable
fun ShadePreview(
    device: DeviceState,
    focus: Element?,
    onSelect: (Element?) -> Unit,
    modifier: Modifier = Modifier,
    /** Anything besides the simulated states that changes how the options look. */
    samplesKey: Any? = null,
) {
    val context = LocalContext.current
    val samples = LocalStatusBarSamples.current
    val renderer = remember(context, samples) { StatusBarRenderer(context, samples) }
    DisposableEffect(renderer) { onDispose { renderer.close() } }
    var sim by remember(renderer) { mutableStateOf(PreviewSim()) }
    LaunchedEffect(renderer, sim) { renderer.simulate(sim) }
    // The samples redraw once a simulated state settles, not on every step of a slider.
    val dark = isSystemInDarkTheme()
    LaunchedEffect(renderer, sim, samplesKey, dark) {
        delay(SAMPLE_SETTLE_MS)
        renderer.requestSamples(SampleKind.entries)
    }
    val barHeight = remember(context) { SystemBarUtils.getStatusBarHeight(context) }
    val motion = MaterialTheme.motionScheme
    Column(modifier) {
        BarStage(renderer, barHeight, device, STATUS_BAR_PARTS, focus, onSelect)
        // The card for the picked part morphs into the next one, and folds away for parts
        // without states to try.
        AnimatedContent(
            targetState = focus.takeIf { renderer.available && it in SIMULATED },
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = true) { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "simCard",
        ) { part ->
            val cardModifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            when (part) {
                Element.STATUS_BAR_BATTERY -> BatterySimCard(sim, { sim = it }, cardModifier)
                Element.STATUS_BAR_ICONS -> IconSimCard(sim, { sim = it }, cardModifier)
                else -> Box(Modifier.fillMaxWidth())
            }
        }
        QuickSettingsCard(
            selected = focus == Element.QUICK_SETTINGS,
            onClick = { onSelect(Element.QUICK_SETTINGS) },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
        )
    }
}

/** Where Quick Settings will be previewed; until SystemUI renders them, it says so. */
@Composable
private fun QuickSettingsCard(selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val outline by animateColorAsState(
        if (selected) colors.primary else Color.Transparent,
        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "qsOutline",
    )
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerHigh)
            .border(2.dp, outline, RoundedCornerShape(24.dp))
            .selectable(selected, onClick = onClick, role = Role.Button)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Element.QUICK_SETTINGS.icon, null, Modifier.size(20.dp), tint = colors.primary)
            Text(stringResource(R.string.element_quick_settings), style = MaterialTheme.typography.titleMedium)
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.surface_preview_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
