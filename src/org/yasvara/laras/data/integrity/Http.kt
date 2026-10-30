/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Why a download failed, precise enough to tell the user what to do about it. */
enum class FailureKind { NO_NETWORK, DNS, TIMEOUT, TLS, HTTP, TOO_LARGE, OTHER }

class FetchFailure(val kind: FailureKind, val detail: String? = null)

class FetchException(val failure: FetchFailure) : IOException(failure.kind.name)

object Http {
    private const val TIMEOUT_MS = 12_000
    private const val USER_AGENT = "Yasvara-Laras"

    /** GETs [url] as text. Throws [FetchException] with a specific [FailureKind]. */
    suspend fun get(
        context: Context,
        url: String,
        maxBytes: Int = 2 shl 20,
        headers: Map<String, String> = emptyMap(),
    ): String =
        withContext(Dispatchers.IO) {
            if (!hasInternet(context)) throw FetchException(FetchFailure(FailureKind.NO_NETWORK))
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    for ((k, v) in headers) setRequestProperty(k, v)
                }
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw FetchException(FetchFailure(FailureKind.HTTP, code.toString()))
                }
                val out = java.io.ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (out.size() + n > maxBytes) {
                            throw FetchException(FetchFailure(FailureKind.TOO_LARGE))
                        }
                        out.write(buffer, 0, n)
                    }
                }
                out.toString(Charsets.UTF_8.name())
            } catch (e: FetchException) {
                throw e
            } catch (e: UnknownHostException) {
                throw FetchException(FetchFailure(FailureKind.DNS, e.message))
            } catch (e: SocketTimeoutException) {
                throw FetchException(FetchFailure(FailureKind.TIMEOUT))
            } catch (e: SSLException) {
                throw FetchException(FetchFailure(FailureKind.TLS, e.message))
            } catch (e: IOException) {
                throw FetchException(FetchFailure(FailureKind.OTHER, e.message))
            } finally {
                conn?.disconnect()
            }
        }

    private fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
