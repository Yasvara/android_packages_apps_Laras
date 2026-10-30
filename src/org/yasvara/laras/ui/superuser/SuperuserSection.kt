/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.superuser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Entry
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.data.PrefEditor
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.SwitchPref
import org.yasvara.laras.ui.components.CheckSwitch
import org.yasvara.laras.ui.glyph.GuardGlyph

/** The guards a package passes on its way in, and those that keep the screen and storage private. */
private val InstallGuards = listOf(
    Prefs.suDisableSigVerify, Prefs.suAllowReplaceSystem, Prefs.suAllowDowngrade, Prefs.suBypassMinSdk,
    Prefs.suAllowTestOnly, Prefs.suDisablePkgVerify, Prefs.suNoStorageRestrict,
)
private val PrivacyGuards = listOf(Prefs.suSecureWindows, Prefs.suHideCaptureStatus, Prefs.suSpoofEncrypted)

/** Guards whose relaxing lets in code nobody vetted: these turn on only when held. */
private val HeldGuards = setOf(Prefs.suDisableSigVerify, Prefs.suAllowReplaceSystem, Prefs.suDisablePkgVerify)

/** Guards that only mean something with another relaxed, drawn hanging under it. */
private val Dependent = setOf(Prefs.suAllowReplaceSystem)

private const val HOLD_MILLIS = 600

/**
 * Superuser, grouped by what each guard protects. A relaxed guard's row rounds out and takes
 * the tertiary tone, so the open guards stand out at a glance; the riskiest ones turn on only
 * when held, while turning any guard back on is a single tap.
 */
@Composable
fun SuperuserSection(prefs: PrefSnapshot, editor: PrefEditor) {
    val entries = SystemGroup.SUPERUSER.entries.associateBy { it.pref }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GuardGroup(R.string.su_section_install, InstallGuards.mapNotNull { entries[it] }, prefs, editor)
        GuardGroup(R.string.su_section_privacy, PrivacyGuards.mapNotNull { entries[it] }, prefs, editor)
        if (entries[Prefs.suSpoofEncrypted]?.applies?.invoke(prefs) == false) {
            Text(
                stringResource(R.string.su_encrypted_hidden),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

@Composable
private fun GuardGroup(title: Int, guards: List<Entry>, prefs: PrefSnapshot, editor: PrefEditor) {
    // A dependent guard counts once it can mean something, and is kept in the list so it can
    // unfold under the guard it hangs from.
    val open = guards.filter { it.applies(prefs) }.map { prefs.isChanged(it.pref) }
    val shown = guards.filter { it.applies(prefs) || it.pref in Dependent }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Header(title, open)
        shown.forEachIndexed { i, entry ->
            val row = @Composable { GuardRow(entry, prefs, editor, last = i == shown.lastIndex) }
            if (entry.pref in Dependent) {
                AnimatedVisibility(
                    entry.applies(prefs),
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    Row {
                        Connector()
                        Box(Modifier.weight(1f)) { row() }
                    }
                }
            } else {
                row()
            }
        }
    }
}

/** The group's title, a pill per guard lit while open, and the count. */
@Composable
private fun Header(title: Int, open: List<Boolean>) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
        color = c.surfaceBright,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                open.forEach { on ->
                    val pill by animateColorAsState(
                        if (on) c.tertiary else c.surfaceContainerHighest,
                        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "pill",
                    )
                    Box(Modifier.size(12.dp, 7.dp).background(pill, RoundedCornerShape(4.dp)))
                }
            }
            Text(
                stringResource(R.string.home_su_count, open.count { it }, open.size),
                style = MaterialTheme.typography.labelLarge,
                color = c.onSurfaceVariant,
            )
        }
    }
}

/** The line from a guard down to the one hanging from it, drawn in the indent. */
@Composable
private fun Connector() {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(28.dp, 36.dp)) {
        val x = 14.dp.toPx()
        val r = 10.dp.toPx()
        val path = Path().apply {
            moveTo(x, 0f)
            lineTo(x, size.height - r)
            quadraticTo(x, size.height, x + r, size.height)
            lineTo(size.width, size.height)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun GuardRow(entry: Entry, prefs: PrefSnapshot, editor: PrefEditor, last: Boolean) {
    val pref = entry.pref as SwitchPref
    val on = prefs[pref]
    val c = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val held = pref in HeldGuards
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val hold = remember { Animatable(0f) }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) {
        if (hint) {
            delay(2200)
            hint = false
        }
    }

    // The row rounds out and takes the tertiary tone as the guard opens.
    val open by animateFloatAsState(if (on) 1f else 0f, motion.slowSpatialSpec(), label = "open")
    val tint by animateColorAsState(
        if (on) lerp(c.surfaceBright, c.tertiary, 0.16f) else c.surfaceBright,
        motion.defaultEffectsSpec(), label = "tint",
    )
    val corner by animateDpAsState(if (on) 20.dp else 6.dp, motion.defaultSpatialSpec(), label = "corner")
    val outer = 24.dp
    val shape = RoundedCornerShape(
        topStart = corner, topEnd = corner,
        bottomStart = if (last) maxOf(outer, corner) else corner, bottomEnd = if (last) maxOf(outer, corner) else corner,
    )
    val iconCorner by animateDpAsState(if (on) 20.dp else 12.dp, motion.defaultSpatialSpec(), label = "iconCorner")
    val iconFill by animateColorAsState(
        if (on) c.tertiaryContainer else c.surfaceContainerHighest, motion.defaultEffectsSpec(), label = "iconFill",
    )

    fun set(value: Boolean) = editor.set(pref, value)

    val stateText = stringResource(if (on) R.string.su_state_open else R.string.su_state_guarded)
    val holdLabel = stringResource(R.string.su_hold)
    Surface(
        shape = shape,
        color = tint,
        modifier = Modifier
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                toggleableState = ToggleableState(on)
                stateDescription = stateText
                // Accessibility services toggle directly; holding is a guard against stray taps.
                onClick(label = if (held && !on) holdLabel else null) { set(!on); true }
            }
            .pointerInput(on, held) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (on || !held) {
                        if (waitForUpOrCancellation() != null) set(!on)
                        return@awaitEachGesture
                    }
                    val filling = scope.launch {
                        hold.snapTo(0f)
                        // A hold is a duration the finger keeps, not a motion, so it fills linearly.
                        hold.animateTo(1f, tween(HOLD_MILLIS, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        set(true)
                    }
                    waitForUpOrCancellation()
                    if (hold.value < 1f) {
                        filling.cancel()
                        hint = true
                        scope.launch { hold.animateTo(0f, motion.fastEffectsSpec()) }
                    } else {
                        scope.launch { hold.snapTo(0f) }
                    }
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(iconFill, RoundedCornerShape(iconCorner)),
                contentAlignment = Alignment.Center,
            ) {
                GuardGlyph(
                    pref, { open },
                    color = lerp(c.onSurfaceVariant, c.onTertiaryContainer, open),
                    accent = c.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
                val summary = when {
                    hint -> holdLabel
                    else -> entry.summary?.let { stringResource(it) }
                }
                summary?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (hint) c.tertiary else c.onSurfaceVariant,
                    )
                }
            }
            Box(contentAlignment = Alignment.Center) {
                CheckSwitch(on)
                if (held && !on) HoldRing { hold.value }
            }
        }
    }
}

/** The outline that runs around the switch while it is held, closing as the hold completes. */
@Composable
private fun HoldRing(progress: () -> Float) {
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.size(60.dp, 40.dp)) {
        val p = progress()
        if (p <= 0f) return@Canvas
        val w = 3.dp.toPx()
        val track = Path().apply {
            addRoundRect(
                RoundRect(
                    w / 2, w / 2, size.width - w / 2, size.height - w / 2,
                    CornerRadius(size.height / 2),
                ),
            )
        }
        val measure = PathMeasure().apply { setPath(track, false) }
        val part = Path()
        measure.getSegment(0f, measure.length * p, part, true)
        drawPath(part, color, style = Stroke(w, cap = StrokeCap.Round))
    }
}
