/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.yasvara.laras.R
import org.yasvara.laras.data.integrity.CheckState
import org.yasvara.laras.data.integrity.IntegrityReport
import org.yasvara.laras.data.integrity.Revocation
import org.yasvara.laras.data.integrity.Tier
import org.yasvara.laras.ui.components.HeroBackdropKind
import org.yasvara.laras.ui.components.LarasHeroBackdrop
import org.yasvara.laras.ui.components.LarasMascotIcon
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.Tone

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IntegrityHero(
    state: IntegrityUiState,
    onRunCheck: () -> Unit,
    onRestart: () -> Unit,
    onFix: (Issue) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (state.health) {
            Health.READY -> colors.primaryContainer
            Health.ATTENTION -> colors.errorContainer
            Health.OFF -> colors.surfaceContainerHighest
        },
        label = "hero",
    )
    val content = when (state.health) {
        Health.READY -> colors.onPrimaryContainer
        Health.ATTENTION -> colors.onErrorContainer
        Health.OFF -> colors.onSurface
    }
    val expected = state.expected
    val report = state.check.report
    var details by rememberSaveable { mutableStateOf(false) }

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = container, contentColor = content) {
        Box(Modifier.fillMaxWidth()) {
            LarasHeroBackdrop(
                kind = HeroBackdropKind.INTEGRITY_KEYBOX,
                modifier = Modifier.matchParentSize(),
                color = content,
                alpha = if (state.health == Health.READY) 0.20f else 0.10f,
                animated = state.health == Health.READY,
            )
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Surface(
                        Modifier.size(48.dp),
                        shape = MaterialShapes.Gem.toShape(),
                        color = colors.surface.copy(alpha = 0.65f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            LarasMascotIcon(
                                modifier = Modifier.size(28.dp),
                                color = content,
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.hero_overline), style = MaterialTheme.typography.labelLarge)
                        Text(
                            expected?.let { stringResource(it.label()) } ?: stringResource(R.string.hero_unverified),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }

                TierSteps(expected)

                if (state.keybox.enabled && state.keybox.info != null) {
                    KeyboxRevocationPill(state.keybox.revocation)
                }

                Text(pathLine(state), style = MaterialTheme.typography.bodySmall)

                NextStep(state, onFix, onRestart, onRunCheck)

                if (report != null && state.keybox.enabled && state.keybox.info != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({ details = !details }) {
                            Text(stringResource(if (details) R.string.check_hide else R.string.check_details))
                            Icon(
                                if (details) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                null,
                                Modifier.padding(start = 4.dp).size(18.dp),
                            )
                        }
                        AnimatedVisibility(details) {
                            Panel { CheckList(report) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboxRevocationPill(revocation: Revocation) {
    val colors = MaterialTheme.colorScheme
    val (bg, fg, icon, text) = when (revocation) {
        is Revocation.Valid -> Quad(
            colors.surface.copy(alpha = 0.72f),
            colors.primary,
            Icons.Rounded.VerifiedUser,
            stringResource(R.string.integrity_keybox_valid) + " · " +
                stringResource(
                    R.string.keybox_checked,
                    DateUtils.getRelativeTimeSpanString(
                        revocation.checkedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString(),
                ),
        )
        is Revocation.Revoked -> Quad(
            colors.error,
            colors.onError,
            Icons.Rounded.Error,
            listOf(
                stringResource(R.string.integrity_keybox_revoked),
                revocation.reason.ifEmpty { null },
            ).filterNotNull().joinToString(" · "),
        )
        is Revocation.Unknown -> Quad(
            colors.surface.copy(alpha = 0.6f),
            colors.onSurfaceVariant,
            Icons.Rounded.Info,
            stringResource(R.string.integrity_keybox_present),
        )
    }
    Surface(shape = CircleShape, color = bg, contentColor = fg) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun pathLine(state: IntegrityUiState): String = when {
    state.issue == Issue.KEYBOX_REVOKED -> stringResource(R.string.hero_keybox_revoked_body)
    state.issue == Issue.KEYBOX_EXPIRED -> stringResource(R.string.issue_keybox_expired)
    state.path == IntegrityPath.KEYBOX -> state.check.report?.let {
        stringResource(
            R.string.hero_via_keybox_checked,
            DateUtils.getRelativeTimeSpanString(
                it.at,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
        )
    } ?: stringResource(R.string.hero_via_keybox)
    state.path == IntegrityPath.PROFILE -> stringResource(
        R.string.hero_via_profile,
        state.pif.stored?.profile?.model.orEmpty(),
    )
    else -> stringResource(R.string.hero_via_none)
}

@Composable
private fun NextStep(
    state: IntegrityUiState,
    onFix: (Issue) -> Unit,
    onRestart: () -> Unit,
    onRunCheck: () -> Unit,
) {
    val issue = state.issue
    when {
        issue != null -> Step(stringResource(issue.message()), stringResource(issue.action())) { onFix(issue) }
        state.restartPending -> Step(
            stringResource(R.string.restart_pending),
            stringResource(R.string.restart_apply),
            Icons.Rounded.RestartAlt,
            onClick = onRestart,
        )
        state.path == IntegrityPath.KEYBOX -> Step(
            stringResource(if (state.check.report == null) R.string.hero_verify_hint else R.string.hero_verify_again_hint),
            stringResource(if (state.check.report == null) R.string.check_run else R.string.check_run_again),
            busy = state.check.running,
            onClick = onRunCheck,
        )
    }
}

@Composable
private fun Step(
    text: String,
    action: String,
    icon: ImageVector? = null,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick, shape = CircleShape, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    icon?.let { Icon(it, null, Modifier.size(18.dp)) }
                    Text(action, Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
                }
            }
        }
    }
}

private fun Issue.message() = when (this) {
    Issue.KEYBOX_REVOKED -> R.string.issue_keybox_revoked
    Issue.KEYBOX_EXPIRED -> R.string.issue_keybox_expired
    Issue.STALE_PROFILE -> R.string.issue_stale_profile
}

private fun Issue.action() = when (this) {
    Issue.KEYBOX_REVOKED, Issue.KEYBOX_EXPIRED -> R.string.keybox_replace
    Issue.STALE_PROFILE -> R.string.integrity_pif_update
}

internal fun Tier.label() = when (this) {
    Tier.NONE -> R.string.tier_none
    Tier.BASIC -> R.string.tier_basic
    Tier.DEVICE -> R.string.tier_device
    Tier.STRONG -> R.string.tier_strong
}

@Composable
private fun TierSteps(tier: Tier?) {
    val reached = (tier ?: Tier.NONE).ordinal
    val steps = listOf(Tier.BASIC, Tier.DEVICE, Tier.STRONG)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, step ->
            val on = step.ordinal <= reached
            val fill by animateColorAsState(
                if (on) colors.primary else colors.surface.copy(alpha = 0.6f),
                label = "step",
            )
            Surface(
                Modifier.weight(1f).height(38.dp),
                shape = RoundedCornerShape(
                    topStart = if (i == 0) 19.dp else 6.dp,
                    bottomStart = if (i == 0) 19.dp else 6.dp,
                    topEnd = if (i == steps.lastIndex) 19.dp else 6.dp,
                    bottomEnd = if (i == steps.lastIndex) 19.dp else 6.dp,
                ),
                color = fill,
                contentColor = if (on) colors.onPrimary else colors.onSurfaceVariant,
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Text(
                        stringResource(step.label()),
                        Modifier.padding(start = if (on) 4.dp else 0.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckList(report: IntegrityReport) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        report.checks.forEach { result ->
            val (icon, tone) = when (result.state) {
                CheckState.PASS -> Icons.Rounded.CheckCircle to Tone.GOOD
                CheckState.FAIL -> Icons.Rounded.Error to Tone.BAD
                CheckState.UNKNOWN -> Icons.Rounded.Info to Tone.NEUTRAL
            }
            StatusLine(
                icon,
                tone,
                stringResource(result.check.title()),
                detail = context.detail(result)
                    ?: if (result.state == CheckState.UNKNOWN) stringResource(R.string.check_unknown) else null,
            )
        }
        Text(
            stringResource(R.string.check_estimate_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
