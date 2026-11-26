/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.tempel

import android.nfc.cardemulation.HostNfcFService
import android.os.Bundle
import org.yasvara.laras.data.tempel.TempelRepository

/** On-demand FeliCa (NFC-F) HCE-F service. Binds strictly during an active FeliCa tap. */
class TempelHostNfcFService : HostNfcFService() {
    private val repo by lazy { TempelRepository(applicationContext) }

    override fun processNfcFPacket(commandPacket: ByteArray?, extras: Bundle?): ByteArray? {
        if (commandPacket == null || commandPacket.isEmpty()) return null
        return repo.processApdu(commandPacket)
    }

    override fun onDeactivated(reason: Int) {
        repo.notifyTapCompleted()
        TempelTile.requestRefresh(applicationContext)
    }
}
