/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

/** A forward-only DER reader over `b[pos, limit)`, just enough for the attestation extension. */
internal class Der(private val b: ByteArray, var pos: Int = 0, private val limit: Int = b.size) {
    class Tlv(val tagClass: Int, val tag: Int, val start: Int, val end: Int)

    fun hasMore() = pos < limit

    /** Reads one element header and moves past the whole element. */
    fun next(): Tlv {
        val first = b[pos++].toInt() and 0xff
        var tag = first and 0x1f
        if (tag == 0x1f) {
            tag = 0
            do {
                val x = b[pos++].toInt() and 0xff
                tag = (tag shl 7) or (x and 0x7f)
            } while (x and 0x80 != 0)
        }
        var length = b[pos++].toInt() and 0xff
        if (length and 0x80 != 0) {
            val n = length and 0x7f
            length = 0
            repeat(n) { length = (length shl 8) or (b[pos++].toInt() and 0xff) }
        }
        val start = pos
        pos += length
        require(pos <= limit) { "truncated DER" }
        return Tlv(first ushr 6, tag, start, pos)
    }

    /** A reader over the contents of [t], for walking a SEQUENCE or an EXPLICIT tag. */
    fun children(t: Tlv) = Der(b, t.start, t.end)

    fun bytes(t: Tlv): ByteArray = b.copyOfRange(t.start, t.end)

    /** INTEGER or ENUMERATED as a signed big-endian int. */
    fun int(t: Tlv): Int {
        var v = if (t.end > t.start && b[t.start] < 0) -1 else 0
        for (i in t.start until t.end) v = (v shl 8) or (b[i].toInt() and 0xff)
        return v
    }

    fun boolean(t: Tlv) = t.end > t.start && b[t.start].toInt() != 0

    companion object {
        const val CONTEXT = 2
    }
}
