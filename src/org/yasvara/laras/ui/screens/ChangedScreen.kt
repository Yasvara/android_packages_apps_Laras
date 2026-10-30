/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Catalog
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.components.EntryRow
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.label

/** Every setting that differs from its default, grouped by where it lives, with undo. */
@Composable
fun ChangedScreen(prefs: PrefSnapshot, editor: PrefEditor, onResetAll: () -> Unit, onBack: () -> Unit) {
    val sections = Catalog.sections
        .map { section -> section to section.entries.filter { prefs.isChanged(it.pref) } }
        .filter { (_, changed) -> changed.isNotEmpty() }

    Page(
        title = stringResource(R.string.changed_title),
        onBack = onBack,
        actions = {
            AnimatedVisibility(sections.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                TextButton(onResetAll) { Text(stringResource(R.string.changed_reset_all)) }
            }
        },
    ) {
        if (sections.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().padding(top = 64.dp).animateItem(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.TaskAlt, null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.changed_empty),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        sections.forEach { (section, changed) ->
            item(key = "section-${section.title}") {
                SectionLabel(section.label(), Modifier.animateItem())
            }
            changed.forEachIndexed { i, entry ->
                item(key = entry.pref.toString()) {
                    Column(Modifier.animateItem()) {
                        EntryRow(entry, prefs, editor, groupedShape(i, changed.size)) {
                            editor.reset(entry.pref)
                        }
                    }
                }
            }
        }
    }
}
