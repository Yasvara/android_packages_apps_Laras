/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Canonical Material 3 Expressive shape scale for Laras. */
object LarasShapes {
    val Hero: Shape = RoundedCornerShape(28.dp)
    val Card: Shape = RoundedCornerShape(24.dp)
    val Inset: Shape = RoundedCornerShape(16.dp)
    val Tag: Shape = RoundedCornerShape(8.dp)
    val Pill: Shape = CircleShape

    private val GroupOuter = 24.dp
    private val GroupInner = 6.dp

    /** Corner shape for item [index] of [count] in a grouped card list. */
    fun grouped(index: Int, count: Int): Shape = RoundedCornerShape(
        topStart = if (index == 0) GroupOuter else GroupInner,
        topEnd = if (index == 0) GroupOuter else GroupInner,
        bottomStart = if (index == count - 1) GroupOuter else GroupInner,
        bottomEnd = if (index == count - 1) GroupOuter else GroupInner,
    )
}

/** Canonical spacing tokens across all Laras screens. */
object LarasSpacing {
    val ItemGap = 2.dp
    val ElementGap = 8.dp
    val GroupGap = 12.dp
    val SectionGap = 20.dp
    val ScreenHorizontal = 16.dp
    val CardHorizontal = 20.dp
    val CardVertical = 14.dp
}

/**
 * Consistent 3-tier M3 surface hierarchy across Light, Dark, and Pure Black themes:
 * [pageCanvas] -> [card] -> [nestedCard], and [sheetContainer] -> [sheetCard].
 *
 * Pure black keeps the container ladder elevated, so the canvas drops to the black surface and
 * every tier steps down one level beneath it.
 */
object LarasSurfaces {
    /** True when the scheme in effect (system or draft) is the pure black dark scheme. */
    val isPureBlack: Boolean
        @Composable get() = MaterialTheme.colorScheme.surface == Color.Black

    val pageCanvas: Color
        @Composable get() = MaterialTheme.colorScheme.let {
            if (isPureBlack) it.surface else it.surfaceContainer
        }

    val card: Color
        @Composable get() = MaterialTheme.colorScheme.let {
            if (isPureBlack) it.surfaceContainerHigh else it.surfaceBright
        }

    val nestedCard: Color
        @Composable get() = MaterialTheme.colorScheme.let {
            if (isPureBlack) it.surfaceContainerHighest else it.surfaceContainerHigh
        }

    val sheetContainer: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

    val sheetCard: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
}
