/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.samar

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.yasvara.laras.R
import org.yasvara.laras.ui.theme.LarasTheme
import kotlin.math.roundToInt

/**
 * The floating map and joystick: an overlay window owned by the Laras process, so it stays up
 * over any app without a notification or a service. Main thread only.
 */
object SamarPanel {
    private val main = Handler(Looper.getMainLooper())
    private var host: PanelHost? = null

    val shown: Boolean get() = host != null

    fun show(context: Context) = onMain {
        if (host == null) host = PanelHost(context.applicationContext).also(PanelHost::attach)
    }

    fun hide() = onMain {
        host?.detach()
        host = null
    }

    fun toggle(context: Context) = onMain { if (host == null) show(context) else hide() }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }
}

internal class PanelHost(private val context: Context) : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = (12 * density).roundToInt()
        y = (context.resources.displayMetrics.heightPixels * 0.18f).roundToInt()
        title = "Samar"
        setFitInsetsTypes(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun attach() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val compose = ComposeView(ContextThemeWrapper(context, R.style.Theme_Laras)).apply {
            setViewTreeLifecycleOwner(this@PanelHost)
            setViewTreeSavedStateRegistryOwner(this@PanelHost)
            setViewTreeViewModelStoreOwner(this@PanelHost)
            setContent {
                LarasTheme {
                    SamarPanelContent(
                        lifecycle = registry,
                        onDrag = ::moveBy,
                        onClose = SamarPanel::hide,
                    )
                }
            }
        }
        view = compose
        wm.addView(compose, params)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun detach() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    /** Drags the window, keeping at least part of it on screen. */
    private fun moveBy(dx: Float, dy: Float) {
        val v = view ?: return
        val bounds = wm.currentWindowMetrics.bounds
        val margin = 48 * density
        params.x = (params.x + dx).coerceIn(-v.width + margin, bounds.width() - margin).roundToInt()
        params.y = (params.y + dy).coerceIn(0f, bounds.height() - margin).roundToInt()
        wm.updateViewLayout(v, params)
    }
}
