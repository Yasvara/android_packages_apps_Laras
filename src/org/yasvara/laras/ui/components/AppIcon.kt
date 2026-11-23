/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, ImageBitmap>(192)

/** Decodes and caches an app icon on [Dispatchers.IO] for reuse across lists and cards. */
suspend fun loadAppIcon(context: Context, packageName: String, px: Int = 112): ImageBitmap? {
    iconCache[packageName]?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(px, px)
                .asImageBitmap()
                .also { iconCache.put(packageName, it) }
        }.getOrNull()
    }
}

/** A launcher icon, decoded off the main thread and cached; tonal placeholder until ready. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(iconCache[packageName], packageName) {
        if (value == null) value = loadAppIcon(context, packageName)
    }
    val img = bitmap
    if (img != null) {
        Image(img, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}
