/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One opt-in source, never invoked by loading a screen, a timer or a boot receiver. */
internal object EvokerProvider {
    const val URL = "https://evoker.qzz.io/key"
    private const val SOCKET_TIMEOUT_MS = 10_000
    private const val TOTAL_TIMEOUT_MS = 30_000L
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Laras-Evoker").apply { isDaemon = true }
    }

    suspend fun fetch(): String = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            val connection = AtomicReference<HttpsURLConnection?>()
            // A cancellable continuation bounds the caller even if platform DNS blocks. The
            // single worker also bounds resources; cancelled/late results can never import.
            val task = FutureTask<Unit> {
                try {
                    val text = download(connection) { continuation.isActive }
                    continuation.resume(text)
                } catch (e: Exception) {
                    continuation.resumeWithException(when (e) {
                        is FetchException -> e
                        is UnknownHostException -> FetchException(FetchFailure(FailureKind.DNS))
                        is SocketTimeoutException -> FetchException(FetchFailure(FailureKind.TIMEOUT))
                        is SSLException -> FetchException(FetchFailure(FailureKind.TLS))
                        else -> FetchException(FetchFailure(FailureKind.OTHER))
                    })
                } finally {
                    connection.getAndSet(null)?.disconnect()
                }
            }
            continuation.invokeOnCancellation {
                task.cancel(true)
                connection.getAndSet(null)?.disconnect()
            }
            executor.execute(task)
        }
    } ?: throw FetchException(FetchFailure(FailureKind.TIMEOUT))

    private fun download(
        connection: AtomicReference<HttpsURLConnection?>,
        active: () -> Boolean,
    ): String {
        val conn = (URL(URL).openConnection() as HttpsURLConnection).apply {
            connectTimeout = SOCKET_TIMEOUT_MS
            readTimeout = SOCKET_TIMEOUT_MS
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", "Yasvara-Laras")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache, no-store")
        }
        connection.set(conn)
        if (!active()) throw IOException("cancelled")
        val code = conn.responseCode
        if (code != HttpsURLConnection.HTTP_OK) {
            throw FetchException(FetchFailure(FailureKind.HTTP, code.toString()))
        }
        if (conn.contentLengthLong > KeyboxPayload.MAX_BYTES) {
            throw FetchException(FetchFailure(FailureKind.TOO_LARGE))
        }
        val out = ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buffer = ByteArray(4096)
            while (true) {
                if (!active()) throw IOException("cancelled")
                val count = input.read(buffer)
                if (count < 0) break
                if (out.size() + count > KeyboxPayload.MAX_BYTES) {
                    throw FetchException(FetchFailure(FailureKind.TOO_LARGE))
                }
                out.write(buffer, 0, count)
            }
        }
        // Decoding and cryptographic validation are shared with manual import, not a
        // provider-specific shortcut. Neither the response nor exceptions are logged.
        return KeyboxPayload.utf8(out.toByteArray())
    }
}
