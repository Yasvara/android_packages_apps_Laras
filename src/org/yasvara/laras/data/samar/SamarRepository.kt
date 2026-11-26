/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.samar

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.ServiceManager
import com.android.server.yasvara.samar.IYasvaraSamar
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** "" is the global profile; a package name is that app's own profile. */
const val GLOBAL = ""

data class SamarPosition(
    val lat: Double,
    val lng: Double,
    val altitude: Double,
    val speed: Double,
    val heading: Double,
    val speedPreset: Speed,
    val hasPlace: Boolean,
)

data class SamarPlace(val index: Int, val name: String, val lat: Double, val lng: Double)

enum class Speed(val mps: Double) { WALK(1.4), RUN(3.0), BIKE(6.0), CAR(14.0) }

/** Ordinals are the service's mode numbers. */
enum class AppMode { FOLLOW, REAL, OWN }

/** Privacy layer bits, as the service stores them. */
enum class Layer(val bit: Int) { RADIO(1), BLE(2), MEDIA(4), LAN(8) }

data class LayerOverride(val mask: Int = 0, val value: Int = 0) {
    /** null: follows the global setting. */
    fun of(layer: Layer): Boolean? = if (mask and layer.bit == 0) null else value and layer.bit != 0
}

data class SamarApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val mode: AppMode,
    val layers: LayerOverride,
) {
    val custom: Boolean get() = mode != AppMode.FOLLOW || layers.mask != 0
}

/** Talks to "yasvara_samar". Calls are short binder transactions into system_server. */
class SamarRepository(private val context: Context) {
    private fun service(): IYasvaraSamar? = try {
        ServiceManager.getService(SERVICE)?.let(IYasvaraSamar.Stub::asInterface)
    } catch (e: RuntimeException) {
        null
    }

    private inline fun <T> call(fallback: T, block: (IYasvaraSamar) -> T): T = try {
        service()?.let(block) ?: fallback
    } catch (e: Exception) {
        fallback
    }

    val available: Boolean get() = service() != null

    fun isOn(): Boolean = call(false) { it.isGlobalOn }

    fun setOn(on: Boolean) = call(Unit) { it.setGlobalOn(on) }

    fun position(target: String = GLOBAL): SamarPosition? = call(null) { s ->
        s.getPosition(target)?.takeIf { it.size >= 7 }?.let {
            SamarPosition(
                lat = it[0],
                lng = it[1],
                altitude = it[2],
                speed = it[3],
                heading = it[4],
                speedPreset = Speed.entries.getOrElse(it[5].toInt()) { Speed.WALK },
                hasPlace = it[6] != 0.0,
            )
        }
    }

    /** Returns the jump speed in m/s the move implies, or NaN when refused. */
    fun setPlace(target: String, lat: Double, lng: Double): Double = call(Double.NaN) { it.setPlace(target, lat, lng) }

    fun setAltitude(target: String, altitude: Double) = call(Unit) { it.setAltitude(target, altitude) }

    fun setJoystick(target: String, x: Float, y: Float) = call(Unit) { it.setJoystick(target, x, y) }

    fun setSpeed(target: String, speed: Speed) = call(Unit) { it.setSpeedPreset(target, speed.ordinal) }

    fun setMode(pkg: String, mode: AppMode) = call(Unit) { it.setAppMode(pkg, mode.ordinal) }

    fun layers(target: String = GLOBAL): LayerOverride = call(LayerOverride()) {
        val v = it.getLayers(target)
        LayerOverride((v ushr 32).toInt(), v.toInt())
    }

    /** Global: [value] is the whole set. App: [mask] is every overridden bit; the rest follow global. */
    fun setLayers(target: String, mask: Int, value: Int) = call(Unit) { it.setLayers(target, mask, value) }

    fun parse(text: String, ref: SamarPosition? = null): Pair<Double, Double>? = call(null) { s ->
        s.parseLocation(text, ref?.lat ?: Double.NaN, ref?.lng ?: Double.NaN)
            ?.takeIf { it.size >= 2 }
            ?.let { it[0] to it[1] }
    }

    /**
     * Like [parse], and also follows a short maps link (maps.app.goo.gl), which carries no
     * coordinates until its redirect is read. Network; call off the main thread.
     */
    fun parseOrResolve(text: String, ref: SamarPosition? = null): Pair<Double, Double>? =
        parse(text, ref) ?: SHORT_LINK.find(text)?.value?.let(::expand)?.let { parse(it, ref) }

    private fun expand(link: String): String? {
        var url = link
        repeat(5) {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = 6000
                readTimeout = 6000
            }
            try {
                val next = conn.getHeaderField("Location") ?: return url.takeIf { it != link }
                url = java.net.URL(java.net.URL(url), next).toString()
            } catch (e: java.io.IOException) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return url
    }

    fun places(): List<SamarPlace> = call(emptyList()) { s ->
        val names = s.placeNames ?: return@call emptyList()
        val coords = s.placeCoords ?: return@call emptyList()
        names.indices.filter { it * 2 + 1 < coords.size }
            .map { SamarPlace(it, names[it], coords[it * 2], coords[it * 2 + 1]) }
    }

    fun savePlace(name: String, lat: Double, lng: Double): Boolean = call(false) { it.savePlace(name, lat, lng) }

    fun removePlace(index: Int): Boolean = call(false) { it.removePlace(index) }

    /** Apps that can ask for location, with what each is set to. */
    fun apps(): List<SamarApp> {
        val configured = call(emptyArray<String>()) { it.configuredApps ?: emptyArray() }
            .mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 4) return@mapNotNull null
                f[0] to (
                    AppMode.entries.getOrElse(f[1].toIntOrNull() ?: 0) { AppMode.FOLLOW } to
                        LayerOverride(f[2].toIntOrNull() ?: 0, f[3].toIntOrNull() ?: 0)
                    )
            }
            .toMap()
        val pm = context.packageManager
        return pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .filter { info ->
                val app = info.applicationInfo ?: return@filter false
                app.uid >= android.os.Process.FIRST_APPLICATION_UID &&
                    info.requestedPermissions.orEmpty().any { it in LOCATION_PERMISSIONS } &&
                    info.packageName != context.packageName
            }
            .map { info ->
                val app = info.applicationInfo!!
                val (mode, layers) = configured[info.packageName] ?: (AppMode.FOLLOW to LayerOverride())
                SamarApp(
                    packageName = info.packageName,
                    label = app.loadLabel(pm).toString(),
                    system = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                    mode = mode,
                    layers = layers,
                )
            }
            .sortedWith(compareByDescending<SamarApp> { it.custom }.thenBy { it.label.lowercase() })
    }

    companion object {
        const val SERVICE = "yasvara_samar"
        private val SHORT_LINK = Regex("""https?://(maps\.app\.goo\.gl|goo\.gl/maps)/\S+""")
        private val LOCATION_PERMISSIONS = setOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        /** Great-circle distance in metres. */
        fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_008.8
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = p2 - p1
            val dl = Math.toRadians(lng2 - lng1)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }

        fun format(lat: Double, lng: Double): String = "%.5f, %.5f".format(java.util.Locale.ROOT, lat, lng)

        fun formatDistance(metres: Double): String = when {
            metres < 1000 -> "%.0f m".format(metres)
            metres < 100_000 -> "%.1f km".format(metres / 1000)
            else -> "%,.0f km".format(metres / 1000)
        }
    }
}
