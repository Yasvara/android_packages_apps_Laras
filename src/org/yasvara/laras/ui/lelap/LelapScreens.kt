/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.lelap

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.lelap.CutGroup
import org.yasvara.laras.data.lelap.LelapCatalog
import org.yasvara.laras.data.lelap.Risk
import org.yasvara.laras.data.lelap.SleepApp
import org.yasvara.laras.data.lelap.WakeEvent
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.HeroBackdropKind
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasHeroBackdrop
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.LarasTabBar
import org.yasvara.laras.ui.components.StatFigure
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.larasStickyControls
import org.yasvara.laras.ui.screens.Page

private const val DETAILS_SHOWN = 6

private enum class LelapTab(val label: Int) {
    APPS(R.string.lelap_tab_apps),
    ACTIVITY(R.string.lelap_tab_activity),
}

private enum class AppFilter(val label: Int, val icon: ImageVector, val test: (SleepApp) -> Boolean) {
    ALL(R.string.lelap_filter_all, Icons.Rounded.Apps, { true }),
    ON(R.string.lelap_filter_on, Icons.Rounded.Bedtime, { it.strict }),
    SUGGESTED(R.string.lelap_filter_suggested, Icons.Rounded.AutoAwesome, { !it.strict && !it.notifications }),
    NOISY(R.string.lelap_filter_noisy, Icons.Rounded.NotificationsOff, { it.blocked > 0 }),
    USER(R.string.lelap_filter_user, Icons.Rounded.Person, { !it.system }),
    SYSTEM(R.string.lelap_filter_system, Icons.Rounded.Android, { it.system }),
}

private enum class LogFilter(val label: Int, val icon: ImageVector, val kinds: Set<String>) {
    ALL(R.string.lelap_log_all, Icons.Rounded.Apps, emptySet()),
    START(R.string.lelap_log_start, Icons.Rounded.PlayArrow, setOf("start")),
    JOB(R.string.lelap_log_job, Icons.Rounded.Schedule, setOf("job")),
    PUSH(R.string.lelap_log_push, Icons.Rounded.NotificationsActive, setOf("push")),
    RESTORE(R.string.lelap_log_restore, Icons.Rounded.Restore, setOf("pin", "setting")),
}

private class LogGroup(
    val packageName: String,
    val count: Int,
    val last: Long,
    val details: List<Triple<String, String, Int>>,
)

private fun groups(events: List<WakeEvent>, filter: LogFilter): List<LogGroup> = events
    .filter { filter.kinds.isEmpty() || it.kind in filter.kinds }
    .groupBy { it.packageName }
    .map { (pkg, list) ->
        LogGroup(
            pkg,
            list.size,
            list.maxOf { it.time },
            list.groupingBy { it.kind to it.detail.substringAfterLast('.') }.eachCount()
                .map { Triple(it.key.first, it.key.second, it.value) }
                .sortedByDescending { it.third },
        )
    }
    .sortedByDescending { it.count }

@Composable
fun LelapAppsScreen(onBack: () -> Unit, viewModel: LelapViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(LelapTab.APPS) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var logFilter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    val shown = remember(ui.apps, query, filter) {
        ui.apps.filter {
            filter.test(it) &&
                (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
        }
    }
    val log = remember(ui.events, logFilter) { groups(ui.events, logFilter) }

    Page(
        title = stringResource(R.string.lelap_apps_title),
        onBack = onBack,
        actions = {
            if (tab == LelapTab.ACTIVITY && ui.events.isNotEmpty()) {
                TextButton(viewModel::clearEvents) { Text(stringResource(R.string.lelap_events_clear)) }
            }
        },
        topContent = {
            LarasTabBar(
                tabs = LelapTab.entries,
                selected = tab,
                label = { stringResource(it.label) },
                onSelect = { tab = it },
            )
        },
    ) {
        if (!ui.available) {
            item(key = "unavailable") { LarasEmptyState(title = stringResource(R.string.lelap_unavailable)) }
            return@Page
        }
        item(key = "stats") {
            val figures = listOf(
                StatFigure(ui.apps.count { it.strict }.toString(), stringResource(R.string.lelap_stat_on)) {
                    tab = LelapTab.APPS
                    filter = AppFilter.ON
                },
                StatFigure(ui.apps.count { it.asleep }.toString(), stringResource(R.string.lelap_stat_asleep)),
                StatFigure(ui.events.size.toString(), stringResource(R.string.lelap_stat_blocked)) {
                    tab = LelapTab.ACTIVITY
                },
            )
            Surface(modifier = Modifier.fillMaxWidth(), shape = LarasShapes.Hero, color = LarasSurfaces.card) {
                Box(Modifier.fillMaxWidth()) {
                    LarasHeroBackdrop(
                        kind = HeroBackdropKind.LELAP_SLEEP,
                        modifier = Modifier.matchParentSize(),
                        color = MaterialTheme.colorScheme.primary,
                        alpha = 0.16f,
                    )
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        figures.forEach { figure ->
                            val cellModifier = Modifier
                                .weight(1f)
                                .clip(LarasShapes.Inset)
                                .let { m -> if (figure.onClick != null) m.clickable(onClick = figure.onClick) else m }
                                .padding(vertical = 8.dp)
                            Column(cellModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(figure.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    figure.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        when (tab) {
            LelapTab.APPS -> {
                larasStickyControls(key = "lelap_sticky_controls") {
                    LarasSearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.visibility_search),
                    )
                    LarasFilterRow(
                        options = AppFilter.entries,
                        selected = filter,
                        label = { stringResource(it.label) },
                        leadingIcon = { it.icon },
                        count = { f -> ui.apps.count(f.test) },
                        onSelect = { filter = it },
                    )
                }
                if (shown.isEmpty()) {
                    item(key = "empty") { LarasEmptyState(title = stringResource(R.string.lelap_apps_empty)) }
                }
                items(shown.size, key = { "app:" + shown[it].packageName }) { i ->
                    val app = shown[i]
                    LarasSwitchRow(
                        title = app.label,
                        summary = stringResource(
                            when {
                                app.asleep && app.widget -> R.string.lelap_app_widget
                                app.asleep -> R.string.lelap_app_asleep
                                app.strict -> R.string.lelap_app_strict
                                !app.notifications -> R.string.lelap_app_suggested
                                else -> R.string.lelap_app_normal
                            },
                        ),
                        checked = app.strict,
                        leading = { AppIcon(app.packageName, size = 38.dp) },
                        badge = if (app.blocked > 0) {
                            { LarasBadge(stringResource(R.string.lelap_app_blocked, app.blocked), pill = true) }
                        } else null,
                        shape = groupedShape(i, shown.size),
                        onCheckedChange = { on ->
                            if (on && app.notifications) confirm = app.packageName
                            else viewModel.setStrict(app.packageName, on)
                        },
                    )
                }
            }
            LelapTab.ACTIVITY -> {
                larasStickyControls(key = "lelap_sticky_log_filters") {
                    LarasFilterRow(
                        options = LogFilter.entries,
                        selected = logFilter,
                        label = { stringResource(it.label) },
                        leadingIcon = { it.icon },
                        count = { f -> ui.events.count { f.kinds.isEmpty() || it.kind in f.kinds } },
                        onSelect = { logFilter = it },
                    )
                }
                if (log.isEmpty()) {
                    item(key = "log_empty") { LarasEmptyState(title = stringResource(R.string.lelap_log_empty)) }
                }
                items(log.size, key = { "log:" + log[it].packageName }) { i ->
                    val group = log[i]
                    LogRow(
                        group = group,
                        label = ui.apps.firstOrNull { it.packageName == group.packageName }?.label,
                        open = expanded == group.packageName,
                        shape = groupedShape(i, log.size),
                    ) { expanded = if (expanded == group.packageName) null else group.packageName }
                }
            }
        }
    }

    confirm?.let { pkg ->
        val label = ui.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
        ConfirmDialog(
            title = stringResource(R.string.lelap_strict_confirm_title, label),
            body = stringResource(R.string.lelap_strict_confirm_body),
            action = stringResource(R.string.lelap_strict_confirm),
            onConfirm = { viewModel.setStrict(pkg, true); confirm = null },
            onDismiss = { confirm = null },
        )
    }
    ErrorDialog(ui, viewModel)
}

@Composable
fun LelapCutsScreen(onBack: () -> Unit, viewModel: LelapViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    Page(stringResource(R.string.lelap_cuts_title), onBack) {
        if (!ui.available) {
            item(key = "unavailable") { LarasEmptyState(title = stringResource(R.string.lelap_unavailable)) }
            return@Page
        }
        item(key = "intro") {
            Text(
                stringResource(R.string.lelap_cuts_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        for (group in CutGroup.entries) {
            val cuts = LelapCatalog.cuts.filter { it.group == group }
            item(key = "group:" + group.name) {
                LarasSectionHeader(
                    title = stringResource(group.title),
                    summary = stringResource(group.summary),
                )
            }
            items(cuts.size, key = { "cut:" + cuts[it].id }) { i ->
                val cut = cuts[i]
                LarasSwitchRow(
                    title = stringResource(cut.title),
                    summary = stringResource(cut.summary),
                    checked = cut.id in ui.cutsOn,
                    leading = if (cut.risk == Risk.DANGEROUS) {
                        { Icon(Icons.Rounded.Warning, stringResource(R.string.lelap_dangerous), tint = MaterialTheme.colorScheme.error) }
                    } else null,
                    shape = groupedShape(i, cuts.size),
                    onCheckedChange = { on ->
                        if (on && cut.risk == Risk.DANGEROUS) confirm = cut.id else viewModel.setCut(cut, on)
                    },
                )
            }
        }
    }

    LelapCatalog.cuts.firstOrNull { it.id == confirm }?.let { cut ->
        ConfirmDialog(
            title = stringResource(cut.title),
            body = stringResource(cut.warning),
            action = stringResource(R.string.lelap_confirm),
            onConfirm = { viewModel.setCut(cut, true); confirm = null },
            onDismiss = { confirm = null },
        )
    }
    ErrorDialog(ui, viewModel)
}

@Composable
private fun kindLabel(kind: String) = stringResource(
    when (kind) {
        "start" -> R.string.lelap_event_start
        "push" -> R.string.lelap_event_push
        "job" -> R.string.lelap_event_job
        "pin" -> R.string.lelap_event_pin
        else -> R.string.lelap_event_setting
    },
)

@Composable
private fun LogRow(group: LogGroup, label: String?, open: Boolean, shape: Shape, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "expand")
    Surface(shape = shape, color = LarasSurfaces.card) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (group.packageName.isNotEmpty()) AppIcon(group.packageName, size = 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        label ?: group.packageName.ifEmpty { stringResource(R.string.lelap_log_system) },
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    stringResource(R.string.lelap_expand),
                    Modifier.rotate(turn),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    for ((kind, detail, count) in group.details.take(DETAILS_SHOWN)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                if (detail.isEmpty()) kindLabel(kind) else "$detail · ${kindLabel(kind)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("×$count", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (group.details.size > DETAILS_SHOWN) {
                        Text(
                            stringResource(R.string.lelap_log_more, group.details.size - DETAILS_SHOWN),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onConfirm) { Text(action) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun ErrorDialog(ui: LelapUi, viewModel: LelapViewModel) {
    val error = ui.error ?: return
    AlertDialog(
        onDismissRequest = viewModel::dismissError,
        title = { Text(stringResource(R.string.lelap_refused)) },
        text = { Text(error) },
        confirmButton = { TextButton(viewModel::dismissError) { Text(stringResource(android.R.string.ok)) } },
    )
}
