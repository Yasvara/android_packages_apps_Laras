/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.preview

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yasvara.laras.catalog.SampleKind
import org.yasvara.laras.data.ChoiceOption

/**
 * Option samples SystemUI draws with its own status bar (SPEC-0003 §4.1 `YAS_SAMPLE`): what
 * each battery style or icon set looks like, in the canvas's state. Filled by the status bar
 * canvas while it is open; empty without one, and the pickers fall back to plain buttons.
 */
class StatusBarSamples {
    private val samples = mutableStateMapOf<String, Sample>()

    /** One option drawn: the image, and whether its marks are light (they need a dark tile). */
    class Sample(val image: ImageBitmap, val light: Boolean)

    operator fun get(kind: SampleKind, value: String): Sample? = samples[key(kind.id, value)]

    /** Whether SystemUI has drawn any option of [kind]; the rest fill in as they arrive. */
    fun has(kind: SampleKind): Boolean = kind.values.any { samples.containsKey(key(kind.id, it)) }

    fun put(kind: String, value: String, bitmap: Bitmap) {
        samples[key(kind, value)] = Sample(bitmap.asImageBitmap(), bitmap.isLight())
    }

    private fun key(kind: String, value: String) = "$kind/$value"

    /** Whether the drawn pixels are mostly light, weighted by their opacity. */
    private fun Bitmap.isLight(): Boolean {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        var weight = 0.0
        var light = 0.0
        for (pixel in pixels) {
            val alpha = (pixel ushr 24) / 255.0
            if (alpha < 0.1) continue
            weight += alpha
            light += alpha * Color(pixel).luminance()
        }
        return weight == 0.0 || light / weight > 0.5
    }
}

val LocalStatusBarSamples = staticCompositionLocalOf<StatusBarSamples?> { null }

/**
 * Picks one of [options] by how it looks: each is SystemUI's own drawing of it, at its real size
 * on a tile in the canvas's tint, named under it. The chosen tile is outlined and rounds fully.
 */
@Composable
fun SampleChooser(
    kind: SampleKind,
    samples: StatusBarSamples,
    options: List<ChoiceOption>,
    selected: ChoiceOption,
    onSelect: (ChoiceOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    LaunchedEffect(Unit) {
        // Opens on the chosen one.
        list.scrollToItem((options.indexOf(selected) - 1).coerceAtLeast(0))
    }
    LazyRow(
        modifier,
        state = list,
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { _, option ->
            SampleTile(
                sample = samples[kind, kind.key(option)],
                label = stringResource(option.label),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun SampleTile(
    sample: StatusBarSamples.Sample?,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(if (selected) 28.dp else 16.dp, motion.fastSpatialSpec(), label = "corner")
    val outline by animateColorAsState(
        if (selected) colors.primary else Color.Transparent, motion.fastEffectsSpec(), label = "outline",
    )
    val shape = RoundedCornerShape(corner)
    // The status bar's marks are white or black; the tile is what they would sit on.
    val tile = when (sample?.light) {
        true -> colors.inverseSurface.takeIf { it.luminance() < 0.5f } ?: colors.surfaceContainerLowest
        false -> colors.surfaceContainerHighest.takeIf { it.luminance() > 0.5f } ?: colors.inverseSurface
        null -> colors.surfaceContainerHigh
    }
    Column(
        Modifier.width(TILE_WIDTH)
            .clip(shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(TILE_WIDTH).height(TILE_HEIGHT)
                .clip(shape)
                .background(tile)
                .border(2.dp, outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (sample == null) {
                // Not drawn (yet, or a style that draws nothing in this state): its name.
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                // At the status bar's own size, as on the phone (same display, same pixels);
                // scaled down only when too wide for the tile.
                Image(
                    sample.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

private val TILE_WIDTH = 104.dp
private val TILE_HEIGHT = 56.dp
