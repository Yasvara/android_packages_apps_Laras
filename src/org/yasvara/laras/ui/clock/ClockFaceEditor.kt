/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.clock

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.core.os.bundleOf
import com.android.systemui.plugins.keyguard.ui.clocks.ThemeConfig
import org.yasvara.clocks.Align
import org.yasvara.clocks.AodLook
import org.yasvara.clocks.ClockDesign
import org.yasvara.clocks.ClockStyle
import org.yasvara.clocks.ClockThumbnail
import org.yasvara.clocks.ColorRole
import org.yasvara.clocks.YasvaraClockProvider
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.ClockFace
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.preview.KeyguardRenderer
import org.yasvara.laras.ui.preview.LocalKeyguardRenderer

/**
 * Picks and styles the lock screen clock. Like every Laras setting, a choice is saved at once.
 * While a slider is dragged the canvas shows the change as a draft rendered by SystemUI, and
 * the value is saved when the drag ends; the draft stays until the saved value comes back, so
 * the canvas never flashes the old clock in between.
 *
 * The thumbnails are drawn by the same designs SystemUI draws, linked into Laras, so they
 * match the real clock rather than being pictures of it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClockFaceEditor(saved: ClockFace, onApply: (ClockFace) -> Unit, modifier: Modifier = Modifier) {
    val renderer = LocalKeyguardRenderer.current
    // The face being dragged to, and the face saved but not yet read back.
    var dragging by remember { mutableStateOf<ClockFace?>(null) }
    var pending by remember { mutableStateOf<ClockFace?>(null) }
    LaunchedEffect(saved) { if (saved == pending) pending = null }
    val draft = dragging ?: pending ?: saved
    val previewed = dragging ?: pending

    LaunchedEffect(previewed) {
        if (previewed != null) {
            renderer.draft(bundleOf(KeyguardRenderer.KEY_CLOCK_FACE to previewed.toJson().toString()))
        } else {
            renderer.clearDraft()
        }
    }
    val drafting by rememberUpdatedState(previewed != null)
    DisposableEffect(renderer) { onDispose { if (drafting) renderer.clearDraft() } }

    fun save(face: ClockFace) {
        dragging = null
        if (face == saved) return
        pending = face
        onApply(face)
    }

    fun drag(face: ClockFace) {
        dragging = face
    }

    val design = YasvaraClockProvider.DESIGNS.firstOrNull { it.id == draft.id }
    val style = ClockStyle.from(axesOf(draft))

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            item(key = "default") {
                FaceCard(selected = draft.id == null, onClick = { save(ClockFace.DEFAULT) }) {
                    Text(
                        stringResource(R.string.clock_face_default),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            items(YasvaraClockProvider.DESIGNS, key = { it.id }) { item ->
                FaceCard(
                    selected = item.id == draft.id,
                    onClick = { save(ClockFace(id = item.id, seedColor = draft.seedColor)) },
                ) {
                    Thumbnail(item, if (item.id == draft.id) style else ClockStyle(), draft.seedColor)
                }
            }
        }

        if (design != null) {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(design.name), style = MaterialTheme.typography.titleMedium)

                Labelled(R.string.clock_axis_size) {
                    Slider(
                        value = style.scale,
                        onValueChange = { drag(draft.with(ClockStyle.SCALE, it)) },
                        onValueChangeFinished = { dragging?.let(::save) },
                        valueRange = ClockStyle.SCALE_RANGE,
                    )
                }

                Text(stringResource(R.string.clock_small_title), style = MaterialTheme.typography.titleSmall)
                Labelled(R.string.clock_axis_small_align) {
                    ChoiceButtons(
                        options = AlignOption.entries,
                        selected = AlignOption.entries[(style.smallAlign ?: design.smallAlign).ordinal],
                        onSelect = { save(draft.with(ClockStyle.SMALL_ALIGN, (it as AlignOption).ordinal.toFloat())) },
                    )
                }
                Labelled(R.string.clock_axis_small_size) {
                    Slider(
                        value = style.smallScale ?: design.smallScale,
                        onValueChange = { drag(draft.with(ClockStyle.SMALL_SCALE, it)) },
                        onValueChangeFinished = { dragging?.let(::save) },
                        valueRange = ClockStyle.SMALL_SCALE_RANGE,
                    )
                }

                Labelled(R.string.clock_axis_color) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ColorRole.entries.forEach { role ->
                            FilterChip(
                                selected = style.color == role,
                                onClick = { save(draft.with(ClockStyle.COLOR, role.ordinal.toFloat())) },
                                label = { Text(stringResource(role.label())) },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.clock_axis_tone_auto),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = style.tone == null,
                        onCheckedChange = { auto -> save(draft.with(ClockStyle.TONE, if (auto) -1f else 80f)) },
                    )
                }
                style.tone?.let { tone ->
                    Labelled(R.string.clock_axis_tone) {
                        Slider(
                            value = tone,
                            onValueChange = { drag(draft.with(ClockStyle.TONE, it)) },
                            onValueChangeFinished = { dragging?.let(::save) },
                            valueRange = 0f..100f,
                        )
                    }
                }

                Labelled(R.string.clock_axis_weight) {
                    Slider(
                        value = style.weight ?: 500f,
                        onValueChange = { drag(draft.with(ClockStyle.WEIGHT, it)) },
                        onValueChangeFinished = { dragging?.let(::save) },
                        valueRange = 100f..1000f,
                    )
                }
                Labelled(R.string.clock_axis_width) {
                    Slider(
                        value = style.width ?: 100f,
                        onValueChange = { drag(draft.with(ClockStyle.WIDTH, it)) },
                        onValueChangeFinished = { dragging?.let(::save) },
                        valueRange = 25f..151f,
                    )
                }
                Labelled(R.string.clock_axis_roundness) {
                    Slider(
                        value = style.roundness,
                        onValueChange = { drag(draft.with(ClockStyle.ROUNDNESS, it)) },
                        onValueChangeFinished = { dragging?.let(::save) },
                        valueRange = 0f..100f,
                    )
                }

                if (design.supportsSeconds) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.clock_axis_seconds),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Switch(
                            checked = style.seconds,
                            onCheckedChange = { save(draft.with(ClockStyle.SECONDS, if (it) 1f else 0f)) },
                        )
                    }
                }

                Labelled(R.string.clock_axis_aod) {
                    ChoiceButtons(
                        options = AodOption.entries,
                        selected = AodOption.entries[style.aod.ordinal],
                        onSelect = { save(draft.with(ClockStyle.AOD, (it as AodOption).ordinal.toFloat())) },
                    )
                }
                Labelled(R.string.clock_axis_align) {
                    ChoiceButtons(
                        options = AlignOption.entries,
                        selected = AlignOption.entries[style.align.ordinal],
                        onSelect = { save(draft.with(ClockStyle.ALIGN, (it as AlignOption).ordinal.toFloat())) },
                    )
                }
            }
        }

    }
}

private fun axesOf(face: ClockFace) =
    com.android.systemui.plugins.keyguard.ui.clocks.ClockAxisStyle(face.axes)

@Composable
private fun FaceCard(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.size(width = 104.dp, height = 132.dp)
            .clip(shape)
            .background(Color(0xFF15161A))
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Thumbnail(design: ClockDesign, style: ClockStyle, seed: Int?) {
    val context = LocalContext.current
    val drawable = remember(design, style, seed) {
        ClockThumbnail(context, design, style, ThemeConfig(isDarkTheme = true, seedColor = seed))
    }
    Canvas(Modifier.fillMaxSize()) {
        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
        drawIntoCanvas { drawable.draw(it.nativeCanvas) }
    }
}

@Composable
private fun Labelled(@StringRes label: Int, content: @Composable () -> Unit) {
    Column {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

private fun ColorRole.label() = when (this) {
    ColorRole.PRIMARY -> R.string.clock_color_primary
    ColorRole.SECONDARY -> R.string.clock_color_secondary
    ColorRole.TERTIARY -> R.string.clock_color_tertiary
    ColorRole.NEUTRAL -> R.string.clock_color_neutral
    ColorRole.GRADIENT -> R.string.clock_color_gradient
    ColorRole.MONO -> R.string.clock_color_mono
}

/** [AodLook] in the order of its entries, for [ChoiceButtons]. */
private enum class AodOption(override val label: Int) : ChoiceOption {
    OUTLINE(R.string.clock_aod_outline),
    THIN(R.string.clock_aod_thin),
    SAME(R.string.clock_aod_same);

    override val value get() = ordinal
}

/** [Align] in the order of its entries, for [ChoiceButtons]. */
private enum class AlignOption(override val label: Int) : ChoiceOption {
    START(R.string.option_left),
    CENTER(R.string.option_center),
    END(R.string.option_right);

    override val value get() = ordinal
}
