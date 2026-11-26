/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.lab

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.yasvara.laras.R
import org.yasvara.laras.catalog.BatteryStyle
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.theme.CommitResult
import org.yasvara.laras.data.theme.ThemeChange
import org.yasvara.laras.data.theme.ThemeKey
import org.yasvara.laras.ui.LarasViewModel
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.groupedShape

/** Lab: the Advanced gate, and on debuggable builds the tools used to test theme apply. */
@Composable
fun LabSection(prefs: PrefSnapshot, editor: PrefEditor, viewModel: LarasViewModel) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AdvancedGate(prefs, editor)
        if (Build.IS_DEBUGGABLE) ThemeTestTools(prefs, viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedGate(prefs: PrefSnapshot, editor: PrefEditor) {
    val enabled = prefs[Prefs.larasAdvanced]
    var warning by rememberSaveable { mutableStateOf(false) }
    Surface(shape = groupedShape(0, 1), color = LarasSurfaces.card) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(enabled, role = Role.Switch) { on ->
                    // Turning it off is always safe; turning it on is confirmed once per change.
                    if (on) warning = true else editor.set(Prefs.larasAdvanced, false)
                }
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lab_advanced), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.lab_advanced_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CheckSwitch(enabled)
        }
    }
    if (warning) {
        ModalBottomSheet(onDismissRequest = { warning = false }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    stringResource(R.string.lab_advanced_warning_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.lab_advanced_warning_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton({ warning = false }) {
                        Text(stringResource(R.string.lab_advanced_warning_cancel))
                    }
                    Button({
                        editor.set(Prefs.larasAdvanced, true)
                        warning = false
                    }) {
                        Text(stringResource(R.string.lab_advanced_warning_confirm))
                    }
                }
            }
        }
    }
}

/**
 * Applies a throwaway three-key theme and undoes it, to check the M0 apply path on a device:
 * one atomic write, the SystemUI reveal from the tapped row, and a single undo.
 */
@Composable
private fun ThemeTestTools(prefs: PrefSnapshot, viewModel: LarasViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val awaitRedraw: suspend () -> Unit = {
        // The first frame after the write shows the new values; the second has been drawn.
        withFrameNanos {}
        withFrameNanos {}
    }
    fun report(result: CommitResult) {
        val message =
            when (result) {
                is CommitResult.Applied -> return
                is CommitResult.Refused -> R.string.lab_theme_nothing_to_undo
                is CommitResult.RolledBack -> R.string.lab_theme_failed
            }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Text(
        stringResource(R.string.lab_debug_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    ToolRow(R.string.lab_theme_test, R.string.lab_theme_test_summary, 0, 2) { at ->
        scope.launch {
            val battery = prefs[Prefs.batteryStyle]
            val next = BatteryStyle.entries[(battery.ordinal + 1) % BatteryStyle.entries.size]
            val change =
                ThemeChange(
                    mapOf(
                        ThemeKey.THEME to
                            JSONObject().put("v", 1).put("statusIcons", "aosp")
                                .put("test", System.currentTimeMillis()).toString(),
                        ThemeKey.BATTERY_STYLE to next.value.toString(),
                        ThemeKey.PACK to
                            JSONObject().put("id", "org.yasvara.theme.test").put("version", 1)
                                .toString(),
                    )
                )
            report(viewModel.themes.apply(change, at.x, at.y, false, awaitRedraw))
        }
    }
    ToolRow(R.string.lab_theme_undo, R.string.lab_theme_undo_summary, 1, 2) { at ->
        scope.launch { report(viewModel.themes.undo(at.x, at.y, false, awaitRedraw)) }
    }
}

/** A tappable row that reports the tap's row centre in display pixels. */
@Composable
private fun ToolRow(title: Int, summary: Int, index: Int, count: Int, onClick: (Offset) -> Unit) {
    val view = LocalView.current
    var centre by remember { mutableStateOf(Offset.Unspecified) }
    Surface(shape = groupedShape(index, count), color = LarasSurfaces.card) {
        Column(
            Modifier.fillMaxWidth()
                .onGloballyPositioned { centre = it.boundsInWindow().center }
                .clickable {
                    val window = IntArray(2).also(view::getLocationOnScreen)
                    onClick(centre + Offset(window[0].toFloat(), window[1].toFloat()))
                }
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
