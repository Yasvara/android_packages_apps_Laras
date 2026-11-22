/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn

/** Reads, writes and observes [Pref]s in the settings providers. */
class SettingsRepository(private val resolver: ContentResolver) {

    fun <T> read(pref: Pref<T>): T = pref.decode(readRaw(pref))

    fun <T> write(pref: Pref<T>, value: T) {
        if (pref is JsonFieldPref<*>) {
            // Read, merge and write as one step, so two fields changed together both stay.
            synchronized(this) {
                writeRaw(pref.namespace, pref.key, pref.merge(readRaw(pref), value))
            }
        } else {
            writeRaw(pref.namespace, pref.key, pref.encode(value))
        }
    }

    /**
     * Sets [fields] of the JSON object setting [key] together (null removes one), keeping its
     * other fields: a read, merge and write as one step ([mergeJsonFields]).
     */
    fun mergeFields(namespace: Namespace, key: String, fields: Map<String, String?>) {
        synchronized(this) {
            writeRaw(namespace, key, mergeJsonFields(readRaw(namespace, key), fields))
        }
    }

    /** Writes a setting's string as stored; null deletes it. */
    fun writeRaw(namespace: Namespace, key: String, raw: String?) {
        when (namespace) {
            Namespace.SYSTEM -> Settings.System.putString(resolver, key, raw)
            Namespace.SECURE -> Settings.Secure.putString(resolver, key, raw)
            Namespace.GLOBAL -> Settings.Global.putString(resolver, key, raw)
        }
    }

    fun snapshot(prefs: Collection<Pref<*>>) = PrefSnapshot(prefs.associateWith { read(it) })

    /** Emits a fresh snapshot now and after any of [prefs] changes, from any writer. */
    fun observe(prefs: Collection<Pref<*>>): Flow<PrefSnapshot> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(snapshot(prefs))
            }
        }
        prefs.map(::uriFor).distinct().forEach {
            resolver.registerContentObserver(it, false, observer)
        }
        send(snapshot(prefs))
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().flowOn(Dispatchers.IO)

    private fun readRaw(pref: Pref<*>): String? = readRaw(pref.namespace, pref.key)

    /** A setting's string as stored, or null when it is not set. */
    fun readRaw(namespace: Namespace, key: String): String? = when (namespace) {
        Namespace.SYSTEM -> Settings.System.getString(resolver, key)
        Namespace.SECURE -> Settings.Secure.getString(resolver, key)
        Namespace.GLOBAL -> Settings.Global.getString(resolver, key)
    }

    private fun uriFor(pref: Pref<*>): Uri = when (pref.namespace) {
        Namespace.SYSTEM -> Settings.System.getUriFor(pref.key)
        Namespace.SECURE -> Settings.Secure.getUriFor(pref.key)
        Namespace.GLOBAL -> Settings.Global.getUriFor(pref.key)
    }
}
