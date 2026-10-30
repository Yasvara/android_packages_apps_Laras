/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.SwitchPref

/**
 * Says which customization families SystemUI has paused after repeated failures, and lets the
 * user try them again. Their settings were kept, so trying again brings the same look back.
 */
@Composable
fun SafeModeBanner(
    prefs: PrefSnapshot,
    editor: PrefEditor,
    onRestoreColors: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = PAUSED.filter { (pref, _) -> prefs[pref] }
    if (paused.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            paused.forEach { (pref, message) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Shape as well as colour, so the warning reads without colour vision.
                    Icon(Icons.Rounded.ReportProblem, null, Modifier.padding(end = 12.dp))
                    Text(
                        stringResource(message),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (pref == Prefs.colorSafeMode) {
                        TextButton({
                            onRestoreColors()
                            editor.set(pref, false)
                        }) { Text(stringResource(R.string.safe_mode_restore_colors)) }
                    }
                    FilledTonalButton({ editor.set(pref, false) }) {
                        Text(stringResource(R.string.safe_mode_try_again))
                    }
                }
            }
        }
    }
}

private val PAUSED: List<Pair<SwitchPref, Int>> = listOf(
    Prefs.lockSafeMode to R.string.safe_mode_lock,
    Prefs.colorSafeMode to R.string.safe_mode_color,
    Prefs.themeSafeMode to R.string.safe_mode_theme,
)
