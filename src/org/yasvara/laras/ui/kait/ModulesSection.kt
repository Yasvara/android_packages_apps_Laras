/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.kait

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.kait.InjectedProcess
import org.yasvara.laras.data.kait.KaitModule
import org.yasvara.laras.data.kait.KaitShortcuts
import org.yasvara.laras.data.kait.OnlineModule
import org.yasvara.laras.data.kait.OnlineRelease
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.groupedShape

/**
 * Full 3-tab Bento M3 Kait Manager (Info · Modules · Repository).
 * Combines Akar's ROM-native Bento M3 layout & Homescreen/App-Drawer integration with
 * Vector Manager's rich module cards, explicit Scope ("Kaitkan aplikasi") & Open UI buttons,
 * live hooked process management, and the 800+ module Online Xposed Repository browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaitScreen(
    onBack: () -> Unit,
    focus: String? = null,
    onOpenScope: (String) -> Unit,
    viewModel: ModulesViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    var handledFocus by rememberSaveable(focus) { mutableStateOf(false) }

    var showModuleSearch by rememberSaveable { mutableStateOf(false) }
    var moduleQuery by rememberSaveable { mutableStateOf("") }
    var moduleFilter by rememberSaveable { mutableStateOf(ModuleListFilter.ALL) }

    var repoQuery by rememberSaveable { mutableStateOf("") }
    var repoFilter by rememberSaveable { mutableStateOf(RepoListFilter.ALL) }
    var repoSort by rememberSaveable { mutableStateOf(RepoSortOrder.UPDATED) }

    var showProcessesSheet by rememberSaveable { mutableStateOf(false) }
    var actionSheetModulePkg by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    LaunchedEffect(focus) {
        if (!focus.isNullOrEmpty() && !handledFocus) {
            handledFocus = true
            onOpenScope(focus)
        }
    }

    BackHandler(pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val titles = listOf(
        stringResource(R.string.group_modules),
        stringResource(R.string.kait_tab_modules),
        stringResource(R.string.kait_tab_repo),
    )

    val activeCount = ui.modules.count { it.enabled && it.problem == null }
    val updateCount = ui.upgradableModulesCount

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
                            contentDescription = stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    if (pagerState.currentPage == 1) {
                        IconButton(onClick = {
                            showModuleSearch = !showModuleSearch
                            if (!showModuleSearch) moduleQuery = ""
                        }) {
                            Icon(
                                if (showModuleSearch) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.kait_search_modules),
                            )
                        }
                    }
                    IconButton(onClick = { showProcessesSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (ui.injectedProcesses.isNotEmpty()) {
                                    Badge { Text("${ui.injectedProcesses.size}") }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Rounded.Memory,
                                contentDescription = stringResource(R.string.kait_bento_processes),
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (pagerState.currentPage == 2) {
                            viewModel.refreshOnlineRepo(forceNetwork = true)
                        } else {
                            viewModel.refresh(loadRepo = true)
                        }
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.akar_refresh))
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceBright) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.kait_tab_info)) },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeCount > 0) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ) {
                                        Text("$activeCount")
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Extension, contentDescription = null)
                        }
                    },
                    label = { Text(stringResource(R.string.kait_tab_modules)) },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (updateCount > 0) {
                                    Badge { Text("$updateCount") }
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                        }
                    },
                    label = { Text(stringResource(R.string.kait_tab_repo)) },
                )
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { page ->
            when (page) {
                0 -> KaitInfoTab(
                    ui = ui,
                    viewModel = viewModel,
                    onJumpToModules = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onJumpToRepo = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onOpenProcesses = { showProcessesSheet = true },
                    onOpenScope = onOpenScope,
                    onPinShortcut = {
                        val ok = KaitShortcuts.requestPin(context)
                        scope.launch {
                            delay(450)
                            viewModel.refreshShortcutState()
                            if (!ok) {
                                snackbar.showSnackbar(context.getString(R.string.kait_pin_unsupported))
                            }
                        }
                    },
                )
                1 -> KaitModulesTab(
                    ui = ui,
                    viewModel = viewModel,
                    showSearch = showModuleSearch,
                    query = moduleQuery,
                    onQueryChange = { moduleQuery = it },
                    filter = moduleFilter,
                    onFilterChange = { moduleFilter = it },
                    onOpenScope = onOpenScope,
                    onOpenActions = { actionSheetModulePkg = it.packageName },
                    onBrowseRepo = { scope.launch { pagerState.animateScrollToPage(2) } },
                )
                2 -> KaitRepoTab(
                    ui = ui,
                    viewModel = viewModel,
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
            processes = ui.injectedProcesses,
            onDismiss = { showProcessesSheet = false },
            onRestart = { pkg ->
                viewModel.restartPackage(pkg) {
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.kait_restarted_targets, 1))
                    }
                }
            },
        )
    }

    val sheetModule = actionSheetModulePkg?.let { pkg -> ui.modules.find { it.packageName == pkg } }
    if (sheetModule != null) {
        val onlineMatch = ui.onlineByPackage[sheetModule.packageName]
        ModuleActionsSheet(
            module = sheetModule,
            hasOnlineEntry = onlineMatch != null,
            onDismiss = { actionSheetModulePkg = null },
            onOpenUi = {
                actionSheetModulePkg = null
                viewModel.repository.openModuleUi(sheetModule.packageName)
            },
            onOpenScope = {
                actionSheetModulePkg = null
                onOpenScope(sheetModule.packageName)
            },
            onRestartTargets = {
                actionSheetModulePkg = null
                viewModel.restartTargetsOf(sheetModule) { count ->
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.kait_restarted_targets, count))
                    }
                }
            },
            onViewInRepo = {
                actionSheetModulePkg = null
                if (onlineMatch != null) {
                    viewModel.openOnlineModuleDetail(onlineMatch)
                }
            },
            onAppInfo = {
                actionSheetModulePkg = null
                viewModel.repository.openAppInfo(sheetModule.packageName)
            },
            onUninstall = {
                actionSheetModulePkg = null
                viewModel.repository.requestUninstall(sheetModule.packageName)
            },
        )
    }

    val detailModule = ui.selectedOnlineModule
    if (detailModule != null) {
        val installedModule = ui.modules.find { it.packageName == detailModule.packageName }
        OnlineModuleDetailSheet(
            module = detailModule,
            loading = ui.detailLoading,
            installedModule = installedModule,
            hasUpdate = ui.hasRepoUpdate(detailModule),
            onDismiss = viewModel::dismissOnlineModuleDetail,
            onOpenScope = {
                viewModel.dismissOnlineModuleDetail()
                onOpenScope(detailModule.packageName)
            },
            onOpenModuleUi = {
                viewModel.dismissOnlineModuleDetail()
                viewModel.repository.openModuleUi(detailModule.packageName)
            },
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Tab 0 — Info & Bento Dashboard                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun KaitInfoTab(
    ui: KaitUi,
    viewModel: ModulesViewModel,
    onJumpToModules: () -> Unit,
    onJumpToRepo: () -> Unit,
    onOpenProcesses: () -> Unit,
    onOpenScope: (String) -> Unit,
    onPinShortcut: () -> Unit,
) {
    val activeCount = ui.modules.count { it.enabled && it.problem == null }
    val totalCount = ui.modules.size
    val onlineCount = ui.onlineCatalog.modules.size
    val updateCount = ui.upgradableModulesCount

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            KaitHeroCard(
                available = ui.available,
                frameworkVersion = ui.frameworkVersion,
                activeCount = activeCount,
                totalCount = totalCount,
                processCount = ui.injectedProcesses.size,
                onlineCount = onlineCount,
                onJumpToModules = onJumpToModules,
                onJumpToRepo = onJumpToRepo,
            )
        }

        if (ui.crashBypassed.isNotEmpty()) {
            item(key = "crash_bypass") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.crashBypassed.forEach { pkg ->
                        CrashBypassBanner(pkg = pkg, onReset = { viewModel.clearCrashBypass(pkg) })
                    }
                }
            }
        }

        item(key = "bento_row_1") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BentoMetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Extension,
                    title = stringResource(R.string.kait_bento_modules),
                    value = "$activeCount / $totalCount",
                    subtitle = stringResource(R.string.kait_bento_modules_sub, activeCount, totalCount),
                    highlighted = activeCount > 0,
                    onClick = onJumpToModules,
                )
                BentoMetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.kait_bento_processes),
                    value = "${ui.injectedProcesses.size}",
                    subtitle = stringResource(R.string.kait_bento_processes_sub, ui.injectedProcesses.size),
                    highlighted = ui.injectedProcesses.isNotEmpty(),
                    onClick = onOpenProcesses,
                )
            }
        }

        item(key = "bento_row_2") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BentoMetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.CloudDownload,
                    title = stringResource(R.string.kait_bento_repo),
                    value = if (onlineCount > 0) "$onlineCount+" else "800+",
                    subtitle = if (onlineCount > 0) {
                        stringResource(R.string.kait_bento_repo_sub, onlineCount, updateCount)
                    } else {
                        stringResource(R.string.kait_bento_repo_tap)
                    },
                    highlighted = updateCount > 0,
                    onClick = onJumpToRepo,
                )
                BentoToggleTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.Apps,
                    title = stringResource(R.string.kait_drawer_icon),
                    subtitle = stringResource(R.string.kait_drawer_icon_sub),
                    checked = ui.drawerEnabled,
                    onCheckedChange = viewModel::setDrawerEnabled,
                )
            }
        }

        item(key = "pin_shortcut_card") {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceBright,
                onClick = onPinShortcut,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.kait_pin_shortcut),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(
                                if (ui.shortcutPinned) R.string.kait_pin_shortcut_done
                                else R.string.kait_pin_shortcut_hint,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (ui.shortcutPinned) Icons.Rounded.CheckCircle else Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = if (ui.shortcutPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        item(key = "quick_modules") {
            KaitQuickModulesCard(
                ui = ui,
                onOpenScope = onOpenScope,
                onOpenUi = { viewModel.repository.openModuleUi(it) },
                onToggle = { pkg, en -> viewModel.setEnabled(pkg, en) },
                onJumpToModules = onJumpToModules,
                onJumpToRepo = onJumpToRepo,
            )
        }

        item(key = "architecture") {
            KaitArchitectureCard()
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun KaitHeroCard(
    available: Boolean,
    frameworkVersion: String,
    activeCount: Int,
    totalCount: Int,
    processCount: Int,
    onlineCount: Int,
    onJumpToModules: () -> Unit,
    onJumpToRepo: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (available) scheme.primaryContainer else scheme.surfaceContainerHighest,
        label = "kaitHeroContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (available) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        label = "kaitHeroContent",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = container,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = contentColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(60.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        KaitClaspArt(color = contentColor, modifier = Modifier.size(36.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = if (available) frameworkVersion else stringResource(R.string.kait_unavailable),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = contentColor.copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = stringResource(
                                    if (available) R.string.kait_badge_active else R.string.akar_badge_off,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.kait_hero_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.88f),
                    )
                    Text(
                        text = stringResource(R.string.kait_hero_sub_ready, activeCount, totalCount, processCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    onClick = onJumpToModules,
                    shape = RoundedCornerShape(16.dp),
                    color = contentColor.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.kait_tab_modules)} ($totalCount)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                    }
                }
                Surface(
                    onClick = onJumpToRepo,
                    shape = RoundedCornerShape(16.dp),
                    color = contentColor.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (onlineCount > 0) {
                                "${stringResource(R.string.kait_tab_repo)} ($onlineCount)"
                            } else {
                                stringResource(R.string.kait_tab_repo)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                    }
                }
            }
        }
    }
}

/** Custom vector-canvas illustration of two interlocking Kait clasps. */
@Composable
private fun KaitClaspArt(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.13f, cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.18f),
            size = Size(w * 0.50f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.21f, w * 0.21f),
            style = stroke,
        )
        drawRoundRect(
            color = color.copy(alpha = 0.78f),
            topLeft = Offset(w * 0.40f, h * 0.40f),
            size = Size(w * 0.50f, h * 0.42f),
            cornerRadius = CornerRadius(w * 0.21f, w * 0.21f),
            style = stroke,
        )
    }
}

@Composable
private fun BentoMetricTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (highlighted) scheme.primaryContainer else scheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (highlighted) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (highlighted) scheme.primary else scheme.onSurface,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BentoToggleTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (checked) scheme.primaryContainer else scheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (checked) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Switch(checked = checked, onCheckedChange = null)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KaitQuickModulesCard(
    ui: KaitUi,
    onOpenScope: (String) -> Unit,
    onOpenUi: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onJumpToModules: () -> Unit,
    onJumpToRepo: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.kait_active_modules_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (ui.modules.isNotEmpty()) {
                    TextButton(onClick = onJumpToModules) {
                        Text(stringResource(R.string.kait_tab_modules))
                    }
                }
            }

            if (ui.modules.isEmpty()) {
                Text(
                    text = stringResource(R.string.kait_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                FilledTonalButton(
                    onClick = onJumpToRepo,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.kait_bento_repo_tap))
                }
            } else {
                ui.modules.forEachIndexed { idx, module ->
                    if (idx > 0) {
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    val isRunning = module.enabled && module.scope.any { it in ui.injected }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AppIcon(module.packageName, size = 40.dp)
                            Column(Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = module.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isRunning) {
                                        KaitPillBadge(
                                            text = stringResource(R.string.kait_running_badge),
                                            containerColor = scheme.primaryContainer,
                                            contentColor = scheme.onPrimaryContainer,
                                        )
                                    }
                                }
                                Text(
                                    text = when {
                                        module.problem != null -> stringResource(R.string.kait_problem, module.problem)
                                        !module.enabled -> stringResource(R.string.kait_off)
                                        module.scope.isEmpty() -> stringResource(R.string.kait_no_scope)
                                        else -> pluralStringResource(
                                            if (isRunning) R.plurals.kait_scope_running else R.plurals.kait_scope_count,
                                            module.scope.size,
                                            module.scope.size,
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (module.problem == null) scheme.onSurfaceVariant else scheme.error,
                                )
                            }
                            Switch(
                                checked = module.enabled,
                                onCheckedChange = { onToggle(module.packageName, it) },
                                enabled = module.problem == null || module.enabled,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenScope(module.packageName) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (module.scope.isEmpty()) {
                                        stringResource(R.string.kait_btn_scope_empty)
                                    } else {
                                        stringResource(R.string.kait_btn_scope, module.scope.size)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (module.canOpenUi) {
                                OutlinedButton(
                                    onClick = { onOpenUi(module.packageName) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kait_btn_open_ui))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KaitArchitectureCard() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.kait_arch_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ArchInfoRow(
                icon = Icons.Rounded.DeveloperBoard,
                label = stringResource(R.string.kait_arch_engine),
                value = stringResource(R.string.kait_arch_engine_val),
            )
            ArchInfoRow(
                icon = Icons.Rounded.Code,
                label = stringResource(R.string.kait_arch_trampoline),
                value = stringResource(R.string.kait_arch_trampoline_val),
            )
            ArchInfoRow(
                icon = Icons.Rounded.Extension,
                label = stringResource(R.string.kait_arch_api),
                value = stringResource(R.string.kait_arch_api_val),
            )
            ArchInfoRow(
                icon = Icons.Rounded.Shield,
                label = stringResource(R.string.kait_arch_stealth),
                value = stringResource(R.string.kait_arch_stealth_val),
            )
        }
    }
}

@Composable
private fun ArchInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Tab 1 — Installed Modules Manager (Vector-Complete Module Cards)           */
/* -------------------------------------------------------------------------- */

@Composable
fun KaitModulesTab(
    ui: KaitUi,
    viewModel: ModulesViewModel,
    showSearch: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: ModuleListFilter,
    onFilterChange: (ModuleListFilter) -> Unit,
    onOpenScope: (String) -> Unit,
    onOpenActions: (KaitModule) -> Unit,
    onBrowseRepo: () -> Unit,
) {
    val allCount = ui.modules.size
    val enabledCount = ui.modules.count { it.enabled }
    val disabledCount = ui.modules.count { !it.enabled }
    val updatesCount = ui.upgradableModulesCount

    val filtered = remember(ui.modules, query, filter, ui.onlineCatalog) {
        ui.modules.filter { m ->
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                m.label.contains(q, ignoreCase = true) ||
                m.packageName.contains(q, ignoreCase = true) ||
                m.description.contains(q, ignoreCase = true) ||
                m.author.contains(q, ignoreCase = true)
            val matchesFilter = when (filter) {
                ModuleListFilter.ALL -> true
                ModuleListFilter.ENABLED -> m.enabled
                ModuleListFilter.DISABLED -> !m.enabled
                ModuleListFilter.UPDATES -> ui.hasUpdate(m)
            }
            matchesQuery && matchesFilter
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search_and_filters") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(visible = showSearch || query.isNotEmpty()) {
                    KaitSearchField(
                        query = query,
                        placeholder = stringResource(R.string.kait_search_modules),
                        onQueryChange = onQueryChange,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filter == ModuleListFilter.ALL,
                        onClick = { onFilterChange(ModuleListFilter.ALL) },
                        label = { Text(stringResource(R.string.kait_filter_all, allCount)) },
                    )
                    FilterChip(
                        selected = filter == ModuleListFilter.ENABLED,
                        onClick = { onFilterChange(ModuleListFilter.ENABLED) },
                        label = { Text(stringResource(R.string.kait_filter_enabled, enabledCount)) },
                    )
                    FilterChip(
                        selected = filter == ModuleListFilter.DISABLED,
                        onClick = { onFilterChange(ModuleListFilter.DISABLED) },
                        label = { Text(stringResource(R.string.kait_filter_disabled, disabledCount)) },
                    )
                    if (updatesCount > 0 || filter == ModuleListFilter.UPDATES) {
                        FilterChip(
                            selected = filter == ModuleListFilter.UPDATES,
                            onClick = { onFilterChange(ModuleListFilter.UPDATES) },
                            label = { Text(stringResource(R.string.kait_filter_updates, updatesCount)) },
                        )
                    }
                }
            }
        }

        when {
            ui.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            !ui.available -> item(key = "unavailable") {
                Message(R.string.kait_unavailable, R.string.kait_unavailable_body)
            }
            ui.modules.isEmpty() -> item(key = "empty") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Message(R.string.kait_empty, R.string.kait_empty_body)
                    FilledTonalButton(
                        onClick = onBrowseRepo,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.kait_bento_repo_tap))
                    }
                }
            }
            filtered.isEmpty() -> item(key = "no_match") {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
                    Text(
                        text = stringResource(R.string.akar_no_matching_apps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            else -> {
                itemsIndexed(filtered, key = { _, m -> m.packageName }) { _, module ->
                    val runningTargets = module.scope.count { it in ui.injected }
                    val hasUpdate = ui.hasUpdate(module)
                    val onlineEntry = ui.onlineByPackage[module.packageName]
                    KaitModuleCard(
                        module = module,
                        runningTargetsCount = runningTargets,
                        hasUpdate = hasUpdate,
                        latestOnlineVersion = onlineEntry?.latestVersion?.versionName,
                        onToggleEnabled = { viewModel.setEnabled(module.packageName, it) },
                        onOpenScope = { onOpenScope(module.packageName) },
                        onOpenUi = { viewModel.repository.openModuleUi(module.packageName) },
                        onOpenUpdate = {
                            if (onlineEntry != null) viewModel.openOnlineModuleDetail(onlineEntry)
                        },
                        onMoreActions = { onOpenActions(module) },
                    )
                }
                item(key = "restart_note") {
                    Text(
                        stringResource(R.string.kait_restart_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun KaitModuleCard(
    module: KaitModule,
    runningTargetsCount: Int,
    hasUpdate: Boolean,
    latestOnlineVersion: String?,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenScope: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenUpdate: () -> Unit,
    onMoreActions: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val loadable = module.problem == null

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: Icon + Title + Version/API badges + More button + Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppIcon(module.packageName, size = 46.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = module.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (hasUpdate) {
                            Surface(
                                onClick = onOpenUpdate,
                                shape = RoundedCornerShape(6.dp),
                                color = scheme.tertiaryContainer,
                            ) {
                                Text(
                                    text = if (!latestOnlineVersion.isNullOrBlank()) {
                                        "${stringResource(R.string.kait_repo_update_badge)} $latestOnlineVersion"
                                    } else {
                                        stringResource(R.string.kait_repo_update_badge)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val verText = if (module.versionName.isNotBlank()) {
                            "v${module.versionName}"
                        } else {
                            "v${module.versionCode}"
                        }
                        Text(
                            text = verText,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                        if (module.apiLabel.isNotBlank()) {
                            KaitPillBadge(
                                text = module.apiLabel,
                                containerColor = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer,
                            )
                        }
                        if (module.hasNativeHook) {
                            KaitPillBadge(
                                text = "NATIVE",
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                            )
                        }
                    }

                    if (module.author.isNotBlank()) {
                        Text(
                            text = module.author,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(onClick = onMoreActions) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = module.enabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = loadable || module.enabled,
                )
            }

            // Description or problem message
            if (!loadable) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = scheme.onErrorContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.kait_problem, module.problem!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onErrorContainer,
                        )
                    }
                }
            } else if (module.description.isNotBlank()) {
                Text(
                    text = module.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Target Apps Reach strip (click to open ScopeScreen — list yang mau dikaitkan!)
            Surface(
                onClick = onOpenScope,
                shape = RoundedCornerShape(16.dp),
                color = scheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (module.scope.isEmpty()) {
                        Icon(
                            Icons.Rounded.Checklist,
                            contentDescription = null,
                            tint = if (module.enabled) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (module.enabled) {
                                stringResource(R.string.kait_no_scope)
                            } else {
                                stringResource(R.string.kait_off)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-6).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            module.scope.take(5).forEach { targetPkg ->
                                AppIcon(targetPkg, size = 24.dp)
                            }
                        }
                        if (module.scope.size > 5) {
                            KaitPillBadge(
                                text = "+${module.scope.size - 5}",
                                containerColor = scheme.secondaryContainer,
                                contentColor = scheme.onSecondaryContainer,
                            )
                        }
                        Text(
                            text = pluralStringResource(
                                if (runningTargetsCount > 0) R.plurals.kait_scope_running else R.plurals.kait_scope_count,
                                module.scope.size,
                                module.scope.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (runningTargetsCount > 0 && module.enabled) {
                            KaitPillBadge(
                                text = stringResource(R.string.kait_running_badge),
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                            )
                        }
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Explicit Action Button Bar: "Kaitkan aplikasi (N)" + "Buka modul"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenScope,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (module.scope.isEmpty()) {
                            stringResource(R.string.kait_btn_scope_empty)
                        } else {
                            stringResource(R.string.kait_btn_scope, module.scope.size)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (module.canOpenUi) {
                    OutlinedButton(onClick = onOpenUi) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.kait_btn_open_ui))
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Tab 2 — Online Xposed Module Repository Browser                            */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun KaitRepoTab(
    ui: KaitUi,
    viewModel: ModulesViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: RepoListFilter,
    onFilterChange: (RepoListFilter) -> Unit,
    sort: RepoSortOrder,
    onSortChange: (RepoSortOrder) -> Unit,
    onOpenScope: (String) -> Unit,
) {
    val catalog = ui.onlineCatalog
    val installedPkgs = ui.installedVersions.keys

    val installedInRepoCount = remember(catalog.modules, installedPkgs) {
        catalog.modules.count { it.packageName in installedPkgs }
    }
    val updatesInRepoCount = remember(catalog.modules, ui.installedVersions) {
        catalog.modules.count { ui.hasRepoUpdate(it) }
    }

    val filtered = remember(catalog.modules, query, filter, sort, ui.installedVersions) {
        val q = query.trim()
        val list = catalog.modules.filter { m ->
            val matchesQuery = q.isEmpty() ||
                m.title.contains(q, ignoreCase = true) ||
                m.packageName.contains(q, ignoreCase = true) ||
                m.summary.contains(q, ignoreCase = true) ||
                m.authors.contains(q, ignoreCase = true) ||
                m.scope.any { it.contains(q, ignoreCase = true) }
            val matchesFilter = when (filter) {
                RepoListFilter.ALL -> true
                RepoListFilter.INSTALLED -> m.packageName in installedPkgs
                RepoListFilter.UPDATES -> ui.hasRepoUpdate(m)
            }
            matchesQuery && matchesFilter
        }
        when (sort) {
            RepoSortOrder.UPDATED -> list.sortedByDescending { it.updatedAt }
            RepoSortOrder.STARS -> list.sortedByDescending { it.stargazerCount }
            RepoSortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.repoRefreshing,
        onRefresh = { viewModel.refreshOnlineRepo(forceNetwork = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "repo_search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KaitSearchField(
                        query = query,
                        placeholder = stringResource(R.string.kait_search_repo),
                        onQueryChange = onQueryChange,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = filter == RepoListFilter.ALL,
                            onClick = { onFilterChange(RepoListFilter.ALL) },
                            label = { Text(stringResource(R.string.kait_filter_all, catalog.modules.size)) },
                        )
                        FilterChip(
                            selected = filter == RepoListFilter.INSTALLED,
                            onClick = { onFilterChange(RepoListFilter.INSTALLED) },
                            label = { Text(stringResource(R.string.kait_filter_installed, installedInRepoCount)) },
                        )
                        FilterChip(
                            selected = filter == RepoListFilter.UPDATES,
                            onClick = { onFilterChange(RepoListFilter.UPDATES) },
                            label = { Text(stringResource(R.string.kait_filter_updates, updatesInRepoCount)) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = sort == RepoSortOrder.UPDATED,
                            onClick = { onSortChange(RepoSortOrder.UPDATED) },
                            label = { Text(stringResource(R.string.kait_sort_updated)) },
                        )
                        FilterChip(
                            selected = sort == RepoSortOrder.STARS,
                            onClick = { onSortChange(RepoSortOrder.STARS) },
                            label = { Text(stringResource(R.string.kait_sort_stars)) },
                        )
                        FilterChip(
                            selected = sort == RepoSortOrder.NAME,
                            onClick = { onSortChange(RepoSortOrder.NAME) },
                            label = { Text(stringResource(R.string.kait_sort_name)) },
                        )
                    }
                }
            }

            if (catalog.fromCache) {
                item(key = "offline_cached_notice") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.kait_repo_offline_cached),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            when {
                ui.repoRefreshing && catalog.modules.isEmpty() -> item(key = "repo_loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                catalog.modules.isEmpty() -> item(key = "repo_empty") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.kait_repo_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            FilledTonalButton(onClick = { viewModel.refreshOnlineRepo(forceNetwork = true) }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.akar_refresh))
                            }
                        }
                    }
                }
                filtered.isEmpty() -> item(key = "repo_no_match") {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
                        Text(
                            text = stringResource(R.string.akar_no_matching_apps),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                else -> {
                    itemsIndexed(filtered, key = { _, m -> m.packageName }) { _, online ->
                        val isInstalled = online.packageName in installedPkgs
                        val hasUpdate = ui.hasRepoUpdate(online)
                        OnlineModuleCard(
                            module = online,
                            isInstalled = isInstalled,
                            hasUpdate = hasUpdate,
                            onClick = { viewModel.openOnlineModuleDetail(online) },
                            onOpenScope = if (isInstalled) {
                                { onOpenScope(online.packageName) }
                            } else null,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineModuleCard(
    module: OnlineModule,
    isInstalled: Boolean,
    hasUpdate: Boolean,
    onClick: () -> Unit,
    onOpenScope: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isInstalled) {
                    AppIcon(module.packageName, size = 42.dp)
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.primaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = module.title.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = module.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (hasUpdate) {
                            KaitPillBadge(
                                text = stringResource(R.string.kait_repo_update_badge),
                                containerColor = scheme.tertiaryContainer,
                                contentColor = scheme.onTertiaryContainer,
                            )
                        } else if (isInstalled) {
                            KaitPillBadge(
                                text = stringResource(R.string.kait_repo_installed_badge),
                                containerColor = scheme.primaryContainer,
                                contentColor = scheme.onPrimaryContainer,
                            )
                        }
                    }
                    Text(
                        text = module.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (module.stargazerCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = scheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${module.stargazerCount}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            if (module.summary.isNotBlank()) {
                Text(
                    text = module.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val metaParts = buildList {
                    if (module.authors.isNotBlank()) add(module.authors)
                    module.latestVersion?.versionName?.takeIf { it.isNotBlank() }?.let { add(it) }
                    if (module.updatedAt.isNotBlank()) add(module.updatedAt)
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (onOpenScope != null) {
                    TextButton(
                        onClick = onOpenScope,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.kait_scope_title),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            if (module.scope.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    module.scope.take(4).forEach { targetPkg ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = scheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = targetPkg,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    if (module.scope.size > 4) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = scheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "+${module.scope.size - 4}",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Bottom Sheets: Live Processes, Module Actions, Online Module Details       */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveProcessesSheet(
    processes: List<InjectedProcess>,
    onDismiss: () -> Unit,
    onRestart: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kait_processes_sheet_title, processes.size),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (processes.isEmpty()) {
                Text(
                    text = stringResource(R.string.kait_processes_sheet_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(processes, key = { i, p -> "${p.processName}:${p.pid}:$i" }) { _, proc ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppIcon(proc.packageName, size = 38.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = proc.processName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${proc.packageName} · pid ${proc.pid}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (proc.packageName != "android") {
                                    IconButton(onClick = { onRestart(proc.packageName) }) {
                                        Icon(
                                            Icons.Rounded.RestartAlt,
                                            contentDescription = stringResource(R.string.kait_btn_restart_targets),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleActionsSheet(
    module: KaitModule,
    hasOnlineEntry: Boolean,
    onDismiss: () -> Unit,
    onOpenUi: () -> Unit,
    onOpenScope: () -> Unit,
    onRestartTargets: () -> Unit,
    onViewInRepo: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppIcon(module.packageName, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = module.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${module.packageName} · v${module.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SheetActionItem(
                icon = Icons.Rounded.Checklist,
                label = if (module.scope.isEmpty()) {
                    stringResource(R.string.kait_btn_scope_empty)
                } else {
                    stringResource(R.string.kait_btn_scope, module.scope.size)
                },
                onClick = onOpenScope,
            )
            if (module.canOpenUi) {
                SheetActionItem(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    label = stringResource(R.string.kait_btn_open_ui),
                    onClick = onOpenUi,
                )
            }
            if (module.scope.isNotEmpty()) {
                SheetActionItem(
                    icon = Icons.Rounded.RestartAlt,
                    label = stringResource(R.string.kait_btn_restart_targets),
                    onClick = onRestartTargets,
                )
            }
            if (hasOnlineEntry) {
                SheetActionItem(
                    icon = Icons.Rounded.CloudDownload,
                    label = stringResource(R.string.kait_action_view_repo),
                    onClick = onViewInRepo,
                )
            }
            SheetActionItem(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.kait_action_app_info),
                onClick = onAppInfo,
            )
            SheetActionItem(
                icon = Icons.Rounded.DeleteOutline,
                label = stringResource(R.string.kait_action_uninstall),
                destructive = true,
                onClick = onUninstall,
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnlineModuleDetailSheet(
    module: OnlineModule,
    loading: Boolean,
    installedModule: KaitModule?,
    hasUpdate: Boolean,
    onDismiss: () -> Unit,
    onOpenScope: () -> Unit,
    onOpenModuleUi: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceBright,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (installedModule != null) {
                            AppIcon(module.packageName, size = 52.dp)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = scheme.primaryContainer,
                                modifier = Modifier.size(52.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = module.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = scheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = module.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = module.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariant,
                            )
                            if (module.authors.isNotBlank()) {
                                Text(
                                    text = module.authors,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.primary,
                                )
                            }
                        }
                    }

                    if (module.summary.isNotBlank()) {
                        Text(
                            text = module.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    // Installed actions (Scope & Open UI)
                    if (installedModule != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = onOpenScope,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (installedModule.scope.isEmpty()) {
                                        stringResource(R.string.kait_btn_scope_empty)
                                    } else {
                                        stringResource(R.string.kait_btn_scope, installedModule.scope.size)
                                    },
                                )
                            }
                            if (installedModule.canOpenUi) {
                                OutlinedButton(onClick = onOpenModuleUi) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kait_btn_open_ui))
                                }
                            }
                        }
                    }

                    // External links (GitHub / Website)
                    if (module.repoUrl.isNotBlank() || module.homepageUrl.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (module.repoUrl.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { openUrl(context, module.repoUrl) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kait_repo_open_github))
                                }
                            }
                            if (module.homepageUrl.isNotBlank() && module.homepageUrl != module.repoUrl) {
                                OutlinedButton(
                                    onClick = { openUrl(context, module.homepageUrl) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.kait_repo_open_homepage))
                                }
                            }
                        }
                    }
                }
            }

            // Recommended Scope Packages
            if (module.scope.isNotEmpty()) {
                item(key = "recommended_scope") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.kait_repo_recommended_scope),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                module.scope.forEach { pkg ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = scheme.surfaceBright,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            AppIcon(pkg, size = 18.dp)
                                            Text(
                                                text = pkg,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Releases & APK Download buttons
            if (module.releases.isNotEmpty()) {
                item(key = "releases_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.kait_repo_releases),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (hasUpdate) {
                            KaitPillBadge(
                                text = stringResource(R.string.kait_repo_update_badge),
                                containerColor = scheme.tertiaryContainer,
                                contentColor = scheme.onTertiaryContainer,
                            )
                        }
                    }
                }
                itemsIndexed(module.releases.take(5), key = { i, r -> "${r.tagName}:$i" }) { _, rel ->
                    ReleaseDownloadCard(
                        release = rel,
                        onDownload = { url -> openUrl(context, url) },
                    )
                }
            } else if (loading) {
                item(key = "detail_loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            // README / Full description
            if (module.readmeText.isNotBlank()) {
                item(key = "readme") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.kait_repo_readme),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = module.readmeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseDownloadCard(
    release: OnlineRelease,
    onDownload: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = release.name.ifBlank { release.tagName },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (release.publishedAt.isNotBlank()) {
                    Text(
                        text = release.publishedAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (release.descriptionText.isNotBlank()) {
                Text(
                    text = release.descriptionText,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            release.apkAssets.forEach { asset ->
                FilledTonalButton(
                    onClick = { onDownload(asset.downloadUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.kait_repo_download_apk, asset.name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Compact section for GroupScreen fallback + Shared UI Helpers               */
/* -------------------------------------------------------------------------- */

/**
 * Installed Kait/Xposed modules section when embedded inside GroupScreen.
 */
@Composable
fun ModulesSection(
    onOpenScope: (String) -> Unit,
    onOpenKait: (() -> Unit)? = null,
    viewModel: ModulesViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (onOpenKait != null) {
            FilledTonalButton(
                onClick = onOpenKait,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.kait_hero_title))
            }
        }
        if (ui.crashBypassed.isNotEmpty()) {
            ui.crashBypassed.forEach { pkg ->
                CrashBypassBanner(pkg = pkg, onReset = { viewModel.clearCrashBypass(pkg) })
            }
        }
        when {
            ui.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !ui.available -> Message(R.string.kait_unavailable, R.string.kait_unavailable_body)
            ui.modules.isEmpty() -> Message(R.string.kait_empty, R.string.kait_empty_body)
            else -> {
                ui.modules.forEach { module ->
                    val runningTargets = module.scope.count { it in ui.injected }
                    KaitModuleCard(
                        module = module,
                        runningTargetsCount = runningTargets,
                        hasUpdate = ui.hasUpdate(module),
                        latestOnlineVersion = ui.onlineByPackage[module.packageName]?.latestVersion?.versionName,
                        onToggleEnabled = { viewModel.setEnabled(module.packageName, it) },
                        onOpenScope = { onOpenScope(module.packageName) },
                        onOpenUi = { viewModel.repository.openModuleUi(module.packageName) },
                        onOpenUpdate = { onOpenKait?.invoke() },
                        onMoreActions = { viewModel.repository.openAppInfo(module.packageName) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KaitSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun KaitPillBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun CrashBypassBanner(pkg: String, onReset: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = c.errorContainer,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.kait_crash_bypassed_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = c.onErrorContainer,
                )
                Text(
                    stringResource(R.string.kait_crash_bypassed_body, pkg),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onErrorContainer,
                )
            }
            TextButton(onClick = onReset) {
                Text(stringResource(R.string.kait_crash_bypassed_reset), color = c.onErrorContainer)
            }
        }
    }
}

@Composable
private fun Message(title: Int, body: Int) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
