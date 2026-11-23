/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Catalog
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.ui.components.EntryRow
import org.yasvara.laras.ui.components.LarasEmptyState
import org.yasvara.laras.ui.components.LarasSectionHeader
import org.yasvara.laras.ui.components.groupedShape
import org.yasvara.laras.ui.components.label

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
                LarasEmptyState(
                    title = stringResource(R.string.changed_empty),
                    icon = Icons.Rounded.TaskAlt,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        sections.forEach { (section, changed) ->
            item(key = "section-${section.title}") {
                LarasSectionHeader(section.label(), Modifier.animateItem())
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
