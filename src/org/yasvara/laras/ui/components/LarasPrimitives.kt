/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R

/**
 * Single M3 Expressive switch card row used across all screens.
 * Full-row toggleable and always renders [CheckSwitch].
 */
@Composable
fun LarasSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
    shape: Shape = LarasShapes.Card,
    containerColor: Color = LarasSurfaces.card,
    enabled: Boolean = true,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(horizontal = LarasSpacing.CardHorizontal, vertical = LarasSpacing.CardVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            badge?.invoke()
            CheckSwitch(checked = checked, enabled = enabled)
        }
    }
}

/**
 * Split-action row: tapping the body opens details, while a [CheckSwitch] behind a
 * [VerticalDivider] toggles the feature directly.
 */
@Composable
fun LarasSplitSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    summaryColor: Color? = null,
    shape: Shape = LarasShapes.Card,
    containerColor: Color = LarasSurfaces.card,
    switchEnabled: Boolean = true,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                leading?.invoke()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 14.dp))
            Box(
                Modifier
                    .fillMaxHeight()
                    .toggleable(checked, enabled = switchEnabled, role = Role.Switch, onValueChange = onCheckedChange)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CheckSwitch(checked = checked, enabled = switchEnabled)
            }
        }
    }
}

/**
 * Reusable collapsible (accordion) card for settings & DSP effects:
 * - Tapping the header expands or collapses [content] with a rotating chevron;
 * - Optional [checked] + [onCheckedChange] renders an independent [CheckSwitch] on the right;
 * - Optional [trailingActions] renders extra icon buttons (e.g. info dialog).
 */
@Composable
fun LarasCollapsibleCard(
    title: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryColor: Color? = null,
    leadingIcon: ImageVector? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    trailingActions: (@Composable RowScope.() -> Unit)? = null,
    shape: Shape = LarasShapes.Card,
    containerColor: Color = LarasSurfaces.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "chevronTurn")
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor) {
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (checked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    summary?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                trailingActions?.invoke(this)
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(turn),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (checked != null && onCheckedChange != null) {
                    Box(
                        Modifier.toggleable(
                            value = checked,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        ),
                    ) {
                        CheckSwitch(checked = checked)
                    }
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
            }
        }
    }
}

/**
 * Sticky search and filter header inside a [LazyColumn] so search bars and filter chips stay
 * pinned at the top of the viewport while scrolling long app/module lists.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.larasStickyControls(
    key: String = "sticky_controls",
    content: @Composable ColumnScope.() -> Unit,
) {
    stickyHeader(key = key) {
        Surface(
            color = LarasSurfaces.pageCanvas,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/** Clickable navigation or action card row. */
@Composable
fun LarasNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = trailing == null,
    loading: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = LarasShapes.Card,
    containerColor: Color = LarasSurfaces.card,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        enabled = enabled,
        onClick = onClick,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LarasSpacing.CardHorizontal, vertical = LarasSpacing.CardVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = summaryColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                trailing != null -> trailing()
                showChevron -> Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Selectable single-choice card row with a [RadioButton]. */
@Composable
fun LarasRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = LarasShapes.Card,
    containerColor: Color = LarasSurfaces.card,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = containerColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(start = LarasSpacing.CardHorizontal, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        }
    }
}

/** Tonal circular icon badge used as a row's leading visual. */
@Composable
fun LarasIconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(modifier.size(40.dp), shape = CircleShape, color = containerColor, contentColor = contentColor) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Unified M3 pill search field used across Home and all filterable lists. */
@Composable
fun LarasSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    containerColor: Color = LarasSurfaces.card,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        shape = LarasShapes.Pill,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = containerColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Unified semantic tag/badge replacing all local Badge/Tag/Pill duplicates. */
@Composable
fun LarasBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone? = null,
    containerColor: Color = tone?.containerColor() ?: MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = tone?.onContainerColor() ?: MaterialTheme.colorScheme.onSecondaryContainer,
    pill: Boolean = false,
    monospace: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = if (pill) LarasShapes.Pill else LarasShapes.Tag,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = if (pill) 10.dp else 7.dp, vertical = if (pill) 4.dp else 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (monospace) FontFamily.Monospace else null,
            ),
            maxLines = 1,
        )
    }
}

/** Consistent section header with optional summary and trailing action button. */
@Composable
fun LarasSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            summary?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Canonical Material 3 Primary Tab Row for intra-screen page tabs
 * (e.g. Saring, Lelap, Tabir) — never use FilterChips or ButtonGroups as page tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LarasTabBar(
    tabs: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val content: @Composable () -> Unit = {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(label(tab), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
    if (scrollable) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.fillMaxWidth(),
            containerColor = LarasSurfaces.pageCanvas,
            edgePadding = LarasSpacing.ScreenHorizontal,
            tabs = content,
        )
    } else {
        PrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.fillMaxWidth(),
            containerColor = LarasSurfaces.pageCanvas,
            tabs = content,
        )
    }
}

/**
 * Horizontal Material 3 [FilterChip] strip with semantic leading icons, optional item counts,
 * and extra trailing chips.
 */
@Composable
fun <T> LarasFilterRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ((T) -> ImageVector?)? = null,
    count: ((T) -> Int)? = null,
    showCheckmark: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in options) {
            val isSelected = option == selected
            val text = label(option)
            val suffix = count?.invoke(option)?.let { " $it" }.orEmpty()
            val iconVector = leadingIcon?.invoke(option)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                label = { Text(text + suffix, maxLines = 1) },
                leadingIcon = when {
                    iconVector != null -> {
                        { Icon(iconVector, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                    }
                    showCheckmark && isSelected -> {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    }
                    else -> null
                },
            )
        }
        trailing?.invoke(this)
    }
}

data class StatFigure(
    val value: String,
    val label: String,
    val onClick: (() -> Unit)? = null,
)

/** Unified KPI summary card replacing duplicate Stat/Figure rows across Saring & Lelap. */
@Composable
fun LarasStatBar(figures: List<StatFigure>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = LarasShapes.Hero, color = LarasSurfaces.card) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            figures.forEach { figure ->
                val cellModifier = Modifier
                    .weight(1f)
                    .clip(LarasShapes.Inset)
                    .let { m -> if (figure.onClick != null) m.clickable(onClick = figure.onClick) else m }
                    .padding(vertical = 8.dp)
                Column(cellModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(figure.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        figure.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Unified empty, unavailable, or planned placeholder card. */
@Composable
fun LarasEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = LarasShapes.Card, color = LarasSurfaces.card) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            icon?.let {
                Icon(it, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            body?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** Standardized Material 3 ModalBottomSheet shell across all features. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LarasModalSheet(
    onDismiss: () -> Unit,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LarasSurfaces.sheetContainer,
    ) {
        val base = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        Column(
            modifier = if (scrollable) base.verticalScroll(rememberScrollState()) else base,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

data class LarasPickerItem(
    val packageName: String,
    val label: String,
    val subtitle: String = packageName,
)

/** Reusable searchable visual app picker sheet (replaces manual package-name typing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LarasAppPickerSheet(
    apps: List<LarasPickerItem>,
    selected: Set<String>,
    searchPlaceholder: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    title: String? = null,
) {
    var query by remember { mutableStateOf("") }
    val visible = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter {
            it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LarasSurfaces.sheetContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            title?.let {
                Text(it, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 4.dp))
            }
            LarasSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = searchPlaceholder,
                containerColor = LarasSurfaces.sheetCard,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(LarasSpacing.ItemGap),
        ) {
            itemsIndexed(visible, key = { _, it -> it.packageName }) { idx, app ->
                val isSelected = app.packageName in selected
                Surface(
                    shape = LarasShapes.grouped(idx, visible.size),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else LarasSurfaces.sheetCard,
                    onClick = { onPick(app.packageName) },
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AppIcon(app.packageName, size = 38.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                app.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
