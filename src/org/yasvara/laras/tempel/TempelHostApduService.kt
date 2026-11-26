/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.tempel

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import org.yasvara.laras.data.tempel.TempelRepository

/**
 * On-demand ISO-DEP / Type-4A NDEF HCE service. Binds strictly during an active RF tap,
 * forwards command APDUs to the zero-alloc Rust responder in `yasvara_tempel`, and notifies
 * completion on deactivation so AfterTap auto-revert restores payment mode immediately.
 */
class TempelHostApduService : HostApduService() {
    private val repo by lazy { TempelRepository(applicationContext) }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.isEmpty()) return byteArrayOf(0x67, 0x00)
        return repo.processApdu(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        repo.notifyTapCompleted()
        TempelTile.requestRefresh(applicationContext)
    }
}
