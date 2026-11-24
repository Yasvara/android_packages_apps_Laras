/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.ketik.KetikCategory
import org.yasvara.laras.data.ketik.KetikFeatureDef
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasNavRow
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSurfaces
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape

@Composable
fun KeyboardSection(
    advanced: Boolean = false,
    onOpenFlags: () -> Unit = {},
    viewModel: KeyboardViewModel = viewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<KetikFeatureDef?>(null) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap)) {
        when {
            ui.loading -> Unit
            !ui.available -> LarasEmptyState(
                title = stringResource(R.string.ketik_unavailable),
                body = stringResource(R.string.ketik_unavailable_body),
            )
            !ui.installed -> LarasEmptyState(
                title = stringResource(R.string.ketik_no_gboard),
                body = stringResource(R.string.ketik_no_gboard_body),
            )
            else -> {
                if (ui.reverted.isNotEmpty()) {
                    RevertCard(ui.reverted, viewModel::dismissRevert)
                    Spacer(Modifier.height(6.dp))
                }

                RuntimeHealthCard(
                    ui = ui,
                    onActivateHook = viewModel::activateHookBridge,
                    onRestartGboard = viewModel::restartGboard,
                )

                val aiFeatures = ui.features.filter { it.category == KetikCategory.AI }
                val coreFeatures = ui.features.filter { it.category == KetikCategory.CORE }
                val labFeatures = ui.features.filter { it.category == KetikCategory.LAB }

                if (aiFeatures.isNotEmpty()) {
                    LarasSectionHeader(
                        title = stringResource(R.string.ketik_section_ai),
                        summary = stringResource(R.string.ketik_section_ai_summary),
                    )
                    FeatureGroup(aiFeatures, ui, onToggle = { f, on ->
                        if (on && f.cloud) asking = f else viewModel.setEnabled(f, on)
                    })
                }

                if (coreFeatures.isNotEmpty()) {
                    LarasSectionHeader(title = stringResource(R.string.ketik_section_core))
                    FeatureGroup(coreFeatures, ui, onToggle = { f, on ->
                        if (on && f.cloud) asking = f else viewModel.setEnabled(f, on)
                    })
                }

                if (labFeatures.isNotEmpty()) {
                    LarasSectionHeader(
                        title = stringResource(R.string.ketik_section_lab),
                        summary = stringResource(R.string.ketik_section_lab_summary),
                    )
                    FeatureGroup(labFeatures, ui, onToggle = { f, on ->
                        if (on && f.cloud) asking = f else viewModel.setEnabled(f, on)
                    })
                }

                LarasSectionHeader(title = stringResource(R.string.ketik_section_tools))
                LarasNavRow(
                    title = stringResource(R.string.ketik_flag_editor_title),
                    summary = if (ui.health.totalFlags > 0) {
                        stringResource(R.string.ketik_flag_editor_summary_count, ui.health.totalFlags)
                    } else {
                        stringResource(R.string.ketik_flag_editor_summary)
                    },
                    shape = groupedShape(0, 1),
                    onClick = onOpenFlags,
                )

                Text(
                    stringResource(R.string.ketik_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    asking?.let { feature ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(stringResource(R.string.ketik_cloud_title)) },
            text = { Text(stringResource(R.string.ketik_cloud_body)) },
            confirmButton = {
                TextButton({ asking = null; viewModel.setEnabled(feature, true) }) {
                    Text(stringResource(R.string.ketik_cloud_confirm))
                }
            },
            dismissButton = {
                TextButton({ asking = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RuntimeHealthCard(
    ui: KeyboardUi,
    onActivateHook: () -> Unit,
    onRestartGboard: () -> Unit,
) {
    val health = ui.health
    val anyBlocked = ui.states.values.any { it == FeatureState.BLOCKED || it == FeatureState.NOT_APPLIED }
    val versionLine = health.versionName.ifBlank { stringResource(R.string.ketik_version_unknown) }
    val handlerLine = when {
        health.handlers.isNotEmpty() -> health.handlers.joinToString()
        health.running -> stringResource(R.string.ketik_handler_none)
        else -> stringResource(R.string.ketik_state_waiting)
    }
    val (icon, tone, hookText) = when (ui.hookState) {
        HookBridgeState.ACTIVE -> Triple(
            Icons.Rounded.CheckCircle,
            Tone.GOOD,
            stringResource(R.string.ketik_hook_active),
        )
        HookBridgeState.PENDING_RESTART -> Triple(
            Icons.Rounded.WarningAmber,
            Tone.WARN,
            stringResource(R.string.ketik_hook_pending_restart),
        )
        HookBridgeState.DISABLED, HookBridgeState.UNSCOPED -> Triple(
            Icons.Rounded.Extension,
            if (anyBlocked) Tone.BAD else Tone.WARN,
            stringResource(R.string.ketik_hook_disabled),
        )
        HookBridgeState.NOT_INSTALLED -> Triple(
            Icons.Rounded.ErrorOutline,
            if (anyBlocked) Tone.BAD else Tone.NEUTRAL,
            stringResource(R.string.ketik_hook_missing),
        )
        HookBridgeState.UNAVAILABLE -> Triple(
            Icons.Rounded.AutoAwesome,
            Tone.NEUTRAL,
            stringResource(R.string.ketik_hook_unavailable),
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        shape = LarasShapes.Card,
        color = LarasSurfaces.card,
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusLine(
                icon = icon,
                tone = tone,
                text = hookText,
                detail = stringResource(R.string.ketik_runtime_detail, versionLine, handlerLine),
            )
            when (ui.hookState) {
                HookBridgeState.DISABLED, HookBridgeState.UNSCOPED -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(onClick = onActivateHook, enabled = !ui.hookBusy) {
                            Text(stringResource(R.string.ketik_hook_enable_action))
                        }
                    }
                }
                HookBridgeState.PENDING_RESTART -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(onClick = onRestartGboard, enabled = !ui.hookBusy) {
                            Text(stringResource(R.string.ketik_restart_gboard))
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun FeatureGroup(
    features: List<KetikFeatureDef>,
    ui: KeyboardUi,
    onToggle: (KetikFeatureDef, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap)) {
        features.forEachIndexed { i, feature ->
            val checked = feature.id in ui.on
            val state = ui.states[feature.id]
            LarasSwitchRow(
                title = feature.title,
                summary = feature.summary,
                checked = checked,
                badge = if (checked && state != null) {
                    {
                        LarasBadge(
                            text = stringResource(state.label()),
                            tone = when (state) {
                                FeatureState.ACTIVE -> Tone.GOOD
                                FeatureState.WAITING -> Tone.NEUTRAL
                                FeatureState.BLOCKED -> Tone.WARN
                                else -> Tone.BAD
                            },
                        )
                    }
                } else null,
                shape = groupedShape(i, features.size),
                onCheckedChange = { on -> onToggle(feature, on) },
            )
        }
    }
}

private fun FeatureState.label() = when (this) {
    FeatureState.ACTIVE -> R.string.ketik_state_active
    FeatureState.BLOCKED -> R.string.ketik_state_blocked
    FeatureState.WAITING -> R.string.ketik_state_waiting
    FeatureState.MISSING -> R.string.ketik_state_missing
    FeatureState.NOT_APPLIED -> R.string.ketik_state_not_applied
}

@Composable
private fun RevertCard(flags: List<String>, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(shape = LarasShapes.Card, color = c.errorContainer, contentColor = c.onErrorContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 6.dp)) {
            Text(stringResource(R.string.ketik_reverted_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ketik_reverted_text), style = MaterialTheme.typography.bodyMedium)
            Text(flags.joinToString(), style = MaterialTheme.typography.bodySmall)
            TextButton(onDismiss) { Text(stringResource(R.string.ketik_reverted_dismiss), color = c.onErrorContainer) }
        }
    }
}
