/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.yasvara.laras.ui.color

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.yasvara.laras.R
import org.yasvara.laras.data.theme.CommitResult
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.screens.Page
import org.yasvara.monet.YasColorSpec
import org.yasvara.monet.YasPresets
import org.yasvara.monet.YasResolvedScheme
import org.yasvara.monet.YasStyle

/**
 * Colour, the simple surface (SPEC-0002 §5.1): where the colours come from, the style, how vivid,
 * how much contrast, and pure black. Everything is a draft, shown at once in this screen's own
 * colours; Apply recolours the system behind SystemUI's reveal from the Apply button.
 */
@Composable
fun ColorScreen(onBack: () -> Unit, viewModel: ColorViewModel = viewModel()) {
    val resolved by viewModel.resolved.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val presetSchemes by viewModel.presetSchemes.collectAsStateWithLifecycle()
    val changed by viewModel.changed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    // An applied theme reaches this screen as a configuration change, handled in place.
    val configuration = LocalConfiguration.current
    val configVersion = remember { mutableIntStateOf(0) }
    LaunchedEffect(configuration) { configVersion.intValue++ }
    val awaitRedraw: suspend () -> Unit = {
        val before = configVersion.intValue
        withTimeoutOrNull(REDRAW_TIMEOUT_MS) {
            snapshotFlow { configVersion.intValue }.first { it != before }
        }
        withFrameNanos {}
        withFrameNanos {}
    }

    DraftTheme(resolved?.scheme) {
        Page(stringResource(R.string.color_title), onBack) {
            item(key = "palette") { PaletteHero(resolved?.scheme) }
            item(key = "source") {
                Section(R.string.color_source) {
                    SourcePicker(
                        spec = draft.spec,
                        candidates = candidates,
                        presets = viewModel.presets,
                        presetSchemes = presetSchemes,
                        onSeed = viewModel::setSeedIndex,
                        onPreset = viewModel::setPreset,
                    )
                }
            }
            item(key = "style") {
                Section(R.string.color_style) {
                    StyleCarousel(draft.spec.style, resolved?.styles.orEmpty(), viewModel::setStyle)
                }
            }
            item(key = "vividness") {
                Vividness(draft.spec, viewModel::setVividness)
            }
            item(key = "contrast") {
                Section(R.string.color_contrast) { ContrastChips(draft.spec.contrast, viewModel::setContrast) }
            }
            item(key = "black") { PureBlack(draft.pureBlack, viewModel::setPureBlack) }
            item(key = "actions") {
                Actions(
                    canApply = changed && resolved != null,
                    onReset = { at -> viewModel.resetToWallpaper(at.x, at.y, awaitRedraw) },
                    onApply = { at ->
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        viewModel.apply(at.x, at.y, awaitRedraw) { result ->
                            if (result !is CommitResult.Applied) {
                                Toast.makeText(context, R.string.color_apply_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

private const val REDRAW_TIMEOUT_MS = 1500L

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        content()
    }
}

/** The draft's main roles as bands, so the colours read before the details. */
@Composable
private fun PaletteHero(scheme: YasResolvedScheme?) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val roles = listOf(
        "primary" to R.string.color_role_primary,
        "secondary" to R.string.color_role_secondary,
        "tertiary" to R.string.color_role_tertiary,
        "surface_container_high" to R.string.color_role_surface,
    )
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).aspectRatio(3.2f)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        roles.forEach { (role, label) ->
            val color = scheme?.color(role, dark, MaterialTheme.colorScheme.surfaceVariant)
                ?: MaterialTheme.colorScheme.surfaceVariant
            val description = scheme?.role(role, dark)?.let {
                ColorNames.describe(context, stringResource(label), it)
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().aspectRatio(0.8f).background(color)
                    .semantics { description?.let { contentDescription = it } }
            )
        }
    }
}

@Composable
private fun SourcePicker(
    spec: YasColorSpec,
    candidates: List<Int>,
    presets: List<YasPresets.Preset>,
    presetSchemes: Map<String, YasResolvedScheme>,
    onSeed: (Int) -> Unit,
    onPreset: (YasPresets.Preset) -> Unit,
) {
    var tab by rememberSaveable {
        mutableStateOf(
            if (spec.source == YasColorSpec.Source.WALLPAPER) SourceTab.WALLPAPER else SourceTab.PRESETS
        )
    }
    val showPresets = tab == SourceTab.PRESETS
    val dark = isSystemInDarkTheme()
    ChoiceButtons(SourceTab.entries, tab, { tab = it as SourceTab })
    val context = LocalContext.current
    if (!showPresets) {
        if (candidates.isEmpty()) {
            Text(
                stringResource(R.string.color_no_wallpaper_colors),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(candidates.size) { i ->
                val selected = spec.source == YasColorSpec.Source.WALLPAPER && spec.sourceIndex == i
                SeedDot(
                    color = Color(candidates[i]),
                    selected = selected,
                    description = ColorNames.describe(context, stringResource(R.string.color_role_seed), candidates[i]),
                ) { onSeed(i) }
            }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(presets, key = { it.id }) { preset ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // The preset as it applies, not its seed: a style can move far from it.
                    val scheme = presetSchemes[preset.id]
                    val fallback = Color(preset.spec.primary ?: 0)
                    SeedDot(
                        color = scheme?.color("primary", dark, fallback) ?: fallback,
                        selected = spec.preset == preset.id,
                        description = preset.englishName,
                        lower = scheme?.let {
                            it.color("secondary", dark, fallback) to it.color("tertiary", dark, fallback)
                        },
                    ) { onPreset(preset) }
                    Text(
                        preset.name,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedDot(
    color: Color,
    selected: Boolean,
    description: String,
    /** When given, the lower half shows these two, left and right, under [color]. */
    lower: Pair<Color, Color>? = null,
    onClick: () -> Unit,
) {
    val ring by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "seed ring",
    )
    // The spring overshoots past both ends; a ring cannot be thinner than nothing.
    val shown = ring.coerceIn(0f, 1f)
    Box(
        Modifier.size(56.dp)
            .border(BorderStroke((3 * shown).dp, MaterialTheme.colorScheme.onSurface), CircleShape)
            .padding((6 * shown).dp)
            .clip(CircleShape)
            .background(color)
            .drawBehind {
                if (lower == null) return@drawBehind
                val half = Size(size.width / 2, size.height / 2)
                drawRect(lower.first, Offset(0f, size.height / 2), half)
                drawRect(lower.second, Offset(size.width / 2, size.height / 2), half)
            }
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
    )
}

/** The six simple styles, each drawn with the swatches it would give (SPEC-0002 §5.2). */
@Composable
private fun StyleCarousel(
    selected: YasStyle,
    schemes: Map<YasStyle, YasResolvedScheme>,
    onSelect: (YasStyle) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(SimpleStyles) { style ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StyleCard(style == selected, schemes[style]) { onSelect(style) }
                Text(
                    stringResource(style.label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private val YasStyle.label: Int
    get() = when (this) {
        YasStyle.TONAL_SPOT -> R.string.color_style_balanced
        YasStyle.NEUTRAL -> R.string.color_style_calm
        YasStyle.VIBRANT -> R.string.color_style_vivid
        YasStyle.EXPRESSIVE -> R.string.color_style_playful
        YasStyle.CONTENT -> R.string.color_style_true
        YasStyle.MONOCHROMATIC -> R.string.color_style_mono
        else -> R.string.color_style_studio
    }

private val CardMorph by lazy {
    Morph(
        RoundedPolygon.rectangle(width = 2f, height = 2f, rounding = CornerRounding(0.36f)).normalized(),
        MaterialShapes.Cookie9Sided.normalized(),
    )
}

/** A shape between a rounded square (0) and the M3 Expressive nine-sided cookie (1). */
private class MorphShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cubics = CardMorph.asCubics(progress)
        val path = Path()
        path.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
        for (cubic in cubics) {
            path.cubicTo(
                cubic.control0X, cubic.control0Y, cubic.control1X, cubic.control1Y,
                cubic.anchor1X, cubic.anchor1Y,
            )
        }
        path.close()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

@Composable
private fun StyleCard(selected: Boolean, scheme: YasResolvedScheme?, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "style card",
    )
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    val swatches = listOf(
        "primary", "secondary", "tertiary",
        "surface_container_high", "primary_container", "on_surface",
    ).map { scheme?.color(it, dark, fallback) ?: fallback }
    Column(
        Modifier.size(96.dp).clip(MorphShape(progress))
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        swatches.chunked(3).forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { Box(Modifier.weight(1f).fillMaxWidth().aspectRatio(0.66f).background(it)) }
            }
        }
    }
}

@Composable
private fun Vividness(spec: YasColorSpec, onChange: (Double) -> Unit) {
    val view = LocalView.current
    val enabled = spec.style.hasChroma()
    var dragging by remember(spec) { mutableStateOf<Float?>(null) }
    val value = dragging ?: spec.chroma(YasColorSpec.A1).toFloat()
    val percent = (value * 100).roundToInt()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.color_vividness),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Text(stringResource(R.string.color_percent, percent), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = { new ->
                // A tick at each tenth, felt rather than seen.
                if ((new * 10).roundToInt() != (value * 10).roundToInt()) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                dragging = new
                onChange(new.toDouble())
            },
            onValueChangeFinished = { dragging = null },
            valueRange = YasColorSpec.CHROMA_MIN.toFloat()..YasColorSpec.CHROMA_MAX.toFloat(),
            enabled = enabled,
            modifier = Modifier.semantics {
                stateDescription = view.context.getString(R.string.color_vividness_state, percent)
            },
        )
    }
}

@Composable
private fun ContrastChips(level: Double?, onSelect: (Double?) -> Unit) {
    val selected = ContrastChoice.entries.firstOrNull { it.level == level } ?: ContrastChoice.SYSTEM
    ChoiceButtons(ContrastChoice.entries, selected, { onSelect((it as ContrastChoice).level) })
}

/** Where the colours come from, on the simple surface. */
private enum class SourceTab(override val value: Int, override val label: Int) : ChoiceOption {
    WALLPAPER(0, R.string.color_source_wallpaper),
    PRESETS(1, R.string.color_source_presets),
}

/** The simple surface's contrast chips; null follows the system (SPEC-0002 §4.4). */
private enum class ContrastChoice(val level: Double?, override val label: Int) : ChoiceOption {
    SYSTEM(null, R.string.color_contrast_system),
    STANDARD(0.0, R.string.color_contrast_standard),
    MEDIUM(0.5, R.string.color_contrast_medium),
    HIGH(1.0, R.string.color_contrast_high);

    override val value: Int
        get() = ordinal
}

@Composable
private fun PureBlack(on: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth().toggleable(on, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pref_pure_black), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.pref_pure_black_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CheckSwitch(on)
        }
    }
}

@Composable
private fun Actions(canApply: Boolean, onReset: (Offset) -> Unit, onApply: (Offset) -> Unit) {
    val view = LocalView.current
    fun onScreen(centre: Offset): Offset {
        val window = IntArray(2).also(view::getLocationOnScreen)
        return centre + Offset(window[0].toFloat(), window[1].toFloat())
    }
    var resetAt by remember { mutableStateOf(Offset.Zero) }
    var applyAt by remember { mutableStateOf(Offset.Zero) }
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            { onReset(onScreen(resetAt)) },
            Modifier.onGloballyPositioned { resetAt = it.boundsInWindow().center },
        ) { Text(stringResource(R.string.color_reset)) }
        Button(
            { onApply(onScreen(applyAt)) },
            Modifier.width(140.dp).onGloballyPositioned { applyAt = it.boundsInWindow().center },
            enabled = canApply,
        ) { Text(stringResource(R.string.color_apply)) }
    }
}
