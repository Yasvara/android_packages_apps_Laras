/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.icons

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.data.LauncherIcons
import org.yasvara.laras.data.SettingsRepository
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.groupedShape

/**
 * The app icons: the shape, the icon pack and themed icons, in one sheet, as
 * the interplay table puts them together. Each choice applies at once; the home canvas above
 * shows the launcher's own drawing of it. Everything is re-read when the theme changes
 * from elsewhere: [theme], the stored `yasvara_theme`, keys the re-read.
 */
@Composable
fun IconsEditor(theme: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icons = remember(context) { LauncherIcons(context, SettingsRepository(context.contentResolver)) }
    val scope = rememberCoroutineScope()
    var shapes by remember { mutableStateOf<List<LauncherIcons.Shape>>(emptyList()) }
    var packs by remember { mutableStateOf<List<LauncherIcons.Pack>>(emptyList()) }
    var choice by remember { mutableStateOf(LauncherIcons.Choice(null, null)) }
    var themed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(icons, theme) {
        withContext(Dispatchers.IO) {
            val read = icons.choice()
            val readShapes = icons.shapes()
            val readPacks = icons.packs()
            val readThemed = icons.themedIcons()
            withContext(Dispatchers.Main) {
                choice = read
                shapes = readShapes
                packs = readPacks
                themed = readThemed
            }
        }
    }
    fun apply(block: LauncherIcons.() -> Unit, local: () -> Unit) {
        local()
        scope.launch(Dispatchers.IO) { icons.block() }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val packChosen = choice.pack != null
        Group(0, 3) {
            Header(
                R.string.icons_shape,
                if (packChosen) R.string.icons_shape_with_pack else R.string.icons_shape_summary,
                onReset = choice.shape?.let { { apply({ setShape(null) }) { choice = choice.copy(shape = null) } } },
            )
            // The launcher's own pick, else its first shape, which it falls back to.
            val current = choice.shape ?: (shapes.firstOrNull { it.current } ?: shapes.firstOrNull())?.key
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shapes, key = { it.key }) { shape ->
                    ShapeTile(shape, shape.key == current) {
                        apply({ setShape(shape) }) { choice = choice.copy(shape = shape.key) }
                    }
                }
            }
        }
        Group(1, 3) {
            Header(R.string.icons_pack, R.string.icons_pack_summary)
            PackRow(stringResource(R.string.icons_pack_none), null, choice.pack == null) {
                apply({ setPack(null) }) { choice = choice.copy(pack = null) }
            }
            packs.forEach { pack ->
                PackRow(pack.label, pack, choice.pack == pack.packageName) {
                    apply({ setPack(pack.packageName) }) { choice = choice.copy(pack = pack.packageName) }
                }
            }
            choice.pack?.takeIf { chosen -> packs.none { it.packageName == chosen } }?.let {
                // The chosen pack was uninstalled: the launcher draws the apps' own icons.
                Note(stringResource(R.string.icons_pack_missing, it))
            }
            if (packs.isEmpty()) Note(stringResource(R.string.icons_pack_empty))
        }
        Group(2, 3) {
            val on = themed == true
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(on, enabled = themed != null, role = Role.Switch) { value ->
                        apply({ setThemedIcons(value) }) { themed = value }
                    }
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.icons_themed), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (packChosen) R.string.icons_themed_with_pack else R.string.icons_themed_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CheckSwitch(on, enabled = themed != null)
            }
        }
    }
}

@Composable
private fun Group(index: Int, count: Int, content: @Composable () -> Unit) {
    Surface(shape = groupedShape(index, count), color = MaterialTheme.colorScheme.surfaceBright) {
        Column { content() }
    }
}

@Composable
private fun Header(title: Int, summary: Int, onReset: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        onReset?.let {
            IconButton(it) { Icon(Icons.Rounded.Restore, stringResource(R.string.changed_reset)) }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
    )
}

/** A shape as the launcher draws it: its own path, filled in the accent. */
@Composable
private fun ShapeTile(shape: LauncherIcons.Shape, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val ring by animateColorAsState(
        if (selected) colors.primary else Color.Transparent, motion.fastEffectsSpec(), label = "ring",
    )
    val inset by animateDpAsState(if (selected) 6.dp else 0.dp, motion.fastSpatialSpec(), label = "inset")
    val path = remember(shape.path) {
        runCatching { PathParser.createPathFromPathData(shape.path).asComposePath() }.getOrNull()
    }
    Column(
        Modifier.width(76.dp).clip(MaterialTheme.shapes.medium)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            val fill = if (selected) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.38f)
            Canvas(Modifier.size(52.dp - inset.coerceAtLeast(0.dp))) {
                val outline = path ?: return@Canvas
                val scaled = androidx.compose.ui.graphics.Path().apply {
                    addPath(outline)
                    transform(Matrix().apply { scale(size.width / 100f, size.height / 100f) })
                }
                drawPath(scaled, fill)
            }
        }
        Text(
            shape.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            // Names like "8-leaf clover" fit on two lines rather than being cut.
            maxLines = 2,
            minLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun PackRow(
    label: String,
    pack: LauncherIcons.Pack?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val icon = remember(pack) { pack?.icon?.toBitmap(96, 96)?.asImageBitmap() }
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, null, Modifier.size(40.dp))
            else Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RadioButton(selected, onClick = null)
    }
}
