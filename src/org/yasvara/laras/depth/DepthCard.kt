/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Entry
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.components.ChangedDot
import org.yasvara.laras.ui.components.CheckSwitch

/**
 * The lock screen editor's Depth card (docs/specs/0013 §11, option B): the depth switch, every
 * saved picture as a strip with the lock screen's own marked, and Edit depth. A saved picture
 * opens a sheet to set it as the wallpaper with its depth, edit it, or delete it (with undo).
 * [entry] is the depth switch's catalog entry, shown here instead of in the entry list.
 */
@Composable
fun DepthCard(entry: Entry, prefs: PrefSnapshot, editor: PrefEditor, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { DepthLibrary(context) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose {}
    }
    var saved by remember { mutableStateOf<List<Saved>>(emptyList()) }
    var current by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<Saved?>(null) }
    // The lock screen's picture while its depth is being made (docs/specs/0013 §12.4).
    val buildState by DepthBuildService.state.collectAsState()
    val making = buildState.takeIf {
        it.sha == current && (it is DepthBuildService.State.Running || it is DepthBuildService.State.Waiting)
    }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(making != null) {
        if (making != null && preview == null) {
            preview = withContext(Dispatchers.IO) { WallpaperPicture.currentPreview(context, PREVIEW_PX)?.asImageBitmap() }
        }
    }
    // A finished build is saved as a project: show it.
    LaunchedEffect(buildState) {
        if (buildState is DepthBuildService.State.Done) resumes++
    }
    var deleting by remember { mutableStateOf<Saved?>(null) }

    LaunchedEffect(resumes) {
        val (list, sha) = withContext(Dispatchers.IO) {
            library.summaries().map { Saved(it, BitmapFactory.decodeFile(it.thumb.path)?.asImageBitmap()) } to
                WallpaperPicture.currentSha(context)
        }
        saved = list
        current = sha
    }

    // A delete waits a few seconds for Undo, and happens anyway when the editor closes first.
    val commit: (Saved) -> Unit = { item ->
        delete(context.applicationContext, item.summary.fingerprint.sha)
        saved = saved - item
    }
    val pending by rememberUpdatedState(deleting)
    LaunchedEffect(deleting) {
        val item = deleting ?: return@LaunchedEffect
        delay(UNDO_MS)
        commit(item)
        deleting = null
    }
    DisposableEffect(Unit) {
        onDispose { pending?.let { delete(context.applicationContext, it.summary.fingerprint.sha) } }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val enabled = prefs[Prefs.depthEnabled]
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(enabled, role = Role.Switch) { editor.set(Prefs.depthEnabled, it) }
                    .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
                        ChangedDot(prefs.isChanged(Prefs.depthEnabled), Modifier.padding(start = 8.dp))
                    }
                    val status = when (val m = making) {
                        is DepthBuildService.State.Running ->
                            stringResource(R.string.depth_making, m.step * 100 / m.steps.coerceAtLeast(1))
                        is DepthBuildService.State.Waiting -> stringResource(R.string.depth_waiting_title)
                        else -> null
                    }
                    (status ?: entry.summary?.let { stringResource(it) })?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                CheckSwitch(enabled)
            }

            val shown = saved.filter { it != deleting }
            val building = making != null && shown.none { it.summary.fingerprint.sha == current }
            if (shown.isNotEmpty() || building) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (building) item(key = "building") {
                        Making(
                            preview,
                            (making as? DepthBuildService.State.Running)?.let { it.step / it.steps.coerceAtLeast(1).toFloat() },
                            onClick = { context.startActivity(Intent(context, DepthStudioActivity::class.java)) },
                        )
                    }
                    items(shown, key = { it.summary.fingerprint.sha }) { item ->
                        Thumb(
                            item,
                            current = item.summary.fingerprint.sha == current,
                            width = 62.dp,
                            height = 110.dp,
                            onClick = { open = item },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = deleting != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.depth_deleted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.depth_undo)) }
                }
            }

            FilledTonalButton(
                onClick = { context.startActivity(Intent(context, DepthStudioActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                Text(stringResource(R.string.depth_edit), Modifier.padding(start = 8.dp))
            }
        }
    }

    open?.let { item ->
        SavedSheet(
            item,
            current = item.summary.fingerprint.sha == current,
            onDismiss = { open = null },
            onSet = { which ->
                open = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { setWallpaper(context, library, item.summary, which) }
                    if (ok) {
                        editor.set(Prefs.depthEnabled, true)
                        current = item.summary.fingerprint.sha
                    } else {
                        Toast.makeText(context, R.string.depth_set_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onEdit = {
                open = null
                context.startActivity(
                    Intent(context, DepthStudioActivity::class.java)
                        .putExtra(DepthStudioActivity.EXTRA_FINGERPRINT, item.summary.fingerprint.sha),
                )
            },
            onDelete = {
                open = null
                deleting?.let(commit)
                deleting = item
            },
        )
    }
}

/** The lock screen's picture while its depth is made: dimmed, with how far the build is. */
@Composable
private fun Making(preview: ImageBitmap?, progress: Float?, onClick: () -> Unit) {
    val label = stringResource(R.string.depth_making_tile)
    Box(
        Modifier.size(62.dp, 110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        preview?.let { Image(it, null, Modifier.size(62.dp, 110.dp), contentScale = ContentScale.Crop, alpha = 0.45f) }
        if (progress != null) {
            CircularProgressIndicator(progress = { progress }, Modifier.size(32.dp), strokeWidth = 4.dp)
        } else {
            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 4.dp)
        }
    }
}

/** A saved picture with its thumbnail decoded. */
private class Saved(val summary: DepthLibrary.Summary, val thumb: ImageBitmap?)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Thumb(item: Saved, current: Boolean, width: Dp, height: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width, height)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    ) {
        item.thumb?.let {
            Image(it, null, Modifier.size(width, height), contentScale = ContentScale.Crop)
        }
        if (current) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp)
                    .clip(MaterialShapes.SoftBurst.toShape())
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Check, stringResource(R.string.depth_on_lock),
                    Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedSheet(
    item: Saved,
    current: Boolean,
    onDismiss: () -> Unit,
    onSet: (which: Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val summary = item.summary
    val date = remember(summary) {
        DateUtils.formatDateTime(context, summary.modified, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                Thumb(item, current, width = 132.dp, height = 232.dp, onClick = {})
                Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.depth_saved_on, date), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.depth_size, summary.pictureWidth, summary.pictureHeight),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        pluralStringResource(R.plurals.depth_steps, summary.steps, summary.steps),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSet(WallpaperManager.FLAG_LOCK) },
                    enabled = !current,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Smartphone, null, Modifier.size(18.dp))
                    Text(
                        stringResource(if (current) R.string.depth_on_lock else R.string.depth_set_lock),
                        Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(
                    onClick = { onSet(WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.depth_set_both)) }
                FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.depth_edit), Modifier.padding(start = 8.dp))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.depth_delete), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * Sets a saved picture as the wallpaper ([which]), its depth handed to SystemUI first so the
 * lock screen finds it the moment the picture changes.
 */
private fun setWallpaper(context: Context, library: DepthLibrary, summary: DepthLibrary.Summary, which: Int): Boolean {
    val sha = summary.fingerprint.sha
    val picture = library.picture(sha).takeIf { it.isFile } ?: return false
    if (!DepthSync.push(context, summary, library)) return false
    return runCatching {
        picture.inputStream().use { WallpaperManager.getInstance(context).setStream(it, null, true, which) } > 0
    }.onFailure { Log.w(TAG, "Could not set the wallpaper", it) }.getOrDefault(false)
}

/** Forgets a saved picture in Laras and on the lock screen, off the main thread. */
private fun delete(context: Context, sha: String) {
    Thread {
        DepthLibrary(context).delete(sha)
        DepthSync.remove(context, sha)
    }.start()
}

private const val TAG = "DepthCard"
private const val UNDO_MS = 6000L
private const val PREVIEW_PX = 186
