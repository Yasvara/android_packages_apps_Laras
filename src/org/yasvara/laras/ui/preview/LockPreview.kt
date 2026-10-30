/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.preview

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.internal.R as InternalR
import com.android.internal.policy.SystemBarUtils
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.theme.emphasized

/**
 * A live, tappable lock screen. SystemUI renders the keyguard itself (status bar, clock, date,
 * fingerprint sensor and shortcuts) over the lock wallpaper, so the canvas is the lock screen,
 * not a drawing of it. Laras adds only what a lock screen at rest cannot show on its own: an
 * example notification, and the tap targets that pick the [Element] to edit.
 */
@Composable
fun LockPreview(
    prefs: PrefSnapshot,
    device: DeviceState,
    view: LockView,
    selected: Element?,
    onSelect: (Element?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = LocalKeyguardRenderer.current
    // As on the real lock screen, waiting notifications keep the clock small.
    val showNotifications = view == LockView.NOTIFICATIONS && prefs[Prefs.lockNotifications]
    val dynamicClock = prefs[Prefs.largeClock] && !showNotifications
    SideEffect { renderer.setDynamicClock(dynamicClock) }

    val metrics = rememberKeyguardMetrics()
    val corner = with(LocalDensity.current) { CanvasCorner.toPx() }
    // Anywhere outside an element stands for the surface as a whole.
    val canvasTap = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(CanvasCorner))
            .clickable(canvasTap, indication = null) { onSelect(Element.LOCK_WALLPAPER) }
    ) {
        val scale = constraints.maxWidth / device.displaySize.width.toFloat()
        KeyguardSurface(
            renderer = renderer,
            wallpaper = device.wallpaper,
            displaySize = device.displaySize,
            scale = scale,
            cornerRadius = corner,
            modifier = Modifier.fillMaxSize(),
        )
        FixedScale(device.displaySize, scale) {
            CompositionLocalProvider(LocalCanvasScale provides scale) {
                // SystemUI reports where notifications start once it has laid the preview out.
                val notificationTop = renderer.notificationTop?.let { with(LocalDensity.current) { it.toDp() } }
                Column(Modifier.fillMaxSize()) {
                    // The status bar belongs to the notification shade, where all of it shows.
                    Spacer(Modifier.fillMaxWidth().height(metrics.statusBar))
                    Spacer(
                        Modifier.fillMaxWidth()
                            .then(
                                if (dynamicClock || notificationTop == null) Modifier.weight(1f)
                                else Modifier.height(notificationTop - metrics.statusBar)
                            )
                            .target(Element.LOCK_CLOCK, selected, onSelect)
                    )
                    if (notificationTop != null && view == LockView.NOTIFICATIONS) {
                        Notifications(
                            shown = showNotifications,
                            private = prefs[Prefs.lockPrivateNotifications],
                            hintWhenHidden = selected == Element.LOCK_NOTIFICATIONS,
                            metrics = metrics,
                            modifier = Modifier.target(Element.LOCK_NOTIFICATIONS, selected, onSelect),
                        )
                    }
                }
            }
        }
    }
}

internal val CanvasCorner = 24.dp

/** How much the display-sized canvas is shrunk, so outlines can stay a constant on-screen width. */
private val LocalCanvasScale = compositionLocalOf { 1f }

/** SystemUI's own keyguard dimensions, read from its resources instead of copied. */
@Immutable
private data class KeyguardMetrics(
    val statusBar: Dp,
    val notificationCorner: Dp,
    val notificationMargin: Dp,
    val notificationBackground: Color,
    val notificationTitle: Color,
    val notificationText: Color,
)

@Composable
private fun rememberKeyguardMetrics(): KeyguardMetrics {
    val context = LocalContext.current
    val density = LocalDensity.current
    return remember(context, density) {
        val systemUi = SystemUiResources(context)
        with(density) {
            KeyguardMetrics(
                statusBar = SystemBarUtils.getStatusBarHeight(context).toDp(),
                notificationCorner = systemUi.dimen("notification_corner_radius"),
                notificationMargin = systemUi.dimen("notification_side_paddings_single"),
                // The framework colours SystemUI paints notification rows with, so the user's
                // theme overlays (such as pure black) apply here too.
                notificationBackground = Color(context.getColor(InternalR.color.materialColorSurfaceContainerHigh)),
                notificationTitle = Color(context.getColor(InternalR.color.materialColorOnSurface)),
                notificationText = Color(context.getColor(InternalR.color.materialColorOnSurfaceVariant)),
            )
        }
    }
}

private class SystemUiResources(context: Context) {
    private val density = context.resources.displayMetrics.density
    private val resources = runCatching {
        context.packageManager.getResourcesForApplication(SYSTEMUI)
    }.onFailure { Log.w("Laras", "SystemUI resources unavailable", it) }.getOrNull()

    fun dimen(name: String): Dp {
        val res = resources ?: return 0.dp
        val id = res.getIdentifier(name, "dimen", SYSTEMUI)
        return if (id == 0) 0.dp else (res.getDimension(id) / density).dp
    }

    private companion object {
        const val SYSTEMUI = "com.android.systemui"
    }
}

/** Lays [content] out at the display's own size and scales it uniformly by [scale]. */
@Composable
private fun FixedScale(size: IntSize, scale: Float, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints.fixed(size.width, size.height))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

/**
 * Makes a region of the canvas select [element]. The selected region is outlined and the rest
 * step back under a scrim, which also covers the parts SystemUI draws.
 */
@Composable
private fun Modifier.target(element: Element, selected: Element?, onSelect: (Element?) -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isSelected = selected == element
    val outline by animateFloatAsState(if (isSelected) 1f else 0f, emphasized(), label = "outline")
    val scrim by animateFloatAsState(
        if (selected == null || isSelected) 0f else 0.55f, emphasized(), label = "scrim",
    )
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, emphasized(200), label = "press")
    val scale = LocalCanvasScale.current
    val label = stringResource(element.title)
    return this
        .graphicsLayer {
            scaleX = press
            scaleY = press
        }
        .drawWithContent {
            drawContent()
            val radius = CornerRadius(12.dp.toPx() / scale)
            if (scrim > 0f) drawRoundRect(Color.Black.copy(alpha = scrim), cornerRadius = radius)
            if (outline > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f * outline),
                    cornerRadius = radius,
                    style = Stroke(width = 2.dp.toPx() / scale),
                )
            }
        }
        .clickable(interaction, indication = null, onClickLabel = label) { onSelect(element) }
}

@Composable
private fun Notifications(
    shown: Boolean,
    private: Boolean,
    hintWhenHidden: Boolean,
    metrics: KeyguardMetrics,
    modifier: Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            shown,
            enter = fadeIn(emphasized()) + expandVertically(emphasized()),
            exit = fadeOut(emphasized(200)) + shrinkVertically(emphasized()),
        ) {
            NotificationCard(private, metrics)
        }
        // A lock screen without notifications shows nothing; the hint only marks where they go.
        AnimatedVisibility(
            !shown && hintWhenHidden,
            enter = fadeIn(emphasized()),
            exit = fadeOut(emphasized(150)),
        ) {
            Icon(
                Icons.Rounded.NotificationsOff, stringResource(R.string.element_notifications),
                Modifier.fillMaxWidth().padding(vertical = 24.dp).size(32.dp),
                tint = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

/** An example notification, laid out and coloured like SystemUI's lock screen rows. */
@Composable
private fun NotificationCard(private: Boolean, metrics: KeyguardMetrics) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.padding(horizontal = metrics.notificationMargin).fillMaxWidth()
            .clip(RoundedCornerShape(metrics.notificationCorner))
            .background(metrics.notificationBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(20.dp),
                tint = colors.onPrimary,
            )
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(
                stringResource(R.string.preview_notification_title),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = metrics.notificationTitle,
            )
            AnimatedContent(private, label = "private") { show ->
                Text(
                    stringResource(
                        if (show) R.string.preview_notification_body else R.string.preview_notification_hidden
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = metrics.notificationText,
                )
            }
        }
    }
}
