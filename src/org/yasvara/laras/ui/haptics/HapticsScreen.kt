/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.haptics

import android.app.AlertDialog
import android.content.Intent
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.raba.CategoryTone
import org.yasvara.laras.data.raba.HapticCategory
import org.yasvara.laras.data.raba.MASTER_MAX
import org.yasvara.laras.data.raba.RabaProfileKind
import org.yasvara.laras.data.raba.RabaSettings
import org.yasvara.laras.data.raba.STRENGTH_MIN
import org.yasvara.laras.data.raba.Texture
import org.yasvara.laras.ui.components.ChangedDot
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.glyph.PENTAS_PACKAGE
import org.yasvara.laras.ui.screens.Page

/** system_server picks a stored change up asynchronously; a preview waits this long for it. */
private const val FEEL_DELAY_MS = 150L

/**
 * Sound › Haptics: Raba's master switch and strength, the profile, each kind of touch, and a pad
 * to try them. Every change is felt through the real path, so a preview is exactly what apps get.
 */
@Composable
fun HapticsScreen(onBack: () -> Unit, viewModel: HapticsViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val s = ui.settings

    fun change(feel: Int?, transform: (RabaSettings) -> RabaSettings) {
        scope.launch {
            viewModel.update(transform)
            if (feel != null) {
                delay(FEEL_DELAY_MS)
                view.performHapticFeedback(feel)
            }
        }
    }

    Page(
        stringResource(R.string.raba_title),
        onBack,
        actions = {
            if (ui.loaded && s != RabaSettings()) {
                TextButton(onClick = { change(HapticFeedbackConstants.VIRTUAL_KEY) { RabaSettings() } }) {
                    Text(stringResource(R.string.raba_reset_all))
                }
            }
        },
    ) {
        item(key = "intro") {
            Text(
                stringResource(R.string.raba_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (!ui.loaded) return@Page
        item(key = "master") {
            MasterCard(
                touchOn = ui.touchOn,
                master = s.master,
                shape = if (ui.touchOn) groupedShape(0, 2) else groupedShape(0, 1),
                onTouch = { on ->
                    scope.launch {
                        viewModel.setTouch(on)
                        if (on) {
                            delay(FEEL_DELAY_MS)
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    }
                },
                onMaster = { m -> change(HapticFeedbackConstants.VIRTUAL_KEY) { it.copy(master = m) } },
            )
        }
        if (!ui.touchOn) return@Page
        item(key = "profile") {
            ProfileCard(
                s,
                onProfile = { p -> change(HapticFeedbackConstants.VIRTUAL_KEY) { it.copy(profile = p) } },
                onReskin = { r -> change(null) { it.copy(reskin = r) } },
            )
        }
        if (s.profile != RabaProfileKind.PIXEL) {
            item(key = "kinds") {
                SectionHeader(R.string.raba_kinds, R.string.raba_kinds_summary)
            }
            val cats = HapticCategory.entries
            items(cats.size, key = { cats[it].key }) { i ->
                val c = cats[i]
                CategoryCard(
                    category = c,
                    tone = s.tone(c),
                    edited = s.isEdited(c),
                    expanded = open == c.key,
                    shape = groupedShape(i, cats.size),
                    onToggle = { open = if (open == c.key) null else c.key },
                    onChange = { t -> change(if (t.on) c.lead else null) { it.edit(c, t) } },
                    onReset = { change(c.lead) { it.reset(c) } },
                )
            }
        }
        item(key = "pad_title") { SectionHeader(R.string.raba_pad, null) }
        item(key = "pad") { TestPad() }
        item(key = "pentas") { PentasRow() }
    }
}

@Composable
private fun MasterCard(
    touchOn: Boolean,
    master: Float,
    shape: Shape,
    onTouch: (Boolean) -> Unit,
    onMaster: (Float) -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright,
        modifier = Modifier.padding(bottom = 2.dp)) {
        Column(Modifier.animateContentSize().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            SwitchRow(
                stringResource(R.string.raba_touch),
                touchOn,
                onTouch,
                summary = stringResource(R.string.raba_touch_summary),
            )
            if (!touchOn) return@Column
            // Follows the finger locally and stores once, when the drag ends.
            var dragging by remember { mutableStateOf<Float?>(null) }
            val shown = dragging ?: master
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.raba_strength), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.raba_strength_value, (shown * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onMaster((it * 20).roundToInt() / 20f) }
                    dragging = null
                },
                valueRange = 0f..MASTER_MAX,
                steps = (MASTER_MAX * 20).roundToInt() - 1,
            )
            Text(
                stringResource(R.string.raba_strength_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileCard(
    s: RabaSettings,
    onProfile: (RabaProfileKind) -> Unit,
    onReskin: (Boolean) -> Unit,
) {
    Surface(shape = groupedShape(1, 2), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.animateContentSize().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            Text(stringResource(R.string.raba_feel), style = MaterialTheme.typography.titleMedium)
            ChoiceButtons(
                options = RabaProfileKind.entries,
                selected = s.profile,
                onSelect = { onProfile(it as RabaProfileKind) },
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                stringResource(
                    when (s.profile) {
                        RabaProfileKind.HYPEROS -> R.string.raba_profile_hyperos_summary
                        RabaProfileKind.PIXEL -> R.string.raba_profile_pixel_summary
                        RabaProfileKind.CUSTOM -> R.string.raba_profile_custom_summary
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            if (s.profile != RabaProfileKind.PIXEL) {
                SwitchRow(
                    stringResource(R.string.raba_reskin),
                    s.reskin,
                    onReskin,
                    summary = stringResource(R.string.raba_reskin_summary),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: Int, summary: Int?) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        summary?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CategoryCard(
    category: HapticCategory,
    tone: CategoryTone,
    edited: Boolean,
    expanded: Boolean,
    shape: Shape,
    onToggle: () -> Unit,
    onChange: (CategoryTone) -> Unit,
    onReset: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(shape = shape, color = c.surfaceBright, onClick = onToggle,
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Column(Modifier.animateContentSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(category.title), style = MaterialTheme.typography.titleMedium)
                ChangedDot(edited, Modifier.padding(start = 8.dp))
            }
            Text(
                if (tone.on) {
                    stringResource(R.string.raba_category_value,
                        stringResource(Texture.of(tone.wave).label), (tone.strength * 100).roundToInt())
                } else {
                    stringResource(R.string.raba_category_off)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (edited) c.primary else c.onSurfaceVariant,
            )
            if (!expanded) return@Column
            Text(
                stringResource(category.summary),
                style = MaterialTheme.typography.bodySmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            SwitchRow(stringResource(R.string.raba_category_on), tone.on, { onChange(tone.copy(on = it)) })
            if (!tone.on) return@Column
            ChoiceButtons(
                options = Texture.entries,
                selected = Texture.of(tone.wave),
                onSelect = { onChange(tone.copy(wave = (it as Texture).wave)) },
                modifier = Modifier.padding(top = 8.dp),
            )
            var dragging by remember(category) { mutableStateOf<Float?>(null) }
            val shown = dragging ?: tone.strength
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.raba_category_strength),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.raba_strength_value, (shown * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge, color = c.primary)
            }
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onChange(tone.copy(strength = (it * 20).roundToInt() / 20f)) }
                    dragging = null
                },
                valueRange = STRENGTH_MIN..1f,
                steps = ((1f - STRENGTH_MIN) * 20).roundToInt() - 1,
            )
            if (edited) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.raba_category_reset)) }
            }
        }
    }
}

/** One control per kind of touch, each firing the constants real widgets fire. */
@Composable
private fun TestPad() {
    val view = LocalView.current
    val feel = { constant: Int -> view.performHapticFeedback(constant); Unit }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressButton(R.string.raba_pad_button, HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.VIRTUAL_KEY_RELEASE, Modifier.weight(1f))
                PressButton(R.string.raba_pad_confirm, HapticFeedbackConstants.CONFIRM, null, Modifier.weight(1f))
                PressButton(R.string.raba_pad_reject, HapticFeedbackConstants.REJECT, null, Modifier.weight(1f),
                    outlined = true)
            }
            Keys()
            var on by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.raba_pad_switch), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f))
                Switch(on, {
                    on = it
                    feel(if (it) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
                })
            }
            SteppedSlider()
            PullTrack()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HoldTile(Modifier.weight(1f))
                DialogButton(Modifier.weight(1f))
            }
            EdgeList()
        }
    }
}

/** Fires on touch down, like a key, and optionally on release. */
@Composable
private fun PressButton(label: Int, press: Int, release: Int?, modifier: Modifier, outlined: Boolean = false) {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect {
            when (it) {
                is PressInteraction.Press -> view.performHapticFeedback(press)
                is PressInteraction.Release -> release?.let(view::performHapticFeedback)
            }
        }
    }
    val content: @Composable () -> Unit = { Text(stringResource(label), maxLines = 1) }
    if (outlined) {
        OutlinedButton(onClick = {}, modifier = modifier, interactionSource = source) { content() }
    } else {
        FilledTonalButton(onClick = {}, modifier = modifier, interactionSource = source) { content() }
    }
}

@Composable
private fun Keys() {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (key in listOf("Q", "W", "E", "R", "T", "Y")) {
            val view = LocalView.current
            val source = remember { MutableInteractionSource() }
            LaunchedEffect(source) {
                source.interactions.collect {
                    when (it) {
                        is PressInteraction.Press -> view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        is PressInteraction.Release ->
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                    }
                }
            }
            Surface(
                onClick = {},
                interactionSource = source,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(key, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Ticks on every step, a limit tick at either end: what a seek bar does. */
@Composable
private fun SteppedSlider() {
    val view = LocalView.current
    var value by remember { mutableFloatStateOf(5f) }
    var step by remember { mutableIntStateOf(5) }
    Column {
        Text(stringResource(R.string.raba_pad_slider), style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = {
                value = it
                val now = it.roundToInt()
                if (now != step) {
                    step = now
                    view.performHapticFeedback(
                        if (now == 0 || now == 10) HapticFeedbackConstants.SCROLL_LIMIT
                        else HapticFeedbackConstants.SEGMENT_TICK,
                    )
                }
            },
            valueRange = 0f..10f,
            steps = 9,
        )
    }
}

/** A drawer handle: crossing the line activates, coming back deactivates, letting go ends. */
@Composable
private fun PullTrack() {
    val view = LocalView.current
    val density = LocalDensity.current
    Column {
        Text(stringResource(R.string.raba_pad_pull), style = MaterialTheme.typography.bodyLarge)
        BoxWithConstraints(
            Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
        ) {
            val knob = 48.dp
            val travel = with(density) { (maxWidth - knob).toPx() }
            var x by remember { mutableFloatStateOf(0f) }
            var past by remember { mutableStateOf(false) }
            val line = 0.7f * travel
            Box(
                Modifier.offset(x = with(density) { (line + knob.toPx() / 2).toDp() })
                    .width(2.dp).fillMaxHeight().padding(vertical = 10.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), 0) }.size(knob)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { d ->
                            x = (x + d).coerceIn(0f, travel)
                            val nowPast = x >= line
                            if (nowPast != past) {
                                past = nowPast
                                view.performHapticFeedback(
                                    if (nowPast) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
                                    else HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE,
                                )
                            }
                        },
                        onDragStarted = { view.performHapticFeedback(HapticFeedbackConstants.GESTURE_START) },
                        onDragStopped = {
                            if (past) view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                            past = false
                            x = 0f
                        },
                    ),
            )
        }
    }
}

/** Long-clickable: Compose fires LONG_PRESS itself. */
@Composable
private fun HoldTile(modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.height(56.dp).combinedClickable(onClick = {}, onLongClick = {}),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.raba_pad_hold), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A framework dialog, so its buttons go through the platform's own dialog haptics. */
@Composable
private fun DialogButton(modifier: Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            AlertDialog.Builder(context)
                .setTitle(R.string.raba_pad_dialog)
                .setMessage(R.string.raba_pad_dialog_body)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        },
        modifier = modifier.height(56.dp),
    ) { Text(stringResource(R.string.raba_pad_dialog)) }
}

/** A platform ScrollView, so its edge goes through the platform's own edge effect. */
@Composable
private fun EdgeList() {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val context = LocalContext.current
    val rows = (1..20).map { context.getString(R.string.raba_pad_list_item, it) }
    Column {
        Text(stringResource(R.string.raba_pad_list), style = MaterialTheme.typography.bodyLarge)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(160.dp),
        ) {
            AndroidView(
                factory = { ctx ->
                    ScrollView(ctx).apply {
                        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
                        val pad = (16 * ctx.resources.displayMetrics.density).roundToInt()
                        for (row in rows) {
                            list.addView(TextView(ctx).apply {
                                this.text = row
                                setTextColor(textColor)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                                setPadding(pad, pad / 2, pad, pad / 2)
                            })
                        }
                        addView(list)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PentasRow() {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        onClick = {
            runCatching {
                context.startActivity(
                    Intent().setClassName(PENTAS_PACKAGE, "$PENTAS_PACKAGE.hub.HubSettingsActivity"),
                )
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.raba_pentas), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.raba_pentas_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
