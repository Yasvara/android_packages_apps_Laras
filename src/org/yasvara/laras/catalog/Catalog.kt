/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.catalog

import android.os.SystemProperties
import android.provider.Settings
import androidx.annotation.StringRes
import lineageos.providers.LineageSettings
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption
import org.yasvara.laras.data.ChoicePref
import org.yasvara.laras.data.ClockFace
import org.yasvara.laras.data.JsonFieldPref
import org.yasvara.laras.data.JsonPref
import org.yasvara.laras.data.IntSliderPref
import org.yasvara.laras.data.Namespace
import org.yasvara.laras.data.Pref
import org.yasvara.laras.data.PrefSnapshot
import org.yasvara.laras.data.StringPref
import org.yasvara.laras.data.SwitchPref

/*
 * The single description of everything Laras can change. Screens, search and the "Changed"
 * page are all derived from it, so a new setting is added here once and nowhere else.
 */

enum class ClockPosition(override val value: Int, override val label: Int) : ChoiceOption {
    LEFT(2, R.string.option_left),
    CENTER(1, R.string.option_center),
    RIGHT(0, R.string.option_right),
}

enum class AmPmStyle(override val value: Int, override val label: Int) : ChoiceOption {
    NORMAL(0, R.string.option_normal),
    SMALL(1, R.string.option_small),
    HIDDEN(2, R.string.option_hidden),
}

/** Lineage's battery styles (0-3) and Yasvara's (100 up), in picker order. */
enum class BatteryStyle(override val value: Int, override val label: Int) : ChoiceOption {
    ICON(0, R.string.option_icon),
    CAPSULE(100, R.string.option_capsule),
    SEGMENTS(101, R.string.option_segments),
    RING(102, R.string.option_ring),
    CIRCLE(1, R.string.option_circle),
    DOTTED(3, R.string.option_dotted),
    GLYPH(103, R.string.option_glyph),
    TEXT(2, R.string.option_text);

    /** Whether the style is itself the percentage, so the percentage setting doesn't apply. */
    val isPercentage: Boolean get() = this == CAPSULE || this == GLYPH || this == TEXT
}

/** Status bar icon sets, by their `yasvara_theme.statusIcons` id. */
enum class StatusIconSet(val id: String, override val label: Int) : ChoiceOption {
    DEFAULT("aosp", R.string.option_default),
    WEDGE("yas-wedge", R.string.icons_wedge),
    BLOCKS("yas-blocks", R.string.icons_blocks),
    DOTS("yas-dots", R.string.icons_dots),
    OUTLINE("yas-outline", R.string.icons_outline),
    THIN("yas-mono-thin", R.string.icons_thin);

    /** Not stored: the set is saved by [id]. */
    override val value: Int get() = ordinal
}

enum class BatteryPercent(override val value: Int, override val label: Int) : ChoiceOption {
    HIDDEN(0, R.string.option_hidden),
    INSIDE(1, R.string.option_inside),
    BESIDE(2, R.string.option_beside),
}

enum class PulldownSide(override val value: Int, override val label: Int) : ChoiceOption {
    OFF(0, R.string.option_off),
    LEFT(2, R.string.option_left),
    RIGHT(1, R.string.option_right),
}

enum class ClockPlacement(override val value: Int, override val label: Int) : ChoiceOption {
    TOP(0, R.string.option_top),
    BOTTOM(1, R.string.option_bottom),
}

enum class DepthClockLayer(override val value: Int, override val label: Int) : ChoiceOption {
    AUTO(0, R.string.depth_clock_auto),
    FRONT(1, R.string.depth_clock_front),
    BEHIND(2, R.string.depth_clock_behind),
}

/** Layang default window size, stored as 0/1/2. */
enum class LayangSize(override val value: Int, override val label: Int) : ChoiceOption {
    SMALL(0, R.string.layang_size_s),
    MEDIUM(1, R.string.layang_size_m),
    LARGE(2, R.string.layang_size_l),
}

/** Frame rate of a game in a mini window (D13); 0 is uncapped. */
enum class LayangMiniFps(override val value: Int, override val label: Int) : ChoiceOption {
    CAPPED(30, R.string.layang_mini_fps_30),
    UNCAPPED(0, R.string.layang_mini_fps_uncapped),
}

enum class SliderPosition(override val value: Int, override val label: Int) : ChoiceOption {
    TOP(0, R.string.option_top),
    BOTTOM(1, R.string.option_bottom),
}

/** How strongly Wi-Fi network selection favours 5/6 GHz, read by the Wi-Fi service. */
enum class WifiBandPref(override val value: Int, override val label: Int) : ChoiceOption {
    STOCK(0, R.string.wifi_band_stock),
    PREFER(1, R.string.wifi_band_prefer),
    STRONG(2, R.string.wifi_band_strong),
}

object Prefs {
    val statusBarClock = ChoicePref(
        Namespace.LINEAGE_SYSTEM, LineageSettings.System.STATUS_BAR_CLOCK,
        ClockPosition.entries, ClockPosition.LEFT,
    )
    val statusBarAmPm = ChoicePref(
        Namespace.LINEAGE_SYSTEM, LineageSettings.System.STATUS_BAR_AM_PM,
        AmPmStyle.entries, AmPmStyle.HIDDEN,
    )
    /** The whole `yasvara_theme` object, for editors that read several fields (app icons). */
    val theme = StringPref(Namespace.SECURE, "yasvara_theme")
    val statusIcons = JsonFieldPref(
        Namespace.SECURE, "yasvara_theme", "statusIcons",
        StatusIconSet.entries, StatusIconSet.DEFAULT, StatusIconSet::id,
    )
    val batteryStyle = ChoicePref(
        Namespace.LINEAGE_SYSTEM, LineageSettings.System.STATUS_BAR_BATTERY_STYLE,
        BatteryStyle.entries, BatteryStyle.ICON,
    )
    val batteryPercent = ChoicePref(
        Namespace.LINEAGE_SYSTEM, LineageSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT,
        BatteryPercent.entries, BatteryPercent.HIDDEN,
    )
    /** The lock screen clock face; edited on the canvas, never as a plain row. */
    val clockFace = JsonPref(
        Namespace.SECURE, "lock_screen_custom_clock_face", ClockFace.DEFAULT,
        read = ClockFace::fromJson,
        write = { if (it == ClockFace.DEFAULT) null else it.toJson() },
    )
    val clockPlacement = ChoicePref(
        Namespace.SECURE, "yasvara_lock_clock_placement", ClockPlacement.entries, ClockPlacement.TOP,
    )
    val parallaxLock = SwitchPref(Namespace.SECURE, "yasvara_parallax_lock", false)
    val parallaxHome = SwitchPref(Namespace.SECURE, "yasvara_parallax_home", false)
    val parallaxStrength = IntSliderPref(Namespace.SECURE, "yasvara_parallax_strength", 18, 4..40)
    val depthEnabled = SwitchPref(Namespace.SECURE, "yasvara_depth_enabled", false)
    val depthClock = ChoicePref(
        Namespace.SECURE, "yasvara_depth_clock", DepthClockLayer.entries, DepthClockLayer.AUTO,
    )
    val depthOpacity = IntSliderPref(Namespace.SECURE, "yasvara_depth_opacity", 100, 0..100)
    val depthParallax = IntSliderPref(Namespace.SECURE, "yasvara_depth_parallax", 60, 0..200)
    val largeClock = SwitchPref(
        Namespace.SECURE, Settings.Secure.LOCKSCREEN_USE_DOUBLE_LINE_CLOCK, true,
    )
    val lockNotifications = SwitchPref(
        Namespace.SECURE, Settings.Secure.LOCK_SCREEN_SHOW_NOTIFICATIONS, true,
    )
    val lockPrivateNotifications = SwitchPref(
        Namespace.SECURE, Settings.Secure.LOCK_SCREEN_ALLOW_PRIVATE_NOTIFICATIONS, true,
    )
    val pureBlack = SwitchPref(
        Namespace.LINEAGE_SECURE, LineageSettings.Secure.BERRY_BLACK_THEME, false,
    )
    val qsBrightnessSlider = SwitchPref(
        Namespace.LINEAGE_SECURE, LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER, true,
    )
    val qsBrightnessPosition = ChoicePref(
        Namespace.LINEAGE_SECURE, LineageSettings.Secure.QS_BRIGHTNESS_SLIDER_POSITION,
        SliderPosition.entries, SliderPosition.TOP,
    )
    val qsAutoBrightness = SwitchPref(
        Namespace.LINEAGE_SECURE, LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS, true,
    )
    val volumePanelLeft = SwitchPref(
        Namespace.LINEAGE_SECURE, LineageSettings.Secure.VOLUME_PANEL_ON_LEFT, false,
    )
    val doubleTapSleep = SwitchPref(
        Namespace.LINEAGE_SYSTEM, LineageSettings.System.DOUBLE_TAP_SLEEP_GESTURE, true,
    )
    val quickPulldown = ChoicePref(
        Namespace.LINEAGE_SYSTEM, LineageSettings.System.STATUS_BAR_QUICK_QS_PULLDOWN,
        PulldownSide.entries, PulldownSide.OFF,
    )
    val scramblePin = SwitchPref(
        Namespace.LINEAGE_SYSTEM, LineageSettings.System.LOCKSCREEN_PIN_SCRAMBLE_LAYOUT, false,
    )
    val alwaysOn = SwitchPref(Namespace.SECURE, Settings.Secure.DOZE_ALWAYS_ON, false)

    // Layang floating windows. Keys are android.app.yasvara.Layang.KEY_*.
    val layangEnabled = SwitchPref(Namespace.SECURE, "yasvara_layang_enabled", true)
    val layangChip = SwitchPref(Namespace.SECURE, "yasvara_layang_chip", true)
    val layangRemember = SwitchPref(Namespace.SECURE, "yasvara_layang_remember", true)
    val layangAudio = SwitchPref(Namespace.SECURE, "yasvara_layang_multi_audio_focus", true)
    val layangSize = ChoicePref(
        Namespace.SECURE, "yasvara_layang_default_size", LayangSize.entries, LayangSize.MEDIUM,
    )
    val layangGrip = IntSliderPref(Namespace.SECURE, "yasvara_layang_grip_dp", 24, 16..40)
    val layangAspectLock = SwitchPref(Namespace.SECURE, "yasvara_layang_game_aspect_lock", true)
    val layangMiniFps = ChoicePref(
        Namespace.SECURE, "yasvara_layang_mini_game_fps", LayangMiniFps.entries,
        LayangMiniFps.CAPPED,
    )
    val layangAutoPark = SwitchPref(Namespace.SECURE, "yasvara_layang_auto_park", true)
    val layangGhostAlpha = IntSliderPref(Namespace.SECURE, "yasvara_layang_ghost_alpha", 60, 20..80)
    // Connectivity. Keys are com.android.server.wifi.YasvaraWifiTuning.KEY_*.
    /** "auto" or empty for automatic, else the manual ISO country; edited on its own screen. */
    val wifiRegion = StringPref(Namespace.GLOBAL, "yasvara_wifi_region")
    /** "CC:source" published by the Wi-Fi service; read only. */
    val wifiRegionState = StringPref(Namespace.GLOBAL, "yasvara_wifi_region_state")
    val wifiLocationFallback = SwitchPref(Namespace.GLOBAL, "yasvara_wifi_location_fallback", false)
    /** Read by the Stk app; off keeps card-initiated popups, tones and calls away. */
    val simToolkit = SwitchPref(Namespace.GLOBAL, "yasvara_sim_toolkit_enabled", false)
    val wifiBandPref = ChoicePref(
        Namespace.GLOBAL, "yasvara_wifi_band_pref", WifiBandPref.entries, WifiBandPref.STOCK,
    )

    /**
     * Unlocks customizations that can make the phone hard to use when they go wrong (imported
     * fonts, emoji, boot and charger art, unsigned theme packs). Turned on from Lab, behind a
     * warning, never as a plain switch.
     */
    val larasAdvanced = SwitchPref(Namespace.SECURE, "yasvara_laras_advanced", false)

    // Set by SystemUI when a customization family keeps failing; Laras
    // clears them when the user asks to try again.
    val lockSafeMode = SwitchPref(Namespace.GLOBAL, "yasvara_lock_safe_mode", false)
    val colorSafeMode = SwitchPref(Namespace.GLOBAL, "yasvara_color_safe", false)
    val themeSafeMode = SwitchPref(Namespace.GLOBAL, "yasvara_theme_safe", false)

    /** The colour spec SystemUI applies; absent means stock colours. */
    val colorSpec = StringPref(Namespace.SECURE, "yasvara_color")

    // Superuser: each relaxes one guard the device owner can already defeat over adb. Off by
    // default. The keys are read by the framework (android.security.yasvara.YasvaraSuperUser),
    // except secureWindows, which the platform reads directly.
    val suSecureWindows = SwitchPref(Namespace.SECURE, "disable_secure_windows", false)
    val suHideCaptureStatus = SwitchPref(Namespace.SECURE, "yasvara_su_hide_capture_status", false)
    val suSpoofEncrypted = SwitchPref(Namespace.SECURE, "yasvara_su_spoof_encrypted", false)
    val suAllowDowngrade = SwitchPref(Namespace.SECURE, "yasvara_su_allow_downgrade", false)
    val suDisableSigVerify = SwitchPref(Namespace.SECURE, "yasvara_su_disable_sigverify", false)
    val suBypassMinSdk = SwitchPref(Namespace.SECURE, "yasvara_su_bypass_min_sdk", false)
    val suDisablePkgVerify = SwitchPref(Namespace.SECURE, "yasvara_su_disable_pkgverify", false)
    val suAllowReplaceSystem = SwitchPref(Namespace.SECURE, "yasvara_su_allow_replace_system", false)
    val suNoStorageRestrict = SwitchPref(Namespace.SECURE, "yasvara_su_no_storage_restrict", false)
    val suAllowTestOnly = SwitchPref(Namespace.SECURE, "yasvara_su_allow_test_only", false)
}

/** Whether the device storage is encrypted (`ro.crypto.state`), fixed for a boot. */
private val DEVICE_ENCRYPTED = SystemProperties.get("ro.crypto.state") == "encrypted"

class Entry(
    val pref: Pref<*>,
    @StringRes val title: Int,
    @StringRes val summary: Int? = null,
    /** Whether the setting means anything with the others as they are; hidden when not. */
    val applies: (PrefSnapshot) -> Boolean = { true },
    /** Options SystemUI can draw for the picker, shown instead of their names when it does. */
    val sample: SampleKind? = null,
)

/**
 * Pickers whose options SystemUI draws (`YAS_SAMPLE`): the draft value of each
 * option, by the kind's id, as the status bar preview takes it.
 */
enum class SampleKind(val id: String, val key: (ChoiceOption) -> String) {
    BATTERY("battery", { it.value.toString() }),
    ICONS("icons", { (it as StatusIconSet).id });

    val values: List<String>
        get() = when (this) {
            BATTERY -> BatteryStyle.entries.map(key)
            ICONS -> StatusIconSet.entries.map(key)
        }
}

/** A titled list of entries: a canvas element or a system group. */
sealed interface Section {
    @get:StringRes val title: Int
    val entries: List<Entry>
}

/**
 * A state of the screen that Laras previews live. Each is edited as one group: its elements are
 * the parts of that state, so "Clock" or "Notifications" always means the one you are looking at.
 */
enum class Surface(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val canvas: CanvasShape = CanvasShape.SCREEN,
) {
    LOCK_SCREEN(R.string.surface_lock_screen, R.string.surface_lock_screen_summary),
    HOME_SCREEN(R.string.surface_home_screen, R.string.surface_home_screen_summary),
    SHADE(R.string.surface_shade, R.string.surface_shade_summary, CanvasShape.TOP_BAR),
    ALWAYS_ON(R.string.surface_always_on, R.string.surface_always_on_summary);

    val elements: List<Element> get() = Element.entries.filter { it.surface == this }
}

/**
 * How a surface is previewed. A whole screen is shown as a phone; a bar is shown at nearly its
 * real size across the width, since a phone-sized canvas shrinks it too far to tap its parts.
 */
enum class CanvasShape {
    SCREEN,

    /** The status bar at the top of the screen, with what hangs from it (Quick Settings). */
    TOP_BAR;

    val isBar: Boolean get() = this != SCREEN
}

/** A part of a [Surface]; on a live canvas it can be tapped to jump to its settings. */
enum class Element(
    val surface: Surface,
    override val title: Int,
    override val entries: List<Entry>,
) : Section {
    LOCK_CLOCK(
        Surface.LOCK_SCREEN, R.string.element_clock,
        listOf(
            Entry(Prefs.clockFace, R.string.pref_clock_face),
            Entry(Prefs.clockPlacement, R.string.pref_clock_placement, R.string.pref_clock_placement_summary),
            Entry(Prefs.largeClock, R.string.pref_large_clock, R.string.pref_large_clock_summary),
        ),
    ),
    LOCK_WALLPAPER(
        Surface.LOCK_SCREEN, R.string.element_wallpaper,
        listOf(
            Entry(Prefs.depthEnabled, R.string.pref_depth, R.string.pref_depth_summary),
            Entry(Prefs.depthClock, R.string.pref_depth_clock),
            Entry(Prefs.depthOpacity, R.string.pref_depth_opacity),
            Entry(Prefs.parallaxLock, R.string.pref_parallax_lock, R.string.pref_parallax_summary),
            Entry(Prefs.parallaxHome, R.string.pref_parallax_home),
            Entry(Prefs.parallaxStrength, R.string.pref_parallax_strength),
            Entry(Prefs.depthParallax, R.string.pref_depth_parallax, R.string.pref_depth_parallax_summary),
        ),
    ),
    LOCK_NOTIFICATIONS(
        Surface.LOCK_SCREEN, R.string.element_notifications,
        listOf(
            Entry(Prefs.lockNotifications, R.string.pref_lock_notifications),
            Entry(
                Prefs.lockPrivateNotifications,
                R.string.pref_private_notifications,
                R.string.pref_private_notifications_summary,
            ),
        ),
    ),
    STATUS_BAR_CLOCK(
        Surface.SHADE, R.string.element_status_bar_clock,
        listOf(
            Entry(Prefs.statusBarClock, R.string.pref_clock_position),
            Entry(Prefs.statusBarAmPm, R.string.pref_am_pm),
        ),
    ),

    STATUS_BAR_ICONS(
        Surface.SHADE, R.string.element_status_bar_icons,
        listOf(Entry(
            Prefs.statusIcons, R.string.pref_status_icons, R.string.pref_status_icons_summary,
            sample = SampleKind.ICONS,
        )),
    ),
    STATUS_BAR_BATTERY(
        Surface.SHADE, R.string.element_status_bar_battery,
        listOf(
            Entry(Prefs.batteryStyle, R.string.pref_battery_style, sample = SampleKind.BATTERY),
            Entry(
                Prefs.batteryPercent, R.string.pref_battery_percent,
                applies = { !it[Prefs.batteryStyle].isPercentage },
            ),
        ),
    ),
    QUICK_SETTINGS(
        Surface.SHADE, R.string.element_quick_settings,
        listOf(
            Entry(Prefs.qsBrightnessSlider, R.string.pref_qs_brightness),
            Entry(Prefs.qsBrightnessPosition, R.string.pref_qs_brightness_position),
            Entry(Prefs.qsAutoBrightness, R.string.pref_qs_auto_brightness),
            Entry(Prefs.quickPulldown, R.string.pref_quick_pulldown, R.string.pref_quick_pulldown_summary),
        ),
    ),
    /** The app icons: shape, pack and themed icons, edited by their own sheet (IconsEditor). */
    HOME_ICONS(Surface.HOME_SCREEN, R.string.element_home_icons, emptyList()),
    AOD_DISPLAY(
        Surface.ALWAYS_ON, R.string.element_always_on,
        listOf(Entry(Prefs.alwaysOn, R.string.pref_always_on, R.string.pref_always_on_summary)),
    ),
}

/**
 * A system-wide settings group. A group is built out of [entries], or of a [section] screen of
 * its own; one with neither is on the roadmap and not built yet.
 */
enum class SystemGroup(
    override val title: Int,
    @StringRes val summary: Int,
    override val entries: List<Entry> = emptyList(),
    val section: Boolean = false,
) : Section {
    /** Colours (and later themes) have screens of their own; see GroupScreen. */
    STYLE(R.string.group_style, R.string.group_style_summary, section = true),
    DISPLAY(R.string.group_display, R.string.group_display_summary),
    SOUND(
        R.string.group_sound, R.string.group_sound_summary,
        listOf(Entry(Prefs.volumePanelLeft, R.string.pref_volume_left)),
    ),
    GESTURES(
        R.string.group_gestures, R.string.group_gestures_summary,
        listOf(Entry(Prefs.doubleTapSleep, R.string.pref_double_tap_sleep)),
    ),
    /** Sleeping apps and telemetry cuts; see GroupScreen. */
    POWER(R.string.group_power, R.string.group_power_summary, section = true),
    /** Wi-Fi region, band preference and the network experiments; see GroupScreen. */
    CONNECTIVITY(
        R.string.group_connectivity, R.string.group_connectivity_summary,
        listOf(
            Entry(Prefs.wifiBandPref, R.string.pref_wifi_band, R.string.pref_wifi_band_summary),
            Entry(
                Prefs.wifiLocationFallback, R.string.pref_wifi_location,
                R.string.pref_wifi_location_summary,
            ),
            Entry(Prefs.simToolkit, R.string.pref_sim_toolkit, R.string.pref_sim_toolkit_summary),
        ),
    ),
    /** Layang floating windows; Desk joins later. */
    MULTITASKING(
        R.string.group_multitasking, R.string.group_multitasking_summary,
        listOf(
            Entry(Prefs.layangEnabled, R.string.pref_layang_enabled, R.string.pref_layang_enabled_summary),
            Entry(Prefs.layangChip, R.string.pref_layang_chip, R.string.pref_layang_chip_summary,
                applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangRemember, R.string.pref_layang_remember,
                applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangAudio, R.string.pref_layang_audio, R.string.pref_layang_audio_summary,
                applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangSize, R.string.pref_layang_size,
                applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangGrip, R.string.pref_layang_grip,
                applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangAspectLock, R.string.pref_layang_aspect,
                R.string.pref_layang_aspect_summary, applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangMiniFps, R.string.pref_layang_mini_fps,
                applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangAutoPark, R.string.pref_layang_auto_park,
                R.string.pref_layang_auto_park_summary, applies = { it[Prefs.layangEnabled] }),
            Entry(Prefs.layangGhostAlpha, R.string.pref_layang_ghost,
                R.string.pref_layang_ghost_summary, applies = { it[Prefs.layangEnabled] }),
        ),
    ),
    PRIVACY(
        R.string.group_privacy, R.string.group_privacy_summary,
        listOf(Entry(Prefs.scramblePin, R.string.pref_scramble_pin, R.string.pref_scramble_pin_summary)),
    ),
    SUPERUSER(
        R.string.group_superuser, R.string.group_superuser_summary,
        listOf(
            Entry(Prefs.suSecureWindows, R.string.pref_su_secure_windows, R.string.pref_su_secure_windows_summary),
            Entry(Prefs.suHideCaptureStatus, R.string.pref_su_hide_capture, R.string.pref_su_hide_capture_summary),
            Entry(Prefs.suDisablePkgVerify, R.string.pref_su_pkg_verify, R.string.pref_su_pkg_verify_summary),
            Entry(Prefs.suDisableSigVerify, R.string.pref_su_sig_verify, R.string.pref_su_sig_verify_summary),
            Entry(Prefs.suAllowDowngrade, R.string.pref_su_downgrade, R.string.pref_su_downgrade_summary),
            Entry(Prefs.suBypassMinSdk, R.string.pref_su_min_sdk, R.string.pref_su_min_sdk_summary),
            Entry(Prefs.suAllowTestOnly, R.string.pref_su_test_only, R.string.pref_su_test_only_summary),
            Entry(
                Prefs.suAllowReplaceSystem, R.string.pref_su_replace_system,
                R.string.pref_su_replace_system_summary,
                applies = { it[Prefs.suDisableSigVerify] },
            ),
            Entry(Prefs.suNoStorageRestrict, R.string.pref_su_storage, R.string.pref_su_storage_summary),
            Entry(
                Prefs.suSpoofEncrypted, R.string.pref_su_encrypted, R.string.pref_su_encrypted_summary,
                // An encrypted device already reports itself so: the switch would do nothing.
                applies = { !DEVICE_ENCRYPTED },
            ),
        ),
    ),
    INTEGRITY(R.string.group_integrity, R.string.group_integrity_summary, section = true),
    APPS(R.string.group_apps, R.string.group_apps_summary, section = true),
    /** Hidden Gboard features; see GroupScreen. */
    KEYBOARD(R.string.group_keyboard, R.string.group_keyboard_summary, section = true),
    /** The ROM's rootless Xposed runtime: modules and the apps each is loaded into. */
    MODULES(R.string.group_modules, R.string.group_modules_summary, section = true),
    LAB(R.string.group_lab, R.string.group_lab_summary, section = true),
    /** Pentas, the game and video sidebars: its own app, opened from here. */
    PENTAS(R.string.group_pentas, R.string.group_pentas_summary, section = true),
    ;

    val planned: Boolean get() = entries.isEmpty() && !section
}

object Catalog {
    val sections: List<Section> = Element.entries + SystemGroup.entries
    val prefs: List<Pref<*>> = sections.flatMap { s -> s.entries.map { it.pref } }

    /**
     * Settings with screens of their own, outside any section's entry list: observed like the
     * others, but not counted as changes or reset with them.
     */
    private val standalone: List<Pref<*>> =
        listOf(
            Prefs.larasAdvanced, Prefs.lockSafeMode, Prefs.colorSafeMode, Prefs.themeSafeMode,
            Prefs.colorSpec, Prefs.pureBlack, Prefs.theme, Prefs.wifiRegion, Prefs.wifiRegionState,
        )

    /** Every setting Laras reads. */
    val observed: List<Pref<*>> = prefs + standalone

    init {
        check(observed.size == observed.toSet().size) { "A preference is listed in two sections" }
    }
}
