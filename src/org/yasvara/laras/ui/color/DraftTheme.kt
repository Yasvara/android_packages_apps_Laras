/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.color

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import org.yasvara.monet.YasResolvedScheme

/** A role's colour in [scheme] for the current mode, or [fallback] if the scheme lacks it. */
fun YasResolvedScheme.color(role: String, dark: Boolean, fallback: Color): Color =
    role(role, dark)?.let { Color(it) } ?: fallback

/**
 * Laras' own colours while a draft is being edited: the screen shows the draft in its own UI at
 * once, resolved by the same engine SystemUI applies with. The system itself changes on Apply.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DraftTheme(scheme: YasResolvedScheme?, content: @Composable () -> Unit) {
    if (scheme == null) {
        content()
        return
    }
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    val colors = remember(scheme, dark, base) { scheme.toColorScheme(dark, base) }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MaterialTheme.motionScheme,
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}

private fun YasResolvedScheme.toColorScheme(dark: Boolean, base: ColorScheme): ColorScheme {
    fun c(role: String, fallback: Color) = color(role, dark, fallback)
    return base.copy(
        primary = c("primary", base.primary),
        onPrimary = c("on_primary", base.onPrimary),
        primaryContainer = c("primary_container", base.primaryContainer),
        onPrimaryContainer = c("on_primary_container", base.onPrimaryContainer),
        inversePrimary = c("inverse_primary", base.inversePrimary),
        secondary = c("secondary", base.secondary),
        onSecondary = c("on_secondary", base.onSecondary),
        secondaryContainer = c("secondary_container", base.secondaryContainer),
        onSecondaryContainer = c("on_secondary_container", base.onSecondaryContainer),
        tertiary = c("tertiary", base.tertiary),
        onTertiary = c("on_tertiary", base.onTertiary),
        tertiaryContainer = c("tertiary_container", base.tertiaryContainer),
        onTertiaryContainer = c("on_tertiary_container", base.onTertiaryContainer),
        background = c("background", base.background),
        onBackground = c("on_background", base.onBackground),
        surface = c("surface", base.surface),
        onSurface = c("on_surface", base.onSurface),
        surfaceVariant = c("surface_variant", base.surfaceVariant),
        onSurfaceVariant = c("on_surface_variant", base.onSurfaceVariant),
        surfaceTint = c("surface_tint", base.surfaceTint),
        inverseSurface = c("inverse_surface", base.inverseSurface),
        inverseOnSurface = c("inverse_on_surface", base.inverseOnSurface),
        error = c("error", base.error),
        onError = c("on_error", base.onError),
        errorContainer = c("error_container", base.errorContainer),
        onErrorContainer = c("on_error_container", base.onErrorContainer),
        outline = c("outline", base.outline),
        outlineVariant = c("outline_variant", base.outlineVariant),
        scrim = c("scrim", base.scrim),
        surfaceBright = c("surface_bright", base.surfaceBright),
        surfaceDim = c("surface_dim", base.surfaceDim),
        surfaceContainer = c("surface_container", base.surfaceContainer),
        surfaceContainerHigh = c("surface_container_high", base.surfaceContainerHigh),
        surfaceContainerHighest = c("surface_container_highest", base.surfaceContainerHighest),
        surfaceContainerLow = c("surface_container_low", base.surfaceContainerLow),
        surfaceContainerLowest = c("surface_container_lowest", base.surfaceContainerLowest),
        primaryFixed = c("primary_fixed", base.primaryFixed),
        primaryFixedDim = c("primary_fixed_dim", base.primaryFixedDim),
        onPrimaryFixed = c("on_primary_fixed", base.onPrimaryFixed),
        onPrimaryFixedVariant = c("on_primary_fixed_variant", base.onPrimaryFixedVariant),
        secondaryFixed = c("secondary_fixed", base.secondaryFixed),
        secondaryFixedDim = c("secondary_fixed_dim", base.secondaryFixedDim),
        onSecondaryFixed = c("on_secondary_fixed", base.onSecondaryFixed),
        onSecondaryFixedVariant = c("on_secondary_fixed_variant", base.onSecondaryFixedVariant),
        tertiaryFixed = c("tertiary_fixed", base.tertiaryFixed),
        tertiaryFixedDim = c("tertiary_fixed_dim", base.tertiaryFixedDim),
        onTertiaryFixed = c("on_tertiary_fixed", base.onTertiaryFixed),
        onTertiaryFixedVariant = c("on_tertiary_fixed_variant", base.onTertiaryFixedVariant),
    )
}
