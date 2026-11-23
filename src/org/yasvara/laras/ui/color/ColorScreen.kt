/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.yasvara.laras.ui.color

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.theme.CommitResult
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.screens.Page
import org.yasvara.monet.YasColorSpec
import org.yasvara.monet.YasPresets
import org.yasvara.monet.YasResolvedScheme
import org.yasvara.monet.YasStyle

@Composable
fun ColorScreen(onBack: () -> Unit, viewModel: ColorViewModel = viewModel()) {
    val resolved by viewModel.resolved.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val presetSchemes by viewModel.presetSchemes.collectAsStateWithLifecycle()
    val changed by viewModel.changed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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

    fun onScreen(centre: Offset): Offset {
        val window = IntArray(2).also(view::getLocationOnScreen)
        return centre + Offset(window[0].toFloat(), window[1].toFloat())
    }
    var resetAt by remember { mutableStateOf(Offset.Zero) }
    var applyAt by remember { mutableStateOf(Offset.Zero) }

    DraftTheme(resolved?.scheme) {
        Page(
            title = stringResource(R.string.color_title),
            onBack = onBack,
            snackbarHostState = snackbar,
            actions = {
                TextButton(
                    onClick = {
                        val at = onScreen(resetAt)
                        viewModel.resetToWallpaper(at.x, at.y, awaitRedraw)
                    },
                    modifier = Modifier.onGloballyPositioned { resetAt = it.boundsInWindow().center },
                ) { Text(stringResource(R.string.color_reset)) }
            },
            bottomBar = {
                val motion = MaterialTheme.motionScheme
                AnimatedVisibility(
                    visible = changed && resolved != null,
                    enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                    exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                ) {
                    Box(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = LarasShapes.Pill,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shadowElevation = 6.dp,
                        ) {
                            Row(
                                Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = viewModel::discard) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        val at = onScreen(applyAt)
                                        viewModel.apply(at.x, at.y, awaitRedraw) { result ->
                                            if (result !is CommitResult.Applied) {
                                                scope.launch {
                                                    snackbar.showSnackbar(context.getString(R.string.color_apply_failed))
                                                }
                                            }
                                        }
                                    },
                                    shape = LarasShapes.Pill,
                                    modifier = Modifier.onGloballyPositioned { applyAt = it.boundsInWindow().center },
                                ) {
                                    Text(stringResource(R.string.color_apply), Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }
            },
        ) {
            item(key = "palette") { PaletteHero(resolved?.scheme) }
            item(key = "source") {
                LarasSectionHeader(stringResource(R.string.color_source))
                SourcePicker(
                    spec = draft.spec,
                    candidates = candidates,
                    presets = viewModel.presets,
                    presetSchemes = presetSchemes,
                    onSeed = viewModel::setSeedIndex,
                    onPreset = viewModel::setPreset,
                )
            }
            item(key = "style") {
                LarasSectionHeader(stringResource(R.string.color_style))
                StyleCarousel(draft.spec.style, resolved?.styles.orEmpty(), viewModel::setStyle)
            }
            item(key = "vividness") { Vividness(draft.spec, viewModel::setVividness) }
            item(key = "contrast") {
                LarasSectionHeader(stringResource(R.string.color_contrast))
                ContrastChips(draft.spec.contrast, viewModel::setContrast)
                Spacer(Modifier.height(16.dp))
            }
            item(key = "black") {
                LarasSwitchRow(
                    title = stringResource(R.string.pref_pure_black),
                    summary = stringResource(R.string.pref_pure_black_summary),
                    checked = draft.pureBlack,
                    onCheckedChange = viewModel::setPureBlack,
                )
            }
            if (changed) {
                item(key = "bottom_pad") { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

private const val REDRAW_TIMEOUT_MS = 1500L

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
        Modifier.fillMaxWidth().padding(vertical = 8.dp).aspectRatio(3.2f).clip(LarasShapes.Hero),
    ) {
        roles.forEach { (role, label) ->
            val color = scheme?.color(role, dark, MaterialTheme.colorScheme.surfaceVariant)
                ?: MaterialTheme.colorScheme.surfaceVariant
            val description = scheme?.role(role, dark)?.let {
                ColorNames.describe(context, stringResource(label), it)
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().aspectRatio(0.8f).background(color)
                    .semantics { description?.let { contentDescription = it } },
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
            if (spec.source == YasColorSpec.Source.WALLPAPER) SourceTab.WALLPAPER else SourceTab.PRESETS,
        )
    }
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    ChoiceButtons(SourceTab.entries, tab, { tab = it as SourceTab })
    if (tab == SourceTab.WALLPAPER) {
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
                SeedDot(
                    color = Color(candidates[i]),
                    selected = spec.source == YasColorSpec.Source.WALLPAPER && spec.sourceIndex == i,
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
                    Text(preset.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
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
    lower: Pair<Color, Color>? = null,
    onClick: () -> Unit,
) {
    val ring by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "seed ring",
    )
    val shown = ring.coerceIn(0f, 1f)
    Box(
        Modifier
            .size(56.dp)
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
            .semantics { contentDescription = description },
    )
}

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
                Text(stringResource(style.label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
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

private class MorphShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cubics = CardMorph.asCubics(progress)
        val path = Path().apply {
            moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
            for (c in cubics) cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
            close()
            transform(Matrix().apply { scale(size.width, size.height) })
        }
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
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.color_vividness),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.color_percent, percent), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = { new ->
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

private enum class SourceTab(override val value: Int, override val label: Int) : ChoiceOption {
    WALLPAPER(0, R.string.color_source_wallpaper),
    PRESETS(1, R.string.color_source_presets),
}

private enum class ContrastChoice(val level: Double?, override val label: Int) : ChoiceOption {
    SYSTEM(null, R.string.color_contrast_system),
    STANDARD(0.0, R.string.color_contrast_standard),
    MEDIUM(0.5, R.string.color_contrast_medium),
    HIGH(1.0, R.string.color_contrast_high);

    override val value: Int get() = ordinal
}
