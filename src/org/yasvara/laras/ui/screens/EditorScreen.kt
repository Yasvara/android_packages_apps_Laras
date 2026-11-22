/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.screens

import org.yasvara.laras.depth.DepthCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.catalog.StatusIconSet
import org.yasvara.laras.catalog.Surface
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.EntryList
import org.yasvara.laras.ui.components.icon
import org.yasvara.laras.ui.icons.IconsEditor
import org.yasvara.laras.ui.preview.DeviceState
import org.yasvara.laras.ui.preview.LocalStatusBarSamples
import org.yasvara.laras.ui.preview.LockView
import org.yasvara.laras.ui.preview.StatusBarSamples
import org.yasvara.laras.ui.preview.SurfaceCanvas
import org.yasvara.laras.ui.sharedCanvas
import org.yasvara.laras.ui.theme.emphasized

/**
 * Edits one surface as a whole: its live canvas stays in view above a sheet listing every
 * element of that surface. Tapping an element on the canvas brings its settings into view and
 * marks it on both; dragging the sheet down or pressing back closes the editor.
 */
@Composable
fun EditorScreen(
    surface: Surface,
    initialFocus: Element?,
    prefs: PrefSnapshot,
    device: DeviceState,
    editor: PrefEditor,
    lockView: LockView,
    onLockView: (LockView) -> Unit,
    onClose: () -> Unit,
) {
    var focus by rememberSaveable { mutableStateOf(initialFocus) }
    val elements = surface.elements
    val list = rememberLazyListState()
    LaunchedEffect(focus) {
        focus?.let { list.animateScrollToItem(elements.indexOf(it)) }
    }
    val navScope = LocalNavAnimatedContentScope.current
    // The canvas draws the pickers' options; the sheet shows them.
    val samples = remember { StatusBarSamples() }

    CompositionLocalProvider(LocalStatusBarSamples provides samples) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            val sheetMax = maxHeight * 0.58f
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SurfaceCanvas(
                        surface = surface,
                        prefs = prefs,
                        device = device,
                        lockView = lockView,
                        focus = focus,
                        onSelect = { focus = it },
                        // A screen is a phone; a bar keeps the width, where its parts are big enough
                        // to tap.
                        modifier = if (surface.canvas.isBar) {
                            Modifier.fillMaxSize().padding(horizontal = 16.dp).sharedCanvas(surface)
                        } else {
                            Modifier.fillMaxHeight()
                                .aspectRatio(device.aspectRatio, matchHeightConstraintsFirst = true)
                                .sharedCanvas(surface)
                        },
                    )
                }
                with(navScope) {
                    Sheet(
                        title = stringResource(surface.title),
                        onClose = onClose,
                        modifier = Modifier.heightIn(max = sheetMax).animateEnterExit(
                            enter = slideInVertically(emphasized()) { it },
                            exit = slideOutVertically(emphasized(250)) { it },
                        ),
                        header = {
                            if (surface == Surface.LOCK_SCREEN) {
                                ChoiceButtons(
                                    options = LockView.entries,
                                    selected = lockView,
                                    onSelect = { onLockView(it as LockView) },
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        },
                    ) {
                        LazyColumn(
                            state = list,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        ) {
                            items(elements, key = { it.name }) { element ->
                                ElementSection(element, element == focus, prefs, editor) { focus = element }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An element's settings under a header that lights up while the element is marked. */
@Composable
private fun ElementSection(
    element: Element,
    focused: Boolean,
    prefs: PrefSnapshot,
    editor: PrefEditor,
    onFocus: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (focused) colors.secondaryContainer else Color.Transparent, emphasized(), label = "header",
    )
    val content by animateColorAsState(
        if (focused) colors.onSecondaryContainer else colors.primary, emphasized(), label = "headerText",
    )
    Column {
        Row(
            Modifier.padding(top = 12.dp, bottom = 8.dp)
                .background(container, CircleShape)
                .clickable(onClick = onFocus)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(element.icon, null, Modifier.size(18.dp), tint = content)
            Text(stringResource(element.title), style = MaterialTheme.typography.titleSmall, color = content)
        }
        // The depth switch lives in the Depth card, with the saved pictures.
        val depthSwitch = element.entries.firstOrNull { it.pref == Prefs.depthEnabled }
        depthSwitch?.let { DepthCard(it, prefs, editor, Modifier.padding(bottom = 8.dp)) }
        if (element == Element.STATUS_BAR_ICONS) ThinIconsSuggestion(prefs, editor)
        if (element == Element.HOME_ICONS) {
            IconsEditor(prefs[Prefs.theme])
        } else if (element.entries.isEmpty()) {
            Text(
                stringResource(R.string.element_pending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        } else {
            EntryList(element.entries.filter { it !== depthSwitch }, prefs, editor)
        }
    }
}

/**
 * With large text, suggests the Thin icon set, whose hairlines stay light when the status bar
 * grows with the font. Gone once Thin is chosen.
 */
@Composable
private fun ThinIconsSuggestion(prefs: PrefSnapshot, editor: PrefEditor) {
    val large = LocalDensity.current.fontScale >= THIN_SUGGESTION_SCALE
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = large && prefs[Prefs.statusIcons] != StatusIconSet.THIN,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Row(
                Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.TextIncrease, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    stringResource(R.string.icons_suggest_thin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                TextButton(onClick = { editor.set(Prefs.statusIcons, StatusIconSet.THIN) }) {
                    Text(stringResource(R.string.icons_use_thin))
                }
            }
        }
    }
}

private const val THIN_SUGGESTION_SCALE = 1.3f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }
    val drag = rememberDraggableState { delta ->
        scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
    }

    Surface(
        modifier = modifier.fillMaxWidth().graphicsLayer { translationY = offset.value },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Column(
                Modifier.fillMaxWidth().draggable(
                    drag, Orientation.Vertical,
                    onDragStopped = {
                        if (offset.value > dismissAt) onClose() else offset.animateTo(0f, emphasized())
                    },
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally))
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClose) {
                        Icon(Icons.Rounded.Check, stringResource(R.string.done))
                    }
                }
                header()
            }
            content()
        }
    }
}
