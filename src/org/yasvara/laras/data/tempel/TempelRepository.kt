/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.tempel

import android.content.Context
import android.content.pm.PackageManager
import android.os.ServiceManager
import com.android.server.yasvara.tempel.IYasvaraTempel

enum class ProfileKind(val code: Int) {
    PAYMENT_WALLET(0),
    UID_TYPE_A(1),
    ISODEP_HCE(2),
    NDEF_T4T(3),
    FELICA_HCEF(4),
    UID_TYPE_B(5),
    SHIELD(6);

    companion object {
        fun fromCode(code: Int): ProfileKind = entries.firstOrNull { it.code == code } ?: UID_TYPE_A
    }
}

enum class SecurityGate(val code: Int) {
    ALWAYS(0),
    SCREEN_ON(1),
    UNLOCKED(2),
    BIOMETRIC(3);

    companion object {
        fun fromCode(code: Int): SecurityGate = entries.firstOrNull { it.code == code } ?: SCREEN_ON
    }
}

enum class RevertType(val code: Int) {
    STICKY(0),
    AFTER_TAP(1),
    TIMEOUT(2);

    companion object {
        fun fromCode(code: Int): RevertType = entries.firstOrNull { it.code == code } ?: TIMEOUT
    }
}

data class TempelProfile(
    val id: Int,
    val kind: ProfileKind,
    val name: String,
    val colorArgb: Int,
    val iconId: Int,
    val gate: SecurityGate,
    val revertType: RevertType,
    val revertSec: Int,
    val showOnLockscreen: Boolean,
    val walletPkg: String,
    val uidHex: String,
    val atqaHex: String,
    val sakHex: String,
    val sysCodeHex: String,
    val pmmHex: String,
    val payloadLen: Int,
)

data class CardAnalysis(
    val preset: Int,
    val verdict: Int,
    val canEmulate: Boolean,
    val canWrite: Boolean,
    val encryptedSectors: Int,
    val totalSectors: Int,
)

data class EMoneyInfo(
    val issuer: Int,
    val balanceIdr: Long,
    val cardNumber: String,
)

data class ImportedDump(
    val tech: Int,
    val uidHex: String,
    val atqaHex: String,
    val sakHex: String,
    val sysCodeHex: String,
    val pmmHex: String,
    val mfcBlocksHex: String,
    val ntagPagesHex: String,
)

data class TempelCaps(
    val nfcOn: Boolean,
    val observeSupported: Boolean,
    val observeEnabled: Boolean,
    val walletPkg: String,
    val activeId: Int,
)

/** Thin Binder client for `yasvara_tempel` in system_server. Holds no threads or caches. */
class TempelRepository(private val context: Context) {
    private val service: IYasvaraTempel?
        get() = ServiceManager.getService("yasvara_tempel")?.let(IYasvaraTempel.Stub::asInterface)

    val available: Boolean get() = service != null

    fun listProfiles(): List<TempelProfile> {
        val rows = runCatching { service?.listProfiles() }.getOrNull() ?: return emptyList()
        return rows.mapNotNull { row ->
            val c = row.split('\t')
            if (c.size < 16) return@mapNotNull null
            TempelProfile(
                id = c[0].toIntOrNull() ?: return@mapNotNull null,
                kind = ProfileKind.fromCode(c[1].toIntOrNull() ?: 1),
                name = c[2],
                colorArgb = c[3].toLongOrNull()?.toInt() ?: 0xFF3B82F6.toInt(),
                iconId = c[4].toIntOrNull() ?: 2,
                gate = SecurityGate.fromCode(c[5].toIntOrNull() ?: 1),
                revertType = RevertType.fromCode(c[6].toIntOrNull() ?: 2),
                revertSec = c[7].toIntOrNull() ?: 60,
                showOnLockscreen = c[8] == "1",
                walletPkg = c[9],
                uidHex = c[10],
                atqaHex = c[11],
                sakHex = c[12],
                sysCodeHex = c[13],
                pmmHex = c[14],
                payloadLen = c[15].toIntOrNull() ?: 0,
            )
        }
    }

    fun activeProfileId(): Int = runCatching { service?.activeProfileId ?: 0 }.getOrDefault(0)
    fun defaultPaymentId(): Int = runCatching { service?.defaultPaymentProfileId ?: 0 }.getOrDefault(0)
    fun setDefaultPayment(id: Int) { runCatching { service?.setDefaultPaymentProfile(id) } }

    fun activate(id: Int): Boolean = runCatching { service?.activateProfile(id) == true }.getOrDefault(false)
    fun setShield(on: Boolean): Boolean = runCatching { service?.setShieldEnabled(on) == true }.getOrDefault(false)
    fun isShield(): Boolean = runCatching { service?.isShieldEnabled == true }.getOrDefault(false)
    fun remove(id: Int): Boolean = runCatching { service?.removeProfile(id) == true }.getOrDefault(false)
    fun getPayload(id: Int): ByteArray = runCatching { service?.getProfilePayload(id) }.getOrNull() ?: ByteArray(0)

    fun upsertProfile(
        id: Int = 0,
        kind: ProfileKind,
        name: String,
        colorArgb: Int = if (kind == ProfileKind.PAYMENT_WALLET) 0xFF00AA5B.toInt() else 0xFF3B82F6.toInt(),
        iconId: Int = kind.code,
        gate: SecurityGate = SecurityGate.SCREEN_ON,
        revertType: RevertType = if (kind == ProfileKind.PAYMENT_WALLET) RevertType.STICKY else RevertType.TIMEOUT,
        revertSec: Int = 60,
        showOnLockscreen: Boolean = true,
        walletPkg: String = "",
        uid: ByteArray = ByteArray(0),
        atqa: ByteArray = byteArrayOf(0x04, 0x00),
        sak: Int = 0x08,
        ratsTb1: Int = if ((sak and 0x20) != 0) 0x78 else 0x00,
        histBytes: ByteArray = ByteArray(0),
        appData: ByteArray = ByteArray(4),
        systemCode: ByteArray = ByteArray(2),
        pmm: ByteArray = ByteArray(8),
        payload: ByteArray = ByteArray(0),
    ): Int = runCatching {
        service?.upsertProfile(
            id, kind.code, name, colorArgb, iconId,
            gate.code, revertType.code, revertSec, showOnLockscreen,
            walletPkg, uid, atqa, sak, ratsTb1, histBytes, appData, systemCode, pmm, payload,
        ) ?: 0
    }.getOrDefault(0)

    fun setApduRules(profileId: Int, rulesText: String): Boolean =
        runCatching { service?.setApduRules(profileId, rulesText) == true }.getOrDefault(false)

    fun processApdu(apdu: ByteArray): ByteArray =
        runCatching { service?.processApdu(apdu) }.getOrNull() ?: byteArrayOf(0x6A, 0x82.toByte())

    fun notifyTapCompleted() { runCatching { service?.notifyTapCompleted() } }

    fun buildNdef(kind: Int, a: String, b: String = "", c: String = ""): ByteArray =
        runCatching { service?.buildNdef(kind, a, b, c) }.getOrNull() ?: ByteArray(0)

    fun classifyTag(
        techMask: Int, uidLen: Int, atqa0: Int, atqa1: Int, sak: Int,
        hasNdef: Boolean, isEMoney: Boolean, totalSectors: Int, readableSectors: Int,
    ): CardAnalysis {
        val raw = runCatching {
            service?.classifyTag(
                techMask, uidLen, atqa0, atqa1, sak, hasNdef, isEMoney, totalSectors, readableSectors,
            )
        }.getOrNull()
        val p = raw?.split('\t')
        if (p == null || p.size < 6) {
            return CardAnalysis(0, 0, uidLen in listOf(4, 7, 10), false, 0, 0)
        }
        return CardAnalysis(
            preset = p[0].toIntOrNull() ?: 0,
            verdict = p[1].toIntOrNull() ?: 0,
            canEmulate = p[2] == "1",
            canWrite = p[3] == "1",
            encryptedSectors = p[4].toIntOrNull() ?: 0,
            totalSectors = p[5].toIntOrNull() ?: 0,
        )
    }

    fun importDump(format: Int, text: String = "", bin: ByteArray = ByteArray(0)): ImportedDump? {
        val raw = runCatching { service?.importDump(format, text, bin) }.getOrNull() ?: return null
        val c = raw.split('\t')
        if (c.size < 8) return null
        return ImportedDump(
            tech = c[0].toIntOrNull() ?: 1,
            uidHex = c[1],
            atqaHex = c[2],
            sakHex = c[3],
            sysCodeHex = c[4],
            pmmHex = c[5],
            mfcBlocksHex = c[6],
            ntagPagesHex = c[7],
        )
    }

    fun exportProfile(profileId: Int, format: Int): String? =
        runCatching { service?.exportProfile(profileId, format) }.getOrNull()

    fun decodeEMoney(issuerCode: Int, infoResp: ByteArray, balanceResp: ByteArray): EMoneyInfo? {
        val raw = runCatching { service?.decodeEMoney(issuerCode, infoResp, balanceResp) }.getOrNull()
            ?: return null
        val c = raw.split('\t')
        if (c.size < 3) return null
        return EMoneyInfo(
            issuer = c[0].toIntOrNull() ?: issuerCode,
            balanceIdr = c[1].toLongOrNull() ?: 0L,
            cardNumber = c[2],
        )
    }

    fun buildMfcBlock0(uid: ByteArray, sak: Int, atqa0: Int, atqa1: Int): ByteArray? =
        runCatching { service?.buildMfcBlock0(uid, sak, atqa0, atqa1) }.getOrNull()

    fun validateMfcAccessBits(b6: Int, b7: Int, b8: Int): Boolean =
        runCatching { service?.validateMfcAccessBits(b6, b7, b8) == true }.getOrDefault(false)

    fun buildWriteFrame(op: Int, arg1: Int, arg2: Int, data: ByteArray): ByteArray? =
        runCatching { service?.buildWriteFrame(op, arg1, arg2, data) }.getOrNull()

    fun buildNtagNdefPages(variant: Int, ndefMsg: ByteArray): ByteArray? =
        runCatching { service?.buildNtagNdefPages(variant, ndefMsg) }.getOrNull()

    fun verifyMfcSectorKeys(sector: Int, isKeyB: Boolean, customKeys: ByteArray? = null): ByteArray? =
        runCatching { service?.verifyMfcSectorKeys(sector, isKeyB, customKeys) }.getOrNull()

    fun capabilities(): TempelCaps {
        val raw = runCatching { service?.capabilitiesSummary }.getOrNull() ?: ""
        val map = raw.split('\t').mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()
        return TempelCaps(
            nfcOn = map["nfcOn"] == "true",
            observeSupported = map["observeSupported"] == "true",
            observeEnabled = map["observeEnabled"] == "true",
            walletPkg = map["wallet"].orEmpty(),
            activeId = map["activeId"]?.toIntOrNull() ?: 0,
        )
    }

    /** Discovers installed payment HCE / wallet apps on device for the Wallet profile picker. */
    fun installedPaymentWallets(): List<Pair<String, String>> {
        val pm = context.packageManager
        val intent = android.content.Intent("android.nfc.cardemulation.action.HOST_APDU_SERVICE")
        val services = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        val seen = LinkedHashMap<String, String>()
        for (ri in services) {
            val pkg = ri.serviceInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (!seen.containsKey(pkg)) {
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                seen[pkg] = label
            }
        }
        return seen.entries.map { it.key to it.value }
    }

    companion object {
        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.replace(":", "").replace("-", "").replace(" ", "").trim()
            if (clean.isEmpty() || (clean.length and 1) != 0) return ByteArray(0)
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }

        fun bytesToHex(bytes: ByteArray, colon: Boolean = false): String {
            if (bytes.isEmpty()) return ""
            val sb = StringBuilder(bytes.size * (if (colon) 3 else 2))
            for (i in bytes.indices) {
                if (colon && i > 0) sb.append(':')
                sb.append(String.format("%02X", bytes[i].toInt() and 0xFF))
            }
            return sb.toString()
        }
    }
}
