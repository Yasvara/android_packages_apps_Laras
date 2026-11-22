/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.saring

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.saring.Refusal
import org.yasvara.laras.data.saring.SaringApp
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.lelap.Chips
import org.yasvara.laras.ui.lelap.Empty
import org.yasvara.laras.ui.screens.Page

private const val DETAILS_SHOWN = 8
private const val PAUSE_SECONDS = 5 * 60

private enum class Tab(override val value: Int, override val label: Int) : ChoiceOption {
    OVERVIEW(0, R.string.saring_tab_overview),
    LISTS(1, R.string.saring_tab_lists),
    APPS(2, R.string.saring_tab_apps),
    ACTIVITY(3, R.string.saring_tab_activity),
}

private enum class AppFilter(val label: Int, val test: (SaringApp) -> Boolean) {
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

/** Refusals folded under one app or one name. */
private class LogGroup(
    val key: String,
    val packageName: String,
    val count: Int,
    val last: Long,
    /** What sits under the heading, with how often, most frequent first. */
    val details: List<Pair<String, Int>>,
)

private fun groups(log: List<Refusal>, by: LogBy): List<LogGroup> = log
    .groupBy { if (by == LogBy.APP) it.packageName.ifEmpty { "uid:" + it.uid } else it.name }
    .map { (key, list) ->
        LogGroup(
            key,
            if (by == LogBy.APP) list.first().packageName else "",
            list.sumOf { it.count },
            list.maxOf { it.time },
            list.groupBy { if (by == LogBy.APP) it.name else it.packageName }
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

/** Laras › Connectivity › Saring: ads, trackers, scam names and pop-ups refused by the platform. */
@Composable
fun SaringScreen(onBack: () -> Unit, viewModel: SaringViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var logBy by rememberSaveable { mutableStateOf(LogBy.APP) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        viewModel.reload()
        onPauseOrDispose {}
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
        stringResource(R.string.saring_title),
        onBack,
        actions = {
            if (tab == Tab.ACTIVITY && ui.log.isNotEmpty()) {
                TextButton(viewModel::clearLog) { Text(stringResource(R.string.lelap_events_clear)) }
            }
        },
    ) {
        item(key = "intro") {
            Text(
                stringResource(if (ui.available) R.string.saring_intro else R.string.saring_unavailable),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
            )
        }
        item(key = "stats") {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceBright) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Figure(compact(ui.stats.refused), R.string.saring_stat_refused, Modifier.weight(1f))
                    Figure(compact(ui.stats.checked), R.string.saring_stat_checked, Modifier.weight(1f))
                    Figure(compact(ui.stats.names), R.string.saring_stat_names, Modifier.weight(1f))
                }
            }
        }
        item(key = "tabs") {
            ChoiceButtons(Tab.entries, tab, { tab = it as Tab }, Modifier.padding(vertical = 12.dp))
        }
        when (tab) {
            Tab.OVERVIEW -> {
                item(key = "names") {
                    ToggleRow(
                        stringResource(R.string.saring_names_title),
                        stringResource(R.string.saring_names_summary),
                        ui.names, groupedShape(0, 9), ui.available, viewModel::setNames,
                    )
                }
                item(key = "pause") {
                    val paused = ui.stats.pausedUntil > System.currentTimeMillis()
                    ActionRow(
                        stringResource(if (paused) R.string.saring_resume else R.string.saring_pause),
                        if (paused) {
                            stringResource(
                                R.string.saring_paused_until,
                                DateUtils.formatDateTime(LocalContext.current, ui.stats.pausedUntil, DateUtils.FORMAT_SHOW_TIME),
                            )
                        } else {
                            stringResource(R.string.saring_pause_summary)
                        },
                        groupedShape(1, 9), ui.names, false,
                    ) { viewModel.pause(if (paused) 0 else PAUSE_SECONDS) }
                }
                item(key = "update") {
                    ActionRow(
                        stringResource(R.string.saring_update),
                        when {
                            ui.busy -> stringResource(R.string.saring_updating)
                            ui.stats.updated == 0L -> stringResource(R.string.saring_never_updated)
                            else -> stringResource(
                                R.string.saring_updated,
                                DateUtils.getRelativeTimeSpanString(ui.stats.updated).toString(),
                            )
                        },
                        groupedShape(2, 9), ui.names && !ui.busy, ui.busy, viewModel::update,
                    )
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
                    ToggleRow(
                        stringResource(R.string.saring_dns_encrypted_title),
                        summary,
                        ui.dnsEncrypted, groupedShape(3, 9), ui.available, viewModel::setDnsEncrypted,
                    )
                }
                item(key = "boost") {
                    ToggleRow(
                        stringResource(R.string.saring_boost_title),
                        stringResource(R.string.saring_boost_summary),
                        ui.boost, groupedShape(4, 9), ui.available, viewModel::setBoost,
                    )
                }
                item(key = "safesearch") {
                    ToggleRow(
                        stringResource(R.string.saring_safesearch_title),
                        stringResource(R.string.saring_safesearch_summary),
                        ui.safesearch, groupedShape(5, 9), ui.available, viewModel::setSafeSearch,
                    )
                }
                item(key = "radio_guard") {
                    val op = ui.ceir.operator.ifEmpty { "Cellular" }
                    val ceirLine = when {
                        ui.ceir.rejectCause != 0 -> stringResource(R.string.saring_ceir_blocked, op, ui.ceir.rejectCause)
                        ui.ceir.inService -> stringResource(R.string.saring_ceir_ok, op)
                        else -> stringResource(R.string.saring_ceir_offline)
                    }
                    ToggleRow(
                        stringResource(R.string.saring_radio_guard_title),
                        stringResource(R.string.saring_radio_guard_summary) + "\n" + ceirLine,
                        ui.radioGuard, groupedShape(6, 9), ui.available, viewModel::setRadioGuard,
                    )
                }
                item(key = "web") {
                    ToggleRow(
                        stringResource(R.string.saring_web_title),
                        stringResource(R.string.saring_web_summary),
                        ui.web, groupedShape(7, 9), ui.available && ui.stats.names > 0, viewModel::setWeb,
                    )
                }
                item(key = "browser") {
                    ToggleRow(
                        stringResource(R.string.saring_browser_title),
                        stringResource(R.string.saring_browser_summary),
                        ui.browser, groupedShape(8, 9), ui.available, viewModel::setBrowser,
                    )
                }
                if (ui.browser) {
                    item(key = "sites_header") {
                        Header(R.string.saring_sites_title, R.string.saring_sites_summary)
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
            Tab.LISTS -> {
                items(ui.categories.size, key = { "cat:" + ui.categories[it].category.id }) { i ->
                    val state = ui.categories[i]
                    ToggleRow(
                        stringResource(state.category.title),
                        stringResource(state.category.summary) +
                            if (state.on && state.rules > 0) {
                                "\n" + stringResource(
                                    R.string.saring_cat_rules, state.rules,
                                    state.category.sources.joinToString { it.name },
                                )
                            } else {
                                ""
                            },
                        state.on, groupedShape(i, ui.categories.size), !ui.busy,
                    ) { viewModel.setCategory(state.category, it) }
                }
                item(key = "owner_header") {
                    Header(R.string.saring_owner_title, R.string.saring_owner_summary)
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
            Tab.APPS -> {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        placeholder = { Text(stringResource(R.string.visibility_search)) },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "filters") {
                    Chips(AppFilter.entries, filter, { stringResource(it.label) }, { f -> ui.apps.count(f.test) }) {
                        filter = it
                    }
                }
                if (shown.isEmpty()) item(key = "empty") { Empty(R.string.lelap_apps_empty) }
                items(shown.size, key = { "app:" + shown[it].packageName }) { i ->
                    val app = shown[i]
                    AppRow(app, groupedShape(i, shown.size)) { on -> viewModel.setExempt(app.packageName, !on) }
                }
            }
            Tab.ACTIVITY -> {
                item(key = "log_by") {
                    Chips(LogBy.entries, logBy, { stringResource(it.label) }, { by -> groups(ui.log, by).size }) {
                        logBy = it
                        expanded = null
                    }
                }
                if (log.isEmpty()) item(key = "log_empty") { Empty(R.string.saring_log_empty) }
                items(log.size, key = { "log:" + log[it].key }) { i ->
                    val group = log[i]
                    LogRow(
                        group,
                        logBy,
                        labels,
                        expanded == group.key,
                        groupedShape(i, log.size),
                        onToggle = { expanded = if (expanded == group.key) null else group.key },
                        onAllow = { name -> viewModel.setOwnerRule(name, true) },
                    )
                }
            }
        }
    }
    ui.notice?.let { notice ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissNotice,
            confirmButton = {
                TextButton(viewModel::dismissNotice) { Text(stringResource(android.R.string.ok)) }
            },
            text = {
                Text(
                    when (notice) {
                        Notice.UPDATED -> stringResource(
                            R.string.saring_notice_updated,
                            Formatter.formatShortFileSize(LocalContext.current, ui.stats.names * 8),
                            ui.stats.names,
                        )
                        Notice.UPDATE_FAILED -> stringResource(R.string.saring_notice_failed)
                        Notice.BAD_NAME -> stringResource(R.string.saring_notice_bad_name)
                    },
                )
            },
        )
    }
}

@Composable
private fun Figure(value: String, label: Int, modifier: Modifier) {
    Column(modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(title: Int, summary: Int) {
    Column(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RowText(title: String, summary: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    shape: Shape,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowText(title, summary, Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String,
    shape: Shape,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowText(title, summary, Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
        }
    }
}

/** A field with one button per decision the owner can make about what was typed. */
@Composable
private fun AddField(hint: Int, actions: List<Pair<Int, Boolean>>, onAdd: (String, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(hint)) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.align(Alignment.End)) {
            for ((label, allow) in actions) {
                TextButton(
                    onClick = {
                        onAdd(text, allow)
                        text = ""
                    },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun RemovableRow(title: String, summary: String?, shape: Shape, onRemove: () -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowText(title, summary, Modifier.weight(1f))
            IconButton(onRemove) { Icon(Icons.Rounded.Close, stringResource(R.string.saring_remove)) }
        }
    }
}

@Composable
private fun AppRow(app: SaringApp, shape: Shape, onChange: (Boolean) -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(!app.exempt, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName)
            Spacer(Modifier.width(16.dp))
            RowText(
                app.label,
                stringResource(if (app.exempt) R.string.saring_app_exempt else R.string.saring_app_filtered),
                Modifier.weight(1f),
            )
            if (app.refused > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.lelap_app_blocked, app.refused),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Switch(checked = !app.exempt, onCheckedChange = null)
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
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (group.packageName.isNotEmpty()) {
                    AppIcon(group.packageName)
                    Spacer(Modifier.width(16.dp))
                }
                RowText(
                    when {
                        by == LogBy.NAME -> group.key
                        group.packageName.isEmpty() -> system
                        else -> labels[group.packageName] ?: group.packageName
                    },
                    stringResource(
                        R.string.lelap_log_group,
                        group.count,
                        DateUtils.getRelativeTimeSpanString(group.last).toString(),
                    ),
                    Modifier.weight(1f),
                )
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
                        Text(
                            "×$count",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
