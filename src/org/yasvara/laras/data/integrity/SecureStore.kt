/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.ContentResolver
import android.provider.Settings
import android.util.Log

/** Typed, exception-safe access to Settings.Secure, the store the engine reads from. */
class SecureStore(private val resolver: ContentResolver) {
    fun getInt(key: String, default: Int): Int =
        try { Settings.Secure.getInt(resolver, key, default) } catch (e: Exception) { Log.w(TAG, "Could not read $key", e); default }

    fun putInt(key: String, value: Int) {
        try { Settings.Secure.putInt(resolver, key, value) } catch (e: Exception) { Log.e(TAG, "Could not write $key", e) }
    }

    fun getBoolean(key: String, default: Boolean): Boolean = getInt(key, if (default) 1 else 0) == 1

    fun putBoolean(key: String, value: Boolean) = putInt(key, if (value) 1 else 0)

    fun getString(key: String): String =
        try { Settings.Secure.getString(resolver, key).orEmpty() } catch (e: Exception) { Log.w(TAG, "Could not read $key", e); "" }

    fun putString(key: String, value: String) {
        try { Settings.Secure.putString(resolver, key, value) } catch (e: Exception) { Log.e(TAG, "Could not write $key", e) }
    }

    fun getLong(key: String): Long = getString(key).toLongOrNull() ?: 0L

    fun putLong(key: String, value: Long) = putString(key, value.toString())
}

private const val TAG = "IntegrityStore"
