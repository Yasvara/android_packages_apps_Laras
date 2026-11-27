/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.security.yasvara.YasvaraIntegrityContract

/*
 * Settings.Secure keys shared with the Rust-backed framework engine (sourced from
 * YasvaraIntegrityContract) plus Laras-only UI persistence keys.
 */
object IntegrityKeys {
    const val PIF_ENABLED = YasvaraIntegrityContract.SECURE_PIF_ENABLED
    const val PIF_CONFIG = YasvaraIntegrityContract.SECURE_PIF_CONFIG
    const val PIF_LAST_FETCH = "yasvara_pif_last_fetch"
    const val PIF_SOURCE = "yasvara_pif_source"
    const val PIF_CUSTOM_URL = "yasvara_pif_custom_url"
    const val PIF_CHANNEL = "yasvara_pif_channel"
    const val PIF_LIBRARY = "yasvara_pif_library"
    const val PIF_DEVICE = "yasvara_pif_device"
    const val PIF_GOOGLE_DEVICES = "yasvara_pif_google_devices"
    const val PIF_GOOGLE_DEVICES_AT = "yasvara_pif_google_devices_at"
    const val PIF_GOOGLE_KEY = "yasvara_pif_google_key"
    const val SPOOF_MODE = YasvaraIntegrityContract.SECURE_SPOOF_MODE
    const val PHOTOS_SPOOF = YasvaraIntegrityContract.SECURE_PHOTOS_SPOOF
    const val SNAP_SPOOF = YasvaraIntegrityContract.SECURE_SNAP_SPOOF
    const val TS_ENABLED = YasvaraIntegrityContract.SECURE_TRICKY_ENABLED
    const val TS_KEYBOX = "yasvara_trickystore_keybox"
    const val TS_TARGETS = YasvaraIntegrityContract.SECURE_TRICKY_TARGETS
    const val TS_LAST_REVOKE_STATUS = "yasvara_trickystore_last_revocation_status"
    /** Per-package patch-date override: {package|"*": "YYYY-MM-DD"}. */
    const val TS_PATCH_OVERRIDE = YasvaraIntegrityContract.SECURE_TRICKY_PATCH_OVERRIDE

    const val MODE_AUTO = 0
    const val MODE_FULL = 1
    const val MODE_MODEL_ONLY = 2
}
