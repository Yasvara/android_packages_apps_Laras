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
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import org.yasvara.laras.data.integrity.Tier
import org.yasvara.laras.ui.components.StatusLine
import org.yasvara.laras.ui.components.Tone

/**
 * Answers one question, "what level will my apps get?", says what carries that level, and
 * offers the single next step. Everything else lives in the rows below it.
 */
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

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = container, contentColor = content) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Badge(
                    when {
                        state.health == Health.ATTENTION -> Icons.Rounded.GppMaybe
                        expected == null || expected == Tier.NONE || expected == Tier.BASIC -> Icons.Rounded.Shield
                        else -> Icons.Rounded.GppGood
                    },
                )
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hero_overline), style = MaterialTheme.typography.labelLarge)
                    Text(
                        expected?.let { stringResource(it.label()) } ?: stringResource(R.string.hero_unverified),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            TierSteps(expected)

            Text(pathLine(state), style = MaterialTheme.typography.bodyMedium)

            NextStep(state, onFix, onRestart, onRunCheck)

            if (state.path == IntegrityPath.KEYBOX && report != null) {
                Column {
                    TextButton({ details = !details }) {
                        Text(stringResource(if (details) R.string.check_hide else R.string.check_details))
                    }
                    AnimatedVisibility(details) { Panel { CheckList(report) } }
                }
            }
        }
    }
}

/** Says what the level rests on, in one line. */
@Composable
private fun pathLine(state: IntegrityUiState): String = when (state.path) {
    IntegrityPath.KEYBOX -> state.check.report?.let {
        stringResource(R.string.hero_via_keybox_checked,
            DateUtils.getRelativeTimeSpanString(it.at, System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS).toString())
    } ?: stringResource(R.string.hero_via_keybox)
    IntegrityPath.PROFILE -> stringResource(R.string.hero_via_profile,
        state.pif.stored?.profile?.model.orEmpty())
    IntegrityPath.NONE -> stringResource(R.string.hero_via_none)
}

/** At most one call to action: the issue first, then a pending restart, then verifying. */
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
        state.restartPending -> Step(stringResource(R.string.restart_pending), stringResource(R.string.restart_apply),
            Icons.Rounded.RestartAlt, onClick = onRestart)
        state.path == IntegrityPath.KEYBOX -> Step(
            stringResource(if (state.check.report == null) R.string.hero_verify_hint else R.string.hero_verify_again_hint),
            stringResource(if (state.check.report == null) R.string.check_run else R.string.check_run_again),
            busy = state.check.running, onClick = onRunCheck,
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
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick, enabled = !busy) {
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

@Composable
private fun Badge(icon: ImageVector) {
    Surface(Modifier.size(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(28.dp)) }
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

/** Basic, Device, Strong as three connected steps, filled and ticked up to [tier]. */
@Composable
private fun TierSteps(tier: Tier?) {
    val reached = (tier ?: Tier.NONE).ordinal
    val steps = listOf(Tier.BASIC, Tier.DEVICE, Tier.STRONG)
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        steps.forEachIndexed { i, step ->
            val on = step.ordinal <= reached
            val fill by animateColorAsState(if (on) colors.primary else colors.surface.copy(alpha = 0.6f), label = "step")
            Surface(
                Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(
                    topStart = if (i == 0) 20.dp else 6.dp, bottomStart = if (i == 0) 20.dp else 6.dp,
                    topEnd = if (i == steps.lastIndex) 20.dp else 6.dp,
                    bottomEnd = if (i == steps.lastIndex) 20.dp else 6.dp,
                ),
                color = fill,
                contentColor = if (on) colors.onPrimary else colors.onSurfaceVariant,
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
                    Text(stringResource(step.label()), Modifier.padding(start = if (on) 4.dp else 0.dp),
                        style = MaterialTheme.typography.labelLarge)
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
            StatusLine(icon, tone, stringResource(result.check.title()), detail = context.detail(result)
                ?: if (result.state == CheckState.UNKNOWN) stringResource(R.string.check_unknown) else null)
        }
        Text(stringResource(R.string.check_estimate_note), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A neutral inset that keeps status colours readable on the tinted hero. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceBright) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}
