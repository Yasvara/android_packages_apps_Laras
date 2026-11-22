/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.yasvara.laras.data.connectivity

import android.content.Context
import android.net.wifi.SoftApConfiguration
import android.net.wifi.SoftApInfo
import android.net.wifi.WifiAvailableChannel
import android.net.wifi.WifiManager
import android.net.wifi.WifiScanner
import android.os.PersistableBundle
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import org.yasvara.laras.R
import org.yasvara.laras.data.ChoiceOption

/*
 * The Connectivity group's data: the Wi-Fi region the Wi-Fi service publishes, and
 * the experiments that go through public system APIs (hotspot width, network mode, signal bars).
 * Nothing here bypasses a country's rules: the firmware keeps DFS and power limits in every mode.
 */

private const val TAG = "LarasConnectivity"

/** Where the Wi-Fi service took the country from (YasvaraWifiTuning region state). */
enum class RegionSource(val id: String, val label: Int) {
    MANUAL("manual", R.string.wifi_source_manual),
    OVERRIDE("override", R.string.wifi_source_override),
    SIM("sim", R.string.wifi_source_sim),
    ROUTERS("routers", R.string.wifi_source_routers),
    LOCATION("location", R.string.wifi_source_location),
    LAST("last", R.string.wifi_source_last),
    DEFAULT("default", R.string.wifi_source_default),
    NONE("none", R.string.wifi_source_none);

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: NONE
    }
}

/** The country in use, or null in world mode, and its [source]. */
data class RegionState(val country: String?, val source: RegionSource) {
    companion object {
        fun parse(raw: String?): RegionState {
            val country = raw?.substringBefore(':', "")?.takeIf { it.length == 2 && it != "00" }
            return RegionState(country, RegionSource.of(raw?.substringAfter(':', "")))
        }
    }
}

/** Bands with at least one usable channel for the current country. */
data class BandAvailability(
    val sta5: Boolean,
    val sta6: Boolean,
    val sap5: Boolean,
    val sap6: Boolean,
    /** Channels as "36 40 …", for the strip's detail line. */
    val channels5: List<Int>,
    val channels6: List<Int>,
)

object WifiRegion {
    const val KEY_REGION = "yasvara_wifi_region"

    fun allowedBands(context: Context): BandAvailability? {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return null
        fun channels(band: Int, mode: Int): List<WifiAvailableChannel> = try {
            wifi.getAllowedChannels(band, mode)
        } catch (e: RuntimeException) {
            // Wi-Fi off, or the HAL can't answer yet.
            Log.w(TAG, "getAllowedChannels($band, $mode): ${e.message}")
            emptyList()
        }
        val sta5 = channels(WifiScanner.WIFI_BAND_5_GHZ_WITH_DFS, WifiAvailableChannel.OP_MODE_STA)
        val sta6 = channels(WifiScanner.WIFI_BAND_6_GHZ, WifiAvailableChannel.OP_MODE_STA)
        val sap5 = channels(WifiScanner.WIFI_BAND_5_GHZ_WITH_DFS, WifiAvailableChannel.OP_MODE_SAP)
        val sap6 = channels(WifiScanner.WIFI_BAND_6_GHZ, WifiAvailableChannel.OP_MODE_SAP)
        return BandAvailability(
            sta5.isNotEmpty(), sta6.isNotEmpty(), sap5.isNotEmpty(), sap6.isNotEmpty(),
            sta5.map { it.channelNumber() }.distinct().sorted(),
            sta6.map { it.channelNumber() }.distinct().sorted(),
        )
    }

    private fun WifiAvailableChannel.channelNumber(): Int =
        android.net.wifi.ScanResult.convertFrequencyMhzToChannelIfSupported(frequencyMhz)

    /** Writes the manual region; null returns to automatic detection. */
    fun setManual(context: Context, country: String?) {
        Settings.Global.putString(context.contentResolver, KEY_REGION, country ?: "auto")
    }
}

/** The hotspot's widest channel (SoftApConfiguration max channel bandwidth). */
enum class HotspotWidth(override val value: Int, override val label: Int) : ChoiceOption {
    AUTO(SoftApInfo.CHANNEL_WIDTH_AUTO, R.string.option_auto),
    W80(SoftApInfo.CHANNEL_WIDTH_80MHZ, R.string.width_80),
    W160(SoftApInfo.CHANNEL_WIDTH_160MHZ, R.string.width_160),
    W320(SoftApInfo.CHANNEL_WIDTH_320MHZ, R.string.width_320),
}

object Hotspot {
    fun width(context: Context): HotspotWidth {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return HotspotWidth.AUTO
        return try {
            val raw = wifi.softApConfiguration.maxChannelBandwidth
            HotspotWidth.entries.firstOrNull { it.value == raw } ?: HotspotWidth.AUTO
        } catch (e: RuntimeException) {
            Log.w(TAG, "softApConfiguration: ${e.message}")
            HotspotWidth.AUTO
        }
    }

    /** Applies [width]; a running hotspot restarts on its own when the change needs it. */
    fun setWidth(context: Context, width: HotspotWidth): Boolean {
        val wifi = context.getSystemService(WifiManager::class.java) ?: return false
        return try {
            val config = SoftApConfiguration.Builder(wifi.softApConfiguration)
                .setMaxChannelBandwidth(width.value)
                .build()
            wifi.setSoftApConfiguration(config)
        } catch (e: RuntimeException) {
            Log.w(TAG, "setMaxChannelBandwidth(${width.value}): ${e.message}")
            false
        }
    }
}

/** Which radio technologies the modem may use (TelephonyManager allowed network types, user reason). */
enum class NetworkMode(override val value: Int, override val label: Int, val risky: Boolean) : ChoiceOption {
    AUTO(0, R.string.net_mode_auto, false),
    NR_LTE(1, R.string.net_mode_nr_lte, false),
    LTE(2, R.string.net_mode_lte, true),
    NR_ONLY(3, R.string.net_mode_nr_only, true);

    fun mask(supported: Long): Long = when (this) {
        AUTO -> supported
        NR_LTE -> supported and (BITS_NR or BITS_LTE)
        LTE -> supported and BITS_LTE
        NR_ONLY -> supported and BITS_NR
    }

    companion object {
        private const val BITS_NR = TelephonyManager.NETWORK_TYPE_BITMASK_NR
        private const val BITS_LTE =
            TelephonyManager.NETWORK_TYPE_BITMASK_LTE or TelephonyManager.NETWORK_TYPE_BITMASK_LTE_CA
    }
}

object Cellular {
    private const val KEY_NR_BARS = "yasvara_nr_honest_bars"

    /**
     * NR SS-RSRP bars on the same scale as LTE's (AOSP defaults put −93 dBm at one bar while LTE
     * shows four at −90 dBm). Display only.
     */
    private val HONEST_NR_RSRP = intArrayOf(-120, -110, -100, -90)

    fun subId(): Int = SubscriptionManager.getDefaultDataSubscriptionId()

    fun telephony(context: Context): TelephonyManager? {
        val sub = subId()
        if (!SubscriptionManager.isValidSubscriptionId(sub)) return null
        return context.getSystemService(TelephonyManager::class.java)?.createForSubscriptionId(sub)
    }

    fun mode(context: Context): NetworkMode? {
        val tm = telephony(context) ?: return null
        return try {
            val supported = tm.supportedRadioAccessFamily
            val allowed = tm.getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER,
            ) and supported
            NetworkMode.entries.firstOrNull { it != NetworkMode.AUTO && it.mask(supported) == allowed }
                ?: NetworkMode.AUTO
        } catch (e: RuntimeException) {
            Log.w(TAG, "allowed network types: ${e.message}")
            null
        }
    }

    fun setMode(context: Context, mode: NetworkMode): Boolean {
        val tm = telephony(context) ?: return false
        return try {
            val mask = mode.mask(tm.supportedRadioAccessFamily)
            if (mask == 0L) return false
            tm.setAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, mask,
            )
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "setAllowedNetworkTypes($mode): ${e.message}")
            false
        }
    }

    fun honestBars(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, KEY_NR_BARS, 0) == 1

    fun setHonestBars(context: Context, on: Boolean): Boolean {
        val sub = subId()
        val ccm = context.getSystemService(CarrierConfigManager::class.java) ?: return false
        if (!SubscriptionManager.isValidSubscriptionId(sub)) return false
        return try {
            val bundle = if (on) {
                PersistableBundle().apply {
                    putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY, HONEST_NR_RSRP)
                    putInt(
                        CarrierConfigManager.KEY_PARAMETERS_USE_FOR_5G_NR_SIGNAL_BAR_INT,
                        CellSignalStrengthNr.USE_SSRSRP,
                    )
                }
            } else {
                null // Drops Laras's override; the carrier's own config applies again.
            }
            ccm.overrideConfig(sub, bundle, true)
            Settings.Global.putInt(context.contentResolver, KEY_NR_BARS, if (on) 1 else 0)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "overrideConfig: ${e.message}")
            false
        }
    }
}

/** A snapshot of the serving cell, from ServiceState and SignalStrength (no location access). */
data class CellInfo(
    val networkType: String,
    val lteBand: Int?,
    val channel: Int,
    val nrFrequencyMhz: Int?,
    val bandwidthsKhz: List<Int>,
    val lteRsrp: Int?,
    val lteRsrq: Int?,
    val lteSinr: Int?,
    val nrRsrp: Int?,
    val nrRsrq: Int?,
    val nrSinr: Int?,
) {
    val aggregated: Boolean get() = bandwidthsKhz.size > 1

    companion object {
        private const val UNAVAILABLE = Int.MAX_VALUE

        fun of(tm: TelephonyManager, service: ServiceState?, signal: SignalStrength?): CellInfo {
            val lte = signal?.getCellSignalStrengths(CellSignalStrengthLte::class.java)?.firstOrNull()
            val nr = signal?.getCellSignalStrengths(CellSignalStrengthNr::class.java)?.firstOrNull()
            val channel = service?.channelNumber ?: -1
            val type = try {
                TelephonyManager.getNetworkTypeName(tm.dataNetworkType)
            } catch (e: SecurityException) {
                "?"
            }
            val isNr = type == "NR"
            return CellInfo(
                networkType = type,
                lteBand = if (!isNr && channel >= 0) lteBand(channel) else null,
                channel = channel,
                nrFrequencyMhz = if (isNr && channel >= 0) nrFrequencyMhz(channel) else null,
                bandwidthsKhz = service?.cellBandwidths?.toList().orEmpty().filter { it > 0 },
                lteRsrp = lte?.rsrp.valid(),
                lteRsrq = lte?.rsrq.valid(),
                lteSinr = lte?.rssnr.valid(),
                nrRsrp = nr?.ssRsrp.valid(),
                nrRsrq = nr?.ssRsrq.valid(),
                nrSinr = nr?.ssSinr.valid(),
            )
        }

        private fun Int?.valid(): Int? = this?.takeIf { it != UNAVAILABLE }

        /** E-UTRA band of a downlink EARFCN (3GPP TS 36.101 table 5.7.3-1), common bands only. */
        fun lteBand(earfcn: Int): Int? = LTE_BANDS.firstOrNull { earfcn in it.second }?.first

        /** Centre frequency of an NR-ARFCN (3GPP TS 38.104 §5.4.2.1). */
        fun nrFrequencyMhz(arfcn: Int): Int = when {
            arfcn < 600_000 -> arfcn / 200
            arfcn < 2_016_667 -> 3000 + (arfcn - 600_000) * 15 / 1000
            else -> 24250 + (arfcn - 2_016_667) * 60 / 1000
        }

        private val LTE_BANDS = listOf(
            1 to 0..599, 2 to 600..1199, 3 to 1200..1949, 4 to 1950..2399, 5 to 2400..2649,
            7 to 2750..3449, 8 to 3450..3799, 12 to 5010..5179, 13 to 5180..5279,
            14 to 5280..5379, 17 to 5730..5849, 18 to 5850..5999, 19 to 6000..6149,
            20 to 6150..6449, 25 to 8040..8689, 26 to 8690..9039, 28 to 9210..9659,
            38 to 37750..38249, 39 to 38250..38649, 40 to 38650..39649, 41 to 39650..41589,
            42 to 41590..43589, 66 to 66436..67335, 71 to 68586..68935,
        )
    }
}
