/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.xposed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.xposed.XposedModule
import org.yasvara.laras.ui.components.AppIcon
import org.yasvara.laras.ui.components.groupedShape

/**
 * The Xposed modules installed on the device. The switch turns a module on or off; the rest of
 * the row opens the apps it is loaded into.
 */
@Composable
fun ModulesSection(onOpenScope: (String) -> Unit, viewModel: ModulesViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    // A module installed, or a scope changed, while away shows on return.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            ui.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            !ui.available -> Message(R.string.xposed_unavailable, R.string.xposed_unavailable_body)
            ui.modules.isEmpty() -> Message(R.string.xposed_empty, R.string.xposed_empty_body)
            else -> {
                ui.modules.forEachIndexed { i, module ->
                    ModuleRow(
                        module,
                        running = module.enabled && module.scope.any { it in ui.injected },
                        shape = groupedShape(i, ui.modules.size),
                        onOpen = { onOpenScope(module.packageName) },
                        onEnabled = { viewModel.setEnabled(module.packageName, it) },
                    )
                }
                Text(
                    stringResource(R.string.xposed_restart_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ModuleRow(
    module: XposedModule,
    running: Boolean,
    shape: Shape,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val loadable = module.problem == null
    Surface(shape = shape, color = c.surfaceBright) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clickable(onClick = onOpen)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(module.packageName)
                Column(Modifier.weight(1f)) {
                    Text(module.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        when {
                            !loadable -> stringResource(R.string.xposed_problem, module.problem!!)
                            !module.enabled -> stringResource(R.string.xposed_off)
                            module.scope.isEmpty() -> stringResource(R.string.xposed_no_scope)
                            else -> pluralStringResource(
                                if (running) R.plurals.xposed_scope_running else R.plurals.xposed_scope_count,
                                module.scope.size, module.scope.size,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (loadable) c.onSurfaceVariant else c.error,
                        maxLines = 2,
                    )
                }
            }
            VerticalDivider(Modifier.height(32.dp), color = c.outlineVariant)
            Box(Modifier.width(84.dp), contentAlignment = Alignment.Center) {
                Switch(checked = module.enabled, onCheckedChange = onEnabled, enabled = loadable || module.enabled)
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
