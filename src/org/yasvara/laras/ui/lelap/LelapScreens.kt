/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.lelap

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.lelap.Cut
import org.yasvara.laras.data.lelap.CutGroup
import org.yasvara.laras.data.lelap.LelapCatalog
import org.yasvara.laras.data.lelap.Risk
import org.yasvara.laras.data.lelap.SleepApp
import org.yasvara.laras.data.lelap.WakeEvent
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.ChoiceButtons
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private const val DETAILS_SHOWN = 6

private enum class Tab(override val value: Int, override val label: Int) : ChoiceOption {
    APPS(0, R.string.lelap_tab_apps),
    ACTIVITY(1, R.string.lelap_tab_activity),
}

private enum class AppFilter(val label: Int, val test: (SleepApp) -> Boolean) {
    ALL(R.string.lelap_filter_all, { true }),
    ON(R.string.lelap_filter_on, { it.strict }),
    SUGGESTED(R.string.lelap_filter_suggested, { !it.strict && !it.notifications }),
    NOISY(R.string.lelap_filter_noisy, { it.blocked > 0 }),
    USER(R.string.lelap_filter_user, { !it.system }),
    SYSTEM(R.string.lelap_filter_system, { it.system }),
}

private enum class LogFilter(val label: Int, val kinds: Set<String>) {
    ALL(R.string.lelap_log_all, emptySet()),
    START(R.string.lelap_log_start, setOf("start")),
    JOB(R.string.lelap_log_job, setOf("job")),
    PUSH(R.string.lelap_log_push, setOf("push")),
    RESTORE(R.string.lelap_log_restore, setOf("pin", "setting")),
}

/** Everything one package was denied, folded into one row. */
private class LogGroup(
    val packageName: String,
    val count: Int,
    val last: Long,
    /** Kind and what was denied, with how often, most frequent first. */
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

/** Laras › Performance & battery › Sleeping apps: which apps run only while open, and who tried to wake. */
@Composable
fun LelapAppsScreen(onBack: () -> Unit, viewModel: LelapViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.APPS) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(AppFilter.ALL) }
    var logFilter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    // The app waiting for the owner to accept losing its notifications.
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = remember(ui.apps, query, filter) {
        ui.apps.filter {
            filter.test(it) &&
                (query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true))
        }
    }
    val log = remember(ui.events, logFilter) { groups(ui.events, logFilter) }

    Page(
        stringResource(R.string.lelap_apps_title),
        onBack,
        actions = {
            if (tab == Tab.ACTIVITY && ui.events.isNotEmpty()) {
                TextButton(viewModel::clearEvents) { Text(stringResource(R.string.lelap_events_clear)) }
            }
        },
    ) {
        item(key = "intro") { Intro(R.string.lelap_apps_intro, ui) }
        item(key = "stats") { Stats(ui) { tab = Tab.ACTIVITY } }
        item(key = "tabs") {
            ChoiceButtons(Tab.entries, tab, { tab = it as Tab }, Modifier.padding(vertical = 12.dp))
        }
        when (tab) {
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
                    AppRow(app, groupedShape(i, shown.size)) { on ->
                        if (on && app.notifications) confirm = app.packageName else viewModel.setStrict(app.packageName, on)
                    }
                }
            }
            Tab.ACTIVITY -> {
                item(key = "log_filters") {
                    Chips(LogFilter.entries, logFilter, { stringResource(it.label) }, { f ->
                        ui.events.count { f.kinds.isEmpty() || it.kind in f.kinds }
                    }) { logFilter = it }
                }
                if (log.isEmpty()) item(key = "log_empty") { Empty(R.string.lelap_log_empty) }
                items(log.size, key = { "log:" + log[it].packageName }) { i ->
                    val group = log[i]
                    LogRow(
                        group,
                        ui.apps.firstOrNull { it.packageName == group.packageName }?.label,
                        expanded == group.packageName,
                        groupedShape(i, log.size),
                    ) { expanded = if (expanded == group.packageName) null else group.packageName }
                }
            }
        }
    }

    confirm?.let { pkg ->
        val label = ui.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
        Confirm(
            title = stringResource(R.string.lelap_strict_confirm_title, label),
            body = stringResource(R.string.lelap_strict_confirm_body),
            action = stringResource(R.string.lelap_strict_confirm),
            onConfirm = {
                viewModel.setStrict(pkg, true)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
    ErrorDialog(ui, viewModel)
}

@Composable
private fun Stats(ui: LelapUi, onBlocked: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Stat(ui.apps.count { it.strict }, R.string.lelap_stat_on, Modifier.weight(1f))
            Stat(ui.apps.count { it.asleep }, R.string.lelap_stat_asleep, Modifier.weight(1f))
            Stat(
                ui.events.size,
                R.string.lelap_stat_blocked,
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = onBlocked),
            )
        }
    }
}

@Composable
internal fun Stat(value: Int, label: Int, modifier: Modifier) {
    Column(modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun <T> Chips(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    count: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (option in options) {
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option) + "  " + count(option)) },
            )
        }
    }
}

@Composable
internal fun Empty(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
    )
}

/** Laras › Performance & battery › Tracking & telemetry: the cut catalog. */
@Composable
fun LelapCutsScreen(onBack: () -> Unit, viewModel: LelapViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    Page(stringResource(R.string.lelap_cuts_title), onBack) {
        item(key = "intro") { Intro(R.string.lelap_cuts_intro, ui) }
        for (group in CutGroup.entries) {
            val cuts = LelapCatalog.cuts.filter { it.group == group }
            item(key = "group:" + group.name) {
                Column(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)) {
                    Text(stringResource(group.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(group.summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(cuts.size, key = { "cut:" + cuts[it].id }) { i ->
                val cut = cuts[i]
                CutRow(cut, cut.id in ui.cutsOn, groupedShape(i, cuts.size)) { on ->
                    if (on && cut.risk == Risk.DANGEROUS) confirm = cut.id else viewModel.setCut(cut, on)
                }
            }
        }
    }

    LelapCatalog.cuts.firstOrNull { it.id == confirm }?.let { cut ->
        Confirm(
            title = stringResource(cut.title),
            body = stringResource(cut.warning),
            action = stringResource(R.string.lelap_confirm),
            onConfirm = {
                viewModel.setCut(cut, true)
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }
    ErrorDialog(ui, viewModel)
}

@Composable
private fun Intro(text: Int, ui: LelapUi) {
    Text(
        stringResource(if (ui.available) text else R.string.lelap_unavailable),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
    )
}

@Composable
private fun AppRow(app: SleepApp, shape: Shape, onChange: (Boolean) -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(app.strict, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(
                        when {
                            app.asleep && app.widget -> R.string.lelap_app_widget
                            app.asleep -> R.string.lelap_app_asleep
                            app.strict -> R.string.lelap_app_strict
                            !app.notifications -> R.string.lelap_app_suggested
                            else -> R.string.lelap_app_normal
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (app.blocked > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(
                        stringResource(R.string.lelap_app_blocked, app.blocked),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Switch(checked = app.strict, onCheckedChange = null)
        }
    }
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
                        style = MaterialTheme.typography.bodyMedium,
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
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp)) {
                    for ((kind, detail, count) in group.details.take(DETAILS_SHOWN)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(
                                if (detail.isEmpty()) kindLabel(kind) else detail + " · " + kindLabel(kind),
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
private fun CutRow(cut: Cut, on: Boolean, shape: Shape, onChange: (Boolean) -> Unit) {
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(on, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cut.risk == Risk.DANGEROUS) {
                Icon(
                    Icons.Rounded.Warning,
                    stringResource(R.string.lelap_dangerous),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(stringResource(cut.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(cut.summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = on, onCheckedChange = null)
        }
    }
}

@Composable
private fun Confirm(title: String, body: String, action: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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
        confirmButton = {
            TextButton(viewModel::dismissError) { Text(stringResource(android.R.string.ok)) }
        },
    )
}
