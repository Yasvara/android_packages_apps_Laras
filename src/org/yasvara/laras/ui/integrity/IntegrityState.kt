/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import org.yasvara.laras.data.integrity.ChainOrigin
import org.yasvara.laras.data.integrity.IntegrityReport
import org.yasvara.laras.data.integrity.IntegrityKeys
import org.yasvara.laras.data.integrity.GoogleDevice
import org.yasvara.laras.data.integrity.KeyboxInfo
import org.yasvara.laras.data.integrity.PifChannel
import org.yasvara.laras.data.integrity.PifProfile
import org.yasvara.laras.data.integrity.Revocation
import org.yasvara.laras.data.integrity.StoredPif
import org.yasvara.laras.data.integrity.Tier

/** A result the user should still see after the action that caused it has finished. */
data class Notice(val text: String, val error: Boolean = false)

data class PifUi(
    val stored: StoredPif? = null,
    val enabled: Boolean = true,
    val mode: Int = IntegrityKeys.MODE_AUTO,
    val customUrl: String = "",
    val updating: Boolean = false,
    val notice: Notice? = null,
    val channel: PifChannel = PifChannel.STABLE,
    val devices: List<PifProfile> = emptyList(),
    /** Live Pixel list from Google; the profile library stays as history. */
    val googleDevices: List<GoogleDevice> = emptyList(),
    val activeDevice: String = "",
    val devicesLoading: Boolean = false,
)

data class KeyboxUi(
    val info: KeyboxInfo? = null,
    val revocation: Revocation = Revocation.Unknown(),
    val enabled: Boolean = true,
    val checking: Boolean = false,
    val notice: Notice? = null,
    /** True while the private key still sits in Settings (service unreachable). */
    val interim: Boolean = false,
)

data class CheckUi(val running: Boolean = false, val report: IntegrityReport? = null)

enum class Health { READY, ATTENTION, OFF }

/** What carries the verdict: a keybox answers attestation, else the profile stands in for it. */
enum class IntegrityPath { KEYBOX, PROFILE, NONE }

/** The one thing worth fixing first, shown as a banner with a single action. */
enum class Issue { KEYBOX_REVOKED, KEYBOX_EXPIRED, STALE_PROFILE }

data class IntegrityUiState(
    val loaded: Boolean = false,
    val pif: PifUi = PifUi(),
    val keybox: KeyboxUi = KeyboxUi(),
    val targetCount: Int = 0,
    val check: CheckUi = CheckUi(),
    val restartPending: Boolean = false,
) {
    val issue: Issue?
        get() = when {
            keybox.enabled && keybox.revocation is Revocation.Revoked -> Issue.KEYBOX_REVOKED
            keybox.enabled && keybox.info?.expired == true -> Issue.KEYBOX_EXPIRED
            (pif.stored?.profile?.patchAgeDays() ?: 0L) > STALE_PROFILE_DAYS -> Issue.STALE_PROFILE
            else -> null
        }

    val keyboxUsable: Boolean
        get() = keybox.enabled && keybox.info?.expired == false &&
            keybox.revocation !is Revocation.Revoked

    val path: IntegrityPath
        get() = when {
            keyboxUsable -> IntegrityPath.KEYBOX
            pif.enabled && pif.stored != null -> IntegrityPath.PROFILE
            else -> IntegrityPath.NONE
        }

    /**
     * The level target apps should reach. With a keybox it is what the last check measured,
     * since only the check sees the chain Google grades; the profile path is judged from the
     * build fields alone, which the check cannot see, so it is the level that path gives.
     */
    val expected: Tier?
        get() = when (path) {
            IntegrityPath.KEYBOX -> check.report?.takeIf { it.origin == ChainOrigin.KEYBOX }?.tier
            IntegrityPath.PROFILE -> if (issue == Issue.STALE_PROFILE) Tier.BASIC else Tier.DEVICE
            IntegrityPath.NONE -> Tier.BASIC
        }

    val health: Health
        get() = when {
            !pif.enabled && !keybox.enabled -> Health.OFF
            issue != null -> Health.ATTENTION
            else -> Health.READY
        }

    private companion object {
        const val STALE_PROFILE_DAYS = 120L
    }
}
