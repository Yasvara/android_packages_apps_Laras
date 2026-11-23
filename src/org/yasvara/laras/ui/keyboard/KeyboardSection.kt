/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import org.yasvara.laras.data.ketik.KetikFeature
import org.yasvara.laras.ui.components.LarasBadge
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasShapes
import org.yasvara.laras.ui.components.LarasSpacing
import org.yasvara.laras.ui.components.LarasSwitchRow
import org.yasvara.laras.ui.components.Tone
import org.yasvara.laras.ui.components.groupedShape

@Composable
fun KeyboardSection(viewModel: KeyboardViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<KetikFeature?>(null) }
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
                    Spacer(Modifier.height(10.dp))
                }
                KetikFeature.entries.forEachIndexed { i, feature ->
                    val checked = feature in ui.on
                    val state = ui.states[feature]
                    LarasSwitchRow(
                        title = stringResource(feature.title),
                        summary = stringResource(feature.summary),
                        checked = checked,
                        badge = if (checked && state != null) {
                            {
                                LarasBadge(
                                    text = stringResource(state.label()),
                                    tone = when (state) {
                                        FeatureState.ACTIVE -> Tone.GOOD
                                        FeatureState.WAITING -> Tone.NEUTRAL
                                        else -> Tone.BAD
                                    },
                                )
                            }
                        } else null,
                        shape = groupedShape(i, KetikFeature.entries.size),
                        onCheckedChange = { on ->
                            if (on && feature.cloud) asking = feature else viewModel.setEnabled(feature, on)
                        },
                    )
                }
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

private fun FeatureState.label() = when (this) {
    FeatureState.ACTIVE -> R.string.ketik_state_active
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
