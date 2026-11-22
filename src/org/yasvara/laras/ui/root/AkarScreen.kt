/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.root.AkarShortcuts
import org.yasvara.laras.data.root.DeniedAttempt
import org.yasvara.laras.data.root.InstalledApp
import org.yasvara.laras.data.root.RootGrant
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.data.root.RootScope
import org.yasvara.laras.data.root.RootTier
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.groupedShape
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

private enum class AppFilter { ALL, ENROLLED, DENIED }

/**
 * Unified Akar & Kait Manager:
 * 5-tab Bento M3 layout (Info · Root · Modul · Repositori · Setelan) combining ROM-native
 * Root access (Akar) and ROM-native Rust+ART Xposed modules & online repository (Kait).
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

    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 4), pageCount = { 5 })
    var handledFocus by rememberSaveable(focus) { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var appQuery by rememberSaveable { mutableStateOf("") }
    var appFilter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    var showAdbDialog by rememberSaveable { mutableStateOf(false) }
    var showIdleDialog by rememberSaveable { mutableStateOf(false) }
    var confirmRevokeAll by rememberSaveable { mutableStateOf(false) }

    var showModuleSearch by rememberSaveable { mutableStateOf(false) }
    var moduleQuery by rememberSaveable { mutableStateOf("") }
    var moduleFilter by rememberSaveable { mutableStateOf(ModuleListFilter.ALL) }
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val titles = listOf(
        stringResource(R.string.akar_title),
        stringResource(R.string.akar_tab_apps),
        stringResource(R.string.kait_tab_modules),
        stringResource(R.string.kait_tab_repo),
        stringResource(R.string.akar_tab_settings),
    )

    val activeModulesCount = kaitUi.modules.count { it.enabled && it.problem == null }
    val moduleUpdatesCount = kaitUi.upgradableModulesCount

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(titles[pagerState.currentPage]) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    AnimatedVisibility(visible = pagerState.currentPage == 1) {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) appQuery = ""
                        }) {
                            Icon(
                                if (showSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                                stringResource(R.string.akar_search_apps),
                            )
                        }
                    }
                    AnimatedVisibility(visible = pagerState.currentPage == 2) {
                        IconButton(onClick = {
                            showModuleSearch = !showModuleSearch
                            if (!showModuleSearch) moduleQuery = ""
                        }) {
                            Icon(
                                if (showModuleSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                                stringResource(R.string.kait_search_modules),
                            )
                        }
                    }
                    IconButton(onClick = { showProcessesSheet = true }) {
                        BadgedBox(badge = {
                            if (kaitUi.injectedProcesses.isNotEmpty()) {
                                Badge { Text("${kaitUi.injectedProcesses.size}") }
                            }
                        }) {
                            Icon(
                                Icons.Rounded.Memory,
                                stringResource(R.string.kait_bento_processes),
                            )
                        }
                    }
                    IconButton(onClick = onOpenLogs) {
                        BadgedBox(badge = {
                            if (ui.denied.isNotEmpty()) Badge()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Article,
                                stringResource(R.string.akar_logs_title),
                            )
                        }
                    }
                    if (pagerState.currentPage == 2 || pagerState.currentPage == 3) {
                        IconButton(onClick = {
                            if (pagerState.currentPage == 3) {
                                kaitViewModel.refreshOnlineRepo(forceNetwork = true)
                            } else {
                                kaitViewModel.refresh(loadRepo = true)
                            }
                        }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                stringResource(R.string.akar_refresh),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surface,
                    scrolledContainerColor = surface,
                ),
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
                    alwaysShowLabel = true,
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = {
                        BadgedBox(badge = {
                            if (ui.denied.isNotEmpty()) Badge { Text("${ui.denied.size}") }
                        }) {
                            Icon(Icons.Rounded.Shield, null)
                        }
                    },
                    label = { Text(stringResource(R.string.akar_tab_apps)) },
                    alwaysShowLabel = true,
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = {
                        BadgedBox(badge = {
                            if (activeModulesCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ) {
                                    Text("$activeModulesCount")
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Extension, null)
                        }
                    },
                    label = { Text(stringResource(R.string.kait_tab_modules)) },
                    alwaysShowLabel = true,
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 3,
                    onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                    icon = {
                        BadgedBox(badge = {
                            if (moduleUpdatesCount > 0) Badge { Text("$moduleUpdatesCount") }
                        }) {
                            Icon(Icons.Rounded.CloudDownload, null)
                        }
                    },
                    label = { Text(stringResource(R.string.kait_tab_repo)) },
                    alwaysShowLabel = true,
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 4,
                    onClick = { scope.launch { pagerState.animateScrollToPage(4) } },
                    icon = { Icon(Icons.Rounded.Settings, null) },
                    label = { Text(stringResource(R.string.akar_tab_settings)) },
                    alwaysShowLabel = true,
                )
            }
        },
    ) { padding ->
        if (ui.loading && pagerState.currentPage != 2 && pagerState.currentPage != 3) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val canUse = ui.available && ui.state.daemonInstalled
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            when (page) {
                0 -> InfoTab(
                    ui = ui,
                    kaitUi = kaitUi,
                    canUse = canUse,
                    gate = gate,
                    viewModel = viewModel,
                    kaitViewModel = kaitViewModel,
                    onJumpApps = { filter ->
                        appFilter = filter
                        scope.launch { pagerState.animateScrollToPage(1) }
                    },
                    onJumpModules = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onOpenProcesses = { showProcessesSheet = true },
                    onOpenAdbDialog = { showAdbDialog = true },
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
                2 -> KaitModulesTab(
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
                3 -> KaitRepoTab(
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
                else -> SettingsTab(
                    ui = ui,
                    canUse = canUse,
                    gate = gate,
                    viewModel = viewModel,
                    onOpenAdbDialog = { showAdbDialog = true },
                    onOpenIdleDialog = { showIdleDialog = true },
                    onConfirmRevokeAll = { confirmRevokeAll = true },
                    onPinShortcut = {
                        val requested = AkarShortcuts.requestPin(context)
                        viewModel.refreshShortcutState()
                        val msg = context.getString(
                            if (requested) R.string.akar_pin_shortcut_done else R.string.akar_pin_unsupported,
                        )
                        scope.launch { snackbar.showSnackbar(msg) }
                    },
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
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.kait_restarted_targets, 1))
                    }
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
            onOpenUi = {
                actionSheetModulePkg = null
                kaitViewModel.repository.openModuleUi(sheetModule.packageName)
            },
            onOpenScope = {
                actionSheetModulePkg = null
                onOpenScope(sheetModule.packageName)
            },
            onRestartTargets = {
                actionSheetModulePkg = null
                kaitViewModel.restartTargetsOf(sheetModule) { count ->
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.kait_restarted_targets, count))
                    }
                }
            },
            onViewInRepo = {
                actionSheetModulePkg = null
                if (onlineMatch != null) {
                    kaitViewModel.openOnlineModuleDetail(onlineMatch)
                }
            },
            onAppInfo = {
                actionSheetModulePkg = null
                kaitViewModel.repository.openAppInfo(sheetModule.packageName)
            },
            onUninstall = {
                actionSheetModulePkg = null
                kaitViewModel.repository.requestUninstall(sheetModule.packageName)
            },
        )
    }

    val detailModule = kaitUi.selectedOnlineModule
    if (detailModule != null) {
        val installedModule = kaitUi.modules.find { it.packageName == detailModule.packageName }
        OnlineModuleDetailSheet(
            module = detailModule,
            loading = kaitUi.detailLoading,
            installedModule = installedModule,
            hasUpdate = kaitUi.hasRepoUpdate(detailModule),
            onDismiss = kaitViewModel::dismissOnlineModuleDetail,
            onOpenScope = {
                kaitViewModel.dismissOnlineModuleDetail()
                onOpenScope(detailModule.packageName)
            },
            onOpenModuleUi = {
                kaitViewModel.dismissOnlineModuleDetail()
                kaitViewModel.repository.openModuleUi(detailModule.packageName)
            },
        )
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
            onDisable = {
                showAdbDialog = false
                viewModel.setAdbShell(false)
            },
            onOpenAdvanced = {
                showAdbDialog = false
                onOpenApp(RootRepository.SHELL_PACKAGE)
            },
        )
    }

    if (showIdleDialog) {
        IdleDaysDialog(
            currentDays = ui.idleDays,
            onDismiss = { showIdleDialog = false },
            onSelect = { days ->
                showIdleDialog = false
                viewModel.setOption("idle_days", days)
            },
        )
    }

    if (confirmRevokeAll) {
        AlertDialog(
            onDismissRequest = { confirmRevokeAll = false },
            title = { Text(stringResource(R.string.akar_revoke_all)) },
            text = { Text(stringResource(R.string.akar_revoke_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRevokeAll = false
                    gate { viewModel.revokeAll() }
                }) {
                    Text(
                        stringResource(R.string.akar_revoke_all_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevokeAll = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

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
    onOpenAdbDialog: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 1. Working Hero Bento Card with inline Allow/Disable button & right botanical Root SVG
        item("hero") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = heroBg,
                contentColor = heroFg,
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                        Modifier
                            .fillMaxWidth(0.74f)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = when {
                                        !canUse || !ui.state.enabled -> Icons.Rounded.ErrorOutline
                                        isFrozen -> Icons.Rounded.AcUnit
                                        else -> Icons.Rounded.CheckCircle
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(
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
                                CompactTag(
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
                                    else -> stringResource(
                                        R.string.akar_hero_sub_ready,
                                        ui.apps.size,
                                        ui.denied.size,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = heroFg.copy(alpha = 0.82f),
                                modifier = Modifier.padding(start = 26.dp),
                            )
                        }

                        if (canUse) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (ui.state.enabled) {
                                    heroFg.copy(alpha = 0.14f)
                                } else {
                                    scheme.primary
                                },
                                contentColor = if (ui.state.enabled) heroFg else scheme.onPrimary,
                                onClick = {
                                    if (ui.state.enabled) {
                                        viewModel.setEnabled(false)
                                    } else {
                                        gate { viewModel.setEnabled(true) }
                                    }
                                },
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.PowerSettingsNew,
                                        null,
                                        Modifier.size(15.dp),
                                    )
                                    Text(
                                        stringResource(
                                            if (ui.state.enabled) R.string.akar_disable_root
                                            else R.string.akar_allow_root,
                                        ),
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
            // 2. Bento 2-Column Grid Row 1: Root Freeze + Enrolled/Denied Apps
            item("bento_row1") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val freezeBg by animateColorAsState(
                        if (isFrozen) scheme.tertiaryContainer else scheme.surfaceBright,
                        label = "freezeBg",
                    )
                    val freezeFg = if (isFrozen) scheme.onTertiaryContainer else scheme.onSurface
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = freezeBg,
                        contentColor = freezeFg,
                        enabled = ui.state.enabled,
                        onClick = {
                            if (ui.state.frozen) gate { viewModel.setFrozen(false) }
                            else viewModel.setFrozen(true)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.AcUnit, null, Modifier.size(18.dp))
                                CompactTag(
                                    text = stringResource(
                                        if (isFrozen) R.string.akar_freeze_on else R.string.akar_freeze_off,
                                    ),
                                    containerColor = freezeFg.copy(alpha = 0.12f),
                                    contentColor = freezeFg,
                                )
                            }
                            Column {
                                Text(
                                    stringResource(R.string.akar_freeze),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(
                                        if (isFrozen) R.string.akar_freeze_tap_thaw
                                        else R.string.akar_freeze_tap_freeze,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = freezeFg.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    val hasDenied = ui.denied.isNotEmpty()
                    val appsBg = if (hasDenied) scheme.errorContainer else scheme.surfaceBright
                    val appsFg = if (hasDenied) scheme.onErrorContainer else scheme.onSurface
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = appsBg,
                        contentColor = appsFg,
                        onClick = {
                            onJumpApps(if (hasDenied) AppFilter.DENIED else AppFilter.ENROLLED)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Shield, null, Modifier.size(18.dp))
                                Text(
                                    text = if (hasDenied) {
                                        stringResource(R.string.akar_bento_denied_count, ui.denied.size)
                                    } else {
                                        stringResource(R.string.akar_bento_enrolled_count, ui.apps.size)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column {
                                Text(
                                    stringResource(
                                        if (hasDenied) R.string.akar_bento_needs_review
                                        else R.string.akar_bento_enrolled,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (hasDenied) {
                                        ui.denied.joinToString(", ") { d ->
                                            if (d.packageName.isEmpty()) "uid ${d.uid}"
                                            else viewModel.repository().labelOf(d.packageName)
                                        }
                                    } else if (ui.apps.isEmpty()) {
                                        stringResource(R.string.akar_bento_no_enrolled)
                                    } else {
                                        ui.apps.joinToString(", ") {
                                            viewModel.repository().labelOf(it.primaryPackage)
                                        }
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

            // 3. Bento 2-Column Grid Row 2: ADB Shell Su + SELinux
            item("bento_row2") {
                val adb = ui.adbShell
                val adbBg = if (adb != null) scheme.secondaryContainer else scheme.surfaceBright
                val adbFg = if (adb != null) scheme.onSecondaryContainer else scheme.onSurface
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = adbBg,
                        contentColor = adbFg,
                        onClick = onOpenAdbDialog,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Terminal, null, Modifier.size(18.dp))
                                Text(
                                    text = if (adb != null) scopeShortLabel(adb)
                                    else stringResource(R.string.akar_bento_adb_off),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Column {
                                Text(
                                    stringResource(R.string.akar_adb_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                                Text(
                                    if (adb != null) {
                                        stringResource(
                                            if (adb.tier == RootTier.ROOT) R.string.akar_tier_root
                                            else R.string.akar_tier_shell,
                                        )
                                    } else {
                                        stringResource(R.string.akar_bento_adb_tap)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = adbFg.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.surfaceBright,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Security,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = scheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(R.string.akar_bento_selinux_state),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Column {
                                Text(
                                    stringResource(R.string.akar_bento_selinux),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                                Text(
                                    stringResource(R.string.akar_bento_selinux_sub),
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

            // Conditional Termux quick setup banner
            if (ui.termuxInstalled && !ui.termuxEnrolled) {
                item("termux_hint") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        onClick = { onOpenApp(RootRepository.TERMUX) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(RootRepository.TERMUX, size = 32.dp)
                            Text(
                                stringResource(R.string.akar_termux_hint),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // Kait Runtime Bento Row: Installed Modules + Live Hooked Processes
        item("bento_kait_row") {
            val activeMods = kaitUi.modules.count { it.enabled && it.problem == null }
            val totalMods = kaitUi.modules.size
            val procCount = kaitUi.injectedProcesses.size
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceBright,
                    onClick = onJumpModules,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Extension,
                                null,
                                Modifier.size(18.dp),
                                tint = if (activeMods > 0) scheme.primary else scheme.onSurfaceVariant,
                            )
                            CompactTag(
                                text = stringResource(R.string.kait_badge_active),
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.kait_bento_modules),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(R.string.kait_bento_modules_sub, activeMods, totalMods),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceBright,
                    onClick = onOpenProcesses,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Memory,
                                null,
                                Modifier.size(18.dp),
                                tint = if (procCount > 0) scheme.primary else scheme.onSurfaceVariant,
                            )
                            Text(
                                text = "$procCount",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (procCount > 0) scheme.primary else scheme.onSurfaceVariant,
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.kait_bento_processes),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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

        // 4. Dense System Info Card (ReSukiSU reference layout)
        item("system_info") {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = scheme.surfaceBright,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    InfoSpecRow(
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.akar_info_daemon),
                        value = stringResource(
                            if (ui.state.daemonRunning) R.string.akar_info_daemon_running
                            else R.string.akar_info_daemon_stopped,
                        ),
                    )
                    InfoSpecRow(
                        icon = Icons.Rounded.Extension,
                        title = stringResource(R.string.kait_arch_engine),
                        value = "${kaitUi.frameworkVersion} · ${stringResource(R.string.kait_arch_engine_val)}",
                    )
                    InfoSpecRow(
                        icon = Icons.Rounded.DeveloperBoard,
                        title = stringResource(R.string.akar_info_kernel),
                        value = ui.device.kernelRelease,
                    )
                    InfoSpecRow(
                        icon = Icons.Rounded.Smartphone,
                        title = stringResource(R.string.akar_info_device),
                        value = "${ui.device.model} · Android ${ui.device.androidVersion}",
                    )
                    InfoSpecRow(
                        icon = Icons.Rounded.Link,
                        title = stringResource(R.string.akar_info_trust),
                        value = stringResource(R.string.akar_info_trust_val),
                    )
                    InfoSpecRow(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.akar_info_control),
                        value = stringResource(R.string.akar_info_control_val, ui.device.buildType),
                    )
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
            Text(
                stringResource(if (!ui.available) R.string.akar_unavailable else R.string.akar_no_daemon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                needle.isEmpty() ||
                    d.packageName.contains(needle, ignoreCase = true) ||
                    repo.labelOf(d.packageName).contains(needle, ignoreCase = true) ||
                    d.uid.toString().contains(needle)
                )
        }
    }

    val filteredEnrolled = remember(ui.grants, needle, filter) {
        if (filter == AppFilter.DENIED) emptyList()
        else ui.grants.filter { g ->
            needle.isEmpty() ||
                g.packages.any { p ->
                    p.contains(needle, ignoreCase = true) ||
                        repo.labelOf(p).contains(needle, ignoreCase = true)
                } ||
                g.uid.toString().contains(needle)
        }
    }

    val filteredOthers = remember(ui.installedApps, grantByUid, deniedByUid, needle, filter, showSystem) {
        if (filter != AppFilter.ALL) emptyList()
        else ui.installedApps.filter { app ->
            app.uid !in grantByUid &&
                app.uid !in deniedByUid &&
                (showSystem || !app.isSystem || needle.isNotEmpty()) &&
                (
                    needle.isEmpty() ||
                        app.label.contains(needle, ignoreCase = true) ||
                        app.sharedPackages.any { it.contains(needle, ignoreCase = true) } ||
                        app.uid.toString().contains(needle)
                    )
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (showSearch) {
                item("search") {
                    TextField(
                        value = query,
                        onValueChange = onQuery,
                        placeholder = { Text(stringResource(R.string.akar_search_apps)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQuery("") }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }

            item("filters") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == AppFilter.ALL,
                        onClick = { onFilter(AppFilter.ALL) },
                        label = { Text(stringResource(R.string.akar_filter_all)) },
                    )
                    FilterChip(
                        selected = filter == AppFilter.ENROLLED,
                        onClick = { onFilter(AppFilter.ENROLLED) },
                        label = { Text(stringResource(R.string.akar_filter_enrolled, ui.grants.size)) },
                    )
                    FilterChip(
                        selected = filter == AppFilter.DENIED,
                        onClick = { onFilter(AppFilter.DENIED) },
                        label = { Text(stringResource(R.string.akar_filter_denied, ui.denied.size)) },
                    )
                    FilterChip(
                        selected = showSystem,
                        onClick = onToggleSystem,
                        label = { Text(stringResource(R.string.akar_filter_system)) },
                    )
                }
            }

            if (filteredDenied.isNotEmpty()) {
                item("denied_hdr") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.akar_denied),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onClearDenied) {
                            Text(stringResource(R.string.akar_denied_clear))
                        }
                    }
                }
                items(filteredDenied.size, key = { "den:${filteredDenied[it].uid}" }) { i ->
                    val d = filteredDenied[i]
                    DeniedAppRow(
                        attempt = d,
                        repo = repo,
                        shape = groupedShape(i, filteredDenied.size),
                        onClick = if (d.packageName.isNotEmpty()) {
                            { onOpenApp(d.packageName) }
                        } else null,
                    )
                }
                item("den_sp") { Spacer(Modifier.height(10.dp)) }
            }

            if (filteredEnrolled.isNotEmpty()) {
                item("enr_hdr") {
                    Text(
                        stringResource(R.string.akar_bento_enrolled),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
                    )
                }
                items(filteredEnrolled.size, key = { "enr:${filteredEnrolled[it].uid}" }) { i ->
                    val g = filteredEnrolled[i]
                    EnrolledAppRow(
                        grant = g,
                        repo = repo,
                        shape = groupedShape(i, filteredEnrolled.size),
                        onClick = { onOpenApp(g.primaryPackage) },
                    )
                }
                item("enr_sp") { Spacer(Modifier.height(10.dp)) }
            }

            if (filteredOthers.isNotEmpty()) {
                item("oth_hdr") {
                    Text(
                        stringResource(R.string.akar_apps),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 6.dp),
                    )
                }
                items(filteredOthers.size, key = { "oth:${filteredOthers[it].uid}" }) { i ->
                    val app = filteredOthers[i]
                    InstalledAppRow(
                        app = app,
                        shape = groupedShape(i, filteredOthers.size),
                        onClick = { onOpenApp(app.packageName) },
                    )
                }
            }

            if (filteredDenied.isEmpty() && filteredEnrolled.isEmpty() && filteredOthers.isEmpty()) {
                item("empty") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.akar_no_matching_apps),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrolledAppRow(
    grant: RootGrant,
    repo: RootRepository,
    shape: Shape,
    onClick: () -> Unit,
) {
    val isAdb = grant.uid == RootRepository.UID_SHELL
    val title = if (isAdb) stringResource(R.string.akar_adb_title) else repo.labelOf(grant.primaryPackage)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isAdb) {
                Surface(
                    Modifier.size(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Terminal,
                            null,
                            Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            } else {
                AppIcon(grant.primaryPackage, size = 38.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    TierChip(grant.tier)
                    CompactTag(
                        text = scopeShortLabel(grant),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (grant.packages.size > 1) {
                        CompactTag(
                            text = stringResource(R.string.akar_badge_shared, grant.packages.size - 1),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
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
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DeniedAppRow(
    attempt: DeniedAttempt,
    repo: RootRepository,
    shape: Shape,
    onClick: (() -> Unit)?,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceBright,
        enabled = onClick != null,
        onClick = onClick ?: {},
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (attempt.packageName.isNotEmpty()) {
                AppIcon(attempt.packageName, size = 38.dp)
            } else {
                Icon(Icons.Rounded.Lock, null, Modifier.size(38.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (attempt.packageName.isEmpty()) "uid ${attempt.uid}"
                        else repo.labelOf(attempt.packageName),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    CompactTag(
                        text = stringResource(R.string.akar_badge_denied_count, attempt.count),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                Text(
                    stringResource(
                        R.string.akar_denied_row,
                        attempt.count,
                        DateUtils.getRelativeTimeSpanString(
                            attempt.lastMs,
                            System.currentTimeMillis(),
                            MINUTE_MS,
                        ).toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onClick != null) {
                Text(
                    stringResource(R.string.akar_review),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledApp,
    shape: Shape,
    onClick: () -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app.packageName, size = 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (app.isSystem) {
                        CompactTag(
                            text = stringResource(R.string.akar_badge_system),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (app.sharedPackages.size > 1) {
                        CompactTag(
                            text = stringResource(R.string.akar_badge_shared, app.sharedPackages.size - 1),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsTab(
    ui: AkarUi,
    canUse: Boolean,
    gate: (() -> Unit) -> Unit,
    viewModel: AkarViewModel,
    onOpenAdbDialog: () -> Unit,
    onOpenIdleDialog: () -> Unit,
    onConfirmRevokeAll: () -> Unit,
    onPinShortcut: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Bento 2-Column Shortcut & App Drawer Controls
        item("shortcuts") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surfaceBright,
                    enabled = ui.pinSupported,
                    onClick = onPinShortcut,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            null,
                            Modifier.size(18.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                        Column {
                            Text(
                                stringResource(R.string.akar_pin_shortcut),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                stringResource(
                                    if (ui.shortcutPinned) R.string.akar_pin_shortcut_done
                                    else R.string.akar_pin_shortcut_hint,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                val drawerBg = if (ui.drawerEnabled) scheme.secondaryContainer else scheme.surfaceBright
                val drawerFg = if (ui.drawerEnabled) scheme.onSecondaryContainer else scheme.onSurface
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = drawerBg,
                    contentColor = drawerFg,
                    onClick = { viewModel.setDrawerEnabled(!ui.drawerEnabled) },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
                            CompactTag(
                                text = stringResource(
                                    if (ui.drawerEnabled) R.string.akar_freeze_on else R.string.akar_freeze_off,
                                ),
                                containerColor = drawerFg.copy(alpha = 0.12f),
                                contentColor = drawerFg,
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.akar_drawer_icon),
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                stringResource(R.string.akar_drawer_icon_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = drawerFg.copy(alpha = 0.72f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (canUse) {
            // 2. Access & Behaviour Card
            item("access_behaviour") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.surfaceBright,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.akar_access_behaviour),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                        )

                        // ADB shell su with duration picker + quick toggle
                        val adb = ui.adbShell
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenAdbDialog),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Terminal,
                                null,
                                Modifier.size(20.dp),
                                tint = scheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.akar_adb_title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    if (adb != null) {
                                        stringResource(
                                            R.string.akar_adb_active,
                                            scopeShortLabel(adb),
                                            stringResource(
                                                if (adb.tier == RootTier.ROOT) R.string.akar_tier_root
                                                else R.string.akar_tier_shell,
                                            ),
                                        )
                                    } else {
                                        "${stringResource(R.string.akar_adb_off)} · ${adbPresetLabel(ui.adbPreset)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            CheckSwitch(
                                checked = adb != null,
                                modifier = Modifier.toggleable(
                                    value = adb != null,
                                    role = Role.Switch,
                                    onValueChange = { on ->
                                        if (on) gate { viewModel.setAdbShell(true) }
                                        else viewModel.setAdbShell(false)
                                    },
                                ),
                            )
                        }

                        SettingSwitchItem(
                            icon = Icons.Rounded.Notifications,
                            title = stringResource(R.string.akar_notify_denied),
                            summary = stringResource(R.string.akar_notify_denied_summary),
                            checked = ui.notifyDenied,
                            onChange = { viewModel.setOption("notify_denied", if (it) 1 else 0) },
                        )

                        SettingSwitchItem(
                            icon = Icons.AutoMirrored.Rounded.Article,
                            title = stringResource(R.string.akar_log_full),
                            summary = stringResource(R.string.akar_log_full_summary),
                            checked = ui.logFull,
                            onChange = { viewModel.setOption("log_full", if (it) 1 else 0) },
                        )

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenIdleDialog),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Schedule,
                                null,
                                Modifier.size(20.dp),
                                tint = scheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.akar_idle),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    stringResource(R.string.akar_idle_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            CompactTag(
                                text = if (ui.idleDays == 0) {
                                    stringResource(R.string.akar_idle_never)
                                } else {
                                    stringResource(R.string.akar_idle_days, ui.idleDays)
                                },
                                containerColor = scheme.surfaceContainerHigh,
                                contentColor = scheme.onSurface,
                            )
                        }
                    }
                }
            }

            // 3. Safety & Diagnostics Card
            item("safety") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = scheme.surfaceBright,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.akar_safety),
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                        )

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = viewModel::runSelfTest),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Science,
                                null,
                                Modifier.size(20.dp),
                                tint = scheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.akar_selftest),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    ui.selfTest ?: stringResource(R.string.akar_selftest_idle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (ui.selfTestOk) {
                                        true -> scheme.primary
                                        false -> scheme.error
                                        null -> scheme.onSurfaceVariant
                                    },
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                null,
                                Modifier.size(20.dp),
                                tint = scheme.onSurfaceVariant,
                            )
                        }

                        val hasGrants = ui.grants.isNotEmpty()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(
                                    if (hasGrants) Modifier.clickable(onClick = onConfirmRevokeAll)
                                    else Modifier,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                null,
                                Modifier.size(20.dp),
                                tint = if (hasGrants) scheme.error else scheme.onSurfaceVariant.copy(alpha = 0.4f),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.akar_revoke_all),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (hasGrants) scheme.error else scheme.onSurface.copy(alpha = 0.4f),
                                )
                                Text(
                                    stringResource(R.string.akar_revoke_all_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant.copy(alpha = if (hasGrants) 1f else 0.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchItem(
    icon: ImageVector,
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            null,
            Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CheckSwitch(checked)
    }
}
