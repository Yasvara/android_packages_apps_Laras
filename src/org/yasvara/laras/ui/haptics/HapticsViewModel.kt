/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.haptics

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.Vibrator
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.yasvara.laras.data.raba.RABA_SETTING
import org.yasvara.laras.data.raba.RabaSettings

data class HapticsUi(
    val loaded: Boolean = false,
    val settings: RabaSettings = RabaSettings(),
    /** The platform's touch feedback switch, which Raba's master switch is. */
    val touchOn: Boolean = true,
)

/** Raba's setting and the touch feedback switch, kept in step with whoever else writes them. */
class HapticsViewModel(app: Application) : AndroidViewModel(app) {
    private val resolver = app.contentResolver
    private val writes = Mutex()
    private val _state = MutableStateFlow(HapticsUi())
    val state: StateFlow<HapticsUi> = _state.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    init {
        for (key in listOf(RABA_SETTING, Settings.System.HAPTIC_FEEDBACK_ENABLED,
            Settings.System.HAPTIC_FEEDBACK_INTENSITY)) {
            resolver.registerContentObserver(Settings.System.getUriFor(key), false, observer)
        }
        refresh()
    }

    override fun onCleared() {
        resolver.unregisterContentObserver(observer)
    }

    fun refresh() {
        viewModelScope.launch {
            val ui = withContext(Dispatchers.IO) {
                HapticsUi(
                    loaded = true,
                    settings = RabaSettings.parse(Settings.System.getString(resolver, RABA_SETTING)),
                    touchOn = Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0 &&
                        Settings.System.getInt(resolver, Settings.System.HAPTIC_FEEDBACK_INTENSITY, 1) != 0,
                )
            }
            // A write still in flight wins over what was read before it landed.
            if (!writes.isLocked) _state.value = ui
        }
    }

    /** Shows the change at once and returns once it is stored, so the caller can feel it. */
    suspend fun update(transform: (RabaSettings) -> RabaSettings) {
        writes.withLock {
            val next = transform(_state.value.settings)
            _state.update { it.copy(settings = next) }
            withContext(Dispatchers.IO) {
                Settings.System.putString(resolver, RABA_SETTING, next.toJson())
            }
        }
    }

    /**
     * The platform's touch feedback switch. Off keeps the intensity the user picked in Settings;
     * on brings an intensity of off back to the default.
     */
    suspend fun setTouch(on: Boolean) {
        writes.withLock {
            _state.update { it.copy(touchOn = on) }
            withContext(Dispatchers.IO) {
                val key = Settings.System.HAPTIC_FEEDBACK_INTENSITY
                if (on && Settings.System.getInt(resolver, key, 1) == Vibrator.VIBRATION_INTENSITY_OFF) {
                    val default = getApplication<Application>().getSystemService(Vibrator::class.java)
                        ?.getDefaultVibrationIntensity(VibrationAttributes.USAGE_TOUCH)
                        ?: Vibrator.VIBRATION_INTENSITY_MEDIUM
                    Settings.System.putInt(resolver, key, default)
                }
                Settings.System.putInt(resolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (on) 1 else 0)
            }
        }
    }
}
