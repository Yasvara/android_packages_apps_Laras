/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.root.AuditEvent
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.ui.components.groupedShape

private enum class LogFilter { ALL, EXEC, DENIED, POLICY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkarLogsScreen(
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    viewModel: AkarViewModel = rememberAkarViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val repo = remember { viewModel.repository() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LogFilter.ALL) }
    var selectedEvent by remember { mutableStateOf<AuditEvent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    val filtered = remember(ui.audit, query, filter) {
        val needle = query.trim()
        ui.audit.filter { ev ->
            val typeMatch = when (filter) {
                LogFilter.ALL -> true
                LogFilter.EXEC -> ev.event.equals("EXEC", true) || ev.event.equals("ALLOW", true)
                LogFilter.DENIED -> ev.event.equals("DENY", true)
                LogFilter.POLICY -> ev.event.uppercase() in setOf("GRANT", "REVOKE", "ENABLE", "FREEZE", "START", "STOP")
            }
            if (!typeMatch) return@filter false
            if (needle.isEmpty()) return@filter true
            val label = ev.packages.split(',')
                .firstOrNull { it.isNotEmpty() && it != "-" }
                ?.let { repo.labelOf(it) }
                .orEmpty()
            ev.event.contains(needle, ignoreCase = true) ||
                ev.packages.contains(needle, ignoreCase = true) ||
                ev.detail.contains(needle, ignoreCase = true) ||
                label.contains(needle, ignoreCase = true) ||
                ev.uid.toString().contains(needle)
        }
    }

    val surface = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        containerColor = surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.akar_logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load(isRefresh = true) }) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.akar_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surface,
                    scrolledContainerColor = surface,
                ),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { viewModel.load(isRefresh = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item("search_filters") {
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.akar_search_logs)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                LogFilter.ALL to R.string.akar_log_all,
                                LogFilter.EXEC to R.string.akar_log_exec,
                                LogFilter.DENIED to R.string.akar_log_denied,
                                LogFilter.POLICY to R.string.akar_log_policy,
                            ).forEach { (f, labelRes) ->
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filter = f },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item("empty") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceBright,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(R.string.akar_no_activity),
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    items(filtered.size, key = { "${filtered[it].time}-${filtered[it].event}-$it" }) { i ->
                        LogItemRow(
                            event = filtered[i],
                            repo = repo,
                            shape = groupedShape(i, filtered.size),
                            onClick = { selectedEvent = filtered[i] },
                        )
                    }
                }
            }
        }
    }

    selectedEvent?.let { ev ->
        val copiedMsg = stringResource(R.string.akar_copied)
        LogDetailDialog(
            event = ev,
            repo = repo,
            onDismiss = { selectedEvent = null },
            onOpenApp = { pkg ->
                selectedEvent = null
                onOpenApp(pkg)
            },
            onCopied = { scope.launch { snackbar.showSnackbar(copiedMsg) } },
        )
    }
}

@Composable
private fun LogItemRow(
    event: AuditEvent,
    repo: RootRepository,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    val dotColor = when {
        event.event.equals("DENY", true) -> MaterialTheme.colorScheme.error
        event.event.equals("EXEC", true) || event.event.equals("ALLOW", true) -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    val who = event.packages.split(',').firstOrNull { it.isNotEmpty() && it != "-" }
        ?.let { if (it == RootRepository.SHELL_PACKAGE) stringResource(R.string.akar_adb_title) else repo.labelOf(it) }
    val relTime = DateUtils.getRelativeTimeSpanString(
        event.time,
        System.currentTimeMillis(),
        MINUTE_MS,
    ).toString()

    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceBright, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(dotColor),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        listOfNotNull(who ?: event.event, if (who != null) event.event else null)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        relTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (event.detail.isNotEmpty()) {
                    Text(
                        event.detail,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tier = event.tier.takeIf { it != "-" && it != "0" && it.isNotEmpty() }
                    if (tier != null) {
                        CompactTag(
                            text = tier.uppercase(),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (event.uid > 0) {
                        CompactTag(
                            text = "UID ${event.uid}",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
