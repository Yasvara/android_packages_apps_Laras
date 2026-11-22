/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.ui.color

import android.app.Application
import android.app.UiModeManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Trace
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.data.Namespace
import org.yasvara.laras.data.SettingsRepository
import org.yasvara.laras.data.theme.CommitResult
import org.yasvara.laras.data.theme.SettingsThemeStore
import org.yasvara.laras.data.theme.ThemeApplier
import org.yasvara.laras.data.theme.ThemeChange
import org.yasvara.laras.data.theme.ThemeCommitter
import org.yasvara.laras.data.theme.ThemeKey
import org.yasvara.laras.data.theme.ThemeTransitionClient
import org.yasvara.monet.YasAospMirror
import org.yasvara.monet.YasColorSpec
import org.yasvara.monet.YasPresets
import org.yasvara.monet.YasResolvedScheme
import org.yasvara.monet.YasSeedResolver
import org.yasvara.monet.YasStyle
import org.yasvara.monet.YasvaraMonet

/** The six styles of the simple surface, in order (Q4); the rest are Studio's. */
val SimpleStyles =
    listOf(
        YasStyle.TONAL_SPOT,
        YasStyle.NEUTRAL,
        YasStyle.VIBRANT,
        YasStyle.EXPRESSIVE,
        YasStyle.CONTENT,
        YasStyle.MONOCHROMATIC,
    )

/**
 * The colour page's state: the draft spec the user is editing, and the applied one. Drafts are
 * resolved off the main thread with the same engine SystemUI applies with; only Apply writes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ColorViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = SettingsRepository(app.contentResolver)
    private val transition = ThemeTransitionClient(app)
    private val themes = ThemeApplier(ThemeCommitter(SettingsThemeStore(settings)), transition)
    private val wallpapers = WallpaperManager.getInstance(app)
    private val uiModes = app.getSystemService(UiModeManager::class.java)

    /** What the user is editing. */
    data class Draft(val spec: YasColorSpec, val pureBlack: Boolean)

    /** A draft resolved: its colours, and a swatch set for every simple style. */
    data class Resolved(
        val draft: Draft,
        val seed: Int,
        val scheme: YasResolvedScheme,
        val styles: Map<YasStyle, YasResolvedScheme>,
    )

    private val applied = MutableStateFlow(readApplied())
    private val _draft = MutableStateFlow(applied.value)
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    private val _candidates = MutableStateFlow(emptyList<Int>())
    /** The wallpaper's seed candidates, best first. */
    val candidates: StateFlow<List<Int>> = _candidates.asStateFlow()
    private var wallpaperColors: WallpaperColors? = null

    val presets: List<YasPresets.Preset> = YasPresets.all()

    /**
     * Each preset as it would really look, resolved by the same engine SystemUI applies, so its
     * swatch is never just the seed: a style such as Neutral moves far from it.
     */
    val presetSchemes: StateFlow<Map<String, YasResolvedScheme>> =
        _draft.map { it.pureBlack }
            .distinctUntilChanged()
            .mapLatest { pureBlack ->
                val contrast = uiModes.contrast.toDouble()
                presets.associate { preset ->
                    val seed = YasSeedResolver.seed(preset.spec, null)
                    preset.id to YasvaraMonet.resolve(preset.spec, seed, contrast, pureBlack)
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** The draft, resolved; the newest draft wins while an older one is still resolving. */
    val resolved: StateFlow<Resolved?> =
        combine(_draft, _candidates) { draft, _ -> draft }
            .mapLatest(::resolve)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether the draft differs from what is applied, so Apply has something to do. */
    val changed: StateFlow<Boolean> =
        combine(_draft, applied) { draft, now -> draft != now }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch { loadWallpaper() }
    }

    fun setSeedIndex(index: Int) = edit {
        it.toBuilder()
            .setSource(YasColorSpec.Source.WALLPAPER, it.lockWallpaper, index)
            .setPrimary(_candidates.value.getOrNull(index))
            .setPreset(null)
            .build()
    }

    fun setPreset(preset: YasPresets.Preset) = edit { current ->
        // A preset is a whole spec; the user's contrast choice stays theirs.
        preset.spec.toBuilder().setContrast(current.contrast).build()
    }

    fun setStyle(style: YasStyle) = edit { it.toBuilder().setStyle(style).build() }

    /** Vividness scales the primary palette, and the other accents with "whole palette". */
    fun setVividness(value: Double) = edit {
        val builder = it.toBuilder().setChroma(YasColorSpec.A1, value)
        if (it.wholePalette) {
            builder.setChroma(YasColorSpec.A2, value).setChroma(YasColorSpec.A3, value)
        }
        builder.build()
    }

    fun setContrast(level: Double?) = edit { it.toBuilder().setContrast(level).build() }

    fun setPureBlack(on: Boolean) = _draft.update { it.copy(pureBlack = on) }

    /** Drops the draft and shows what is applied. */
    fun discard() {
        _draft.value = applied.value
    }

    /**
     * Applies the draft behind SystemUI's reveal from ([x], [y]). [awaitRedraw] returns once this
     * screen has drawn in the new colours.
     */
    fun apply(x: Float, y: Float, awaitRedraw: suspend () -> Unit, done: (CommitResult) -> Unit) {
        val resolved = resolved.value ?: return
        viewModelScope.launch {
            val spec =
                resolved.draft.spec.toBuilder()
                    .setPrimary(resolved.seed)
                    .setFingerprint(resolved.scheme.fingerprint)
                    .build()
            val aosp = withContext(Dispatchers.IO) {
                YasAospMirror.mirror(
                    settings.readRaw(Namespace.SECURE, ThemeKey.OVERLAYS.key),
                    spec,
                    resolved.seed,
                    System.currentTimeMillis(),
                )
            }
            val change = ThemeChange(
                mapOf(
                    ThemeKey.COLOR to spec.toJson(true),
                    ThemeKey.PURE_BLACK to if (resolved.draft.pureBlack) "1" else "0",
                    ThemeKey.OVERLAYS to aosp,
                )
            )
            val result = themes.apply(change, x, y, expectThemeChange = true, awaitRedraw)
            if (result is CommitResult.Applied) {
                applied.value = Draft(spec, resolved.draft.pureBlack)
                _draft.value = applied.value
                checkParity(resolved.scheme.fingerprint)
            }
            done(result)
        }
    }

    /** Goes back to the stock wallpaper colours: the spec is removed, the AOSP key reset. */
    fun resetToWallpaper(x: Float, y: Float, awaitRedraw: suspend () -> Unit) {
        viewModelScope.launch {
            val aosp = withContext(Dispatchers.IO) {
                YasAospMirror.reset(
                    settings.readRaw(Namespace.SECURE, ThemeKey.OVERLAYS.key),
                    System.currentTimeMillis(),
                )
            }
            val change = ThemeChange(mapOf(ThemeKey.COLOR to null, ThemeKey.OVERLAYS to aosp))
            if (themes.apply(change, x, y, expectThemeChange = true, awaitRedraw) is CommitResult.Applied) {
                applied.value = withContext(Dispatchers.IO) { readApplied() }
                _draft.value = applied.value
            }
        }
    }

    private fun edit(change: (YasColorSpec) -> YasColorSpec) =
        _draft.update { it.copy(spec = change(it.spec)) }

    private fun readApplied(): Draft {
        val raw = settings.readRaw(Namespace.SECURE, ThemeKey.COLOR.key)
        // With no spec yet, start from what the stock setup shows; nothing is written until Apply.
        val spec = YasColorSpec.parse(raw)
            ?: YasAospMirror.migrate(settings.readRaw(Namespace.SECURE, ThemeKey.OVERLAYS.key))
        val black = settings.readRaw(Namespace.SECURE, ThemeKey.PURE_BLACK.key) == "1"
        return Draft(spec, black)
    }

    private suspend fun loadWallpaper() {
        val colors = withContext(Dispatchers.IO) {
            wallpapers.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }
        wallpaperColors = colors
        _candidates.value = YasSeedResolver.candidates(colors)
    }

    private fun resolve(draft: Draft): Resolved {
        Trace.beginSection("YasResolve")
        try {
            val seed = YasSeedResolver.seed(draft.spec, wallpaperColors)
            val contrast = uiModes.contrast.toDouble()
            val scheme = YasvaraMonet.resolve(draft.spec, seed, contrast, draft.pureBlack)
            val styles = SimpleStyles.associateWith { style ->
                if (style == draft.spec.style) scheme
                else YasvaraMonet.resolve(
                    draft.spec.toBuilder().setStyle(style).build(), seed, contrast, draft.pureBlack,
                )
            }
            return Resolved(draft, seed, scheme, styles)
        } finally {
            Trace.endSection()
        }
    }

    /** SystemUI must have applied the very colours Laras resolved. */
    private suspend fun checkParity(expected: String) {
        val actual = withContext(Dispatchers.IO) { transition.appliedFingerprint() }
        if (actual == expected) Log.i(TAG, "Fingerprint parity ok: $expected")
        else Log.w(TAG, "Fingerprint mismatch: Laras $expected, SystemUI $actual")
    }

    private companion object {
        const val TAG = "LarasColor"
    }
}
