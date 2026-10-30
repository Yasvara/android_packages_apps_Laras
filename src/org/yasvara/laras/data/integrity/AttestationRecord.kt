/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import java.security.cert.X509Certificate

/** The parts of a key attestation extension (KeyDescription) that decide integrity tiers. */
class AttestationRecord(
    val attestationSecurityLevel: Int,
    val challenge: ByteArray,
    val deviceLocked: Boolean?,
    val verifiedBootState: Int?,
    /** YYYYMM, or YYYYMMDD on newer KeyMint; null when the TEE reported none. */
    val osPatchLevel: Int?,
) {
    val hardwareBacked get() = attestationSecurityLevel >= SECURITY_TEE
    val strongBox get() = attestationSecurityLevel >= SECURITY_STRONGBOX
    val bootVerified get() = verifiedBootState == BOOT_VERIFIED

    companion object {
        const val SECURITY_TEE = 1
        const val SECURITY_STRONGBOX = 2
        const val BOOT_VERIFIED = 0

        private const val OID = "1.3.6.1.4.1.11129.2.1.17"
        private const val TAG_OS_PATCH_LEVEL = 706
        private const val TAG_ROOT_OF_TRUST = 704

        /** Null when [cert] carries no (or an unreadable) attestation extension. */
        fun of(cert: X509Certificate): AttestationRecord? {
            val wrapped = cert.getExtensionValue(OID) ?: return null
            return try { parse(wrapped) } catch (_: Exception) { null }
        }

        // getExtensionValue is OCTET STRING { KeyDescription SEQUENCE { ... } }.
        private fun parse(wrapped: ByteArray): AttestationRecord {
            val top = Der(wrapped)
            val octet = top.next()
            val root = top.children(octet).next()
            val seq = top.children(root)
            seq.next() // attestationVersion
            val level = seq.int(seq.next())
            seq.next() // keyMintVersion
            seq.next() // keyMintSecurityLevel
            val challenge = seq.bytes(seq.next())
            seq.next() // uniqueId
            var locked: Boolean? = null
            var bootState: Int? = null
            var patch: Int? = null
            // softwareEnforced, then hardwareEnforced; the TEE's answer is read last and wins.
            repeat(2) {
                val entries = seq.children(seq.next())
                while (entries.hasMore()) {
                    val entry = entries.next()
                    if (entry.tagClass != Der.CONTEXT) continue
                    val value = entries.children(entry).next()
                    when (entry.tag) {
                        TAG_OS_PATCH_LEVEL -> patch = entries.int(value)
                        TAG_ROOT_OF_TRUST -> {
                            val rot = entries.children(value)
                            rot.next() // verifiedBootKey
                            locked = rot.boolean(rot.next())
                            bootState = rot.int(rot.next())
                        }
                    }
                }
            }
            return AttestationRecord(level, challenge, locked, bootState, patch)
        }
    }
}
