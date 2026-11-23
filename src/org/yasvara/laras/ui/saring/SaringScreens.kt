/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.saring

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.saring.Refusal
import org.yasvara.laras.data.saring.SaringApp
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasStatBar
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.LarasTabBar
import org.yasvara.laras.ui.components.StatFigure
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private const val DETAILS_SHOWN = 8
private const val PAUSE_SECONDS = 5 * 60

private enum class SaringTab(val label: Int) {
    OVERVIEW(R.string.saring_tab_overview),
    LISTS(R.string.saring_tab_lists),
    APPS(R.string.saring_tab_apps),
    ACTIVITY(R.string.saring_tab_activity),
}

private enum class SaringAppFilter(val label: Int, val test: (SaringApp) -> Boolean) {
    ALL(R.string.lelap_filter_all, { true }),
    BLOCKED(R.string.saring_filter_blocked, { it.refused > 0 }),
    EXEMPT(R.string.saring_filter_exempt, { it.exempt }),
    USER(R.string.lelap_filter_user, { !it.system }),
    SYSTEM(R.string.lelap_filter_system, { it.system }),
}

private enum class LogBy(val label: Int) {
    APP(R.string.saring_log_by_app),
    NAME(R.string.saring_log_by_name),
}

private class LogGroup(
    val key: String,
    val packageName: String,
    val count: Int,
    val last: Long,
    val details: List<Pair<String, Int>>,
)

private fun groups(log: List<Refusal>, by: LogBy): List<LogGroup> = log
    .groupBy { if (by == LogBy.APP) it.packageName.ifEmpty { "uid:" + it.uid } else it.name }
    .map { (key, list) ->
        LogGroup(
            key = key,
            packageName = if (by == LogBy.APP) list.first().packageName else "",
            count = list.sumOf { it.count },
            last = list.maxOf { it.time },
            details = list.groupBy { if (by == LogBy.APP) it.name else it.packageName }
                .map { it.key to it.value.sumOf(Refusal::count) }
                .sortedByDescending { it.second },
        )
    }
    .sortedByDescending { it.count }

private fun compact(value: Long): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1e6)
    value >= 10_000 -> "%dk".format(value / 1000)
    else -> value.toString()
}

@Composable
fun SaringScreen(onBack: () -> Unit, viewModel: SaringViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(SaringTab.OVERVIEW) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SaringAppFilter.ALL) }
    var logBy by rememberSaveable { mutableStateOf(LogBy.APP) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
    }

    LaunchedEffect(ui.notice) {
        val notice = ui.notice ?: return@LaunchedEffect
        val text = when (notice) {
            Notice.UPDATED -> context.getString(
                R.string.saring_notice_updated,
                Formatter.formatShortFileSize(context, ui.stats.names * 8),
                ui.stats.names,
            )
            Notice.UPDATE_FAILED -> context.getString(R.string.saring_notice_failed)
            Notice.BAD_NAME -> context.getString(R.string.saring_notice_bad_name)
        }
        viewModel.dismissNotice()
        snackbar.showSnackbar(text)
    }

    val shown = remember(ui.apps, query, filter) {
        ui.apps.filter {
            filter.test(it) &&
                (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
        }
    }
    val log = remember(ui.log, logBy) { groups(ui.log, logBy) }
    val labels = remember(ui.apps) { ui.apps.associate { it.packageName to it.label } }

    Page(
        title = stringResource(R.string.saring_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            if (tab == SaringTab.ACTIVITY && ui.log.isNotEmpty()) {
                TextButton(viewModel::clearLog) { Text(stringResource(R.string.lelap_events_clear)) }
            }
        },
        topContent = {
            LarasTabBar(
                tabs = SaringTab.entries,
                selected = tab,
                label = { stringResource(it.label) },
                onSelect = { tab = it },
            )
        },
    ) {
        if (!ui.available) {
            item(key = "unavailable") {
                LarasEmptyState(title = stringResource(R.string.saring_unavailable))
            }
            return@Page
        }

        when (tab) {
            SaringTab.OVERVIEW -> {
                item(key = "hero") {
                    SaringMasterHero(ui, viewModel)
                    Spacer(Modifier.height(10.dp))
                }
                item(key = "stats") {
                    LarasStatBar(
                        listOf(
                            StatFigure(compact(ui.stats.refused), stringResource(R.string.saring_stat_refused)) {
                                tab = SaringTab.ACTIVITY
                            },
                            StatFigure(compact(ui.stats.checked), stringResource(R.string.saring_stat_checked)),
                            StatFigure(compact(ui.stats.names), stringResource(R.string.saring_stat_names)) {
                                tab = SaringTab.LISTS
                            },
                        ),
                    )
                }
                item(key = "net_hdr") {
                    LarasSectionHeader(stringResource(R.string.saring_dns_encrypted_title))
                }
                item(key = "dns_encrypted") {
                    val summary = if (ui.stats.cacheHits > 0 || ui.stats.poisonBlocked > 0) {
                        stringResource(
                            R.string.saring_dns_encrypted_stats,
                            compact(ui.stats.cacheHits),
                            compact(ui.stats.poisonBlocked),
                        )
                    } else {
                        stringResource(R.string.saring_dns_encrypted_summary)
                    }
                    LarasSwitchRow(
                        title = stringResource(R.string.saring_dns_encrypted_title),
                        summary = summary,
                        checked = ui.dnsEncrypted,
                        shape = groupedShape(0, 4),
                        onCheckedChange = viewModel::setDnsEncrypted,
                    )
                }
                item(key = "boost") {
                    LarasSwitchRow(
                        title = stringResource(R.string.saring_boost_title),
                        summary = stringResource(R.string.saring_boost_summary),
                        checked = ui.boost,
                        shape = groupedShape(1, 4),
                        onCheckedChange = viewModel::setBoost,
                    )
                }
                item(key = "safesearch") {
                    LarasSwitchRow(
                        title = stringResource(R.string.saring_safesearch_title),
                        summary = stringResource(R.string.saring_safesearch_summary),
                        checked = ui.safesearch,
                        shape = groupedShape(2, 4),
                        onCheckedChange = viewModel::setSafeSearch,
                    )
                }
                item(key = "radio_guard") {
                    val op = ui.ceir.operator.ifEmpty { "Cellular" }
                    val ceirLine = when {
                        ui.ceir.rejectCause != 0 -> stringResource(R.string.saring_ceir_blocked, op, ui.ceir.rejectCause)
                        ui.ceir.inService -> stringResource(R.string.saring_ceir_ok, op)
                        else -> stringResource(R.string.saring_ceir_offline)
                    }
                    LarasSwitchRow(
                        title = stringResource(R.string.saring_radio_guard_title),
                        summary = stringResource(R.string.saring_radio_guard_summary) + "\n" + ceirLine,
                        checked = ui.radioGuard,
                        shape = groupedShape(3, 4),
                        onCheckedChange = viewModel::setRadioGuard,
                    )
                }
                item(key = "web_hdr") {
                    LarasSectionHeader(stringResource(R.string.saring_browser_title))
                }
                item(key = "web") {
                    LarasSwitchRow(
                        title = stringResource(R.string.saring_web_title),
                        summary = stringResource(R.string.saring_web_summary),
                        checked = ui.web,
                        enabled = ui.stats.names > 0,
                        shape = groupedShape(0, 2),
                        onCheckedChange = viewModel::setWeb,
                    )
                }
                item(key = "browser") {
                    LarasSwitchRow(
                        title = stringResource(R.string.saring_browser_title),
                        summary = stringResource(R.string.saring_browser_summary),
                        checked = ui.browser,
                        shape = groupedShape(1, 2),
                        onCheckedChange = viewModel::setBrowser,
                    )
                }
                if (ui.browser) {
                    item(key = "sites_header") {
                        LarasSectionHeader(
                            title = stringResource(R.string.saring_sites_title),
                            summary = stringResource(R.string.saring_sites_summary),
                        )
                    }
                    item(key = "sites_add") {
                        AddField(R.string.saring_sites_hint, listOf(R.string.saring_allow to true)) { site, _ ->
                            viewModel.setSite(site, true)
                        }
                    }
                    items(ui.sites.size, key = { "site:" + ui.sites[it] }) { i ->
                        RemovableRow(ui.sites[i].removePrefix("[*.]"), null, groupedShape(i, ui.sites.size)) {
                            viewModel.setSite(ui.sites[i], false)
                        }
                    }
                }
            }
            SaringTab.LISTS -> {
                items(ui.categories.size, key = { "cat:" + ui.categories[it].category.id }) { i ->
                    val state = ui.categories[i]
                    val details = if (state.on && state.rules > 0) {
                        "\n" + stringResource(
                            R.string.saring_cat_rules,
                            state.rules,
                            state.category.sources.joinToString { it.name },
                        )
                    } else ""
                    LarasSwitchRow(
                        title = stringResource(state.category.title),
                        summary = stringResource(state.category.summary) + details,
                        checked = state.on,
                        enabled = !ui.busy,
                        shape = groupedShape(i, ui.categories.size),
                        onCheckedChange = { viewModel.setCategory(state.category, it) },
                    )
                }
                item(key = "owner_header") {
                    LarasSectionHeader(
                        title = stringResource(R.string.saring_owner_title),
                        summary = stringResource(R.string.saring_owner_summary),
                    )
                }
                item(key = "owner_add") {
                    AddField(
                        R.string.saring_owner_hint,
                        listOf(R.string.saring_block to false, R.string.saring_allow to true),
                    ) { name, allow -> viewModel.setOwnerRule(name, allow) }
                }
                items(ui.owner.size, key = { "owner:" + ui.owner[it].name }) { i ->
                    val rule = ui.owner[i]
                    RemovableRow(
                        rule.name,
                        stringResource(if (rule.allow) R.string.saring_owner_allowed else R.string.saring_owner_blocked),
                        groupedShape(i, ui.owner.size),
                    ) { viewModel.setOwnerRule(rule.name, null) }
                }
            }
            SaringTab.APPS -> {
                item(key = "search") {
                    LarasSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.visibility_search),
                    )
                    Spacer(Modifier.height(6.dp))
                }
                item(key = "filters") {
                    LarasFilterRow(
                        options = SaringAppFilter.entries,
                        selected = filter,
                        label = { stringResource(it.label) },
                        count = { f -> ui.apps.count(f.test) },
                        onSelect = { filter = it },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "empty") { LarasEmptyState(title = stringResource(R.string.lelap_apps_empty)) }
                }
                items(shown.size, key = { "app:" + shown[it].packageName }) { i ->
                    val app = shown[i]
                    LarasSwitchRow(
                        title = app.label,
                        summary = stringResource(if (app.exempt) R.string.saring_app_exempt else R.string.saring_app_filtered),
                        checked = !app.exempt,
                        leading = { AppIcon(app.packageName) },
                        badge = if (app.refused > 0) {
                            { LarasBadge(stringResource(R.string.lelap_app_blocked, app.refused), pill = true) }
                        } else null,
                        shape = groupedShape(i, shown.size),
                        onCheckedChange = { on -> viewModel.setExempt(app.packageName, !on) },
                    )
                }
            }
            SaringTab.ACTIVITY -> {
                item(key = "log_by") {
                    LarasFilterRow(
                        options = LogBy.entries,
                        selected = logBy,
                        label = { stringResource(it.label) },
                        count = { by -> groups(ui.log, by).size },
                        onSelect = { logBy = it; expanded = null },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (log.isEmpty()) {
                    item(key = "log_empty") { LarasEmptyState(title = stringResource(R.string.saring_log_empty)) }
                }
                items(log.size, key = { "log:" + log[it].key }) { i ->
                    val group = log[i]
                    LogRow(
                        group = group,
                        by = logBy,
                        labels = labels,
                        open = expanded == group.key,
                        shape = groupedShape(i, log.size),
                        onToggle = { expanded = if (expanded == group.key) null else group.key },
                        onAllow = { name -> viewModel.setOwnerRule(name, true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SaringMasterHero(ui: SaringUi, viewModel: SaringViewModel) {
    val paused = ui.stats.pausedUntil > System.currentTimeMillis()
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when {
            !ui.names -> LarasSurfaces.card
            paused -> scheme.tertiaryContainer
            else -> scheme.primaryContainer
        },
        label = "saringHero",
    )
    val content = when {
        !ui.names -> scheme.onSurface
        paused -> scheme.onTertiaryContainer
        else -> scheme.onPrimaryContainer
    }
    Surface(Modifier.fillMaxWidth(), shape = LarasShapes.Hero, color = container, contentColor = content) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(ui.names, role = Role.Switch, onValueChange = viewModel::setNames),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.saring_names_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (paused) {
                            stringResource(
                                R.string.saring_paused_until,
                                DateUtils.formatDateTime(LocalContext.current, ui.stats.pausedUntil, DateUtils.FORMAT_SHOW_TIME),
                            )
                        } else {
                            stringResource(R.string.saring_names_summary)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = content.copy(alpha = 0.8f),
                    )
                }
                CheckSwitch(ui.names)
            }
            if (ui.names) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.pause(if (paused) 0 else PAUSE_SECONDS) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(if (paused) R.string.saring_resume else R.string.saring_pause), maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = viewModel::update,
                        enabled = !ui.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (ui.busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.saring_update), maxLines = 1)
                        }
                    }
                }
                Text(
                    when {
                        ui.busy -> stringResource(R.string.saring_updating)
                        ui.stats.updated == 0L -> stringResource(R.string.saring_never_updated)
                        else -> stringResource(
                            R.string.saring_updated,
                            DateUtils.getRelativeTimeSpanString(ui.stats.updated).toString(),
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun AddField(hint: Int, actions: List<Pair<Int, Boolean>>, onAdd: (String, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(hint)) },
            singleLine = true,
            shape = LarasShapes.Hero,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.align(Alignment.End)) {
            for ((label, allow) in actions) {
                TextButton(
                    onClick = { onAdd(text, allow); text = "" },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun RemovableRow(title: String, summary: String?, shape: Shape, onRemove: () -> Unit) {
    Surface(shape = shape, color = LarasSurfaces.card) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                summary?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.saring_remove)) }
        }
    }
}

@Composable
private fun LogRow(
    group: LogGroup,
    by: LogBy,
    labels: Map<String, String>,
    open: Boolean,
    shape: Shape,
    onToggle: () -> Unit,
    onAllow: (String) -> Unit,
) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "expand")
    val system = stringResource(R.string.saring_log_system)
    Surface(shape = shape, color = LarasSurfaces.card) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (group.packageName.isNotEmpty()) AppIcon(group.packageName)
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            by == LogBy.NAME -> group.key
                            group.packageName.isEmpty() -> system
                            else -> labels[group.packageName] ?: group.packageName
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.lelap_log_group,
                            group.count,
                            DateUtils.getRelativeTimeSpanString(group.last).toString(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.ExpandMore, stringResource(R.string.lelap_expand), Modifier.rotate(turn))
            }
            if (open) {
                if (by == LogBy.NAME) {
                    TextButton({ onAllow(group.key) }, Modifier.padding(start = 12.dp)) {
                        Text(stringResource(R.string.saring_allow_name))
                    }
                }
                for ((detail, count) in group.details.take(DETAILS_SHOWN)) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (by == LogBy.APP) detail else labels[detail] ?: detail.ifEmpty { system },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        Text("×$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (by == LogBy.APP) {
                            TextButton({ onAllow(detail) }) { Text(stringResource(R.string.saring_allow)) }
                        } else {
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                }
                if (group.details.size > DETAILS_SHOWN) {
                    Text(
                        stringResource(R.string.saring_log_more, group.details.size - DETAILS_SHOWN),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}
