/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
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
import org.yasvara.laras.ui.brightness.BrightnessScreen
import org.yasvara.laras.ui.color.ColorScreen
import org.yasvara.laras.ui.connectivity.WifiRegionScreen
import org.yasvara.laras.ui.haptics.HapticsScreen
import org.yasvara.laras.ui.integrity.TargetAppsScreen
import org.yasvara.laras.ui.kait.ScopeScreen
import org.yasvara.laras.ui.keyboard.KetikFlagEditorScreen
import org.yasvara.laras.ui.lelap.LelapAppsScreen
import org.yasvara.laras.ui.padat.PadatScreen
import org.yasvara.laras.ui.preview.LocalKeyguardRenderer
import org.yasvara.laras.ui.preview.LockView
import org.yasvara.laras.ui.preview.rememberDeviceState
import org.yasvara.laras.ui.preview.rememberKeyguardRenderer
import org.yasvara.laras.ui.root.AkarAppScreen
import org.yasvara.laras.ui.root.AkarLogsScreen
import org.yasvara.laras.ui.root.AkarScreen
import org.yasvara.laras.ui.root.AkarSettingsScreen
import org.yasvara.laras.ui.saring.SaringScreen
import org.yasvara.laras.ui.saring.SaringTab
import org.yasvara.laras.ui.screens.ChangedScreen
import org.yasvara.laras.ui.screens.EditorScreen
import org.yasvara.laras.ui.screens.GroupScreen
import org.yasvara.laras.ui.screens.HomeScreen
import org.yasvara.laras.ui.sound.NadaScreen
import org.yasvara.laras.ui.sound.SoundAppsScreen
import org.yasvara.laras.ui.theme.EmphasizedAccelerate
import org.yasvara.laras.ui.theme.EmphasizedDecelerate
import org.yasvara.laras.ui.theme.emphasized
import org.yasvara.laras.ui.updates.AppUpdatesScreen
import org.yasvara.laras.ui.visibility.AppVisibilityScreen

sealed interface Route {
    val id: String

    data object Home : Route { override val id = "home" }

    data class Editor(val surface: Surface, val focus: Element? = null) : Route {
        override val id = "editor:${surface.name}:${focus?.name.orEmpty()}"
    }

    data class Group(val group: SystemGroup) : Route { override val id = "group:${group.name}" }
    data object Changed : Route { override val id = "changed" }
    data object TargetApps : Route { override val id = "targets" }
    data class XposedScope(val module: String) : Route { override val id = "xscope:$module" }
    data class Kait(val focus: String? = null) : Route { override val id = "kait:${focus.orEmpty()}" }
    data class AppUpdates(val focus: String? = null) : Route { override val id = "updates:${focus.orEmpty()}" }
    data class Akar(val focus: String? = null) : Route { override val id = "akar:${focus.orEmpty()}" }
    data class AkarApp(val packageName: String) : Route { override val id = "akar_app:$packageName" }
    data object AkarLogs : Route { override val id = "akar_logs" }
    data object AkarSettings : Route { override val id = "akar_settings" }
    data object AppVisibility : Route { override val id = "visibility" }
    data object LelapApps : Route { override val id = "lelap_apps" }
    data object LelapCuts : Route { override val id = "lelap_cuts" }
    data object Saring : Route { override val id = "saring" }
    data object Color : Route { override val id = "color" }
    data object SoundApps : Route { override val id = "sound_apps" }
    data object Nada : Route { override val id = "nada" }
    data object Haptics : Route { override val id = "haptics" }
    data object Brightness : Route { override val id = "brightness" }
    data object WifiRegion : Route { override val id = "wifi_region" }
    data object KetikFlags : Route { override val id = "ketik_flags" }
    data class Padat(val focus: String? = null) : Route { override val id = "padat:${focus.orEmpty()}" }

    companion object {
        fun parse(id: String): Route = id.substringAfter(':').let { arg ->
            when (id.substringBefore(':')) {
                "editor" -> Editor(
                    Surface.valueOf(arg.substringBefore(':')),
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
                "brightness" -> Brightness
                "wifi_region" -> WifiRegion
                "ketik_flags" -> KetikFlags
                "padat" -> Padat(arg.takeIf { it.isNotEmpty() && it != id })
                "updates" -> AppUpdates(arg.takeIf { it.isNotEmpty() && it != id })
                "akar" -> Akar(arg.takeIf { it.isNotEmpty() && it != id })
                "akar_app" -> AkarApp(arg)
                "akar_logs" -> AkarLogs
                "akar_settings" -> AkarSettings
                else -> Home
            }
        }
    }
}

private val BackStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.id } },
    restore = { ids -> mutableStateListOf<Route>().apply { addAll(ids.map(Route::parse)) } },
)

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope")
}

@Composable
fun Modifier.sharedCanvas(surface: Surface): Modifier = with(LocalSharedTransitionScope.current) {
    this@sharedCanvas.sharedElement(
        rememberSharedContentState("canvas-${surface.name}"),
        LocalNavAnimatedContentScope.current,
        boundsTransform = { _, _ -> emphasized<Rect>(500) },
    )
}

private fun forward(): AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
    (slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / 6 } + fadeIn(tween(250, 80))) togetherWith
        (slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { -it / 10 } + fadeOut(tween(120)))
}

private fun backward(): AnimatedContentTransitionScope<Scene<Route>>.() -> ContentTransform = {
    (slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 10 } + fadeIn(tween(250, 80))) togetherWith
        (slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { it / 6 } + fadeOut(tween(120)))
}

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
    var lockView by rememberSaveable { mutableStateOf(LockView.NOTIFICATIONS) }
    var surface by rememberSaveable { mutableStateOf(Surface.LOCK_SCREEN) }
    val activity = LocalActivity.current
    val back: () -> Unit = {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else if (start != Route.Home) activity?.finish()
    }
    val open: (Route) -> Unit = { backStack.add(it) }

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
                            onOpen = open,
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
                        GroupScreen(route.group, prefs, viewModel, onOpen = open, onBack = back)
                    }
                    entry<Route.TargetApps> { TargetAppsScreen(onBack = back) }
                    entry<Route.XposedScope> { route -> ScopeScreen(route.module, onBack = back) }
                    entry<Route.Kait> { route ->
                        AkarScreen(
                            onBack = back,
                            focus = route.focus,
                            initialTab = 2,
                            onOpenApp = { open(Route.AkarApp(it)) },
                            onOpenLogs = { open(Route.AkarLogs) },
                            onOpenScope = { open(Route.XposedScope(it)) },
                            onOpenSettings = { open(Route.AkarSettings) },
                        )
                    }
                    entry<Route.AppUpdates> { route ->
                        AppUpdatesScreen(advanced = prefs[Prefs.larasAdvanced], onBack = back, focus = route.focus)
                    }
                    entry<Route.Akar> { route ->
                        AkarScreen(
                            onBack = back,
                            focus = route.focus,
                            onOpenApp = { open(Route.AkarApp(it)) },
                            onOpenLogs = { open(Route.AkarLogs) },
                            onOpenScope = { open(Route.XposedScope(it)) },
                            onOpenSettings = { open(Route.AkarSettings) },
                        )
                    }
                    entry<Route.AkarApp> { route -> AkarAppScreen(packageName = route.packageName, onBack = back) }
                    entry<Route.AkarLogs> { AkarLogsScreen(onBack = back, onOpenApp = { open(Route.AkarApp(it)) }) }
                    entry<Route.AkarSettings> {
                        AkarSettingsScreen(onBack = back, onOpenApp = { open(Route.AkarApp(it)) })
                    }
                    entry<Route.AppVisibility> { AppVisibilityScreen(onBack = back) }
                    entry<Route.LelapApps> { LelapAppsScreen(onBack = back) }
                    entry<Route.LelapCuts> { SaringScreen(onBack = back, initialTab = SaringTab.TELEMETRY) }
                    entry<Route.Saring> { SaringScreen(onBack = back, initialTab = SaringTab.OVERVIEW) }
                    entry<Route.Color> { ColorScreen(onBack = back) }
                    entry<Route.SoundApps> { SoundAppsScreen(onBack = back) }
                    entry<Route.Nada> { NadaScreen(onBack = back) }
                    entry<Route.Haptics> { HapticsScreen(onBack = back) }
                    entry<Route.Brightness> { BrightnessScreen(onBack = back) }
                    entry<Route.WifiRegion> { WifiRegionScreen(prefs, onBack = back) }
                    entry<Route.KetikFlags> { KetikFlagEditorScreen(onBack = back) }
                    entry<Route.Padat> { route -> PadatScreen(onBack = back, focus = route.focus) }
                    entry<Route.Changed> {
                        ChangedScreen(prefs, viewModel, onResetAll = viewModel::resetAll, onBack = back)
                    }
                },
            )
        }
    }
}
