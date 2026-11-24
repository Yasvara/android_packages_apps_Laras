/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.brightness

import android.app.Application
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.yasvara.laras.data.brightness.BRIGHTNESS_KEYS
import org.yasvara.laras.data.brightness.BrightnessProfile
import org.yasvara.laras.data.brightness.BrightnessSettings
import org.yasvara.laras.data.brightness.KEY_CEILING
import org.yasvara.laras.data.brightness.KEY_DEBOUNCE_DOWN
import org.yasvara.laras.data.brightness.KEY_DEBOUNCE_UP
import org.yasvara.laras.data.brightness.KEY_FLOOR
import org.yasvara.laras.data.brightness.KEY_PROFILE
import org.yasvara.laras.data.brightness.KEY_SPEED_DOWN
import org.yasvara.laras.data.brightness.KEY_SPEED_UP
import org.yasvara.laras.data.brightness.KEY_SUN
import org.yasvara.laras.data.brightness.KEY_SUN_LUX
import org.yasvara.laras.data.brightness.KEY_THERMAL_GUARD
import org.yasvara.laras.data.brightness.SunMode

/** The auto curve, lux strictly increasing and nits in the framework's panel scale. */
class BrightnessCurve(val lux: FloatArray, val nits: FloatArray) {
    val minNits: Float get() = nits.first()
    val maxNits: Float get() = nits.last()

    fun nitsAt(l: Float): Float {
        if (l <= lux.first()) return nits.first()
        for (i in 1 until lux.size) {
            if (l < lux[i]) {
                val t = (l - lux[i - 1]) / (lux[i] - lux[i - 1])
                return nits[i - 1] + t * (nits[i] - nits[i - 1])
            }
        }
        return nits.last()
    }
}

data class BrightnessUi(
    val loaded: Boolean = false,
    val settings: BrightnessSettings = BrightnessSettings(),
    val curve: BrightnessCurve? = null,
)

/** Pijar's keys, kept in step with whoever else writes them, plus the curve and live lux. */
class BrightnessViewModel(app: Application) : AndroidViewModel(app) {
    private val resolver = app.contentResolver
    private val writes = Mutex()
    private val _state = MutableStateFlow(BrightnessUi())
    val state: StateFlow<BrightnessUi> = _state.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh()
        }
    }

    /** The light sensor the framework uses, registered only while the screen collects it. */
    val lux: StateFlow<Float?> = callbackFlow {
        val sensors = app.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensors.unregisterListener(listener) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)

    init {
        for (key in BRIGHTNESS_KEYS) {
            resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        }
        refresh()
    }

    override fun onCleared() {
        resolver.unregisterContentObserver(observer)
    }

    fun refresh() {
        viewModelScope.launch {
            val ui = withContext(Dispatchers.IO) {
                BrightnessUi(loaded = true, settings = BrightnessSettings.read(resolver), curve = readCurve())
            }
            // A write still in flight wins over what was read before it landed.
            if (!writes.isLocked) _state.value = ui
        }
    }

    private fun readCurve(): BrightnessCurve? = runCatching {
        val config = getApplication<Application>().getSystemService(DisplayManager::class.java)
            ?.brightnessConfiguration ?: return null
        val curve = config.curve
        if (curve.first.size < 2) null else BrightnessCurve(curve.first, curve.second)
    }.getOrNull()

    fun setProfile(profile: BrightnessProfile) = write({ it.copy(
        profile = profile, speedUp = profile.speedUp, speedDown = profile.speedDown) }) {
        put(KEY_PROFILE, if (profile == BrightnessProfile.BALANCED) null else profile.value.toString())
        put(KEY_SPEED_UP, null)
        put(KEY_SPEED_DOWN, null)
        put(KEY_DEBOUNCE_UP, null)
        put(KEY_DEBOUNCE_DOWN, null)
    }

    /** Any speed edit makes the profile Custom, keeping the preset it came from as the debounce. */
    fun setSpeed(up: Float, down: Float) {
        val from = _state.value.settings.profile
        write({ it.copy(profile = BrightnessProfile.CUSTOM, speedUp = up, speedDown = down) }) {
            put(KEY_PROFILE, BrightnessProfile.CUSTOM.value.toString())
            put(KEY_SPEED_UP, up.toString())
            put(KEY_SPEED_DOWN, down.toString())
            if (from != BrightnessProfile.CUSTOM) {
                put(KEY_DEBOUNCE_UP, from.debounceUp?.toString())
                put(KEY_DEBOUNCE_DOWN, from.debounceDown?.toString())
            }
        }
    }

    fun setSun(on: Boolean, mode: SunMode, lux: Float?) = write({ it.copy(
        sunOn = on, sunMode = mode, sunLux = lux) }) {
        put(KEY_SUN, when {
            !on -> "0"
            mode == SunMode.FIXED -> "2"
            else -> null
        })
        put(KEY_SUN_LUX, lux?.toString())
    }

    fun setLimits(floor: Float?, ceiling: Float?) = write({ it.copy(
        floorNits = floor, ceilingNits = ceiling) }) {
        put(KEY_FLOOR, floor?.toString())
        put(KEY_CEILING, ceiling?.toString())
    }

    fun setThermalGuard(on: Boolean) = write({ it.copy(thermalGuard = on) }) {
        put(KEY_THERMAL_GUARD, if (on) "1" else null)
    }

    fun resetAll() = write({ BrightnessSettings() }) {
        for (key in BRIGHTNESS_KEYS) put(key, null)
    }

    /** Shows the change at once; a null value removes the key, which means the build default. */
    private fun write(
        transform: (BrightnessSettings) -> BrightnessSettings,
        keys: MutableMap<String, String?>.() -> Unit,
    ) {
        viewModelScope.launch {
            writes.withLock {
                _state.update { it.copy(settings = transform(it.settings)) }
                val values = buildMap<String, String?>(keys)
                withContext(Dispatchers.IO) {
                    for ((key, value) in values) Settings.Secure.putString(resolver, key, value)
                }
            }
        }
    }
}
