/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Entry
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.ChoicePref
import org.yasvara.laras.data.FloatPref
import org.yasvara.laras.data.IntSliderPref
import org.yasvara.laras.data.JsonFieldPref
import org.yasvara.laras.data.JsonPref
import org.yasvara.laras.data.OptionsPref
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.StringPref
import org.yasvara.laras.data.SwitchPref
import org.yasvara.laras.data.setAny
import org.yasvara.laras.ui.clock.ClockFaceEditor
import org.yasvara.laras.ui.preview.LocalStatusBarSamples
import org.yasvara.laras.ui.preview.SampleChooser
import org.yasvara.laras.ui.theme.emphasized

/** Corner shape for item [index] of [count] in a grouped list: round outside, tight inside. */
fun groupedShape(index: Int, count: Int): Shape = LarasShapes.grouped(index, count)

/**
 * Entries as one grouped card list. [onReset], when given, adds a per-entry reset action. An
 * entry that doesn't apply to the other settings as they are folds away.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EntryList(
    entries: List<Entry>,
    prefs: PrefSnapshot,
    editor: PrefEditor,
    modifier: Modifier = Modifier,
    onReset: ((Entry) -> Unit)? = null,
) {
    val shown = entries.filter { it.applies(prefs) }
    val motion = MaterialTheme.motionScheme
    Column(modifier) {
        entries.forEach { entry ->
            val index = shown.indexOf(entry)
            val lastShape = remember(entry) { arrayOf(groupedShape(0, 1)) }
            if (index >= 0) lastShape[0] = groupedShape(index, shown.size)
            val shape = lastShape[0]
            AnimatedVisibility(
                visible = index >= 0,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Box(Modifier.padding(bottom = LarasSpacing.ItemGap)) {
                    EntryRow(entry, prefs, editor, shape, onReset?.let { { it(entry) } })
                }
            }
        }
    }
}

/**
 * Display-only M3 Expressive switch with an animated check thumb.
 * Pair with a `toggleable` parent so the entire row is the touch target.
 */
@Composable
fun CheckSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        modifier = modifier,
        enabled = enabled,
        thumbContent = { CheckThumb(checked) },
    )
}

@Composable
private fun CheckThumb(checked: Boolean) {
    AnimatedVisibility(checked, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize))
    }
}

@Composable
fun EntryRow(
    entry: Entry,
    prefs: PrefSnapshot,
    editor: PrefEditor,
    shape: Shape,
    onReset: (() -> Unit)? = null,
) {
    Surface(shape = shape, color = LarasSurfaces.card) {
        when (val pref = entry.pref) {
            is SwitchPref -> {
                val checked = prefs[pref]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(checked, role = Role.Switch) { editor.set(pref, it) }
                        .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EntryText(entry, prefs.isChanged(pref), Modifier.weight(1f))
                    onReset?.let { ResetButton(it) }
                    CheckSwitch(checked)
                }
            }
            is ChoicePref<*>, is JsonFieldPref<*> -> Column(
                Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryText(entry, prefs.isChanged(pref), Modifier.weight(1f))
                    onReset?.let { ResetButton(it) }
                }
                val options = (pref as OptionsPref).choices
                val selected = prefs[pref] as ChoiceOption
                val samples = LocalStatusBarSamples.current
                val kind = entry.sample
                if (kind != null && samples != null && samples.has(kind)) {
                    SampleChooser(
                        kind, samples, options, selected,
                        onSelect = { editor.setAny(pref, it) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    ChoiceButtons(
                        options = options,
                        selected = selected,
                        onSelect = { editor.setAny(pref, it) },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            is FloatPref -> Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryText(entry, prefs.isChanged(pref), Modifier.weight(1f))
                    onReset?.let { ResetButton(it) }
                }
                var dragging by remember(pref) { mutableStateOf<Float?>(null) }
                Slider(
                    value = dragging ?: prefs[pref],
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { editor.set(pref, it) }
                        dragging = null
                    },
                    valueRange = pref.range,
                    steps = pref.steps,
                )
            }
            is IntSliderPref -> Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
                var dragging by remember(pref) { mutableStateOf<Int?>(null) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryText(entry, prefs.isChanged(pref), Modifier.weight(1f))
                    Text(
                        (dragging ?: prefs[pref]).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    onReset?.let { ResetButton(it) }
                }
                Slider(
                    value = (dragging ?: prefs[pref]).toFloat(),
                    onValueChange = { dragging = it.toInt() },
                    onValueChangeFinished = {
                        dragging?.let { editor.set(pref, it) }
                        dragging = null
                    },
                    valueRange = pref.range.first.toFloat()..pref.range.last.toFloat(),
                )
            }
            is JsonPref<*> -> when (pref) {
                Prefs.clockFace -> ClockFaceEditor(prefs[Prefs.clockFace], { editor.set(Prefs.clockFace, it) })
                else -> Unit
            }
            is StringPref -> Unit
        }
    }
}

@Composable
private fun EntryText(entry: Entry, changed: Boolean, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
            ChangedDot(changed, Modifier.padding(start = 8.dp))
        }
        entry.summary?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Marks a setting that differs from its default. */
@Composable
fun ChangedDot(visible: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (visible) 1f else 0f, emphasized(), label = "dot")
    Box(
        modifier
            .size(6.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = scale }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    IconButton(onClick) {
        Icon(Icons.Rounded.Restore, stringResource(R.string.changed_reset))
    }
}

/** Connected M3 Expressive button group for [ChoiceOption] enums. */
@Composable
fun ChoiceButtons(
    options: List<ChoiceOption>,
    selected: ChoiceOption,
    onSelect: (ChoiceOption) -> Unit,
    modifier: Modifier = Modifier,
) = ChoiceGroup(
    options = options,
    selected = selected,
    label = { stringResource(it.label) },
    onSelect = onSelect,
    modifier = modifier,
)

/**
 * Generic M3 Expressive Connected Button Group replacing legacy `SingleChoiceSegmentedButtonRow`
 * everywhere in Laras. Wraps into balanced rows when options exceed [MAX_PER_ROW].
 */
@Composable
fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val rows = (options.size + MAX_PER_ROW - 1) / MAX_PER_ROW
    val perRow = (options.size + rows - 1) / rows
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(perRow).forEach { row ->
            ChoiceRow(row, selected, label, onSelect, enabled)
        }
    }
}

private const val MAX_PER_ROW = 4

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                shapes = when {
                    options.size == 1 -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    i == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    i == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(
                    label(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Shrinks slightly while pressed, for cards that navigate. */
@Composable
fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, emphasized(250), label = "press")
    return scale
}

@Composable
fun rememberInteraction() = remember { MutableInteractionSource() }
