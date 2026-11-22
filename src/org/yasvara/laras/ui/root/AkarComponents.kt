/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.root

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.text.format.DateUtils
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.yasvara.laras.R
import org.yasvara.laras.data.root.AuditEvent
import org.yasvara.laras.data.root.GrantPreset
import org.yasvara.laras.data.root.RootGrant
import org.yasvara.laras.data.root.RootRepository
import org.yasvara.laras.data.root.RootScope
import org.yasvara.laras.data.root.RootTier

internal const val MINUTE_MS = 60_000L
internal const val HOUR_MS = 60 * MINUTE_MS
internal const val DAY_MS = 24 * HOUR_MS

/** Shares one [AkarViewModel] across Akar's main, app-profile, and log screens. */
@Composable
fun rememberAkarViewModel(): AkarViewModel {
    val owner = LocalActivity.current as? ViewModelStoreOwner
    return if (owner != null) viewModel(viewModelStoreOwner = owner) else viewModel()
}

/**
 * Gates sensitive root changes behind biometrics or the device credential (PIN / pattern /
 * password). Uses framework [BiometricPrompt] so fingerprint/face works on [ComponentActivity],
 * falling back to [KeyguardManager] when biometric auth is unavailable.
 */
@Composable
fun rememberCredentialGate(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var noLock by remember { mutableStateOf(false) }
    val keyguardLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK) pending?.invoke()
        pending = null
    }
    val title = stringResource(R.string.akar_credential_title)
    val body = stringResource(R.string.akar_credential_body)

    if (noLock) {
        AlertDialog(
            onDismissRequest = { noLock = false },
            title = { Text(stringResource(R.string.akar_no_lock_title)) },
            text = { Text(stringResource(R.string.akar_no_lock_body)) },
            confirmButton = {
                TextButton({ noLock = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }

    return { action ->
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km?.isDeviceSecure != true) {
            noLock = true
        } else {
            val bm = context.getSystemService(BiometricManager::class.java)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canBio = bm?.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
            if (canBio) {
                val prompt = BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setSubtitle(body)
                    .setAllowedAuthenticators(authenticators)
                    .build()
                prompt.authenticate(
                    CancellationSignal(),
                    context.mainExecutor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            action()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE
                            ) {
                                @Suppress("DEPRECATION")
                                val intent = km.createConfirmDeviceCredentialIntent(title, body)
                                if (intent != null) {
                                    pending = action
                                    keyguardLauncher.launch(intent)
                                } else {
                                    noLock = true
                                }
                            }
                        }
                    },
                )
            } else {
                @Suppress("DEPRECATION")
                val intent = km.createConfirmDeviceCredentialIntent(title, body)
                if (intent == null) {
                    noLock = true
                } else {
                    pending = action
                    keyguardLauncher.launch(intent)
                }
            }
        }
    }
}

/**
 * Organic botanical root watermark rendered on the right side of the Working hero card.
 */
@Composable
fun BotanicalRootArt(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    alpha: Float = 0.22f,
) {
    Canvas(modifier) {
        val sx = size.width / 180f
        val sy = size.height / 150f
        val s = minOf(sx, sy)
        val ox = size.width - 180f * s
        val oy = (size.height - 150f * s) / 2f

        fun pt(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)

        fun stroke(w: Float, a: Float = 1f, block: Path.() -> Unit) {
            val path = Path().apply(block)
            drawPath(
                path = path,
                color = color.copy(alpha = alpha * a),
                style = Stroke(width = w * s, cap = StrokeCap.Round),
            )
        }

        // Main taproot
        stroke(3.6f) {
            val p0 = pt(112f, -6f)
            moveTo(p0.x, p0.y)
            val c1 = pt(112f, 18f); val c2 = pt(108f, 38f); val e1 = pt(104f, 62f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = pt(100f, 86f); val c4 = pt(108f, 112f); val e2 = pt(102f, 146f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }
        stroke(2.1f, 0.8f) {
            val p0 = pt(112f, -4f)
            moveTo(p0.x, p0.y)
            val c1 = pt(113f, 18f); val c2 = pt(118f, 34f); val e1 = pt(122f, 54f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = pt(126f, 76f); val c4 = pt(120f, 100f); val e2 = pt(128f, 132f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }

        // Left primary lateral branches
        stroke(2.6f) {
            val p0 = pt(110f, 18f)
            moveTo(p0.x, p0.y)
            val c1 = pt(92f, 26f); val c2 = pt(72f, 30f); val e1 = pt(52f, 46f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = pt(38f, 57f); val c4 = pt(28f, 70f); val e2 = pt(20f, 88f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }
        stroke(1.7f, 0.9f) {
            val p0 = pt(74f, 34f)
            moveTo(p0.x, p0.y)
            val c1 = pt(68f, 52f); val c2 = pt(72f, 68f); val e1 = pt(60f, 88f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = pt(53f, 100f); val c4 = pt(44f, 108f); val e2 = pt(34f, 118f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }
        stroke(1.2f, 0.75f) {
            val p0 = pt(48f, 50f)
            moveTo(p0.x, p0.y)
            val c1 = pt(34f, 54f); val c2 = pt(22f, 52f); val e1 = pt(10f, 62f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }
        stroke(2.0f) {
            val p0 = pt(105f, 54f)
            moveTo(p0.x, p0.y)
            val c1 = pt(86f, 64f); val c2 = pt(72f, 78f); val e1 = pt(56f, 102f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = pt(48f, 114f); val c4 = pt(44f, 128f); val e2 = pt(38f, 142f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }
        stroke(1.2f, 0.75f) {
            val p0 = pt(78f, 76f)
            moveTo(p0.x, p0.y)
            val c1 = pt(80f, 94f); val c2 = pt(74f, 112f); val e1 = pt(82f, 134f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }
        stroke(1.4f, 0.8f) {
            val p0 = pt(103f, 86f)
            moveTo(p0.x, p0.y)
            val c1 = pt(90f, 100f); val c2 = pt(84f, 116f); val e1 = pt(70f, 138f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }

        // Right lateral branches
        stroke(2.3f) {
            val p0 = pt(113f, 14f)
            moveTo(p0.x, p0.y)
            val c1 = pt(130f, 22f); val c2 = pt(148f, 30f); val e1 = pt(168f, 48f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }
        stroke(1.5f, 0.85f) {
            val p0 = pt(138f, 28f)
            moveTo(p0.x, p0.y)
            val c1 = pt(142f, 46f); val c2 = pt(136f, 62f); val e1 = pt(148f, 82f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = pt(154f, 92f); val c4 = pt(162f, 100f); val e2 = pt(170f, 108f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }
        stroke(1.9f) {
            val p0 = pt(107f, 48f)
            moveTo(p0.x, p0.y)
            val c1 = pt(126f, 60f); val c2 = pt(144f, 74f); val e1 = pt(160f, 98f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
            val c3 = pt(166f, 108f); val c4 = pt(170f, 120f); val e2 = pt(174f, 134f)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e2.x, e2.y)
        }
        stroke(1.2f, 0.75f) {
            val p0 = pt(134f, 68f)
            moveTo(p0.x, p0.y)
            val c1 = pt(130f, 88f); val c2 = pt(136f, 106f); val e1 = pt(128f, 128f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }
        stroke(1.4f, 0.8f) {
            val p0 = pt(106f, 94f)
            moveTo(p0.x, p0.y)
            val c1 = pt(118f, 108f); val c2 = pt(124f, 124f); val e1 = pt(134f, 144f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }

        // Delicate secondary rootlets
        stroke(0.9f, 0.65f) {
            val p0 = pt(62f, 84f)
            moveTo(p0.x, p0.y)
            val c1 = pt(52f, 86f); val c2 = pt(44f, 82f); val e1 = pt(34f, 88f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }
        stroke(0.9f, 0.65f) {
            val p0 = pt(150f, 42f)
            moveTo(p0.x, p0.y)
            val c1 = pt(158f, 42f); val c2 = pt(166f, 38f); val e1 = pt(174f, 44f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }
        stroke(0.9f, 0.6f) {
            val p0 = pt(90f, 112f)
            moveTo(p0.x, p0.y)
            val c1 = pt(86f, 124f); val c2 = pt(88f, 134f); val e1 = pt(84f, 146f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        }
    }
}

@Composable
fun CompactTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(shape = RoundedCornerShape(6.dp), color = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
fun InfoSpecRow(
    icon: ImageVector,
    title: String,
    value: String,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TierChip(tier: RootTier) {
    val scheme = MaterialTheme.colorScheme
    CompactTag(
        text = stringResource(if (tier == RootTier.ROOT) R.string.akar_tier_root else R.string.akar_tier_shell),
        containerColor = if (tier == RootTier.ROOT) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (tier == RootTier.ROOT) scheme.onErrorContainer else scheme.onSecondaryContainer,
    )
}

@Composable
fun scopeShortLabel(g: RootGrant): String = when (g.scope) {
    RootScope.ALWAYS -> stringResource(R.string.akar_badge_always)
    RootScope.UNTIL_REBOOT -> stringResource(R.string.akar_badge_reboot)
    RootScope.TIMED -> DateUtils.getRelativeTimeSpanString(
        g.expiresMs,
        System.currentTimeMillis(),
        MINUTE_MS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
}

@Composable
fun grantSummaryLine(g: RootGrant): String {
    val now = System.currentTimeMillis()
    val scope = when (g.scope) {
        RootScope.ALWAYS -> stringResource(R.string.akar_scope_always)
        RootScope.UNTIL_REBOOT -> stringResource(R.string.akar_scope_reboot)
        RootScope.TIMED -> stringResource(
            R.string.akar_scope_until,
            DateUtils.getRelativeTimeSpanString(g.expiresMs, now, MINUTE_MS).toString(),
        )
    }
    val used = if (g.lastUsedMs > 0) {
        stringResource(
            R.string.akar_last_used,
            DateUtils.getRelativeTimeSpanString(g.lastUsedMs, now, MINUTE_MS).toString(),
        )
    } else {
        stringResource(R.string.akar_never_used)
    }
    return "$scope · $used"
}

@Composable
fun adbPresetLabel(preset: GrantPreset): String = when (preset.scope) {
    RootScope.ALWAYS -> stringResource(R.string.akar_scope_always)
    RootScope.UNTIL_REBOOT -> stringResource(R.string.akar_scope_reboot)
    RootScope.TIMED -> when {
        preset.durationMs <= 10 * MINUTE_MS -> stringResource(R.string.akar_scope_10min)
        preset.durationMs <= HOUR_MS -> stringResource(R.string.akar_scope_1h)
        else -> stringResource(R.string.akar_scope_24h)
    }
}

private data class AdbDurationOption(val labelRes: Int, val scope: RootScope, val durationMs: Long)

private val ADB_DURATIONS = listOf(
    AdbDurationOption(R.string.akar_scope_10min, RootScope.TIMED, 10 * MINUTE_MS),
    AdbDurationOption(R.string.akar_scope_1h, RootScope.TIMED, HOUR_MS),
    AdbDurationOption(R.string.akar_scope_24h, RootScope.TIMED, DAY_MS),
    AdbDurationOption(R.string.akar_scope_reboot, RootScope.UNTIL_REBOOT, 0L),
    AdbDurationOption(R.string.akar_scope_always, RootScope.ALWAYS, 0L),
)

/** Lets the owner configure ADB shell su duration (e.g. 1 hour or permanent) or turn it off. */
@Composable
fun AdbDurationDialog(
    currentGrant: RootGrant?,
    preset: GrantPreset,
    onDismiss: () -> Unit,
    onApply: (RootTier, RootScope, Long) -> Unit,
    onDisable: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    var tier by remember(currentGrant, preset) {
        mutableStateOf(currentGrant?.tier ?: preset.tier)
    }
    val initialIndex = remember(currentGrant, preset) {
        val scope = currentGrant?.scope ?: preset.scope
        when (scope) {
            RootScope.ALWAYS -> 4
            RootScope.UNTIL_REBOOT -> 3
            RootScope.TIMED -> {
                val ms = if (currentGrant != null) {
                    (currentGrant.expiresMs - System.currentTimeMillis()).coerceAtLeast(0L)
                } else {
                    preset.durationMs
                }
                when {
                    ms <= 15 * MINUTE_MS -> 0
                    ms <= 2 * HOUR_MS -> 1
                    else -> 2
                }
            }
        }
    }
    var selected by remember(initialIndex) { mutableStateOf(initialIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.akar_adb_dialog_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.akar_adb_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    RootTier.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = tier == t,
                            onClick = { tier = t },
                            shape = SegmentedButtonDefaults.itemShape(i, RootTier.entries.size),
                        ) {
                            Text(stringResource(if (t == RootTier.ROOT) R.string.akar_tier_root else R.string.akar_tier_shell))
                        }
                    }
                }
                Column {
                    ADB_DURATIONS.forEachIndexed { idx, opt ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selected = idx }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == idx, onClick = { selected = idx })
                            Text(
                                stringResource(opt.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentGrant != null) {
                        OutlinedButton(onClick = onDisable) {
                            Text(stringResource(R.string.akar_revoke))
                        }
                    }
                    TextButton(onClick = onOpenAdvanced) {
                        Text(stringResource(R.string.akar_adb_open_profile))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val opt = ADB_DURATIONS[selected]
                onApply(tier, opt.scope, opt.durationMs)
            }) {
                Text(stringResource(R.string.akar_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
fun IdleDaysDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.akar_idle)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.akar_idle_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                listOf(0, 7, 30, 90).forEach { days ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(days) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currentDays == days, onClick = { onSelect(days) })
                        Text(
                            if (days == 0) stringResource(R.string.akar_idle_never)
                            else stringResource(R.string.akar_idle_days, days),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.akar_close)) }
        },
    )
}

@Composable
fun LogDetailDialog(
    event: AuditEvent,
    repo: RootRepository,
    onDismiss: () -> Unit,
    onOpenApp: ((String) -> Unit)? = null,
    onCopied: () -> Unit,
) {
    val context = LocalContext.current
    val pkgs = event.packages.split(',').filter { it.isNotEmpty() && it != "-" }
    val primaryPkg = pkgs.firstOrNull()
    val appTitle = primaryPkg?.let { repo.labelOf(it) } ?: event.event
    val formattedTime = DateUtils.formatDateTime(
        context,
        event.time,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_NUMERIC_DATE,
    )
    val detailBlock = buildString {
        appendLine("time: $formattedTime")
        appendLine("event: ${event.event}")
        if (event.uid > 0) appendLine("uid: ${event.uid}")
        if (event.tier.isNotEmpty() && event.tier != "-" && event.tier != "0") {
            appendLine("tier: ${event.tier}")
        }
        if (pkgs.isNotEmpty()) appendLine("packages: ${pkgs.joinToString(", ")}")
        if (event.detail.isNotEmpty()) append("detail: ${event.detail}")
    }.trimEnd()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(appTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    event.event,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            text = detailBlock,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
                if (primaryPkg != null && onOpenApp != null && primaryPkg != "all") {
                    OutlinedButton(
                        onClick = { onOpenApp(primaryPkg) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.akar_open_app))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clipText = event.detail.ifEmpty { detailBlock }
                context.getSystemService(ClipboardManager::class.java)
                    ?.setPrimaryClip(ClipData.newPlainText("akar_log", clipText))
                onCopied()
                onDismiss()
            }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                Text(stringResource(R.string.akar_copy), Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.akar_close))
            }
        },
    )
}
