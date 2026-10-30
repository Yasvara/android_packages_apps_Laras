/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TargetApp(val packageName: String, val label: String, val isSystem: Boolean)

/** The apps whose key attestation is answered from the keybox. */
class TargetsRepository(private val context: Context, private val store: SecureStore) {
    private val pm: PackageManager = context.packageManager

    /** Selected packages, always including [REQUIRED] (DroidGuard lives in Play Services). */
    fun selected(): Set<String> {
        val raw = store.getString(IntegrityKeys.TS_TARGETS)
        val saved = raw.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
        return (saved.ifEmpty { DEFAULTS.toList() } + REQUIRED).toSet()
    }

    fun save(packages: Set<String>) {
        store.putString(IntegrityKeys.TS_TARGETS,
            (packages + REQUIRED).sorted().joinToString("\n", postfix = "\n"))
    }

    /**
     * Installed apps worth listing: everything the user installed, plus system apps that have a
     * launcher entry. [selected] and [REQUIRED] packages are always listed.
     */
    suspend fun installed(includeSystem: Boolean, selected: Set<String>): List<TargetApp> =
        withContext(Dispatchers.IO) {
            val launchable = pm.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
                PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS,
            ).map { it.activityInfo.packageName }.toSet()
            pm.getInstalledApplications(0)
                .filter { it.packageName != SELF }
                .mapNotNull { info ->
                    val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
                    val pinned = info.packageName in REQUIRED || info.packageName in selected
                    val listed = pinned || (if (system) includeSystem && info.packageName in launchable
                        else info.packageName in launchable)
                    if (!listed) null
                    else TargetApp(info.packageName, info.loadLabel(pm).toString(), system)
                }
                .sortedWith(compareBy({ it.packageName !in REQUIRED }, { it.label.lowercase() }))
        }

    /** Installed packages from the bundled list of apps known to require attestation. */
    fun suggestions(installed: List<TargetApp>): Set<String> =
        installed.map { it.packageName }.filter { it in KNOWN || it in REQUIRED }.toSet()

    companion object {
        private const val SELF = "org.yasvara.laras"
        const val GMS = "com.google.android.gms"

        /** Locked on: Play Services (DroidGuard). Also the ones the engine seeds. */
        val REQUIRED = setOf(GMS)
        val DEFAULTS = setOf(GMS, "com.android.vending", "com.google.android.apps.walletnfcrel")

        /** Wallets, banks and payment apps that check attestation. */
        val KNOWN = DEFAULTS + setOf(
            "com.google.android.apps.nbu.paisa.user", "id.dana", "ovo.id", "com.telkom.mwallet",
            "com.gojek.app", "com.grabtaxi.passenger", "com.shopee.id", "com.tokopedia.tkpd",
            "id.co.bri.brimo", "id.bmri.livin", "com.bca", "src.com.bni", "id.co.bca.blu",
            "com.jago.digitalBanking", "com.btpn.dc", "id.co.seabank.android",
            "com.paypal.android.p2pmobile", "com.revolut.revolut", "com.transferwise.android",
        )
    }
}
