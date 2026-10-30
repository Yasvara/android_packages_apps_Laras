/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.data.theme

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.os.Trace
import android.util.Log
import androidx.core.os.bundleOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.yasvara.laras.data.Namespace
import org.yasvara.laras.data.SettingsRepository

/** [ThemeStore] over the settings providers. */
class SettingsThemeStore(private val settings: SettingsRepository) : ThemeStore {
    override fun read(key: ThemeKey) = settings.readRaw(key.namespace, key.key)

    override fun write(key: ThemeKey, value: String?) =
        settings.writeRaw(key.namespace, key.key, value)

    override var undo: String?
        get() = settings.readRaw(Namespace.SECURE, KEY_UNDO)
        set(value) = settings.writeRaw(Namespace.SECURE, KEY_UNDO, value)

    private companion object {
        const val KEY_UNDO = "yasvara_theme_undo"
    }
}

/**
 * SystemUI's theme apply transition (SPEC-0002 §7.1): it covers the screen with a snapshot
 * before a theme is written and reveals the result from the touch point once the new theme is
 * drawn. Every call degrades to a plain apply when SystemUI does not offer it.
 */
class ThemeTransitionClient(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    /** One transition; [cover] is false when SystemUI could not show a snapshot. */
    class Session(val token: Int, val cover: Boolean) {
        internal val ended = CompletableDeferred<String?>()
    }

    /** Covers the screen; ([x], [y]) is the reveal centre in display pixels. Blocking. */
    fun begin(x: Float, y: Float, expectThemeChange: Boolean): Session? {
        var session: Session? = null
        val callback =
            Messenger(
                Handler(Looper.getMainLooper()) { message ->
                    if (message.what == MESSAGE_END) {
                        session?.ended?.complete(message.data.getString(KEY_FINGERPRINT))
                    }
                    true
                }
            )
        val extras =
            bundleOf(
                KEY_X to x,
                KEY_Y to y,
                KEY_TIMEOUT_MS to TIMEOUT_MS,
                KEY_EXPECT_THEME_CHANGE to expectThemeChange,
                KEY_CALLBACK to callback,
            )
        val result = call(METHOD_BEGIN, extras) ?: return null
        val token = result.getInt(KEY_TOKEN)
        if (token == 0) return null
        return Session(token, result.getBoolean(KEY_COVER)).also { session = it }
    }

    /** Laras has drawn in the new theme; SystemUI may reveal. */
    fun ready(session: Session) {
        call(METHOD_READY, bundleOf(KEY_TOKEN to session.token))
    }

    /** The fingerprint of the colours SystemUI has applied, or null for stock colours. */
    fun appliedFingerprint(): String? = call(METHOD_END, Bundle.EMPTY)?.getString(KEY_FINGERPRINT)

    /** Waits for the reveal to start and returns SystemUI's colour fingerprint, if any. */
    suspend fun awaitEnd(session: Session): String? =
        withTimeoutOrNull(TIMEOUT_MS + END_GRACE_MS) { session.ended.await() }

    private fun call(method: String, extras: Bundle): Bundle? =
        try {
            resolver.call(AUTHORITY, method, null, extras)
        } catch (e: RuntimeException) {
            // An older SystemUI, or none at the moment: apply without the transition.
            Log.w(TAG, "SystemUI transition unavailable: $method", e)
            null
        }

    private companion object {
        const val TAG = "Laras"
        val AUTHORITY: Uri = Uri.parse("content://com.android.systemui.customization")
        const val METHOD_BEGIN = "yas_transition_begin"
        const val METHOD_READY = "yas_transition_ready"
        const val METHOD_END = "yas_transition_end"
        const val MESSAGE_END = 0x59410201
        const val KEY_X = "x"
        const val KEY_Y = "y"
        const val KEY_TIMEOUT_MS = "timeout_ms"
        const val KEY_EXPECT_THEME_CHANGE = "expect_theme_change"
        const val KEY_CALLBACK = "callback"
        const val KEY_TOKEN = "token"
        const val KEY_COVER = "cover"
        const val KEY_FINGERPRINT = "fp"
        const val TIMEOUT_MS = 1200L
        const val END_GRACE_MS = 500L
    }
}

/**
 * Applies theme changes the way the user sees them: the transition covers the screen, the
 * [ThemeCommitter] writes, Laras redraws, and the new theme is revealed from [x], [y].
 *
 * [awaitRedraw] suspends until Laras has drawn a frame in the new theme; the caller provides it
 * because only the UI knows when that is.
 */
class ThemeApplier(
    private val committer: ThemeCommitter,
    private val transition: ThemeTransitionClient,
) {
    suspend fun apply(
        change: ThemeChange,
        x: Float,
        y: Float,
        expectThemeChange: Boolean,
        awaitRedraw: suspend () -> Unit,
    ): CommitResult = covered(x, y, expectThemeChange, awaitRedraw) { committer.commit(change) }

    suspend fun undo(x: Float, y: Float, expectThemeChange: Boolean, awaitRedraw: suspend () -> Unit) =
        covered(x, y, expectThemeChange, awaitRedraw) { committer.undo() }

    /** Runs [write] under the transition cover, which always comes off afterwards. */
    private suspend fun covered(
        x: Float,
        y: Float,
        expectThemeChange: Boolean,
        awaitRedraw: suspend () -> Unit,
        write: () -> CommitResult,
    ): CommitResult {
        val session =
            withContext(Dispatchers.IO) {
                Trace.beginSection("ThemeApplier#begin")
                try {
                    transition.begin(x, y, expectThemeChange)
                } finally {
                    Trace.endSection()
                }
            }
        val result = withContext(Dispatchers.IO) { write() }
        if (session != null) {
            // Reveal even after a failed write: the cover must always come off.
            if (result is CommitResult.Applied) awaitRedraw()
            withContext(Dispatchers.IO) { transition.ready(session) }
        }
        if (result !is CommitResult.Applied) Log.w("Laras", "Theme apply: $result")
        return result
    }
}
