/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import org.json.JSONArray
import org.yasvara.laras.R
import java.io.ByteArrayInputStream
import java.io.File
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Google's hardware attestation roots. A bundled copy works offline; a copy refreshed from
 * Google is kept in app storage so a rotated root is picked up without an OS update.
 */
class AttestationRoots(private val context: Context) {
    private val cache = File(context.filesDir, "attestation_roots.json")

    fun publicKeys(): List<PublicKey> =
        (load(cache.takeIf { it.exists() }?.readText()) + load(bundled())).map { it.publicKey }

    /** Best effort, at most weekly; a failure keeps whatever roots are already known. */
    suspend fun refreshIfStale() {
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < WEEK_MS) return
        try {
            val body = Http.get(context, ROOTS_URL, 64 * 1024)
            if (load(body).isNotEmpty()) cache.writeText(body)
        } catch (_: FetchException) { }
    }

    private fun bundled() = context.resources.openRawResource(R.raw.attestation_roots)
        .use { it.readBytes().toString(Charsets.UTF_8) }

    private fun load(json: String?): List<X509Certificate> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val factory = CertificateFactory.getInstance("X.509")
            val array = JSONArray(json)
            (0 until array.length()).map {
                factory.generateCertificate(ByteArrayInputStream(array.getString(it).toByteArray()))
                    as X509Certificate
            }
        } catch (_: Exception) { emptyList() }
    }

    private companion object {
        const val ROOTS_URL = "https://android.googleapis.com/attestation/root"
        const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
