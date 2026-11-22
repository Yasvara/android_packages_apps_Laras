/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import org.yasvara.laras.catalog.Element
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.catalog.Surface
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.ui.preview.LocalKeyguardRenderer
import org.yasvara.laras.ui.preview.LockView
import org.yasvara.laras.ui.preview.rememberDeviceState
import org.yasvara.laras.ui.preview.rememberKeyguardRenderer
import org.yasvara.laras.ui.color.ColorScreen
import org.yasvara.laras.ui.connectivity.WifiRegionScreen
import org.yasvara.laras.ui.integrity.TargetAppsScreen
import org.yasvara.laras.ui.root.AkarAppScreen
import org.yasvara.laras.ui.root.AkarLogsScreen
import org.yasvara.laras.ui.root.AkarScreen
import org.yasvara.laras.ui.updates.AppUpdatesScreen
import org.yasvara.laras.ui.lelap.LelapAppsScreen
import org.yasvara.laras.ui.lelap.LelapCutsScreen
import org.yasvara.laras.ui.padat.PadatScreen
import org.yasvara.laras.ui.saring.SaringScreen
import org.yasvara.laras.ui.visibility.AppVisibilityScreen
import org.yasvara.laras.ui.kait.KaitScreen
import org.yasvara.laras.ui.kait.ScopeScreen
import org.yasvara.laras.ui.screens.ChangedScreen
import org.yasvara.laras.ui.screens.EditorScreen
import org.yasvara.laras.ui.screens.GroupScreen
import org.yasvara.laras.ui.screens.HomeScreen
import org.yasvara.laras.ui.theme.EmphasizedAccelerate
import org.yasvara.laras.ui.haptics.HapticsScreen
import org.yasvara.laras.ui.sound.NadaScreen
import org.yasvara.laras.ui.sound.SoundAppsScreen
import org.yasvara.laras.ui.theme.EmphasizedDecelerate
import org.yasvara.laras.ui.theme.emphasized

sealed interface Route {
    val id: String

    data object Home : Route {
        override val id = "home"
    }

    /** Edits one [surface]; [focus] is the element to open on, if the user tapped one. */
    data class Editor(val surface: Surface, val focus: Element? = null) : Route {
        override val id = "editor:${surface.name}:${focus?.name.orEmpty()}"
    }

    data class Group(val group: SystemGroup) : Route {
        override val id = "group:${group.name}"
    }

    data object Changed : Route {
        override val id = "changed"
    }

    /** Which apps the keybox answers attestation for; opened from the Integrity group. */
    data object TargetApps : Route {
        override val id = "targets"
    }

    /** The apps one Xposed module is loaded into; opened from Kait. */
    data class XposedScope(val module: String) : Route {
        override val id = "xscope:$module"
    }

    /** Full Kait module manager (3-tab Info · Modules · Repository); opened from Home or KaitActivity. */
    data class Kait(val focus: String? = null) : Route {
        override val id = "kait:${focus.orEmpty()}"
    }

    /** Per-app update policy; opened from the Apps group, or on [focus] from App info. */
    data class AppUpdates(val focus: String? = null) : Route {
        override val id = "updates:${focus.orEmpty()}"
    }

    /** Root access; opened from the Superuser group, or on [focus] from a notification. */
    data class Akar(val focus: String? = null) : Route {
        override val id = "akar:${focus.orEmpty()}"
    }

    /** Per-app root profile inside Akar. */
    data class AkarApp(val packageName: String) : Route {
        override val id = "akar_app:$packageName"
    }

    /** Merged Akar activity and denial logs. */
    data object AkarLogs : Route {
        override val id = "akar_logs"
    }

    /** Which apps other apps may see; opened from the Superuser group. */
    data object AppVisibility : Route {
        override val id = "visibility"
    }

    /** Apps that run only while open; opened from the Performance & battery group. */
    data object LelapApps : Route {
        override val id = "lelap_apps"
    }

    /** Telemetry and tracking cuts; opened from the Performance & battery group. */
    data object LelapCuts : Route {
        override val id = "lelap_cuts"
    }

    /** Ad, tracker and pop-up filtering; opened from the Connectivity group. */
    data object Saring : Route {
        override val id = "saring"
    }

    /** System colours; opened from the Style group. */
    data object Color : Route {
        override val id = "color"
    }

    /** Per-app sound; opened from the Sound group. */
    data object SoundApps : Route {
        override val id = "sound_apps"
    }

    /** Processor effects (Nada); opened from the Sound group. */
    data object Nada : Route {
        override val id = "nada"
    }

    /** UI haptics (Raba); opened from the Sound group. */
    data object Haptics : Route {
        override val id = "haptics"
    }

    /** Wi-Fi region; opened from the Connectivity group or Settings › Hotspot. */
    data object WifiRegion : Route {
        override val id = "wifi_region"
    }

    /** Realtime per-app F2FS compression; opened from Performance or Settings › Storage. */
    data class Padat(val focus: String? = null) : Route {
        override val id = "padat:${focus.orEmpty()}"
    }

    companion object {
        fun parse(id: String): Route = id.substringAfter(':').let { arg ->
            when (id.substringBefore(':')) {
                "editor" -> Editor(
                    Surface.valueOf(arg.substringBefore(':')),
                    // An element renamed since the state was saved opens the surface unfocused.
                    arg.substringAfter(':').let { name -> Element.entries.find { it.name == name } },
                )
                "group" -> Group(SystemGroup.valueOf(arg))
                "changed" -> Changed
                "targets" -> TargetApps
                "xscope" -> XposedScope(arg)
                "kait" -> Kait(arg.takeIf { it.isNotEmpty() && it != id })
                "color" -> Color
                "visibility" -> AppVisibility
                "lelap_apps" -> LelapApps
                "lelap_cuts" -> LelapCuts
                "saring" -> Saring
                "sound_apps" -> SoundApps
                "nada" -> Nada
                "haptics" -> Haptics
                "wifi_region" -> WifiRegion
                "padat" -> Padat(arg.takeIf { it.isNotEmpty() && it != id })
                "updates" -> AppUpdates(arg.takeIf { it.isNotEmpty() && it != id })
                "akar" -> Akar(arg.takeIf { it.isNotEmpty() && it != id })
                "akar_app" -> AkarApp(arg)
                "akar_logs" -> AkarLogs
                else -> Home
            }
        }
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.id } },
    restore = { ids -> mutableStateListOf<Route>().apply { addAll(ids.map(Route::parse)) } },
)

/** The scope that lets the lock canvas travel between the home and editor screens. */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope")
}

/** Marks a surface's canvas so it morphs, rather than cuts, between screens. */
@Composable
fun Modifier.sharedCanvas(surface: Surface): Modifier = with(LocalSharedTransitionScope.current) {
    this@sharedCanvas.sharedElement(
        rememberSharedContentState("canvas-${surface.name}"),
        LocalNavAnimatedContentScope.current,
        boundsTransform = { _, _ -> emphasized<Rect>(500) },
    )
}

// Material shared-axis motion: the next page slides in a short way while the old one fades.
private fun forward(): AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
    (slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / 6 } +
        fadeIn(tween(250, 80))) togetherWith
        (slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { -it / 10 } +
            fadeOut(tween(120)))
}

private fun backward(): AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
    (slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 10 } +
        fadeIn(tween(250, 80))) togetherWith
        (slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { it / 6 } +
            fadeOut(tween(120)))
}

/** The editor only cross-fades: the travelling canvas carries the motion. */
private val EditorMotion =
    NavDisplay.transitionSpec { fadeIn(tween(300, 100)) togetherWith fadeOut(tween(150)) } +
        NavDisplay.popTransitionSpec { fadeIn(tween(300, 100)) togetherWith fadeOut(tween(150)) } +
        NavDisplay.predictivePopTransitionSpec { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }

@Composable
fun LarasApp(
    start: Route = Route.Home,
    launchSeq: Int = 0,
    viewModel: LarasViewModel = viewModel(),
) {
    val backStack = rememberSaveable(saver = BackStackSaver) { mutableStateListOf(start) }
    LaunchedEffect(launchSeq) {
        if (launchSeq > 0) {
            backStack.clear()
            backStack.add(start)
        }
    }
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val device = rememberDeviceState()
    val keyguard = rememberKeyguardRenderer()
    // Shared by every canvas so the lock screen keeps its look across screens.
    var lockView by rememberSaveable { mutableStateOf(LockView.NOTIFICATIONS) }
    var surface by rememberSaveable { mutableStateOf(Surface.LOCK_SCREEN) }
    val activity = LocalActivity.current
    // A deep link starts on its own screen, so its up arrow leaves Laras instead of doing nothing.
    val back: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else if (start != Route.Home) activity?.finish()
    }

    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalKeyguardRenderer provides keyguard,
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = back,
                transitionSpec = forward(),
                popTransitionSpec = backward(),
                predictivePopTransitionSpec = { backward().invoke(this) },
                entryProvider = entryProvider {
                    entry<Route.Home> {
                        HomeScreen(
                            prefs = prefs,
                            device = device,
                            editor = viewModel,
                            surface = surface,
                            onSurface = { surface = it },
                            lockView = lockView,
                            onLockView = { lockView = it },
                            onOpen = { backStack.add(it) },
                        )
                    }
                    entry<Route.Editor>(metadata = EditorMotion) { route ->
                        EditorScreen(
                            surface = route.surface,
                            initialFocus = route.focus,
                            prefs = prefs,
                            device = device,
                            editor = viewModel,
                            lockView = lockView,
                            onLockView = { lockView = it },
                            onClose = back,
                        )
                    }
                    entry<Route.Group> { route ->
                        GroupScreen(route.group, prefs, viewModel, onOpen = { backStack.add(it) }, onBack = back)
                    }
                    entry<Route.TargetApps> {
                        TargetAppsScreen(onBack = back)
                    }
                    entry<Route.XposedScope> { route ->
                        ScopeScreen(route.module, onBack = back)
                    }
                    entry<Route.Kait> { route ->
                        AkarScreen(
                            onBack = back,
                            focus = route.focus,
                            initialTab = 2,
                            onOpenApp = { backStack.add(Route.AkarApp(it)) },
                            onOpenLogs = { backStack.add(Route.AkarLogs) },
                            onOpenScope = { backStack.add(Route.XposedScope(it)) },
                        )
                    }
                    entry<Route.AppUpdates> { route ->
                        AppUpdatesScreen(advanced = prefs[Prefs.larasAdvanced], onBack = back, focus = route.focus)
                    }
                    entry<Route.Akar> { route ->
                        AkarScreen(
                            onBack = back,
                            focus = route.focus,
                            onOpenApp = { backStack.add(Route.AkarApp(it)) },
                            onOpenLogs = { backStack.add(Route.AkarLogs) },
                            onOpenScope = { backStack.add(Route.XposedScope(it)) },
                        )
                    }
                    entry<Route.AkarApp> { route ->
                        AkarAppScreen(
                            packageName = route.packageName,
                            onBack = back,
                        )
                    }
                    entry<Route.AkarLogs> {
                        AkarLogsScreen(
                            onBack = back,
                            onOpenApp = { backStack.add(Route.AkarApp(it)) },
                        )
                    }
                    entry<Route.AppVisibility> {
                        AppVisibilityScreen(onBack = back)
                    }
                    entry<Route.LelapApps> {
                        LelapAppsScreen(onBack = back)
                    }
                    entry<Route.LelapCuts> {
                        LelapCutsScreen(onBack = back)
                    }
                    entry<Route.Saring> {
                        SaringScreen(onBack = back)
                    }
                    entry<Route.Color> {
                        ColorScreen(onBack = back)
                    }
                    entry<Route.SoundApps> {
                        SoundAppsScreen(onBack = back)
                    }
                    entry<Route.Nada> {
                        NadaScreen(onBack = back)
                    }
                    entry<Route.Haptics> {
                        HapticsScreen(onBack = back)
                    }
                    entry<Route.WifiRegion> {
                        WifiRegionScreen(prefs, onBack = back)
                    }
                    entry<Route.Padat> { route ->
                        PadatScreen(onBack = back, focus = route.focus)
                    }
                    entry<Route.Changed> {
                        ChangedScreen(prefs, viewModel, onResetAll = viewModel::resetAll, onBack = back)
                    }
                },
            )
        }
    }
}
