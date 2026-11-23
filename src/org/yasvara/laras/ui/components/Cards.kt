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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A titled card on the list surface; unified on [LarasShapes.Card] and [LarasSurfaces.card]. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = LarasShapes.Card, color = LarasSurfaces.card) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

/** Inline switch row inside cards; upgraded to M3 Expressive [CheckSwitch]. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        CheckSwitch(checked = checked, enabled = enabled)
    }
}

/** Semantic status tone; always paired with an icon so status is never colour-only. */
enum class Tone { GOOD, WARN, BAD, NEUTRAL }

@Composable
fun Tone.color(): Color = when (this) {
    Tone.GOOD -> MaterialTheme.colorScheme.primary
    Tone.WARN -> MaterialTheme.colorScheme.tertiary
    Tone.BAD -> MaterialTheme.colorScheme.error
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun Tone.containerColor(): Color = when (this) {
    Tone.GOOD -> MaterialTheme.colorScheme.primaryContainer
    Tone.WARN -> MaterialTheme.colorScheme.tertiaryContainer
    Tone.BAD -> MaterialTheme.colorScheme.errorContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
fun Tone.onContainerColor(): Color = when (this) {
    Tone.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
    Tone.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
    Tone.BAD -> MaterialTheme.colorScheme.onErrorContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun StatusLine(
    icon: ImageVector,
    tone: Tone,
    text: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp).padding(top = 2.dp), tint = tone.color())
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = tone.color())
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
