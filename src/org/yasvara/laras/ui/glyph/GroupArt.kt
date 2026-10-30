/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.glyph

import android.app.WallpaperManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.catalog.Prefs
import org.yasvara.laras.catalog.SystemGroup
import org.yasvara.laras.ui.connectivity.regionSummary
import org.yasvara.laras.data.LauncherIcons
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.SettingsRepository
import org.yasvara.laras.data.integrity.AppProfilesRepository
import org.yasvara.laras.data.integrity.Tier
import org.yasvara.laras.ui.apps.AppsViewModel
import org.yasvara.laras.ui.components.BadgeTone
import org.yasvara.laras.ui.integrity.Health
import org.yasvara.laras.ui.integrity.IntegrityViewModel

private const val TAG = "GroupArt"

/** How navigation is set (`Settings.Secure.NAVIGATION_MODE`). */
enum class NavMode { THREE_BUTTON, TWO_BUTTON, GESTURE }

/**
 * What the home cards draw: the system as it is set now, read where each group reads it. The
 * fields a card shows also replay its motion when they change, so a card answers an edit.
 */
@Stable
data class HomeFacts(
    val wallpaper: ImageBitmap? = null,
    val shapePath: String? = null,
    val shapeTitle: String? = null,
    val navMode: NavMode = NavMode.GESTURE,
    /** Media, ring, alarm and notification volume, each 0..1. */
    val volumes: List<Float> = emptyList(),
    val integrityTier: Tier? = null,
    val integrityHealth: Health? = null,
    val spoofedApps: List<ImageBitmap> = emptyList(),
    val spoofedCount: Int = 0,
    /** Pentas: games it serves, their icons (last played first, at most three) and the last. */
    val pentasGames: Int = 0,
    val pentasIcons: List<ImageBitmap> = emptyList(),
    val pentasLast: String? = null,
)

/** Reads [HomeFacts] and keeps them current: on each resume, and as the theme or apps change. */
@Composable
fun rememberHomeFacts(prefs: PrefSnapshot): HomeFacts {
    val context = LocalContext.current
    val integrity by viewModel<IntegrityViewModel>().state.collectAsStateWithLifecycle()
    val apps by viewModel<AppsViewModel>().state.collectAsStateWithLifecycle()
    var system by remember { mutableStateOf(HomeFacts()) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose {}
    }
    val theme = prefs[Prefs.theme]
    LaunchedEffect(theme, resumes) {
        system = withContext(Dispatchers.IO) { readSystem(context) }
    }
    val spoofed = buildList {
        if (apps.photos) add(AppProfilesRepository.PHOTOS)
        if (apps.snapchat) add(AppProfilesRepository.SNAPCHAT)
        apps.assignments.forEach { add(it.packageName) }
    }
    var icons by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    LaunchedEffect(spoofed) {
        icons = withContext(Dispatchers.IO) {
            spoofed.mapNotNull { pkg ->
                runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
                    ?.toBitmap(96, 96)?.asImageBitmap()
            }.take(3)
        }
    }
    return system.copy(
        integrityTier = if (integrity.loaded) integrity.expected else null,
        integrityHealth = if (integrity.loaded) integrity.health else null,
        spoofedApps = icons,
        spoofedCount = spoofed.size,
    )
}

private fun readSystem(context: Context): HomeFacts {
    val icons = LauncherIcons(context, SettingsRepository(context.contentResolver))
    val shapes = icons.shapes()
    val chosen = icons.choice().shape
    // The theme's pick, else the launcher's own, else its first, which it falls back to.
    val shape = shapes.firstOrNull { it.key == chosen } ?: shapes.firstOrNull { it.current } ?: shapes.firstOrNull()
    val wallpaper = try {
        // The lock wallpaper, or the home one when the lock screen shares it.
        val manager = WallpaperManager.getInstance(context)
        (manager.getDrawable(WallpaperManager.FLAG_LOCK) ?: manager.getDrawable(WallpaperManager.FLAG_SYSTEM))
            ?.toBitmap(270, 540)?.asImageBitmap()
    } catch (e: Exception) {
        // Without a readable wallpaper the miniature keeps the theme's own gradient.
        Log.w(TAG, "No wallpaper for the style card", e)
        null
    }
    val nav = when (Settings.Secure.getInt(context.contentResolver, "navigation_mode", 2)) {
        0 -> NavMode.THREE_BUTTON
        1 -> NavMode.TWO_BUTTON
        else -> NavMode.GESTURE
    }
    val audio = context.getSystemService(AudioManager::class.java)
    val volumes = listOf(
        AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING,
        AudioManager.STREAM_ALARM, AudioManager.STREAM_NOTIFICATION,
    ).map { stream ->
        val max = audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        audio.getStreamVolume(stream).toFloat() / max
    }
    val pentas = readPentas(context)
    return HomeFacts(
        wallpaper = wallpaper,
        shapePath = shape?.path,
        shapeTitle = shape?.title,
        navMode = nav,
        volumes = volumes,
        pentasGames = pentas.first.size,
        pentasIcons = pentas.first.take(3).mapNotNull { pkg ->
            runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
                ?.toBitmap(96, 96)?.asImageBitmap()
        },
        pentasLast = pentas.second,
    )
}

const val PENTAS_PACKAGE = "org.yasvara.pentas"

/**
 * The games Pentas serves, by its own rule (SPEC-0012 §3): the user's list, plus installed
 * apps that declare themselves games while auto-detect is on. Last played first, then by name.
 * Empty when Pentas isn't installed.
 */
private fun readPentas(context: Context): Pair<List<String>, String?> {
    val pm = context.packageManager
    val installed = runCatching { pm.getApplicationInfo(PENTAS_PACKAGE, 0) }.isSuccess
    if (!installed) return emptyList<String>() to null
    val cr = context.contentResolver
    val listed = Settings.System.getString(cr, "pentas_game_list").orEmpty()
        .split(';').filter { it.isNotBlank() }.map { it.substringBefore('=') }
    val auto = Settings.System.getInt(cr, "pentas_auto_game_detect", 1) != 0
    @Suppress("DEPRECATION")
    val detected = if (!auto) emptyList() else pm.getInstalledApplications(0)
        .filter {
            (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 &&
                (it.category == android.content.pm.ApplicationInfo.CATEGORY_GAME ||
                    (it.flags and android.content.pm.ApplicationInfo.FLAG_IS_GAME) != 0)
        }
        .map { it.packageName }
    val last = Settings.System.getString(cr, "pentas_last_game")?.takeIf { it.isNotEmpty() }
    val label = { pkg: String ->
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
    }
    val games = (listed + detected).distinct().filter { label(it) != null }
        .sortedWith(compareBy<String> { it != last }.thenBy { label(it) })
    return games to last?.let(label)
}

/** The state line under a card's title. */
@Composable
fun SystemGroup.status(facts: HomeFacts, prefs: PrefSnapshot): String = when (this) {
    SystemGroup.STYLE ->
        facts.shapeTitle?.let { stringResource(R.string.home_style_icons, it) } ?: stringResource(summary)
    SystemGroup.GESTURES -> stringResource(
        when (facts.navMode) {
            NavMode.GESTURE -> R.string.home_nav_gesture
            NavMode.TWO_BUTTON -> R.string.home_nav_two
            NavMode.THREE_BUTTON -> R.string.home_nav_three
        },
    )
    SystemGroup.SUPERUSER ->
        stringResource(if (relaxedGuards(prefs).first > 0) R.string.home_su_relaxed else R.string.home_su_none)
    SystemGroup.INTEGRITY -> when (facts.integrityHealth) {
        Health.READY -> stringResource(R.string.home_integrity_ready)
        Health.ATTENTION -> stringResource(R.string.home_integrity_attention)
        Health.OFF -> stringResource(R.string.home_integrity_off)
        null -> stringResource(summary)
    }
    SystemGroup.PRIVACY ->
        if (prefs[Prefs.scramblePin]) stringResource(R.string.home_privacy_scramble) else stringResource(summary)
    SystemGroup.APPS -> if (facts.spoofedCount > 0) {
        pluralStringResource(R.plurals.home_apps_spoofed, facts.spoofedCount, facts.spoofedCount)
    } else {
        stringResource(summary)
    }
    SystemGroup.LAB ->
        if (prefs[Prefs.larasAdvanced]) stringResource(R.string.home_lab_advanced) else stringResource(summary)
    SystemGroup.CONNECTIVITY -> regionSummary(prefs[Prefs.wifiRegionState])
    SystemGroup.PENTAS ->
        facts.pentasLast?.let { stringResource(R.string.home_pentas_last, it) } ?: stringResource(summary)
    else -> stringResource(summary)
}

/** Superuser guards relaxed now, and how many apply on this device. */
fun relaxedGuards(prefs: PrefSnapshot): Pair<Int, Int> {
    val shown = SystemGroup.SUPERUSER.entries.filter { it.applies(prefs) }
    return shown.count { prefs.isChanged(it.pref) } to shown.size
}

/** What a card's motion follows: when it changes the card replays, as it does when tapped. */
fun SystemGroup.artKey(facts: HomeFacts, prefs: PrefSnapshot): Any? = when (this) {
    SystemGroup.STYLE -> facts.shapePath
    SystemGroup.GESTURES -> facts.navMode
    SystemGroup.SUPERUSER -> relaxedGuards(prefs)
    SystemGroup.INTEGRITY -> facts.integrityTier to facts.integrityHealth
    SystemGroup.SOUND -> facts.volumes
    SystemGroup.PRIVACY -> prefs[Prefs.scramblePin]
    SystemGroup.APPS -> facts.spoofedCount
    SystemGroup.LAB -> prefs[Prefs.larasAdvanced]
    SystemGroup.PENTAS -> facts.pentasGames to facts.pentasLast
    else -> null
}

/** Where a card's title starts: past the art on cards whose art sits at the start. */
val SystemGroup.labelStart: Dp get() = if (this == SystemGroup.STYLE) 124.dp else 18.dp

/**
 * A card's picture of its group as set now. [t] is the motion's progress, read only while
 * drawing; every picture rests in its true state at t = 1.
 */
@Composable
fun BoxScope.GroupArt(group: SystemGroup, facts: HomeFacts, prefs: PrefSnapshot, tone: BadgeTone, t: () -> Float) {
    when (group) {
        SystemGroup.STYLE -> StyleArt(facts, t)
        SystemGroup.GESTURES -> NavArt(facts.navMode, tone, t)
        SystemGroup.SUPERUSER -> GuardsArt(prefs, tone, t)
        SystemGroup.INTEGRITY -> IntegrityArt(facts, tone, t)
        SystemGroup.SOUND -> VolumeArt(facts.volumes, tone, t)
        SystemGroup.PRIVACY -> PinArt(prefs[Prefs.scramblePin], tone, t)
        SystemGroup.APPS -> AppsArt(facts.spoofedApps, tone, t)
        SystemGroup.LAB -> FlaskArt(prefs[Prefs.larasAdvanced], tone, t)
        SystemGroup.PENTAS -> PentasArt(facts.pentasIcons, tone, t)
        else -> GroupGlyph(
            group, tone.content.copy(alpha = 0.5f),
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(56.dp),
        )
    }
}

/** Progress of a part that starts at [start] and takes [length] of the motion. */
private fun stage(t: Float, start: Float, length: Float) = ((t - start) / length).coerceIn(0f, 1f)

/** The lock screen in miniature on the real wallpaper, the palette and the icon shape. */
@Composable
private fun BoxScope.StyleArt(facts: HomeFacts, t: () -> Float) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    Box(
        Modifier.align(Alignment.CenterStart).padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
            .width(92.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(c.primaryContainer, c.surfaceContainerLowest))),
    ) {
        facts.wallpaper?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        val now = remember { Calendar.getInstance() }
        val hour = now.get(if (DateFormat.is24HourFormat(context)) Calendar.HOUR_OF_DAY else Calendar.HOUR)
            .let { if (it == 0 && !DateFormat.is24HourFormat(context)) 12 else it }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 20.dp).graphicsLayer {
                val s = stage(t(), 0f, 0.5f)
                alpha = s
                translationY = (1f - s) * 14.dp.toPx()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            listOf(hour, now.get(Calendar.MINUTE)).forEach {
                Text(
                    "%02d".format(it), color = Color.White, fontSize = 34.sp, lineHeight = 34.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }
    }
    // The palette turns in, as the swatches settle on the theme's three accents.
    Box(
        Modifier.padding(start = 124.dp, top = 20.dp).size(width = 124.dp, height = 56.dp).graphicsLayer {
            val s = stage(t(), 0f, 0.6f)
            rotationZ = -40f * (1f - s)
            alpha = s
            transformOrigin = TransformOrigin(28f / 124f, 0.5f)
        },
    ) {
        listOf(c.tertiary, c.secondary, c.primary).forEachIndexed { i, color ->
            Box(Modifier.offset(x = (68 - i * 34).dp).size(56.dp).background(color, CircleShape))
        }
    }
    // The icons, in the shape the launcher draws them in.
    val path = remember(facts.shapePath) {
        facts.shapePath?.let { runCatching { PathParser.createPathFromPathData(it).asComposePath() }.getOrNull() }
    }
    Row(Modifier.padding(start = 124.dp, top = 88.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(c.primary, c.secondary, c.tertiary, c.primaryContainer).forEachIndexed { i, color ->
            Canvas(
                Modifier.size(30.dp).graphicsLayer {
                    val s = stage(t(), 0.2f + i * 0.08f, 0.4f)
                    scaleX = s
                    scaleY = s
                },
            ) {
                val outline = path
                if (outline == null) {
                    drawCircle(color)
                } else {
                    drawPath(
                        Path().apply {
                            addPath(outline)
                            transform(Matrix().apply { scale(size.width / 100f, size.height / 100f) })
                        },
                        color,
                    )
                }
            }
        }
    }
    Text(
        "Aa",
        style = MaterialTheme.typography.headlineMedium,
        color = c.tertiary,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 18.dp)
            .graphicsLayer { alpha = stage(t(), 0.4f, 0.4f) },
    )
}

/** The navigation bar as set: the gesture it answers to, or its buttons arriving. */
@Composable
private fun BoxScope.NavArt(mode: NavMode, tone: BadgeTone, t: () -> Float) {
    val c = MaterialTheme.colorScheme
    Canvas(
        Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(width = 150.dp, height = 100.dp)
            .clip(RoundedCornerShape(16.dp)).background(tone.container),
    ) {
        val p = t()
        val accent = tone.content
        val bottom = size.height - 10.dp.toPx()
        val mid = size.width / 2f
        when (mode) {
            NavMode.GESTURE -> {
                // A finger swipes home from the handle, leaving its track; then back from the edge.
                val up = stage(p, 0.1f, 0.4f)
                val top = 24.dp.toPx()
                val y = bottom - 8.dp.toPx() - (bottom - 8.dp.toPx() - top) * up
                val trace = 0.45f - 0.3f * stage(p, 0.5f, 0.3f)
                drawLine(accent.copy(alpha = trace), Offset(mid, bottom - 8.dp.toPx()), Offset(mid, y), 3.dp.toPx(), StrokeCap.Round)
                val dot = (if (p < 0.55f) stage(p, 0.08f, 0.08f) else 1f - stage(p, 0.55f, 0.1f))
                drawCircle(accent.copy(alpha = dot), 9.dp.toPx(), Offset(mid, y))
                val back = stage(p, 0.6f, 0.15f) * (1f - stage(p, 0.85f, 0.15f))
                val w = 22.dp.toPx() * stage(p, 0.6f, 0.15f)
                drawRoundRect(
                    accent.copy(alpha = 0.35f * back), Offset(0f, size.height / 2f - 16.dp.toPx()),
                    Size(w, 32.dp.toPx()), CornerRadius(16.dp.toPx()),
                )
                drawRoundRect(
                    c.onSurface, Offset(mid - 24.dp.toPx(), bottom - 2.dp.toPx()),
                    Size(48.dp.toPx(), 4.dp.toPx()), CornerRadius(2.dp.toPx()),
                )
            }
            else -> {
                val keys = if (mode == NavMode.THREE_BUTTON) 3 else 2
                val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val r = 7.dp.toPx()
                val cy = bottom - 8.dp.toPx()
                for (i in 0 until keys) {
                    val s = stage(p, 0.1f + i * 0.12f, 0.35f)
                    val cx = size.width * (i + 1) / (keys + 1)
                    val color = accent.copy(alpha = s)
                    val k = r * s
                    when {
                        i == 0 -> drawPath(
                            Path().apply {
                                moveTo(cx + k, cy - k); lineTo(cx - k, cy); lineTo(cx + k, cy + k); close()
                            },
                            color, style = stroke,
                        )
                        i == 1 && keys == 3 -> drawCircle(color, k, Offset(cx, cy), style = stroke)
                        i == 1 -> drawRoundRect(color, Offset(cx - 2 * k, cy - k / 2), Size(4 * k, k), CornerRadius(k), style = stroke)
                        else -> drawRect(color, Offset(cx - k, cy - k), Size(2 * k, 2 * k), style = stroke)
                    }
                }
            }
        }
    }
}

/** One switch per guard that applies here, flicked on in turn for those relaxed. */
@Composable
private fun BoxScope.GuardsArt(prefs: PrefSnapshot, tone: BadgeTone, t: () -> Float) {
    val c = MaterialTheme.colorScheme
    val guards = SystemGroup.SUPERUSER.entries.filter { it.applies(prefs) }.map { prefs.isChanged(it.pref) }
    val offTrack = c.surfaceContainerHighest
    val offKnob = c.outline
    // Two rows of five across the top, so the count and title below keep their room.
    Canvas(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 18.dp).size(width = 134.dp, height = 34.dp)) {
        val p = t()
        val w = 22.dp.toPx()
        val h = 12.dp.toPx()
        val columns = 5
        val gapX = (size.width - columns * w) / (columns - 1)
        var lit = 0
        guards.forEachIndexed { i, on ->
            val x = size.width - w - (columns - 1 - i % columns) * (w + gapX)
            val y = (i / columns) * (h + 10.dp.toPx())
            val k = if (on) stage(p, 0.1f + lit++ * 0.1f, 0.3f) else 0f
            drawRoundRect(lerp(offTrack, tone.container, k), Offset(x, y), Size(w, h), CornerRadius(h / 2))
            drawCircle(lerp(offKnob, tone.content, k), 4.dp.toPx(), Offset(x + 6.dp.toPx() + 10.dp.toPx() * k, y + h / 2))
        }
    }
}

/** The shield draws itself, then each level lands as the last check measured it. */
@Composable
private fun BoxScope.IntegrityArt(facts: HomeFacts, tone: BadgeTone, t: () -> Float) {
    val c = MaterialTheme.colorScheme
    val tier = facts.integrityTier
    val attention = facts.integrityHealth == Health.ATTENTION
    val line = if (attention) c.error else tone.content
    Canvas(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp).size(width = 56.dp, height = 64.dp)) {
        val p = stage(t(), 0f, 0.5f)
        val sx = size.width / 52f
        val sy = size.height / 64f
        val shield = Path().apply {
            moveTo(26f * sx, 3f * sy); lineTo(49f * sx, 12f * sy); lineTo(49f * sx, 30f * sy)
            cubicTo(49f * sx, 46f * sy, 39f * sx, 57f * sy, 26f * sx, 61f * sy)
            cubicTo(13f * sx, 57f * sy, 3f * sx, 46f * sy, 3f * sx, 30f * sy)
            lineTo(3f * sx, 12f * sy); close()
        }
        drawPath(shield, line.copy(alpha = 0.16f * p))
        val measure = PathMeasure().apply { setPath(shield, false) }
        val drawn = Path()
        measure.getSegment(0f, measure.length * p, drawn, true)
        drawPath(drawn, line, style = Stroke(3.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round))
    }
    Column(Modifier.padding(start = 16.dp, top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(Tier.BASIC to R.string.tier_basic, Tier.DEVICE to R.string.tier_device, Tier.STRONG to R.string.tier_strong)
            .forEachIndexed { i, (level, label) ->
                val color = when {
                    tier == null -> c.onSurfaceVariant
                    tier >= level -> tone.content
                    else -> c.error
                }
                Text(
                    (if (tier == null) "" else if (tier >= level) "✓ " else "✕ ") + stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    modifier = Modifier
                        .graphicsLayer {
                            val s = stage(t(), 0.35f + i * 0.1f, 0.3f)
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .background(color.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
    }
}

/** Media, ring, alarm and notifications, each bar rising to its volume. */
@Composable
private fun BoxScope.VolumeArt(volumes: List<Float>, tone: BadgeTone, t: () -> Float) {
    Canvas(Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 18.dp).size(width = 83.dp, height = 72.dp)) {
        val p = t()
        val w = 14.dp.toPx()
        val gap = 9.dp.toPx()
        volumes.forEachIndexed { i, level ->
            val x = i * (w + gap)
            drawRoundRect(tone.content.copy(alpha = 0.16f), Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
            val h = (w + (size.height - w) * level) * stage(p, i * 0.06f, 0.5f)
            drawRoundRect(tone.content, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}

/** The PIN pad; with scrambling on, its keys trade places, which is what the setting does. */
@Composable
private fun BoxScope.PinArt(scramble: Boolean, tone: BadgeTone, t: () -> Float) {
    // Where each key lands when scrambled: a fixed shuffle, so the picture is stable.
    val slots = intArrayOf(7, 2, 5, 0, 4, 8, 1, 6, 3)
    Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 16.dp).size(70.dp)) {
        for (key in 0 until 9) {
            Box(
                Modifier.size(20.dp).graphicsLayer {
                    val p = t()
                    val home = Offset((key % 3) * 25.dp.toPx(), (key / 3) * 25.dp.toPx())
                    val to = slots[key]
                    val away = Offset((to % 3) * 25.dp.toPx(), (to / 3) * 25.dp.toPx())
                    val m = if (scramble) stage(p, 0.3f, 0.4f) else 0f
                    translationX = home.x + (away.x - home.x) * m
                    translationY = home.y + (away.y - home.y) * m
                    alpha = stage(p, key * 0.02f, 0.25f)
                }.background(tone.container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${key + 1}", style = MaterialTheme.typography.labelSmall, color = tone.content)
            }
        }
    }
}

/** The apps given a device profile, dealt like cards; blanks when there are none. */
@Composable
private fun BoxScope.AppsArt(icons: List<ImageBitmap>, tone: BadgeTone, t: () -> Float) {
    val angles = floatArrayOf(-14f, 0f, 12f)
    Box(Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 22.dp).size(width = 72.dp, height = 56.dp)) {
        for (i in 0 until 3) {
            val icon = icons.getOrNull(i)
            val placeholder = Modifier.background(tone.container.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            Box(
                Modifier.offset(x = (i * 16).dp, y = if (i == 1) 0.dp else 6.dp).size(40.dp).graphicsLayer {
                    val s = stage(t(), i * 0.1f, 0.4f)
                    translationX = (1f - s) * 40.dp.toPx()
                    translationY = (1f - s) * 20.dp.toPx()
                    rotationZ = angles[i] + (1f - s) * 18f
                    alpha = s
                }.then(if (icon == null) placeholder else Modifier),
            ) {
                icon?.let { Image(it, null, Modifier.fillMaxSize()) }
            }
        }
    }
}

/**
 * A landscape screen holding one squircle per game (the last played lit), with the sidebar
 * pill sliding in along its edge.
 */
@Composable
private fun BoxScope.PentasArt(icons: List<ImageBitmap>, tone: BadgeTone, t: () -> Float) {
    Box(
        Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).size(width = 104.dp, height = 56.dp)
            .border(2.dp, tone.content.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
    ) {
        Box(
            Modifier.offset(x = (-2).dp, y = 14.dp).size(width = 6.dp, height = 26.dp).graphicsLayer {
                val s = stage(t(), 0.3f, 0.5f)
                translationY = (1f - s) * 14.dp.toPx()
                alpha = s
            }.background(tone.content, RoundedCornerShape(3.dp)),
        )
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 0 until 3) {
                val icon = icons.getOrNull(i)
                Box(
                    Modifier.size(24.dp).graphicsLayer {
                        val s = stage(t(), i * 0.1f, 0.4f)
                        scaleX = 0.6f + 0.4f * s
                        scaleY = 0.6f + 0.4f * s
                        alpha = s * if (i == 0 || icon == null) 1f else 0.7f
                    }.clip(RoundedCornerShape(8.dp))
                        .background(if (icon == null) tone.container.copy(alpha = 0.6f) else Color.Transparent),
                ) {
                    icon?.let { Image(it, null, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/** A flask: bubbling while Advanced is on, empty and still while it is off. */
@Composable
private fun BoxScope.FlaskArt(advanced: Boolean, tone: BadgeTone, t: () -> Float) {
    Canvas(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 20.dp).size(width = 52.dp, height = 70.dp)) {
        val p = t()
        val s = size.width / 52f
        val flask = Path().apply {
            moveTo(21f * s, 6f * s); lineTo(21f * s, 26f * s); lineTo(7f * s, 58f * s)
            cubicTo(5f * s, 63f * s, 8f * s, 66f * s, 13f * s, 66f * s); lineTo(39f * s, 66f * s)
            cubicTo(44f * s, 66f * s, 47f * s, 63f * s, 45f * s, 58f * s); lineTo(31f * s, 26f * s); lineTo(31f * s, 6f * s)
        }
        if (advanced) {
            val fill = stage(p, 0f, 0.5f)
            val top = 66f * s - 20f * s * fill
            drawRect(tone.content.copy(alpha = 0.3f), Offset(8f * s, top), Size(36f * s, 66f * s - top))
            // Two bubbles rise once through the neck and are gone when the motion rests.
            for (b in 0 until 2) {
                val q = stage(p, 0.2f + b * 0.25f, 0.5f)
                if (q in 0.01f..0.99f) {
                    drawCircle(tone.content.copy(alpha = 1f - q), (3f + b) * s, Offset((22f + b * 8f) * s, 56f * s - 46f * s * q))
                }
            }
        }
        drawPath(flask, tone.content, style = Stroke(3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(tone.content, Offset(17f * s, 6f * s), Offset(35f * s, 6f * s), 3f * s, StrokeCap.Round)
    }
}
