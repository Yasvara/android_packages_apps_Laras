/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Brightness2
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.RoundedPolygon
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.catalog.Section
import org.yasvara.laras.catalog.Surface
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.ui.glyph.GroupGlyph

val Surface.icon: ImageVector
    get() = when (this) {
        Surface.LOCK_SCREEN -> Icons.Rounded.Lock
        Surface.HOME_SCREEN -> Icons.Rounded.Home
        Surface.SHADE -> Icons.Rounded.Tune
        Surface.ALWAYS_ON -> Icons.Rounded.Brightness2
    }

/** A section's name as shown out of context: an element is named with its surface. */
@Composable
fun Section.label(): String = when (this) {
    is Element -> stringResource(R.string.section_path, stringResource(surface.title), stringResource(title))
    is SystemGroup -> stringResource(title)
}

val Element.icon: ImageVector
    get() = when (this) {
        Element.LOCK_CLOCK -> Icons.Rounded.Schedule
        Element.LOCK_WALLPAPER -> Icons.Rounded.Wallpaper
        Element.LOCK_NOTIFICATIONS -> Icons.Rounded.Notifications
        Element.STATUS_BAR_CLOCK -> Icons.Rounded.Schedule
        Element.STATUS_BAR_ICONS -> Icons.Rounded.SignalCellularAlt
        Element.STATUS_BAR_BATTERY -> Icons.Rounded.BatteryFull
        Element.QUICK_SETTINGS -> Icons.Rounded.GridView
        Element.HOME_ICONS -> Icons.Rounded.Apps
        Element.AOD_DISPLAY -> Icons.Rounded.Brightness2
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val SystemGroup.polygon: RoundedPolygon
    get() = when (this) {
        SystemGroup.STYLE -> MaterialShapes.Flower
        SystemGroup.DISPLAY -> MaterialShapes.Sunny
        SystemGroup.SOUND -> MaterialShapes.Cookie6Sided
        SystemGroup.GESTURES -> MaterialShapes.Pill
        SystemGroup.POWER -> MaterialShapes.Cookie9Sided
        SystemGroup.CONNECTIVITY -> MaterialShapes.Arch
        SystemGroup.MULTITASKING -> MaterialShapes.Slanted
        SystemGroup.PRIVACY -> MaterialShapes.Circle
        SystemGroup.SUPERUSER -> MaterialShapes.Pentagon
        SystemGroup.INTEGRITY -> MaterialShapes.Gem
        SystemGroup.APPS -> MaterialShapes.Square
        SystemGroup.MODULES -> MaterialShapes.Clover4Leaf
        SystemGroup.LAB -> MaterialShapes.Puffy
        SystemGroup.PENTAS -> MaterialShapes.Cookie9Sided
    }

/** A group's colour pair: its badge, and the tint of its home card. */
data class BadgeTone(val container: Color, val content: Color)

@Composable
fun SystemGroup.tone(): BadgeTone {
    val c = MaterialTheme.colorScheme
    if (planned) return BadgeTone(c.surfaceContainerHighest, c.onSurfaceVariant)
    return when (ordinal % 3) {
        0 -> BadgeTone(c.primaryContainer, c.onPrimaryContainer)
        1 -> BadgeTone(c.secondaryContainer, c.onSecondaryContainer)
        else -> BadgeTone(c.tertiaryContainer, c.onTertiaryContainer)
    }
}

/**
 * A group's animated glyph inside its own expressive shape. When [interaction] is given the
 * glyph replays each time it is pressed, so the icon answers the touch before the page opens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupBadge(
    group: SystemGroup,
    size: Dp,
    modifier: Modifier = Modifier,
    interaction: MutableInteractionSource? = null,
) {
    var replay by remember { mutableIntStateOf(0) }
    if (interaction != null) {
        LaunchedEffect(interaction) {
            interaction.interactions.collect { if (it is PressInteraction.Press) replay++ }
        }
    }
    val tone = group.tone()
    Box(
        modifier.size(size).clip(group.polygon.toShape()).background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        GroupGlyph(group, tone.content, Modifier.size(size * 0.55f), replay)
    }
}
