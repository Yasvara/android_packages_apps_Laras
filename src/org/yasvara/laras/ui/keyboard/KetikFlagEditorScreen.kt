/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.data.ketik.FlagState
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasFilterRow
import org.yasvara.laras.ui.components.LarasSearchBar
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.screens.Page

private val AI_LAB_PATTERN = Regex(
    "writing|proofread|jarvis|genai|agentic|jetson|rambler|llm|gm3|expressive|blur|free_cursor|super_insert|floating|emoji2|rounded|smart_reply|ocr|inline_suggestion",
    RegexOption.IGNORE_CASE,
)

@Composable
fun KetikFlagEditorScreen(
    onBack: () -> Unit,
    viewModel: KeyboardViewModel = viewModel(),
) {
    val ui by viewModel.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<FlagState?>(null) }
    var importDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadAllFlags() }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearEditorMessage()
        }
    }

    val filtered = remember(ui.flags, ui.query, ui.filter, ui.overrides) {
        val q = ui.query.trim()
        ui.flags.filter { f ->
            val filterOk = when (ui.filter) {
                FlagFilter.ALL -> true
                FlagFilter.MODIFIED -> f.fromUs || f.name in ui.overrides
                FlagFilter.AI_LAB -> AI_LAB_PATTERN.containsMatchIn(f.name)
                FlagFilter.OFF -> f.type == "Boolean" && f.value == "false" && f.editable
            }
            filterOk && (q.isEmpty() || f.name.contains(q, ignoreCase = true) || f.value.contains(q, ignoreCase = true))
        }
    }

    Page(
        title = stringResource(R.string.ketik_flag_editor_title),
        onBack = onBack,
        snackbarHostState = snackbar,
        actions = {
            IconButton(onClick = {
                scope.launch {
                    val json = viewModel.exportJson()
                    if (json != null) {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Ketik flags JSON", json))
                        snackbar.showSnackbar(context.getString(R.string.ketik_flags_exported))
                    }
                }
            }) {
                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.ketik_flags_export))
            }
            IconButton(onClick = { importDialog = true }) {
                Icon(Icons.Rounded.FileUpload, stringResource(R.string.ketik_flags_import))
            }
            IconButton(onClick = { clearDialog = true }) {
                Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.ketik_flags_clear_all))
            }
            IconButton(onClick = viewModel::loadAllFlags) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.ketik_flags_refresh))
            }
        },
        topContent = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LarasSpacing.ScreenHorizontal, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LarasSearchBar(
                    query = ui.query,
                    onQueryChange = viewModel::setEditorQuery,
                    placeholder = stringResource(R.string.ketik_flags_search),
                )
                LarasFilterRow(
                    options = FlagFilter.entries,
                    selected = ui.filter,
                    label = { f ->
                        when (f) {
                            FlagFilter.ALL -> stringResource(R.string.ketik_filter_all)
                            FlagFilter.MODIFIED -> stringResource(R.string.ketik_filter_modified)
                            FlagFilter.AI_LAB -> stringResource(R.string.ketik_filter_ai_lab)
                            FlagFilter.OFF -> stringResource(R.string.ketik_filter_off)
                        }
                    },
                    onSelect = viewModel::setEditorFilter,
                )
            }
        },
    ) {
        when {
            ui.loading -> item(key = "loading") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            filtered.isEmpty() -> item(key = "empty") {
                LarasEmptyState(
                    title = stringResource(R.string.ketik_flags_empty),
                    body = stringResource(R.string.ketik_flags_empty_body),
                )
            }
            else -> {
                itemsIndexed(filtered, key = { _, f -> f.name }) { i, flag ->
                    val overridden = flag.fromUs || flag.name in ui.overrides
                    FlagRow(
                        flag = flag,
                        overridden = overridden,
                        shape = groupedShape(i, filtered.size),
                        onBooleanToggle = { v -> viewModel.setRawFlag(flag.name, v) },
                        onEdit = { editing = flag },
                        onReset = { viewModel.resetRawFlag(flag.name) },
                    )
                }
            }
        }
    }

    editing?.let { flag ->
        EditFlagDialog(
            flag = flag,
            onDismiss = { editing = null },
            onSave = { value ->
                editing = null
                viewModel.setRawFlag(flag.name, value)
            },
        )
    }

    if (importDialog) {
        ImportJsonDialog(
            onDismiss = { importDialog = false },
            onImport = { text ->
                importDialog = false
                viewModel.importJson(text)
            },
        )
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.ketik_flags_clear_all)) },
            text = { Text(stringResource(R.string.ketik_flags_clear_all_body)) },
            confirmButton = {
                TextButton({ clearDialog = false; viewModel.clearAllOverrides() }) {
                    Text(stringResource(R.string.ketik_flags_clear_confirm))
                }
            },
            dismissButton = {
                TextButton({ clearDialog = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun FlagRow(
    flag: FlagState,
    overridden: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onBooleanToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
) {
    val isBool = flag.type == "Boolean"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (flag.editable && !isBool) Modifier.clickable(onClick = onEdit)
                else if (flag.editable && isBool) Modifier.clickable { onBooleanToggle(flag.value != "true") }
                else Modifier,
            ),
        shape = shape,
        color = LarasSurfaces.card,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    flag.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${flag.type}: ${flag.value.take(48)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (overridden) {
                        LarasBadge(text = "KETIK", tone = Tone.GOOD)
                    } else if (!flag.editable) {
                        LarasBadge(text = "RO", tone = Tone.NEUTRAL)
                    }
                }
            }
            if (overridden) {
                IconButton(onClick = onReset) {
                    Icon(Icons.Rounded.Restore, stringResource(R.string.ketik_flags_reset))
                }
            }
            if (isBool && flag.editable) {
                CheckSwitch(checked = flag.value == "true")
            }
        }
    }
}

@Composable
private fun EditFlagDialog(
    flag: FlagState,
    onDismiss: () -> Unit,
    onSave: (Any) -> Unit,
) {
    var text by remember(flag) { mutableStateOf(flag.value) }
    val isNumeric = flag.type == "Long" || flag.type == "Integer"
    val parsed: Any? = if (isNumeric) text.trim().toIntOrNull() else text

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                flag.name,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(flag.type) },
                singleLine = isNumeric,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let(onSave) },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun ImportJsonDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ketik_flags_import)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("{\"more_pill_keys\": true}") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onImport(text) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
