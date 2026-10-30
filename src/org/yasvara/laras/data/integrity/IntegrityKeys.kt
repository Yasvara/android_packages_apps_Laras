/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

/*
 * Settings.Secure keys shared with the framework engine (YasvaraSpoofEngine /
 * YasvaraTrickyStore / YasvaraKeyboxProvider), which reads the same keys from every app process.
 * Renaming one here breaks the engine.
 */
object IntegrityKeys {
    const val PIF_ENABLED = "yasvara_pif_enabled"
    const val PIF_CONFIG = "yasvara_pif_config"
    const val PIF_LAST_FETCH = "yasvara_pif_last_fetch"
    const val PIF_SOURCE = "yasvara_pif_source"
    const val PIF_CUSTOM_URL = "yasvara_pif_custom_url"
    const val PIF_CHANNEL = "yasvara_pif_channel"
    const val PIF_LIBRARY = "yasvara_pif_library"
    const val PIF_DEVICE = "yasvara_pif_device"
    const val PIF_GOOGLE_DEVICES = "yasvara_pif_google_devices"
    const val PIF_GOOGLE_DEVICES_AT = "yasvara_pif_google_devices_at"
    const val PIF_GOOGLE_KEY = "yasvara_pif_google_key"
    const val SPOOF_MODE = "yasvara_spoof_mode"
    const val PHOTOS_SPOOF = "yasvara_photos_spoof"
    const val SNAP_SPOOF = "yasvara_snap_spoof"
    const val TS_ENABLED = "yasvara_trickystore_enabled"
    const val TS_KEYBOX = "yasvara_trickystore_keybox"
    const val TS_TARGETS = "yasvara_trickystore_targets"
    const val TS_PATCH = "yasvara_trickystore_patch"
    const val TS_LAST_REVOKE_STATUS = "yasvara_trickystore_last_revocation_status"
    /** Last ladder rung per package, written by the engine: {pkg: {rung, at, reason}}. */
    const val TS_RUNG = "yasvara_trickystore_rung"
    /** Debug override for the ladder: "" (auto), "passthrough", "patch", "synthesize". */
    const val TS_FORCE = "yasvara_trickystore_force"
    /** Per-package patch-date override: {package|"*": "YYYY-MM-DD"}. */
    const val TS_PATCH_OVERRIDE = "yasvara_trickystore_patch_override"
    /** Timestamp of the last "Re-probe TEE" request; the engine polls it. */
    const val TS_TEE_REPROBE = "yasvara_trickystore_tee_reprobe"

    const val MODE_AUTO = 0
    const val MODE_FULL = 1
    const val MODE_MODEL_ONLY = 2
}
