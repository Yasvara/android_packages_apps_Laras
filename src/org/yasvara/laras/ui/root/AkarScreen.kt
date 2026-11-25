/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.kait.KaitShortcuts
import org.yasvara.laras.data.root.AkarShortcuts
import org.yasvara.laras.data.root.DeniedAttempt
import org.yasvara.laras.data.root.InstalledApp
import org.yasvara.laras.data.root.RootGrant
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.data.root.RootTier
import org.yasvara.laras.data.sound.NadaShortcuts
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasMascotIcon
import org.yasvara.laras.ui.components.LarasNavRow
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSplitSwitchRow
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.larasStickyControls
import org.yasvara.laras.ui.kait.CrashBypassBanner
import org.yasvara.laras.ui.kait.KaitModulesTab
import org.yasvara.laras.ui.kait.KaitRepoTab
import org.yasvara.laras.ui.kait.KaitUi
import org.yasvara.laras.ui.kait.LiveProcessesSheet
import org.yasvara.laras.ui.kait.ModuleActionsSheet
import org.yasvara.laras.ui.kait.ModuleListFilter
import org.yasvara.laras.ui.kait.ModulesViewModel
import org.yasvara.laras.ui.kait.OnlineModuleDetailSheet
import org.yasvara.laras.ui.kait.RepoListFilter
import org.yasvara.laras.ui.kait.RepoSortOrder
import org.yasvara.laras.ui.screens.Page

private enum class AppFilter { ALL, ENROLLED, DENIED }

/**
 * Unified Akar & Kait Manager:
 * Compact single-line TopAppBar + 4-tab Material 3 layout (Info · Root · Modul · Repositori)
 * with sticky search/filter headers and zero redundant bottom-nav badges.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkarScreen(
    onBack: () -> Unit,
    focus: String? = null,
    initialTab: Int = 0,
    onOpenApp: (String) -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenScope: (String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: AkarViewModel = rememberAkarViewModel(),
    kaitViewModel: ModulesViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val kaitUi by kaitViewModel.state.collectAsStateWithLifecycle()
    val repo = remember { viewModel.repository() }
    val gate = rememberCredentialGate()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 3), pageCount = { 4 })
    var handledFocus by rememberSaveable(focus) { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var appQuery by rememberSaveable { mutableStateOf("") }
    var appFilter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var showSystem by rememberSaveable { mutableStateOf(false) }

    var showModuleSearch by rememberSaveable { mutableStateOf(false) }
    var moduleQuery by rememberSaveable { mutableStateOf("") }
    var moduleFilter by rememberSaveable { mutableStateOf(ModuleListFilter.ALL) }
    var moduleSubTab by rememberSaveable { mutableStateOf(0) } // 0 = Akar Modules, 1 = Kait (Xposed)
    var repoQuery by rememberSaveable { mutableStateOf("") }
    var repoFilter by rememberSaveable { mutableStateOf(RepoListFilter.ALL) }
    var repoSort by rememberSaveable { mutableStateOf(RepoSortOrder.UPDATED) }
    var showProcessesSheet by rememberSaveable { mutableStateOf(false) }
    var actionSheetModulePkg by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }
    LifecycleResumeEffect(Unit) {
        kaitViewModel.refresh()
        onPauseOrDispose {}
    }
    LaunchedEffect(focus) {
        if (!focus.isNullOrEmpty() && !handledFocus) {
            handledFocus = true
            if (initialTab == 2) onOpenScope(focus) else onOpenApp(focus)
        }
    }

    val errorMsg = stringResource(R.string.akar_error)
    LaunchedEffect(ui.error) {
        if (ui.error) {
            snackbar.showSnackbar(errorMsg)
            viewModel.dismissError()
        }
    }

    BackHandler(pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val surface = LarasSurfaces.pageCanvas
    val titles = listOf(
        stringResource(R.string.akar_title),
        stringResource(R.string.akar_tab_apps),
        stringResource(R.string.kait_tab_modules),
        stringResource(R.string.kait_tab_repo),
    )

    val moduleUpdatesCount = kaitUi.upgradableModulesCount

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(titles[pagerState.currentPage], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                actions = {
                    AnimatedVisibility(visible = pagerState.currentPage == 1) {
                        IconButton(onClick = { showSearch = !showSearch; if (!showSearch) appQuery = "" }) {
                            Icon(if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search, stringResource(R.string.akar_search_apps))
                        }
                    }
                    AnimatedVisibility(visible = pagerState.currentPage == 2) {
                        IconButton(onClick = { showModuleSearch = !showModuleSearch; if (!showModuleSearch) moduleQuery = "" }) {
                            Icon(if (showModuleSearch) Icons.Rounded.Close else Icons.Rounded.Search, stringResource(R.string.kait_search_modules))
                        }
                    }
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.AutoMirrored.Rounded.Article, stringResource(R.string.akar_logs_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, stringResource(R.string.akar_tab_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surface, scrolledContainerColor = surface),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Rounded.Home, null) },
                    label = { Text(stringResource(R.string.akar_tab_info)) },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = {
                        BadgedBox(badge = { if (ui.denied.isNotEmpty()) Badge { Text("${ui.denied.size}") } }) {
                            Icon(Icons.Rounded.Shield, null)
                        }
                    },
                    label = { Text(stringResource(R.string.akar_tab_apps)) },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = { Icon(Icons.Rounded.Extension, null) },
                    label = { Text(stringResource(R.string.kait_tab_modules)) },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    icon = {
                        BadgedBox(badge = { if (moduleUpdatesCount > 0) Badge { Text("$moduleUpdatesCount") } }) {
                            Icon(Icons.Rounded.CloudDownload, null)
                        }
                    },
                    label = { Text(stringResource(R.string.kait_tab_repo), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        },
    ) { padding ->
        if (ui.loading && pagerState.currentPage < 2) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val canUse = ui.available && ui.state.daemonInstalled
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            when (page) {
                0 -> InfoTab(
                    ui = ui,
                    kaitUi = kaitUi,
                    canUse = canUse,
                    gate = gate,
                    viewModel = viewModel,
                    kaitViewModel = kaitViewModel,
                    onJumpApps = { filter -> appFilter = filter; scope.launch { pagerState.animateScrollToPage(1) } },
                    onJumpModules = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onOpenProcesses = { showProcessesSheet = true },
                    onOpenApp = onOpenApp,
                )
                1 -> AppsTab(
                    ui = ui,
                    canUse = canUse,
                    repo = repo,
                    showSearch = showSearch,
                    query = appQuery,
                    onQuery = { appQuery = it },
                    filter = appFilter,
                    onFilter = { appFilter = it },
                    showSystem = showSystem,
                    onToggleSystem = { showSystem = !showSystem },
                    onRefresh = { viewModel.load(isRefresh = true) },
                    onClearDenied = viewModel::clearDenied,
                    onOpenApp = onOpenApp,
                )
                2 -> Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = moduleSubTab == 0,
                            onClick = { moduleSubTab = 0 },
                            label = {
                                Text(stringResource(R.string.akar_mod_subtab_akar, ui.modules.size))
                            },
                        )
                        FilterChip(
                            selected = moduleSubTab == 1,
                            onClick = { moduleSubTab = 1 },
                            label = {
                                Text(stringResource(R.string.akar_mod_subtab_kait, kaitUi.modules.size))
                            },
                        )
                    }
                    if (moduleSubTab == 0) {
                        AkarModulesTab(
                            ui = ui,
                            canUse = canUse,
                            gate = gate,
                            viewModel = viewModel,
                            showSearch = showModuleSearch,
                            query = moduleQuery,
                            onQueryChange = { moduleQuery = it },
                        )
                    } else {
                        KaitModulesTab(
                            ui = kaitUi,
                            viewModel = kaitViewModel,
                            showSearch = showModuleSearch,
                            query = moduleQuery,
                            onQueryChange = { moduleQuery = it },
                            filter = moduleFilter,
                            onFilterChange = { moduleFilter = it },
                            onOpenScope = onOpenScope,
                            onOpenActions = { actionSheetModulePkg = it.packageName },
                            onBrowseRepo = { scope.launch { pagerState.animateScrollToPage(3) } },
                        )
                    }
                }
                else -> KaitRepoTab(
                    ui = kaitUi,
                    viewModel = kaitViewModel,
                    query = repoQuery,
                    onQueryChange = { repoQuery = it },
                    filter = repoFilter,
                    onFilterChange = { repoFilter = it },
                    sort = repoSort,
                    onSortChange = { repoSort = it },
                    onOpenScope = onOpenScope,
                )
            }
        }
    }

    if (showProcessesSheet) {
        LiveProcessesSheet(
            processes = kaitUi.injectedProcesses,
            onDismiss = { showProcessesSheet = false },
            onRestart = { pkg ->
                kaitViewModel.restartPackage(pkg) {
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.kait_restarted_targets, 1)) }
                }
            },
        )
    }

    val sheetModule = actionSheetModulePkg?.let { pkg -> kaitUi.modules.find { it.packageName == pkg } }
    if (sheetModule != null) {
        val onlineMatch = kaitUi.onlineByPackage[sheetModule.packageName]
        ModuleActionsSheet(
            module = sheetModule,
            hasOnlineEntry = onlineMatch != null,
            onDismiss = { actionSheetModulePkg = null },
            onOpenUi = { actionSheetModulePkg = null; kaitViewModel.repository.openModuleUi(sheetModule.packageName) },
            onOpenScope = { actionSheetModulePkg = null; onOpenScope(sheetModule.packageName) },
            onRestartTargets = {
                actionSheetModulePkg = null
                kaitViewModel.restartTargetsOf(sheetModule) { count ->
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.kait_restarted_targets, count)) }
                }
            },
            onViewInRepo = { actionSheetModulePkg = null; onlineMatch?.let(kaitViewModel::openOnlineModuleDetail) },
            onAppInfo = { actionSheetModulePkg = null; kaitViewModel.repository.openAppInfo(sheetModule.packageName) },
            onUninstall = { actionSheetModulePkg = null; kaitViewModel.repository.requestUninstall(sheetModule.packageName) },
        )
    }

    val detailModule = kaitUi.selectedOnlineModule
    if (detailModule != null) {
        OnlineModuleDetailSheet(
            module = detailModule,
            loading = kaitUi.detailLoading,
            installedModule = kaitUi.modules.find { it.packageName == detailModule.packageName },
            hasUpdate = kaitUi.hasRepoUpdate(detailModule),
            onDismiss = kaitViewModel::dismissOnlineModuleDetail,
            onOpenScope = { kaitViewModel.dismissOnlineModuleDetail(); onOpenScope(detailModule.packageName) },
            onOpenModuleUi = {
                kaitViewModel.dismissOnlineModuleDetail()
                kaitViewModel.repository.openModuleUi(detailModule.packageName)
            },
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Dedicated Settings Page (unified Akar + Kait + Nada shortcuts, ADB, safety)*/
/* -------------------------------------------------------------------------- */

@Composable
fun AkarSettingsScreen(
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    viewModel: AkarViewModel = rememberAkarViewModel(),
    kaitViewModel: ModulesViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val kaitUi by kaitViewModel.state.collectAsStateWithLifecycle()
    val gate = rememberCredentialGate()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAdbDialog by rememberSaveable { mutableStateOf(false) }
    var showIdleDialog by rememberSaveable { mutableStateOf(false) }
    var confirmRevokeAll by rememberSaveable { mutableStateOf(false) }
    var nadaDrawerEnabled by remember { mutableStateOf(NadaShortcuts.isDrawerEnabled(context)) }
    var nadaPinned by remember { mutableStateOf(NadaShortcuts.isPinned(context)) }

    val canUse = ui.available && ui.state.daemonInstalled

    Page(
        title = stringResource(R.string.akar_tab_settings),
        onBack = onBack,
        snackbarHostState = snackbar,
    ) {
        // Launcher Shortcuts (Akar, Kait & Nada Processor Effects unified in one clean section)
        item("shortcuts_hdr") { LarasSectionHeader(stringResource(R.string.akar_pin_shortcut)) }
        item("akar_pin") {
            LarasNavRow(
                title = stringResource(R.string.akar_pin_shortcut),
                summary = stringResource(if (ui.shortcutPinned) R.string.akar_pin_shortcut_done else R.string.akar_pin_shortcut_hint),
                leading = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = ui.pinSupported,
                shape = groupedShape(0, 6),
                onClick = {
                    val ok = AkarShortcuts.requestPin(context)
                    viewModel.refreshShortcutState()
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(if (ok) R.string.akar_pin_shortcut_done else R.string.akar_pin_unsupported),
                        )
                    }
                },
            )
        }
        item("akar_drawer") {
            LarasSwitchRow(
                title = stringResource(R.string.akar_drawer_icon),
                summary = stringResource(R.string.akar_drawer_icon_sub),
                checked = ui.drawerEnabled,
                leading = { Icon(Icons.Rounded.Apps, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = groupedShape(1, 6),
                onCheckedChange = viewModel::setDrawerEnabled,
            )
        }
        item("kait_pin") {
            LarasNavRow(
                title = stringResource(R.string.kait_pin_shortcut),
                summary = stringResource(if (kaitUi.shortcutPinned) R.string.kait_pin_shortcut_done else R.string.kait_pin_shortcut_hint),
                leading = { Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = kaitUi.pinSupported,
                shape = groupedShape(2, 6),
                onClick = {
                    val ok = KaitShortcuts.requestPin(context)
                    kaitViewModel.refreshShortcutState()
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(if (ok) R.string.kait_pin_shortcut_done else R.string.kait_pin_unsupported),
                        )
                    }
                },
            )
        }
        item("kait_drawer") {
            LarasSwitchRow(
                title = stringResource(R.string.kait_drawer_icon),
                summary = stringResource(R.string.kait_drawer_icon_sub),
                checked = kaitUi.drawerEnabled,
                leading = { Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = groupedShape(3, 6),
                onCheckedChange = kaitViewModel::setDrawerEnabled,
            )
        }
        item("nada_pin") {
            LarasNavRow(
                title = "${stringResource(R.string.nada_title)} · ${stringResource(R.string.kait_pin_shortcut)}",
                summary = stringResource(if (nadaPinned) R.string.kait_pin_shortcut_done else R.string.kait_pin_shortcut_hint),
                leading = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                enabled = NadaShortcuts.isPinSupported(context),
                shape = groupedShape(4, 6),
                onClick = {
                    val ok = NadaShortcuts.requestPin(context)
                    nadaPinned = NadaShortcuts.isPinned(context)
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(if (ok) R.string.kait_pin_shortcut_done else R.string.kait_pin_unsupported),
                        )
                    }
                },
            )
        }
        item("nada_drawer") {
            LarasSwitchRow(
                title = "${stringResource(R.string.nada_title)} · ${stringResource(R.string.kait_drawer_icon)}",
                summary = stringResource(R.string.nada_entry_summary),
                checked = nadaDrawerEnabled,
                leading = { Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                shape = groupedShape(5, 6),
                onCheckedChange = {
                    NadaShortcuts.setDrawerEnabled(context, it)
                    nadaDrawerEnabled = it
                },
            )
        }

        if (canUse) {
            item("access_hdr") { LarasSectionHeader(stringResource(R.string.akar_access_behaviour)) }
            item("adb") {
                val adb = ui.adbShell
                LarasSplitSwitchRow(
                    title = stringResource(R.string.akar_adb_title),
                    summary = if (adb != null) {
                        stringResource(
                            R.string.akar_adb_active,
                            scopeShortLabel(adb),
                            stringResource(if (adb.tier == RootTier.ROOT) R.string.akar_tier_root else R.string.akar_tier_shell),
                        )
                    } else {
                        "${stringResource(R.string.akar_adb_off)} · ${adbPresetLabel(ui.adbPreset)}"
                    },
                    checked = adb != null,
                    leading = { Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = groupedShape(0, 4),
                    onClick = { showAdbDialog = true },
                    onCheckedChange = { on -> if (on) gate { viewModel.setAdbShell(true) } else viewModel.setAdbShell(false) },
                )
            }
            item("notify") {
                LarasSwitchRow(
                    title = stringResource(R.string.akar_notify_denied),
                    summary = stringResource(R.string.akar_notify_denied_summary),
                    checked = ui.notifyDenied,
                    leading = { Icon(Icons.Rounded.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = groupedShape(1, 4),
                    onCheckedChange = { viewModel.setOption("notify_denied", if (it) 1 else 0) },
                )
            }
            item("log_full") {
                LarasSwitchRow(
                    title = stringResource(R.string.akar_log_full),
                    summary = stringResource(R.string.akar_log_full_summary),
                    checked = ui.logFull,
                    leading = { Icon(Icons.AutoMirrored.Rounded.Article, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = groupedShape(2, 4),
                    onCheckedChange = { viewModel.setOption("log_full", if (it) 1 else 0) },
                )
            }
            item("idle") {
                LarasNavRow(
                    title = stringResource(R.string.akar_idle),
                    summary = stringResource(R.string.akar_idle_summary),
                    leading = { Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailing = {
                        LarasBadge(
                            text = if (ui.idleDays == 0) stringResource(R.string.akar_idle_never)
                            else stringResource(R.string.akar_idle_days, ui.idleDays),
                            tone = Tone.NEUTRAL,
                        )
                    },
                    shape = groupedShape(3, 4),
                    onClick = { showIdleDialog = true },
                )
            }

            item("safety_hdr") { LarasSectionHeader(stringResource(R.string.akar_safety)) }
            item("selftest") {
                LarasNavRow(
                    title = stringResource(R.string.akar_selftest),
                    summary = ui.selfTest ?: stringResource(R.string.akar_selftest_idle),
                    summaryColor = when (ui.selfTestOk) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.error
                        null -> null
                    },
                    leading = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = groupedShape(0, 2),
                    onClick = viewModel::runSelfTest,
                )
            }
            item("revoke_all") {
                val hasGrants = ui.grants.isNotEmpty()
                LarasNavRow(
                    title = stringResource(R.string.akar_revoke_all),
                    summary = stringResource(R.string.akar_revoke_all_body),
                    leading = {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            null,
                            tint = if (hasGrants) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    },
                    enabled = hasGrants,
                    showChevron = false,
                    shape = groupedShape(1, 2),
                    onClick = { confirmRevokeAll = true },
                )
            }
        }
    }

    if (showAdbDialog) {
        AdbDurationDialog(
            currentGrant = ui.adbShell,
            preset = ui.adbPreset,
            onDismiss = { showAdbDialog = false },
            onApply = { tier, scopeChoice, durationMs ->
                showAdbDialog = false
                gate { viewModel.setAdbConfig(tier, scopeChoice, durationMs) }
            },
            onDisable = { showAdbDialog = false; viewModel.setAdbShell(false) },
            onOpenAdvanced = { showAdbDialog = false; onOpenApp(RootRepository.SHELL_PACKAGE) },
        )
    }
    if (showIdleDialog) {
        IdleDaysDialog(
            currentDays = ui.idleDays,
            onDismiss = { showIdleDialog = false },
            onSelect = { days -> showIdleDialog = false; viewModel.setOption("idle_days", days) },
        )
    }
    if (confirmRevokeAll) {
        AlertDialog(
            onDismissRequest = { confirmRevokeAll = false },
            title = { Text(stringResource(R.string.akar_revoke_all)) },
            text = { Text(stringResource(R.string.akar_revoke_all_body)) },
            confirmButton = {
                TextButton(onClick = { confirmRevokeAll = false; gate { viewModel.revokeAll() } }) {
                    Text(stringResource(R.string.akar_revoke_all_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeAll = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Tab 0 (Info) & Tab 1 (Root Apps)                                           */
/* -------------------------------------------------------------------------- */

@Composable
private fun InfoTab(
    ui: AkarUi,
    kaitUi: KaitUi,
    canUse: Boolean,
    gate: (() -> Unit) -> Unit,
    viewModel: AkarViewModel,
    kaitViewModel: ModulesViewModel,
    onJumpApps: (AppFilter) -> Unit,
    onJumpModules: () -> Unit,
    onOpenProcesses: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val isWorking = canUse && ui.state.enabled && !ui.state.frozen
    val isFrozen = canUse && ui.state.enabled && ui.state.frozen

    val heroBg by animateColorAsState(
        when {
            !canUse -> scheme.errorContainer
            isFrozen -> scheme.tertiaryContainer
            isWorking -> scheme.primaryContainer
            else -> scheme.surfaceContainerHigh
        },
        label = "heroBg",
    )
    val heroFg = when {
        !canUse -> scheme.onErrorContainer
        isFrozen -> scheme.onTertiaryContainer
        isWorking -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val heroBadgeBg = when {
        !canUse -> scheme.error
        isFrozen -> scheme.tertiary
        isWorking -> scheme.primary
        else -> scheme.outline
    }
    val heroBadgeFg = when {
        !canUse -> scheme.onError
        isFrozen -> scheme.onTertiary
        isWorking -> scheme.onPrimary
        else -> scheme.surface
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ui.bootguard.safeMode || ui.bootguard.disabledSuspects.isNotEmpty() || ui.bootguard.zygiskBypassed) {
            item("bg_info_banner") {
                BootguardRecoveryBanner(
                    status = ui.bootguard,
                    onReset = { gate { viewModel.clearSafeMode() } },
                )
            }
        }
        item("hero") {
            Surface(shape = LarasShapes.Hero, color = heroBg, contentColor = heroFg, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    BotanicalRootArt(
                        modifier = Modifier.matchParentSize(),
                        color = heroFg,
                        alpha = when {
                            isWorking -> 0.24f
                            isFrozen -> 0.16f
                            else -> 0.09f
                        },
                    )
                    Column(
                        Modifier.fillMaxWidth(0.78f).padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LarasMascotIcon(
                                    modifier = Modifier.size(22.dp),
                                    color = heroFg,
                                )
                                Text(
                                    stringResource(
                                        when {
                                            !canUse -> R.string.akar_status_unavailable_label
                                            isFrozen -> R.string.akar_status_frozen_label
                                            ui.state.enabled -> R.string.akar_status_working_label
                                            else -> R.string.akar_status_off_label
                                        },
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                LarasBadge(
                                    text = stringResource(
                                        when {
                                            isFrozen -> R.string.akar_badge_frozen
                                            isWorking -> R.string.akar_badge_working
                                            else -> R.string.akar_badge_off
                                        },
                                    ),
                                    containerColor = heroBadgeBg,
                                    contentColor = heroBadgeFg,
                                )
                            }
                            Text(
                                text = when {
                                    !ui.available -> stringResource(R.string.akar_unavailable)
                                    !ui.state.daemonInstalled -> stringResource(R.string.akar_no_daemon)
                                    isFrozen -> stringResource(R.string.akar_status_frozen)
                                    !ui.state.enabled -> stringResource(R.string.akar_status_off)
                                    else -> stringResource(R.string.akar_hero_sub_ready, ui.apps.size, ui.denied.size)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = heroFg.copy(alpha = 0.82f),
                                modifier = Modifier.padding(start = 30.dp),
                            )
                        }
                        if (canUse) {
                            Surface(
                                shape = LarasShapes.Pill,
                                color = if (ui.state.enabled) heroFg.copy(alpha = 0.14f) else scheme.primary,
                                contentColor = if (ui.state.enabled) heroFg else scheme.onPrimary,
                                onClick = {
                                    if (ui.state.enabled) viewModel.setEnabled(false) else gate { viewModel.setEnabled(true) }
                                },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(16.dp))
                                    Text(
                                        stringResource(if (ui.state.enabled) R.string.akar_disable_root else R.string.akar_allow_root),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (canUse) {
            item("bento_root") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val freezeBg by animateColorAsState(if (isFrozen) scheme.tertiaryContainer else LarasSurfaces.card, label = "freezeBg")
                    val freezeFg = if (isFrozen) scheme.onTertiaryContainer else scheme.onSurface
                    Surface(
                        shape = LarasShapes.Card,
                        color = freezeBg,
                        contentColor = freezeFg,
                        enabled = ui.state.enabled,
                        onClick = { if (ui.state.frozen) gate { viewModel.setFrozen(false) } else viewModel.setFrozen(true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AcUnit, null, Modifier.size(20.dp))
                                LarasBadge(
                                    text = stringResource(if (isFrozen) R.string.akar_freeze_on else R.string.akar_freeze_off),
                                    containerColor = freezeFg.copy(alpha = 0.12f),
                                    contentColor = freezeFg,
                                )
                            }
                            Column {
                                Text(stringResource(R.string.akar_freeze), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    stringResource(if (isFrozen) R.string.akar_freeze_tap_thaw else R.string.akar_freeze_tap_freeze),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = freezeFg.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    val hasDenied = ui.denied.isNotEmpty()
                    val appsBg = if (hasDenied) scheme.errorContainer else LarasSurfaces.card
                    val appsFg = if (hasDenied) scheme.onErrorContainer else scheme.onSurface
                    Surface(
                        shape = LarasShapes.Card,
                        color = appsBg,
                        contentColor = appsFg,
                        onClick = { onJumpApps(if (hasDenied) AppFilter.DENIED else AppFilter.ENROLLED) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Shield, null, Modifier.size(20.dp))
                                Text(
                                    if (hasDenied) stringResource(R.string.akar_bento_denied_count, ui.denied.size)
                                    else stringResource(R.string.akar_bento_enrolled_count, ui.apps.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column {
                                Text(
                                    stringResource(if (hasDenied) R.string.akar_bento_needs_review else R.string.akar_bento_enrolled),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                                Text(
                                    if (hasDenied) {
                                        ui.denied.joinToString(", ") { d ->
                                            if (d.packageName.isEmpty()) "uid ${d.uid}" else viewModel.repository().labelOf(d.packageName)
                                        }
                                    } else if (ui.apps.isEmpty()) {
                                        stringResource(R.string.akar_bento_no_enrolled)
                                    } else {
                                        ui.apps.joinToString(", ") { viewModel.repository().labelOf(it.primaryPackage) }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appsFg.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (ui.termuxInstalled && !ui.termuxEnrolled) {
                item("termux_hint") {
                    LarasNavRow(
                        title = stringResource(R.string.akar_termux_hint),
                        leading = { AppIcon(RootRepository.TERMUX, size = 34.dp) },
                        containerColor = scheme.secondaryContainer,
                        shape = LarasShapes.Card,
                        onClick = { onOpenApp(RootRepository.TERMUX) },
                    )
                }
            }
        }

        item("bento_kait") {
            val activeMods = kaitUi.modules.count { it.enabled && it.problem == null }
            val procCount = kaitUi.injectedProcesses.size
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = LarasShapes.Card, color = LarasSurfaces.card, onClick = onJumpModules, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Extension, null, Modifier.size(20.dp), tint = if (activeMods > 0) scheme.primary else scheme.onSurfaceVariant)
                            LarasBadge(stringResource(R.string.kait_badge_active), tone = Tone.GOOD)
                        }
                        Column {
                            Text(stringResource(R.string.kait_bento_modules), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                stringResource(R.string.kait_bento_modules_sub, activeMods, kaitUi.modules.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Surface(shape = LarasShapes.Card, color = LarasSurfaces.card, onClick = onOpenProcesses, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Memory, null, Modifier.size(20.dp), tint = if (procCount > 0) scheme.primary else scheme.onSurfaceVariant)
                            Text("$procCount", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (procCount > 0) scheme.primary else scheme.onSurfaceVariant)
                        }
                        Column {
                            Text(stringResource(R.string.kait_bento_processes), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                stringResource(R.string.kait_bento_processes_sub, procCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (kaitUi.crashBypassed.isNotEmpty()) {
            item("kait_crash_bypass") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kaitUi.crashBypassed.forEach { pkg ->
                        CrashBypassBanner(pkg = pkg, onReset = { kaitViewModel.clearCrashBypass(pkg) })
                    }
                }
            }
        }

        item("system_info") {
            Surface(shape = LarasShapes.Card, color = LarasSurfaces.card, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoSpecRow(
                        Icons.Rounded.Memory,
                        stringResource(R.string.akar_info_daemon),
                        stringResource(if (ui.state.daemonRunning) R.string.akar_info_daemon_running else R.string.akar_info_daemon_stopped),
                    )
                    InfoSpecRow(
                        Icons.Rounded.Extension,
                        stringResource(R.string.kait_arch_engine),
                        "${kaitUi.frameworkVersion} · ${stringResource(R.string.kait_arch_engine_val)}",
                    )
                    InfoSpecRow(
                        Icons.Rounded.Security,
                        stringResource(R.string.akar_bento_selinux),
                        "${stringResource(R.string.akar_bento_selinux_state)} · ${stringResource(R.string.akar_bento_selinux_sub)}",
                    )
                    InfoSpecRow(Icons.Rounded.DeveloperBoard, stringResource(R.string.akar_info_kernel), ui.device.kernelRelease)
                    InfoSpecRow(Icons.Rounded.Smartphone, stringResource(R.string.akar_info_device), "${ui.device.model} · Android ${ui.device.androidVersion}")
                    InfoSpecRow(Icons.Rounded.Link, stringResource(R.string.akar_info_trust), stringResource(R.string.akar_info_trust_val))
                    InfoSpecRow(Icons.Rounded.Security, stringResource(R.string.akar_info_control), stringResource(R.string.akar_info_control_val, ui.device.buildType))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsTab(
    ui: AkarUi,
    canUse: Boolean,
    repo: RootRepository,
    showSearch: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    filter: AppFilter,
    onFilter: (AppFilter) -> Unit,
    showSystem: Boolean,
    onToggleSystem: () -> Unit,
    onRefresh: () -> Unit,
    onClearDenied: () -> Unit,
    onOpenApp: (String) -> Unit,
) {
    if (!canUse) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            LarasEmptyState(title = stringResource(if (!ui.available) R.string.akar_unavailable else R.string.akar_no_daemon))
        }
        return
    }

    val needle = query.trim()
    val grantByUid = remember(ui.grants) { ui.grants.associateBy { it.uid } }
    val deniedByUid = remember(ui.denied) { ui.denied.associateBy { it.uid } }

    val filteredDenied = remember(ui.denied, needle, filter) {
        if (filter == AppFilter.ENROLLED) emptyList()
        else ui.denied.filter { d ->
            d.uid !in grantByUid && (
                needle.isEmpty() || d.packageName.contains(needle, true) ||
                    repo.labelOf(d.packageName).contains(needle, true) || d.uid.toString().contains(needle)
                )
        }
    }
    val filteredEnrolled = remember(ui.grants, needle, filter) {
        if (filter == AppFilter.DENIED) emptyList()
        else ui.grants.filter { g ->
            needle.isEmpty() || g.packages.any { p -> p.contains(needle, true) || repo.labelOf(p).contains(needle, true) } ||
                g.uid.toString().contains(needle)
        }
    }
    val filteredOthers = remember(ui.installedApps, grantByUid, deniedByUid, needle, filter, showSystem) {
        if (filter != AppFilter.ALL) emptyList()
        else ui.installedApps.filter { app ->
            app.uid !in grantByUid && app.uid !in deniedByUid && (showSystem || !app.isSystem || needle.isNotEmpty()) && (
                needle.isEmpty() || app.label.contains(needle, true) ||
                    app.sharedPackages.any { it.contains(needle, true) } || app.uid.toString().contains(needle)
                )
        }
    }

    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap),
        ) {
            larasStickyControls(key = "akar_apps_sticky_controls") {
                AnimatedVisibility(visible = showSearch || query.isNotEmpty()) {
                    LarasSearchBar(
                        query = query,
                        onQueryChange = onQuery,
                        placeholder = stringResource(R.string.akar_search_apps),
                    )
                }
                LarasFilterRow(
                    options = AppFilter.entries,
                    selected = filter,
                    label = {
                        when (it) {
                            AppFilter.ALL -> stringResource(R.string.akar_filter_all)
                            AppFilter.ENROLLED -> stringResource(R.string.akar_filter_enrolled, ui.grants.size)
                            AppFilter.DENIED -> stringResource(R.string.akar_filter_denied, ui.denied.size)
                        }
                    },
                    leadingIcon = {
                        when (it) {
                            AppFilter.ALL -> Icons.Rounded.Apps
                            AppFilter.ENROLLED -> Icons.Rounded.CheckCircle
                            AppFilter.DENIED -> Icons.Rounded.Block
                        }
                    },
                    onSelect = onFilter,
                    trailing = {
                        FilterChip(
                            selected = showSystem,
                            onClick = onToggleSystem,
                            label = { Text(stringResource(R.string.akar_filter_system)) },
                            leadingIcon = { Icon(Icons.Rounded.Android, null, Modifier.size(FilterChipDefaults.IconSize)) },
                        )
                    },
                )
            }

            if (filteredDenied.isNotEmpty()) {
                item("denied_hdr") {
                    LarasSectionHeader(
                        title = stringResource(R.string.akar_denied),
                        actionLabel = stringResource(R.string.akar_denied_clear),
                        onAction = onClearDenied,
                    )
                }
                items(filteredDenied.size, key = { "den:${filteredDenied[it].uid}" }) { i ->
                    val d = filteredDenied[i]
                    DeniedAppRow(d, repo, groupedShape(i, filteredDenied.size)) {
                        if (d.packageName.isNotEmpty()) onOpenApp(d.packageName)
                    }
                }
            }

            if (filteredEnrolled.isNotEmpty()) {
                item("enr_hdr") { LarasSectionHeader(stringResource(R.string.akar_bento_enrolled)) }
                items(filteredEnrolled.size, key = { "enr:${filteredEnrolled[it].uid}" }) { i ->
                    val g = filteredEnrolled[i]
                    EnrolledAppRow(g, repo, groupedShape(i, filteredEnrolled.size)) { onOpenApp(g.primaryPackage) }
                }
            }

            if (filteredOthers.isNotEmpty()) {
                item("oth_hdr") { LarasSectionHeader(stringResource(R.string.akar_apps)) }
                items(filteredOthers.size, key = { "oth:${filteredOthers[it].uid}" }) { i ->
                    val app = filteredOthers[i]
                    InstalledAppRow(app, groupedShape(i, filteredOthers.size)) { onOpenApp(app.packageName) }
                }
            }

            if (filteredDenied.isEmpty() && filteredEnrolled.isEmpty() && filteredOthers.isEmpty()) {
                item("empty") { LarasEmptyState(title = stringResource(R.string.akar_no_matching_apps)) }
            }
        }
    }
}

@Composable
private fun EnrolledAppRow(grant: RootGrant, repo: RootRepository, shape: Shape, onClick: () -> Unit) {
    val isAdb = grant.uid == RootRepository.UID_SHELL
    val title = if (isAdb) stringResource(R.string.akar_adb_title) else repo.labelOf(grant.primaryPackage)
    Surface(shape = shape, color = LarasSurfaces.card, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isAdb) {
                Surface(Modifier.size(38.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Terminal, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            } else AppIcon(grant.primaryPackage, size = 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    TierChip(grant.tier)
                    LarasBadge(scopeShortLabel(grant), tone = Tone.NEUTRAL)
                    if (grant.packages.size > 1) {
                        LarasBadge(stringResource(R.string.akar_badge_shared, grant.packages.size - 1), tone = Tone.GOOD)
                    }
                }
                Text(
                    "${grant.primaryPackage} · ${grantSummaryLine(grant)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeniedAppRow(attempt: DeniedAttempt, repo: RootRepository, shape: Shape, onClick: () -> Unit) {
    Surface(shape = shape, color = LarasSurfaces.card, enabled = attempt.packageName.isNotEmpty(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (attempt.packageName.isNotEmpty()) AppIcon(attempt.packageName, size = 38.dp)
            else Icon(Icons.Rounded.Lock, null, Modifier.size(38.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (attempt.packageName.isEmpty()) "uid ${attempt.uid}" else repo.labelOf(attempt.packageName),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    LarasBadge(stringResource(R.string.akar_badge_denied_count, attempt.count), tone = Tone.BAD)
                }
                Text(
                    stringResource(
                        R.string.akar_denied_row,
                        attempt.count,
                        DateUtils.getRelativeTimeSpanString(attempt.lastMs, System.currentTimeMillis(), MINUTE_MS).toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (attempt.packageName.isNotEmpty()) {
                Text(stringResource(R.string.akar_review), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun InstalledAppRow(app: InstalledApp, shape: Shape, onClick: () -> Unit) {
    Surface(shape = shape, color = LarasSurfaces.card, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app.packageName, size = 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (app.isSystem) LarasBadge(stringResource(R.string.akar_badge_system), tone = Tone.NEUTRAL)
                    if (app.sharedPackages.size > 1) {
                        LarasBadge(stringResource(R.string.akar_badge_shared, app.sharedPackages.size - 1), tone = Tone.NEUTRAL)
                    }
                }
                Text(
                    "${app.packageName} · uid ${app.uid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
