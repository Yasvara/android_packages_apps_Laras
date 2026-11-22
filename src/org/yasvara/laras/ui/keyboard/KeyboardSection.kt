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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.ketik.KetikFeature
import org.yasvara.laras.ui.components.SwitchRow
import org.yasvara.laras.ui.components.groupedShape

/** Ketik: Gboard features behind flags, on the stock Google-signed keyboard. */
@Composable
fun KeyboardSection(viewModel: KeyboardViewModel = viewModel()) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf<KetikFeature?>(null) }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when {
            ui.loading -> Unit
            !ui.available -> Message(R.string.ketik_unavailable, R.string.ketik_unavailable_body)
            !ui.installed -> Message(R.string.ketik_no_gboard, R.string.ketik_no_gboard_body)
            else -> {
                if (ui.reverted.isNotEmpty()) {
                    RevertCard(ui.reverted, viewModel::dismissRevert)
                    Spacer(Modifier.height(12.dp))
                }
                KetikFeature.entries.forEachIndexed { i, feature ->
                    FeatureRow(
                        feature,
                        checked = feature in ui.on,
                        state = ui.states[feature],
                        shape = groupedShape(i, KetikFeature.entries.size),
                        onChange = { on ->
                            if (on && feature.cloud) asking = feature
                            else viewModel.setEnabled(feature, on)
                        },
                    )
                }
                Text(
                    stringResource(R.string.ketik_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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
                TextButton({
                    asking = null
                    viewModel.setEnabled(feature, true)
                }) { Text(stringResource(R.string.ketik_cloud_confirm)) }
            },
            dismissButton = {
                TextButton({ asking = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun FeatureRow(
    feature: KetikFeature,
    checked: Boolean,
    state: FeatureState?,
    shape: Shape,
    onChange: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(shape = shape, color = c.surfaceBright) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            SwitchRow(
                stringResource(feature.title),
                checked,
                onChange,
                summary = stringResource(feature.summary),
            )
            if (checked && state != null) {
                Text(
                    stringResource(state.label()),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (state) {
                        FeatureState.ACTIVE -> c.primary
                        FeatureState.WAITING -> c.onSurfaceVariant
                        else -> c.error
                    },
                )
            }
        }
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
    Surface(shape = RoundedCornerShape(24.dp), color = c.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.ketik_reverted_title), style = MaterialTheme.typography.titleMedium,
                color = c.onErrorContainer)
            Text(stringResource(R.string.ketik_reverted_text), style = MaterialTheme.typography.bodyMedium,
                color = c.onErrorContainer)
            Text(flags.joinToString(), style = MaterialTheme.typography.bodySmall, color = c.onErrorContainer)
            TextButton(onDismiss) { Text(stringResource(R.string.ketik_reverted_dismiss)) }
        }
    }
}

@Composable
private fun Message(title: Int, body: Int) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
