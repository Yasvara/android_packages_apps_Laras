/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
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
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.ChoicePref
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.SwitchPref
import org.yasvara.laras.data.FloatPref
import org.yasvara.laras.data.JsonFieldPref
import org.yasvara.laras.data.JsonPref
import org.yasvara.laras.data.OptionsPref
import org.yasvara.laras.data.StringPref
import org.yasvara.laras.data.IntSliderPref
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.ui.clock.ClockFaceEditor
import org.yasvara.laras.data.setAny
import org.yasvara.laras.ui.preview.LocalStatusBarSamples
import org.yasvara.laras.ui.preview.SampleChooser
import org.yasvara.laras.ui.theme.emphasized

/** Corner shape for item [index] of [count] in a grouped list: round outside, tight inside. */
fun groupedShape(index: Int, count: Int): Shape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

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
            // A folding row keeps the shape it had while it leaves.
            val index = shown.indexOf(entry)
            val lastShape = remember(entry) { arrayOf(groupedShape(0, 1)) }
            if (index >= 0) lastShape[0] = groupedShape(index, shown.size)
            val shape = lastShape[0]
            AnimatedVisibility(
                visible = index >= 0,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Box(Modifier.padding(bottom = 2.dp)) {
                    EntryRow(entry, prefs, editor, shape, onReset?.let { { it(entry) } })
                }
            }
        }
    }
}

/**
 * Display-only M3 switch with an animated check thumb. Pair it with a `toggleable` parent so the
 * whole row is the touch target. Kept receiver-free so the thumb's AnimatedVisibility never binds
 * to an enclosing RowScope/ColumnScope (Kotlin DSL-marker rules reject that from nested scopes).
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
        Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize))
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
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        when (val pref = entry.pref) {
            is SwitchPref -> {
                val checked = prefs[pref]
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(checked, role = Role.Switch) { editor.set(pref, it) }
                        .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EntryText(entry, prefs.isChanged(pref), Modifier.weight(1f))
                    onReset?.let { ResetButton(it) }
                    CheckSwitch(checked)
                }
            }
            is ChoicePref<*>, is JsonFieldPref<*> -> Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryText(entry, prefs.isChanged(pref), Modifier.weight(1f))
                    onReset?.let { ResetButton(it) }
                }
                val options = (pref as OptionsPref).choices
                val selected = prefs[pref] as ChoiceOption
                val samples = LocalStatusBarSamples.current
                val kind = entry.sample
                // Drawn options once SystemUI has drawn any, names until then.
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
                // Follows the finger locally and stores the value once, when the drag ends.
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
                // Follows the finger locally and stores the value once, when the drag ends.
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
            // Structured values have editors of their own.
            is JsonPref<*> -> when (pref) {
                Prefs.clockFace -> ClockFaceEditor(prefs[Prefs.clockFace], { editor.set(Prefs.clockFace, it) })
                else -> Unit
            }
            // Raw text is never edited as a row; its owners have screens of their own.
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
        modifier.size(6.dp).graphicsLayer { scaleX = scale; scaleY = scale; alpha = scale }
            .clip(CircleShape).background(MaterialTheme.colorScheme.primary)
    )
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    IconButton(onClick) {
        Icon(Icons.Rounded.Restore, stringResource(R.string.changed_reset))
    }
}

/**
 * Connected button groups choosing one option; the chosen one widens its corners. More than
 * [MAX_PER_ROW] options wrap into even rows, each a group of its own, so labels never shrink to
 * an ellipsis.
 */
@Composable
fun ChoiceButtons(
    options: List<ChoiceOption>,
    selected: ChoiceOption,
    onSelect: (ChoiceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = (options.size + MAX_PER_ROW - 1) / MAX_PER_ROW
    val perRow = (options.size + rows - 1) / rows
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(perRow).forEach { row -> ChoiceRow(row, selected, onSelect) }
    }
}

private const val MAX_PER_ROW = 4

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChoiceRow(
    options: List<ChoiceOption>,
    selected: ChoiceOption,
    onSelect: (ChoiceOption) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { i, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                modifier = Modifier.weight(1f),
                shapes = when {
                    i == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    i == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(
                    stringResource(option.label),
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
