/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.depth

import java.security.MessageDigest

/**
 * Identity of a wallpaper picture (docs/specs/0013 §6).
 *
 * [sha] is the SHA-256 of the wallpaper's original file, lowercase hex. SystemUI computes the same
 * from the same file, so both sides find a picture's depth by it, whatever wallpaper id it gets.
 * [dhash] is a 64-bit difference hash of how the picture looks, to recognise a re-encoded or
 * resized copy of a saved picture, whose bytes (and so [sha]) differ.
 */
data class Fingerprint(val sha: String, val dhash: Long) {

    companion object {
        /** The grid [dhash] reads: one column more than bits per row. */
        const val DHASH_WIDTH = 9
        const val DHASH_HEIGHT = 8

        /** Copies whose [dhash] differ in at most this many bits look the same. */
        const val SIMILAR_BITS = 6

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /**
         * [luma] is the picture shrunk to [DHASH_WIDTH] x [DHASH_HEIGHT] brightness values, row by
         * row; bit 8·row + column is set where a value is brighter than its right neighbour.
         */
        fun dhash(luma: FloatArray): Long {
            require(luma.size == DHASH_WIDTH * DHASH_HEIGHT)
            var hash = 0L
            for (y in 0 until DHASH_HEIGHT) for (x in 0 until DHASH_WIDTH - 1) {
                val i = y * DHASH_WIDTH + x
                if (luma[i] > luma[i + 1]) hash = hash or (1L shl (y * (DHASH_WIDTH - 1) + x))
            }
            return hash
        }

        fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

        fun hex(value: Long): String = "%016x".format(value)

        fun parseHex(text: String): Long = java.lang.Long.parseUnsignedLong(text, 16)

        fun isSha(text: String) = text.length == 64 && text.all { it in '0'..'9' || it in 'a'..'f' }
    }
}
