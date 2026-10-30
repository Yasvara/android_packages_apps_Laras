/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.catalog.Surface
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.components.icon

/**
 * Which of the lock screen's two looks the canvas shows. It changes only the preview, never a
 * setting: the real lock screen picks its look from whether notifications are waiting.
 */
enum class LockView(override val value: Int, override val label: Int) : ChoiceOption {
    NOTIFICATIONS(0, R.string.lock_view_notifications),
    CLEAR(1, R.string.lock_view_clear),
}

/**
 * The live canvas for [surface]. [focus] is the element being edited, if any; tapping an
 * element reports it through [onSelect], and tapping a canvas without elements reports null.
 */
@Composable
fun SurfaceCanvas(
    surface: Surface,
    prefs: PrefSnapshot,
    device: DeviceState,
    lockView: LockView,
    focus: Element?,
    onSelect: (Element?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (surface) {
        Surface.LOCK_SCREEN -> LockPreview(prefs, device, lockView, focus, onSelect, modifier)
        Surface.HOME_SCREEN -> HomePreview(device, onSelect, modifier)
        Surface.SHADE -> ShadePreview(
            device, focus, onSelect, modifier,
            // The battery samples show the percentage as set.
            samplesKey = prefs[Prefs.batteryPercent],
        )
        Surface.ALWAYS_ON -> PendingCanvas(surface, device, modifier.clickable { onSelect(null) })
    }
}

/** Stands in for a surface whose SystemUI renderer has not landed yet, and says so. */
@Composable
private fun PendingCanvas(surface: Surface, device: DeviceState, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
        device.wallpaper?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(surface.icon, null, Modifier.size(40.dp), tint = Color.White)
            Text(
                stringResource(R.string.surface_preview_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
