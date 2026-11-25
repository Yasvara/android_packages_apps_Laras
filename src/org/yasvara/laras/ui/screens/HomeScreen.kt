/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.screens

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Catalog
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.catalog.Surface
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.Route
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.EntryList
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasNavRow
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.SafeModeBanner
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.rememberInteraction
import org.yasvara.laras.ui.components.rememberPressScale
import org.yasvara.laras.ui.components.tone
import org.yasvara.laras.ui.glyph.GroupArt
import org.yasvara.laras.ui.glyph.HomeFacts
import org.yasvara.laras.ui.glyph.PENTAS_PACKAGE
import org.yasvara.laras.ui.glyph.artKey
import org.yasvara.laras.ui.glyph.labelStart
import org.yasvara.laras.ui.glyph.relaxedGuards
import org.yasvara.laras.ui.glyph.rememberHomeFacts
import org.yasvara.laras.ui.glyph.status
import org.yasvara.laras.ui.preview.DeviceState
import org.yasvara.laras.ui.preview.LockView
import org.yasvara.laras.ui.preview.SurfaceCanvas
import org.yasvara.laras.ui.sharedCanvas
import org.yasvara.laras.ui.theme.EmphasizedDecelerate
import org.yasvara.laras.ui.theme.EmphasizedEasing
import org.yasvara.laras.ui.theme.emphasized

private data class FeatureDestination(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val route: Route,
    val aliases: List<String> = emptyList(),
)

/** Standalone feature routes indexed for Home global search alongside Catalog preferences. */
private val SEARCH_DESTINATIONS = listOf(
    FeatureDestination(R.string.color_title, R.string.color_entry_summary, Route.Color, listOf("monet", "palette", "theme")),
    FeatureDestination(R.string.group_integrity, R.string.group_integrity_summary, Route.Group(SystemGroup.INTEGRITY), listOf("pif", "keybox", "play integrity", "strong", "attestation")),
    FeatureDestination(R.string.targets_title, R.string.targets_manage, Route.TargetApps, listOf("trickystore", "keybox")),
    FeatureDestination(R.string.akar_title, R.string.akar_entry_summary, Route.Akar(), listOf("root", "su", "superuser", "shell", "adb")),
    FeatureDestination(R.string.group_modules, R.string.kait_hero_title, Route.Kait(), listOf("kait", "xposed", "lsposed", "module", "repo")),
    FeatureDestination(R.string.visibility_title, R.string.visibility_entry_summary, Route.AppVisibility, listOf("tabir", "hide", "hma", "applist")),
    FeatureDestination(R.string.updates_title, R.string.updates_entry_summary, Route.AppUpdates(), listOf("play store", "rollback", "vault", "pause")),
    FeatureDestination(R.string.lelap_apps_title, R.string.lelap_apps_entry_summary, Route.LelapApps, listOf("lelap", "sleep", "battery", "freeze")),
    FeatureDestination(R.string.lelap_cuts_title, R.string.lelap_cuts_entry_summary, Route.LelapCuts, listOf("lelap", "telemetry", "tracking", "gms")),
    FeatureDestination(R.string.padat_title, R.string.padat_entry_summary, Route.Padat(), listOf("compress", "f2fs", "zstd", "lz4", "storage")),
    FeatureDestination(R.string.pasang_title, R.string.pasang_entry_summary, Route.Pasang, listOf("ota", "update", "system update", "pasang", "slot", "firmware", "overlay")),
    FeatureDestination(R.string.saring_title, R.string.saring_entry_summary, Route.Saring, listOf("adblock", "dns", "tracker", "doh", "safesearch")),
    FeatureDestination(R.string.wifi_region_title, R.string.group_connectivity_summary, Route.WifiRegion, listOf("country", "channel", "5ghz", "6ghz")),
    FeatureDestination(R.string.nada_title, R.string.nada_entry_summary, Route.Nada, listOf("dsp", "eq", "equalizer", "dolby", "bass", "viper")),
    FeatureDestination(R.string.swara_title, R.string.swara_entry_summary, Route.SoundApps, listOf("audio", "volume", "mic", "per-app")),
    FeatureDestination(R.string.raba_title, R.string.raba_entry_summary, Route.Haptics, listOf("haptic", "vibration", "touch")),
    FeatureDestination(R.string.pijar_title, R.string.pijar_entry_summary, Route.Brightness, listOf("brightness", "kecerahan", "hbm", "sunlight", "nits")),
    FeatureDestination(R.string.group_apps, R.string.group_apps_summary, Route.Group(SystemGroup.APPS), listOf("spoof", "photos", "snapchat", "netflix")),
    FeatureDestination(R.string.group_keyboard, R.string.group_keyboard_summary, Route.Group(SystemGroup.KEYBOARD), listOf("ketik", "gboard", "writing tools")),
    FeatureDestination(R.string.group_lab, R.string.group_lab_summary, Route.Group(SystemGroup.LAB), listOf("advanced", "experimental")),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: PrefSnapshot,
    device: DeviceState,
    editor: PrefEditor,
    surface: Surface,
    onSurface: (Surface) -> Unit,
    lockView: LockView,
    onLockView: (LockView) -> Unit,
    onOpen: (Route) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var query by rememberSaveable { mutableStateOf("") }
    val changed = Catalog.prefs.count(prefs::isChanged)
    val facts = rememberHomeFacts(prefs)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = LarasSurfaces.pageCanvas,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                actions = { ChangedAction(changed) { onOpen(Route.Changed) } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LarasSurfaces.pageCanvas,
                    scrolledContainerColor = LarasSurfaces.pageCanvas,
                ),
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "safe-mode") {
                SafeModeBanner(
                    prefs,
                    editor,
                    onRestoreColors = { editor.set(Prefs.colorSpec, null) },
                    modifier = Modifier.padding(horizontal = LarasSpacing.ScreenHorizontal),
                )
            }
            if (query.isBlank()) {
                item(key = "surfaces") {
                    SurfaceCarousel(surface, onSurface, prefs, device, lockView, onLockView) { s, element ->
                        onOpen(Route.Editor(s, element))
                    }
                }
            }
            item(key = "search") {
                LarasSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = stringResource(R.string.home_search),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(horizontal = LarasSpacing.ScreenHorizontal, vertical = 4.dp),
                )
            }

            if (query.isBlank()) {
                item(key = "system") {
                    SectionLabel(stringResource(R.string.home_system), Modifier.padding(horizontal = 16.dp))
                }
                val shown = SystemGroup.entries.filterNot { it.planned || it == SystemGroup.MODULES }
                val rows = shown.filter { it.wide }.map { listOf(it) } + shown.filterNot { it.wide }.chunked(2)
                items(rows.size, key = { "groups-$it" }) { row ->
                    Row(
                        Modifier.padding(horizontal = LarasSpacing.ScreenHorizontal).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rows[row].forEachIndexed { column, group ->
                            GroupCard(group, row * 2 + column, facts, prefs, Modifier.weight(1f)) {
                                when (group) {
                                    SystemGroup.PENTAS -> openPentas(context)
                                    else -> onOpen(Route.Group(group))
                                }
                            }
                        }
                        if (rows[row].size == 1 && !rows[row][0].wide) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                item(key = "results") { SearchResults(query, prefs, editor, onOpen) }
            }
        }
    }
}

@Composable
private fun ChangedAction(count: Int, onClick: () -> Unit) {
    IconButton(onClick) {
        BadgedBox(badge = {
            AnimatedVisibility(count > 0, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Badge {
                    AnimatedContent(count, label = "count", transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut()
                    }) { Text("$it") }
                }
            }
        }) {
            Icon(Icons.Rounded.History, stringResource(R.string.changed_action))
        }
    }
}

@Composable
private fun SurfaceCarousel(
    surface: Surface,
    onSurface: (Surface) -> Unit,
    prefs: PrefSnapshot,
    device: DeviceState,
    lockView: LockView,
    onLockView: (LockView) -> Unit,
    onEdit: (Surface, Element?) -> Unit,
) {
    val surfaces = Surface.entries
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fullWidth = maxWidth
        // Balanced height so the Search bar and System Groups peek comfortably above the fold.
        val phoneWidth = fullWidth * 0.46f
        val canvasHeight = phoneWidth / device.aspectRatio
        val spacing = 16.dp
        fun widthOf(s: Surface) = if (s.canvas.isBar) fullWidth - 32.dp else phoneWidth
        fun centring(k: Int) =
            with(density) { ((widthOf(surfaces[k]) - widthOf(surfaces.first())) / 2).roundToPx() }
        val list = rememberLazyListState(surface.ordinal, centring(surface.ordinal))
        val spacingPx = with(density) { spacing.roundToPx() }
        val centred by remember(list) { derivedStateOf { list.centredItem(spacingPx) } }
        val current = surfaces[centred.first]
        LaunchedEffect(list) {
            snapshotFlow { list.isScrollInProgress to centred.first }
                .collect { (scrolling, index) -> if (!scrolling) onSurface(surfaces[index]) }
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(current, label = "surface", transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(emphasized()) { dir * it / 3 } + fadeIn(emphasized(300))) togetherWith
                    (slideOutHorizontally(emphasized(200)) { -dir * it / 3 } + fadeOut(emphasized(150)))
            }) { shown ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(shown.title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(shown.summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            PageDots(surfaces.size, centred.first, centred.second, Modifier.padding(vertical = 10.dp))
            LazyRow(
                state = list,
                flingBehavior = rememberSnapFlingBehavior(list, SnapPosition.Center),
                contentPadding = PaddingValues(
                    start = (fullWidth - widthOf(surfaces.first())) / 2,
                    end = (fullWidth - widthOf(surfaces.last())) / 2,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(surfaces, key = { _, it -> it.name }) { index, pageSurface ->
                    SurfaceCanvas(
                        surface = pageSurface,
                        prefs = prefs,
                        device = device,
                        lockView = lockView,
                        focus = null,
                        onSelect = { element ->
                            if (index == centred.first) {
                                onEdit(pageSurface, element)
                            } else {
                                scope.launch { list.animateScrollToItem(index, centring(index)) }
                            }
                        },
                        modifier = Modifier.width(widthOf(pageSurface)).height(canvasHeight)
                            .sharedCanvas(pageSurface),
                    )
                }
            }
            AnimatedVisibility(
                current == Surface.LOCK_SCREEN,
                enter = fadeIn(emphasized()) + expandVertically(emphasized()),
                exit = fadeOut(emphasized(150)) + shrinkVertically(emphasized()),
            ) {
                ChoiceButtons(
                    options = LockView.entries,
                    selected = lockView,
                    onSelect = { onLockView(it as LockView) },
                    modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 12.dp),
                )
            }
            Text(
                stringResource(R.string.home_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun LazyListState.centredItem(spacing: Int): Pair<Int, Float> {
    val info = layoutInfo
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val item = info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - middle) }
        ?: return firstVisibleItemIndex to 0f
    return item.index to (middle - (item.offset + item.size / 2f)) / (item.size + spacing)
}

@Composable
private fun PageDots(count: Int, page: Int, offset: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val nearness = (1f - abs(i - (page + offset))).coerceIn(0f, 1f)
            Box(
                Modifier.height(6.dp).width(6.dp + 12.dp * nearness)
                    .background(lerp(colors.outlineVariant, colors.primary, nearness), CircleShape),
            )
        }
    }
}

/** Kept as public alias so callers across screens stay source-compatible. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    LarasSectionHeader(title = text, modifier = modifier)
}

private fun openPentas(context: Context) {
    runCatching {
        context.startActivity(
            Intent().setClassName(PENTAS_PACKAGE, "$PENTAS_PACKAGE.hub.HubSettingsActivity"),
        )
    }
}

private val SystemGroup.wide: Boolean
    get() = this == SystemGroup.STYLE || this == SystemGroup.GESTURES

private val SystemGroup.cardHeight: Dp
    get() = when (this) {
        SystemGroup.STYLE -> 196.dp
        SystemGroup.GESTURES -> 128.dp
        else -> 164.dp
    }

@Composable
private fun GroupCard(
    group: SystemGroup,
    index: Int,
    facts: HomeFacts,
    prefs: PrefSnapshot,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = rememberInteraction()
    val press = rememberPressScale(interaction)
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(500, delayMillis = 40 * index, easing = EmphasizedDecelerate))
    }
    var replay by remember { mutableIntStateOf(0) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Press) replay++ }
    }
    val play = remember { Animatable(0f) }
    LaunchedEffect(replay, group.artKey(facts, prefs)) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            play.snapTo(1f)
            return@LaunchedEffect
        }
        play.snapTo(0f)
        play.animateTo(
            1f,
            tween(1100, delayMillis = if (replay == 0) 120 + 40 * index else 0, easing = EmphasizedEasing),
        )
    }
    val rise = with(LocalDensity.current) { 24.dp.toPx() }
    val tone = group.tone()
    Surface(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            alpha = enter.value
            translationY = (1f - enter.value) * rise
            scaleX = press
            scaleY = press
        },
        shape = RoundedCornerShape(if (group.wide) 32.dp else 28.dp),
        color = tone.container.copy(alpha = 0.32f).compositeOver(LarasSurfaces.card),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxWidth().height(group.cardHeight)) {
            GroupArt(group, facts, prefs, tone) { play.value }
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = group.labelStart, end = 16.dp, bottom = 16.dp),
            ) {
                if (group == SystemGroup.PENTAS) {
                    Text("${facts.pentasGames}", style = MaterialTheme.typography.headlineMedium, color = tone.content)
                }
                if (group == SystemGroup.SUPERUSER) {
                    val (relaxed, total) = relaxedGuards(prefs)
                    Text(
                        stringResource(R.string.home_su_count, relaxed, total),
                        style = MaterialTheme.typography.headlineMedium,
                        color = tone.content,
                    )
                }
                Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    group.status(facts, prefs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    prefs: PrefSnapshot,
    editor: PrefEditor,
    onOpen: (Route) -> Unit,
) {
    val context = LocalContext.current
    val needle = query.trim()
    val features = remember(needle) {
        SEARCH_DESTINATIONS.filter { dest ->
            context.getString(dest.title).contains(needle, ignoreCase = true) ||
                context.getString(dest.summary).contains(needle, ignoreCase = true) ||
                dest.aliases.any { it.contains(needle, ignoreCase = true) }
        }
    }
    val entries = remember(needle) {
        Catalog.sections.flatMap { section ->
            val names = listOfNotNull(section.title, (section as? Element)?.surface?.title)
            section.entries.filter { entry ->
                (names + entry.title).any { context.getString(it).contains(needle, ignoreCase = true) }
            }
        }
    }

    Column(
        Modifier.padding(horizontal = LarasSpacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(LarasSpacing.GroupGap),
    ) {
        if (features.isEmpty() && entries.isEmpty()) {
            LarasEmptyState(title = stringResource(R.string.home_no_results, needle))
        } else {
            if (features.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap)) {
                    features.forEachIndexed { idx, dest ->
                        LarasNavRow(
                            title = stringResource(dest.title),
                            summary = stringResource(dest.summary),
                            shape = groupedShape(idx, features.size),
                            onClick = { onOpen(dest.route) },
                        )
                    }
                }
            }
            if (entries.isNotEmpty()) {
                EntryList(entries, prefs, editor)
            }
        }
    }
}
