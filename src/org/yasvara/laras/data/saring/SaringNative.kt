/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.saring

/** The list compiler in libsaring_jni. */
object SaringNative {
    /** Per input: block, allow, deny, third party, pop-up, cosmetic, unsupported. */
    const val COUNTS = 7

    init {
        System.loadLibrary("saring_jni")
    }

    /** Returns the entry count followed by [COUNTS] numbers per input, or null on failure. */
    @JvmStatic
    external fun compile(inputs: Array<String>, output: String, created: Long): LongArray?
}
