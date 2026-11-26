/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.tempel

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV

enum class WriteTarget(val label: String) {
    GEN2_CUID("MIFARE Gen2 / CUID / FUID"),
    GEN3_APDU("MIFARE Gen3 APDU Magic"),
    GEN4_UMC("Gen4 Ultimate Magic (UMC)"),
    NTAG_UL("NTAG213 / 215 / 216 / Ultralight"),
    NFC_V("ISO 15693 (NFC-V)"),
    FELICA_LITE("FeliCa Lite-S"),
    NDEF_T4T("NDEF (Any NDEF / Type-4 Tag)"),
}

data class ScannedCard(
    val techMask: Int,
    val uid: ByteArray,
    val atqa: ByteArray,
    val sak: Int,
    val histBytes: ByteArray,
    val appData: ByteArray,
    val systemCode: ByteArray,
    val pmm: ByteArray,
    val ndefBytes: ByteArray,
    val dumpBytes: ByteArray,
    val totalSectors: Int,
    val readableSectors: Int,
    val usedHardware2f45: Boolean,
    val eMoney: EMoneyInfo?,
    val analysis: CardAnalysis,
    val suggestedKind: ProfileKind,
    val techLabel: String,
)

data class WriteReport(
    val ok: Boolean,
    val blocksWritten: Int,
    val blocksTotal: Int,
    val message: String,
)

/**
 * Hardware NFC tag reader & multi-tech writer. Runs strictly on-demand inside Reader Mode
 * while the user is scanning or writing; all frame encoding and anti-brick checks delegate
 * to the Rust engine in `yasvara_tempel`.
 */
object TempelTagEngine {
    private const val TECH_NFC_A = 1 shl 0
    private const val TECH_NFC_B = 1 shl 1
    private const val TECH_NFC_F = 1 shl 2
    private const val TECH_NFC_V = 1 shl 3
    private const val TECH_ISO_DEP = 1 shl 4
    private const val TECH_MIFARE_CLASSIC = 1 shl 5
    private const val TECH_MIFARE_ULTRALIGHT = 1 shl 6
    private const val TECH_NDEF = 1 shl 7

    // Indonesian e-Money read-only APDU probes (never modifies card state)
    private val MANDIRI_SEL = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x08, 0, 0, 0, 0, 0, 0, 0, 1)
    private val MANDIRI_INFO = byteArrayOf(0x00, 0xB3.toByte(), 0x00, 0x00, 0x3F)
    private val MANDIRI_BAL = byteArrayOf(0x00, 0xB5.toByte(), 0x00, 0x00, 0x0A)
    private val FLAZZ_SEL = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x0B,
        0xA0.toByte(), 0x00, 0x00, 0x00, 0x18, 0x0F, 0x00, 0x00, 0x01, 0x80.toByte(), 0x01,
    )
    private val FLAZZ_BAL = byteArrayOf(0x80.toByte(), 0x32, 0x00, 0x03, 0x04, 0, 0, 0, 0)
    private val TAPCASH_SEL = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x08,
        0xA0.toByte(), 0x00, 0x42, 0x4E, 0x49, 0x10, 0x00, 0x01,
    )
    private val TAPCASH_BAL = byteArrayOf(0x90.toByte(), 0x32, 0x03, 0x00, 0x00)
    private val BRIZZI_SEL = byteArrayOf(0x90.toByte(), 0x5A, 0x00, 0x00, 0x03, 0x01, 0x00, 0x00, 0x00)
    private val BRIZZI_INFO = byteArrayOf(0x90.toByte(), 0xBD.toByte(), 0, 0, 7, 0, 0, 0, 0, 0x17, 0, 0, 0)
    private val BRIZZI_BAL = byteArrayOf(0x90.toByte(), 0x6C, 0x00, 0x00, 0x01, 0x03, 0x00)

    private val FALLBACK_MFC_KEYS = arrayOf(
        MifareClassic.KEY_DEFAULT,
        MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
        MifareClassic.KEY_NFC_FORUM,
        byteArrayOf(0, 0, 0, 0, 0, 0),
        byteArrayOf(1, 2, 3, 4, 5, 6),
    )

    fun scan(tag: Tag, repo: TempelRepository): ScannedCard {
        val techs = tag.techList.toSet()
        var mask = 0
        if (NfcA::class.java.name in techs) mask = mask or TECH_NFC_A
        if (NfcB::class.java.name in techs) mask = mask or TECH_NFC_B
        if (NfcF::class.java.name in techs) mask = mask or TECH_NFC_F
        if (NfcV::class.java.name in techs) mask = mask or TECH_NFC_V
        if (IsoDep::class.java.name in techs) mask = mask or TECH_ISO_DEP
        if (MifareClassic::class.java.name in techs) mask = mask or TECH_MIFARE_CLASSIC
        if (MifareUltralight::class.java.name in techs) mask = mask or TECH_MIFARE_ULTRALIGHT
        if (Ndef::class.java.name in techs) mask = mask or TECH_NDEF

        val uid = tag.id ?: ByteArray(0)
        var atqa = byteArrayOf(0x04, 0x00)
        var sak = 0x00
        var hist = ByteArray(0)
        var appData = ByteArray(4)
        var sysCode = ByteArray(2)
        var pmm = ByteArray(8)
        var ndefBytes = ByteArray(0)
        var dumpBytes = ByteArray(0)
        var totalSectors = 0
        var readableSectors = 0
        var used2f45 = false
        var eMoney: EMoneyInfo? = null

        NfcA.get(tag)?.let { a ->
            a.atqa?.takeIf { it.size == 2 }?.let { atqa = it }
            sak = a.sak.toInt() and 0xFF
        }
        NfcB.get(tag)?.let { b ->
            b.applicationData?.takeIf { it.size >= 4 }?.let { appData = it.copyOf(4) }
            b.protocolInfo?.let { hist = it }
        }
        NfcF.get(tag)?.let { f ->
            f.systemCode?.takeIf { it.size >= 2 }?.let { sysCode = it.copyOf(2) }
            f.manufacturer?.takeIf { it.size >= 8 }?.let { pmm = it.copyOf(8) }
        }

        // Read NDEF if present
        Ndef.get(tag)?.let { ndef ->
            runCatching {
                ndef.connect()
                ndef.ndefMessage?.toByteArray()?.let { ndefBytes = it }
            }
            runCatching { ndef.close() }
        }

        // Probe ISO-DEP + read-only Indonesian e-Money
        IsoDep.get(tag)?.let { iso ->
            runCatching {
                iso.connect()
                iso.timeout = 1200
                iso.historicalBytes?.takeIf { it.isNotEmpty() }?.let { hist = it }
                eMoney = probeEMoney(iso, repo)
            }
            runCatching { iso.close() }
        }

        // MIFARE Classic full sector scan (tries SN220U 2F 45 hardware 40-key verifier first)
        MifareClassic.get(tag)?.let { mfc ->
            runCatching {
                mfc.connect()
                totalSectors = mfc.sectorCount
                val totalBlocks = mfc.blockCount
                val buf = ByteArray(totalBlocks * 16)
                for (sec in 0 until totalSectors) {
                    var authed = false
                    var keyA: ByteArray? = null
                    var keyB: ByteArray? = null

                    // Fast hardware 2F 45 40-key batch check (returns matching 6B key on SN220U)
                    val hwKeyA = repo.verifyMfcSectorKeys(sec, false, null)
                    if (hwKeyA != null && hwKeyA.size == 6) {
                        used2f45 = true
                        if (mfc.authenticateSectorWithKeyA(sec, hwKeyA)) {
                            authed = true
                            keyA = hwKeyA
                        }
                    }
                    if (!authed) {
                        for (k in FALLBACK_MFC_KEYS) {
                            if (mfc.authenticateSectorWithKeyA(sec, k)) {
                                authed = true
                                keyA = k
                                break
                            }
                        }
                    }
                    if (!authed) {
                        val hwKeyB = repo.verifyMfcSectorKeys(sec, true, null)
                        if (hwKeyB != null && hwKeyB.size == 6 && mfc.authenticateSectorWithKeyB(sec, hwKeyB)) {
                            used2f45 = true
                            authed = true
                            keyB = hwKeyB
                        }
                    }
                    if (!authed) {
                        for (k in FALLBACK_MFC_KEYS) {
                            if (mfc.authenticateSectorWithKeyB(sec, k)) {
                                authed = true
                                keyB = k
                                break
                            }
                        }
                    }
                    if (authed) {
                        readableSectors++
                        val firstBlk = mfc.sectorToBlock(sec)
                        val blkCnt = mfc.getBlockCountInSector(sec)
                        for (i in 0 until blkCnt) {
                            val blkIdx = firstBlk + i
                            val data = runCatching { mfc.readBlock(blkIdx) }.getOrNull()
                            if (data != null && data.size == 16) {
                                // Preserve known Key A/B in sector trailer (MIFARE masks Key A as 00 on read)
                                if (i == blkCnt - 1) {
                                    keyA?.copyInto(data, 0, 0, 6)
                                    keyB?.copyInto(data, 10, 0, 6)
                                }
                                data.copyInto(buf, blkIdx * 16, 0, 16)
                            }
                        }
                    }
                }
                dumpBytes = buf
            }
            runCatching { mfc.close() }
        }

        // NTAG / Ultralight full page dump via FAST_READ (0x3A) or READ (0x30)
        if (dumpBytes.isEmpty() && (mask and TECH_MIFARE_ULTRALIGHT) != 0) {
            NfcA.get(tag)?.let { a ->
                runCatching {
                    a.connect()
                    val ver = runCatching { a.transceive(byteArrayOf(0x60)) }.getOrNull()
                    val totalPages = when {
                        ver != null && ver.size >= 7 && ver[1].toInt() == 0x04 -> when (ver[6].toInt() and 0xFF) {
                            0x0F -> 45   // NTAG213
                            0x11 -> 135  // NTAG215
                            0x13 -> 231  // NTAG216
                            else -> 45
                        }
                        else -> 16 // Ultralight
                    }
                    val out = ByteArray(totalPages * 4)
                    var page = 0
                    while (page < totalPages) {
                        val end = (page + 15).coerceAtMost(totalPages - 1)
                        val resp = runCatching {
                            a.transceive(byteArrayOf(0x3A, page.toByte(), end.toByte()))
                        }.getOrNull()
                        if (resp != null && resp.size == (end - page + 1) * 4) {
                            resp.copyInto(out, page * 4)
                            page = end + 1
                        } else {
                            val p4 = runCatching { a.transceive(byteArrayOf(0x30, page.toByte())) }.getOrNull()
                            if (p4 != null && p4.size >= 4) {
                                val copyPages = (p4.size / 4).coerceAtMost(totalPages - page)
                                p4.copyInto(out, page * 4, 0, copyPages * 4)
                                page += copyPages
                            } else break
                        }
                    }
                    dumpBytes = out
                }
                runCatching { a.close() }
            }
        }

        // NFC-V (ISO 15693) block dump
        if (dumpBytes.isEmpty() && (mask and TECH_NFC_V) != 0 && uid.size == 8) {
            NfcV.get(tag)?.let { v ->
                runCatching {
                    v.connect()
                    val acc = java.io.ByteArrayOutputStream()
                    for (blk in 0 until 64) {
                        val cmd = repo.buildWriteFrame(7, 0, blk, uid) ?: break
                        val resp = runCatching { v.transceive(cmd) }.getOrNull() ?: break
                        if (resp.size < 5 || resp[0].toInt() != 0x00) break
                        acc.write(resp, 1, resp.size - 1)
                    }
                    dumpBytes = acc.toByteArray()
                }
                runCatching { v.close() }
            }
        }

        val analysis = repo.classifyTag(
            mask, uid.size,
            atqa.getOrElse(0) { 0x04 }.toInt() and 0xFF,
            atqa.getOrElse(1) { 0x00 }.toInt() and 0xFF,
            sak, ndefBytes.isNotEmpty(), eMoney != null, totalSectors, readableSectors,
        )

        val suggestedKind = when {
            (mask and TECH_NFC_F) != 0 -> ProfileKind.FELICA_HCEF
            (mask and TECH_NFC_B) != 0 -> ProfileKind.UID_TYPE_B
            ndefBytes.isNotEmpty() -> ProfileKind.NDEF_T4T
            (mask and TECH_ISO_DEP) != 0 && eMoney == null -> ProfileKind.ISODEP_HCE
            else -> ProfileKind.UID_TYPE_A
        }

        val techLabel = buildString {
            if ((mask and TECH_MIFARE_CLASSIC) != 0) append("MIFARE Classic")
            else if ((mask and TECH_MIFARE_ULTRALIGHT) != 0) append("NTAG / Ultralight")
            else if ((mask and TECH_NFC_F) != 0) append("FeliCa (NFC-F)")
            else if ((mask and TECH_NFC_B) != 0) append("ISO 14443-B")
            else if ((mask and TECH_NFC_V) != 0) append("ISO 15693 (NFC-V)")
            else if ((mask and TECH_ISO_DEP) != 0) append("ISO-DEP Type 4A")
            else append("ISO 14443-A")
            if (eMoney != null) append(" · e-Money")
        }

        return ScannedCard(
            techMask = mask,
            uid = uid,
            atqa = atqa,
            sak = sak,
            histBytes = hist,
            appData = appData,
            systemCode = sysCode,
            pmm = pmm,
            ndefBytes = ndefBytes,
            dumpBytes = dumpBytes,
            totalSectors = totalSectors,
            readableSectors = readableSectors,
            usedHardware2f45 = used2f45,
            eMoney = eMoney,
            analysis = analysis,
            suggestedKind = suggestedKind,
            techLabel = techLabel,
        )
    }

    private fun probeEMoney(iso: IsoDep, repo: TempelRepository): EMoneyInfo? {
        fun swOk(r: ByteArray?) = r != null && r.size >= 2 &&
            ((r[r.size - 2] == 0x90.toByte() && r[r.size - 1] == 0x00.toByte()) ||
                (r[r.size - 2] == 0x91.toByte() && r[r.size - 1] == 0x00.toByte()))

        // 1. Mandiri e-Money
        runCatching {
            if (swOk(iso.transceive(MANDIRI_SEL))) {
                val info = iso.transceive(MANDIRI_INFO)
                val bal = iso.transceive(MANDIRI_BAL)
                repo.decodeEMoney(1, info, bal)?.let { return it }
            }
        }
        // 2. BCA Flazz
        runCatching {
            if (swOk(iso.transceive(FLAZZ_SEL))) {
                val bal = iso.transceive(FLAZZ_BAL)
                repo.decodeEMoney(2, ByteArray(0), bal)?.let { return it }
            }
        }
        // 3. BNI TapCash
        runCatching {
            if (swOk(iso.transceive(TAPCASH_SEL))) {
                val bal = iso.transceive(TAPCASH_BAL)
                repo.decodeEMoney(3, ByteArray(0), bal)?.let { return it }
            }
        }
        // 4. BRI Brizzi
        runCatching {
            if (swOk(iso.transceive(BRIZZI_SEL))) {
                val info = iso.transceive(BRIZZI_INFO)
                val bal = iso.transceive(BRIZZI_BAL)
                repo.decodeEMoney(4, info, bal)?.let { return it }
            }
        }
        return null
    }

    /**
     * Writes `profile` (and its `payload` dump/NDEF) onto a physical blank or magic tag.
     * Every frame and anti-brick check (BCC, sector trailer inverted access bits, NTAG lock pages)
     * is produced and validated by the Rust engine in `yasvara_tempel`.
     */
    fun writeToTag(
        tag: Tag,
        target: WriteTarget,
        profile: TempelProfile,
        payload: ByteArray,
        repo: TempelRepository,
        gen4Pwd: Int = 0,
    ): WriteReport {
        val uid = TempelRepository.hexToBytes(profile.uidHex)
        val atqa = TempelRepository.hexToBytes(profile.atqaHex).takeIf { it.size == 2 }
            ?: byteArrayOf(0x04, 0x00)
        val sak = profile.sakHex.toIntOrNull(16) ?: 0x08

        return when (target) {
            WriteTarget.GEN2_CUID -> writeGen2Cuid(tag, uid, sak, atqa, payload, repo)
            WriteTarget.GEN3_APDU -> writeGen3Apdu(tag, uid, sak, atqa, repo)
            WriteTarget.GEN4_UMC -> writeGen4Umc(tag, uid, sak, atqa, payload, gen4Pwd, repo)
            WriteTarget.NTAG_UL -> writeNtag(tag, profile, payload, repo)
            WriteTarget.NFC_V -> writeNfcV(tag, payload, repo)
            WriteTarget.FELICA_LITE -> writeFelicaLite(tag, payload, repo)
            WriteTarget.NDEF_T4T -> writeNdefGeneric(tag, payload)
        }
    }

    private fun writeGen2Cuid(
        tag: Tag, uid: ByteArray, sak: Int, atqa: ByteArray, dump: ByteArray, repo: TempelRepository,
    ): WriteReport {
        val mfc = MifareClassic.get(tag)
            ?: return WriteReport(false, 0, 0, "Tag is not MIFARE Classic / CUID")
        val block0 = repo.buildMfcBlock0(uid, sak, atqa[0].toInt() and 0xFF, atqa[1].toInt() and 0xFF)
            ?: return WriteReport(false, 0, 0, "Invalid UID length (need 4B or 7B)")

        var written = 0
        val hasFullDump = dump.size == 1024 || dump.size == 4096
        val total = if (hasFullDump) dump.size / 16 else 1
        return try {
            mfc.connect()
            // Authenticate Sector 0 with default keys (blank CUID cards ship with FF..FF)
            val s0Authed = FALLBACK_MFC_KEYS.any { mfc.authenticateSectorWithKeyA(0, it) } ||
                FALLBACK_MFC_KEYS.any { mfc.authenticateSectorWithKeyB(0, it) }
            if (!s0Authed) return WriteReport(false, 0, total, "Cannot auth Sector 0 on target CUID")

            mfc.writeBlock(0, block0)
            written++

            if (hasFullDump) {
                val sectors = (dump.size / 64).coerceAtMost(mfc.sectorCount)
                for (sec in 0 until sectors) {
                    val authed = FALLBACK_MFC_KEYS.any { mfc.authenticateSectorWithKeyA(sec, it) } ||
                        FALLBACK_MFC_KEYS.any { mfc.authenticateSectorWithKeyB(sec, it) }
                    if (!authed) continue
                    val firstBlk = mfc.sectorToBlock(sec)
                    val blkCnt = mfc.getBlockCountInSector(sec)
                    for (i in 0 until blkCnt) {
                        val blkIdx = firstBlk + i
                        if (blkIdx == 0) continue
                        val blkData = dump.copyOfRange(blkIdx * 16, blkIdx * 16 + 16)
                        // Anti-brick guard on sector trailers
                        if (i == blkCnt - 1 && !repo.validateMfcAccessBits(
                                blkData[6].toInt() and 0xFF,
                                blkData[7].toInt() and 0xFF,
                                blkData[8].toInt() and 0xFF,
                            )
                        ) {
                            continue
                        }
                        mfc.writeBlock(blkIdx, blkData)
                        written++
                    }
                }
            }
            WriteReport(true, written, total, "CUID written ($written/$total blocks)")
        } catch (e: Exception) {
            WriteReport(false, written, total, "CUID write error: ${e.message}")
        } finally {
            runCatching { mfc.close() }
        }
    }

    private fun writeGen3Apdu(
        tag: Tag, uid: ByteArray, sak: Int, atqa: ByteArray, repo: TempelRepository,
    ): WriteReport {
        val a = NfcA.get(tag) ?: return WriteReport(false, 0, 1, "Tag is not NFC-A")
        val cmd = if (uid.size == 7) {
            repo.buildWriteFrame(1, 0, 0, uid)
        } else {
            val b0 = repo.buildMfcBlock0(uid, sak, atqa[0].toInt() and 0xFF, atqa[1].toInt() and 0xFF)
                ?: return WriteReport(false, 0, 1, "Invalid UID length")
            repo.buildWriteFrame(0, 0, 0, b0)
        } ?: return WriteReport(false, 0, 1, "Could not build Gen3 frame")

        return try {
            a.connect()
            val rsp = a.transceive(cmd)
            val ok = rsp.size >= 2 && rsp[rsp.size - 2] == 0x90.toByte() && rsp[rsp.size - 1] == 0x00.toByte()
            WriteReport(ok, if (ok) 1 else 0, 1, if (ok) "Gen3 UID/Block 0 updated" else "Gen3 rejected: ${TempelRepository.bytesToHex(rsp)}")
        } catch (e: Exception) {
            WriteReport(false, 0, 1, "Gen3 error: ${e.message}")
        } finally {
            runCatching { a.close() }
        }
    }

    private fun writeGen4Umc(
        tag: Tag, uid: ByteArray, sak: Int, atqa: ByteArray, dump: ByteArray, pwd: Int, repo: TempelRepository,
    ): WriteReport {
        // Gen4 UMC responds to raw `CF <pwd4> ...` frames over NfcA or MifareClassic raw bypass
        val a = NfcA.get(tag) ?: return WriteReport(false, 0, 1, "Tag is not NFC-A (Gen4 UMC)")
        val block0 = repo.buildMfcBlock0(uid, sak, atqa[0].toInt() and 0xFF, atqa[1].toInt() and 0xFF)
            ?: return WriteReport(false, 0, 1, "Invalid UID length")
        val mode = if (dump.size == 4096 || sak == 0x18) 1 else 0
        val cfgArg2 = (mode and 0xFF) or ((uid.size and 0xFF) shl 8) or ((sak and 0xFF) shl 16)
        val cfgData = byteArrayOf(atqa[0], atqa[1], 1, 0)
        val setCfgCmd = repo.buildWriteFrame(5, pwd, cfgArg2, cfgData)
            ?: return WriteReport(false, 0, 1, "Invalid Gen4 config")

        var written = 0
        val total = if (dump.size == 1024 || dump.size == 4096) dump.size / 16 else 1
        return try {
            a.connect()
            a.timeout = 1000
            // 1. Configure persona (mode, UID len, ATQA, SAK)
            a.transceive(setCfgCmd)
            // 2. Write Block 0 via direct backdoor (CF <pwd> CD 00 <16B>)
            val w0 = repo.buildWriteFrame(4, pwd, 0, block0)
                ?: return WriteReport(false, 0, total, "Block 0 validation failed")
            val r0 = a.transceive(w0)
            if (r0.size >= 2 && r0[0] == 0x90.toByte() && r0[1] == 0x00.toByte()) written++

            // 3. Write remaining dump blocks (Rust validates every sector trailer automatically)
            if (dump.size == 1024 || dump.size == 4096) {
                for (blk in 1 until (dump.size / 16).coerceAtMost(256)) {
                    val b = dump.copyOfRange(blk * 16, blk * 16 + 16)
                    val frame = repo.buildWriteFrame(4, pwd, blk, b) ?: continue
                    val rsp = a.transceive(frame)
                    if (rsp.size >= 2 && rsp[0] == 0x90.toByte() && rsp[1] == 0x00.toByte()) {
                        written++
                    }
                }
            }
            WriteReport(written > 0, written, total, "Gen4 UMC written ($written/$total blocks)")
        } catch (e: Exception) {
            WriteReport(false, written, total, "Gen4 UMC error: ${e.message}")
        } finally {
            runCatching { a.close() }
        }
    }

    private fun writeNtag(
        tag: Tag, profile: TempelProfile, payload: ByteArray, repo: TempelRepository,
    ): WriteReport {
        val a = NfcA.get(tag) ?: return WriteReport(false, 0, 1, "Tag is not NTAG / Ultralight")
        return try {
            a.connect()
            val ver = runCatching { a.transceive(byteArrayOf(0x60)) }.getOrNull()
            val variant = when {
                ver != null && ver.size >= 7 && ver[1].toInt() == 0x04 -> when (ver[6].toInt() and 0xFF) {
                    0x0F -> 2 // NTAG213
                    0x11 -> 3 // NTAG215
                    0x13 -> 4 // NTAG216
                    else -> 2
                }
                else -> 0 // Ultralight
            }
            var written = 0
            if (profile.kind == ProfileKind.NDEF_T4T && payload.isNotEmpty()) {
                val flat = repo.buildNtagNdefPages(variant, payload)
                    ?: return WriteReport(false, 0, 0, "NDEF payload exceeds tag capacity")
                val total = flat.size / 5
                for (i in 0 until total) {
                    val pg = flat[i * 5].toInt() and 0xFF
                    val d4 = flat.copyOfRange(i * 5 + 1, i * 5 + 5)
                    val frame = repo.buildWriteFrame(6, variant, pg, d4) ?: continue
                    a.transceive(frame)
                    written++
                }
                WriteReport(true, written, total, "NTAG NDEF written ($written/$total pages)")
            } else if (payload.size >= 64 && (payload.size % 4) == 0) {
                val totalPages = payload.size / 4
                var totalUser = 0
                for (pg in 4 until totalPages) {
                    val d4 = payload.copyOfRange(pg * 4, pg * 4 + 4)
                    val frame = repo.buildWriteFrame(6, variant, pg, d4) ?: continue
                    totalUser++
                    a.transceive(frame)
                    written++
                }
                WriteReport(written > 0, written, totalUser, "NTAG user pages cloned ($written/$totalUser)")
            } else {
                WriteReport(false, 0, 0, "Profile has no NDEF or NTAG page dump to write")
            }
        } catch (e: Exception) {
            WriteReport(false, 0, 0, "NTAG write error: ${e.message}")
        } finally {
            runCatching { a.close() }
        }
    }

    private fun writeNfcV(tag: Tag, payload: ByteArray, repo: TempelRepository): WriteReport {
        val v = NfcV.get(tag) ?: return WriteReport(false, 0, 0, "Tag is not ISO 15693 (NFC-V)")
        val uid = tag.id ?: return WriteReport(false, 0, 0, "Missing NFC-V UID")
        if (uid.size != 8 || payload.size < 4) {
            return WriteReport(false, 0, 0, "Need 8B NFC-V UID and >=4B block payload")
        }
        val total = payload.size / 4
        var written = 0
        return try {
            v.connect()
            for (blk in 0 until total) {
                val arg = ByteArray(12)
                uid.copyInto(arg, 0, 0, 8)
                payload.copyInto(arg, 8, blk * 4, blk * 4 + 4)
                val cmd = repo.buildWriteFrame(8, 0, blk, arg) ?: continue
                val rsp = v.transceive(cmd)
                if (rsp.isNotEmpty() && rsp[0].toInt() == 0x00) written++
            }
            WriteReport(written > 0, written, total, "NFC-V written ($written/$total blocks)")
        } catch (e: Exception) {
            WriteReport(false, written, total, "NFC-V write error: ${e.message}")
        } finally {
            runCatching { v.close() }
        }
    }

    private fun writeFelicaLite(tag: Tag, payload: ByteArray, repo: TempelRepository): WriteReport {
        val f = NfcF.get(tag) ?: return WriteReport(false, 0, 0, "Tag is not FeliCa (NFC-F)")
        val idm = tag.id ?: return WriteReport(false, 0, 0, "Missing FeliCa IDm")
        if (idm.size != 8 || payload.size < 16) {
            return WriteReport(false, 0, 0, "Need 8B FeliCa IDm and >=16B block payload")
        }
        val total = (payload.size / 16).coerceAtMost(14) // FeliCa Lite-S S_PAD0..13
        var written = 0
        return try {
            f.connect()
            for (blk in 0 until total) {
                val arg = ByteArray(24)
                idm.copyInto(arg, 0, 0, 8)
                payload.copyInto(arg, 8, blk * 16, blk * 16 + 16)
                val cmd = repo.buildWriteFrame(10, 0, blk, arg) ?: continue
                val rsp = f.transceive(cmd)
                if (rsp.size >= 12 && rsp[10].toInt() == 0x00 && rsp[11].toInt() == 0x00) written++
            }
            WriteReport(written > 0, written, total, "FeliCa Lite-S written ($written/$total blocks)")
        } catch (e: Exception) {
            WriteReport(false, written, total, "FeliCa write error: ${e.message}")
        } finally {
            runCatching { f.close() }
        }
    }

    private fun writeNdefGeneric(tag: Tag, payload: ByteArray): WriteReport {
        if (payload.isEmpty()) return WriteReport(false, 0, 1, "Empty NDEF payload")
        return try {
            val msg = NdefMessage(payload)
            Ndef.get(tag)?.let { ndef ->
                ndef.connect()
                try {
                    if (!ndef.isWritable) return WriteReport(false, 0, 1, "Tag is read-only")
                    ndef.writeNdefMessage(msg)
                    return WriteReport(true, 1, 1, "NDEF written (${payload.size} B)")
                } finally {
                    runCatching { ndef.close() }
                }
            }
            NdefFormatable.get(tag)?.let { fmt ->
                fmt.connect()
                try {
                    fmt.format(msg)
                    return WriteReport(true, 1, 1, "Formatted & wrote NDEF (${payload.size} B)")
                } finally {
                    runCatching { fmt.close() }
                }
            }
            WriteReport(false, 0, 1, "Tag does not support NDEF")
        } catch (e: Exception) {
            WriteReport(false, 0, 1, "NDEF write error: ${e.message}")
        }
    }
}
