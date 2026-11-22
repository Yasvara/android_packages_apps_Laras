/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.saring

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.ServiceManager
import android.os.UserHandle
import android.telephony.AccessNetworkConstants
import android.telephony.NetworkRegistrationInfo
import android.telephony.TelephonyManager
import com.android.server.yasvara.saring.IYasvaraSaring
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class SaringStats(
    val checked: Long = 0,
    val refused: Long = 0,
    val names: Long = 0,
    val pausedUntil: Long = 0,
    /** Wall time of the last successful update, 0 when never. */
    val updated: Long = 0,
    val cacheHits: Long = 0,
    val poisonBlocked: Long = 0,
)

data class RadioCeirStatus(
    val operator: String = "",
    val rejectCause: Int = 0,
    val inService: Boolean = false,
)

data class CategoryState(val category: Category, val on: Boolean, val rules: Long, val skipped: Long)

/** A name the owner decided on. */
data class OwnerRule(val name: String, val allow: Boolean)

data class SaringApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val exempt: Boolean,
    val refused: Int,
)

data class Refusal(
    val time: Long,
    val uid: Int,
    val count: Int,
    val name: String,
    /** Empty for a system uid. */
    val packageName: String,
)

/** Talks to "yasvara_saring" and keeps the downloaded lists. Every call blocks; stay off the main thread. */
class SaringRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("saring", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "saring")

    private fun service(): IYasvaraSaring? = try {
        ServiceManager.getService(SERVICE)?.let(IYasvaraSaring.Stub::asInterface)
    } catch (e: RuntimeException) {
        null
    }

    val available: Boolean get() = service() != null

    fun layer(layer: String): Boolean = try {
        service()?.getLayer(layer) == true
    } catch (e: Exception) {
        false
    }

    fun setLayer(layer: String, on: Boolean) {
        val service = service() ?: return
        try {
            if (layer == BROWSER && on) service.setList("browsers", browsers().toTypedArray())
            service.setLayer(layer, on)
        } catch (e: Exception) {
            // The screen reloads and shows what the service holds.
        }
    }

    fun pause(seconds: Int) {
        try {
            service()?.pause(seconds)
        } catch (e: Exception) {
        }
    }

    fun stats(): SaringStats = try {
        val s = service()?.stats
        if (s == null) {
            SaringStats()
        } else {
            SaringStats(
                checked = s[0],
                refused = s[1],
                names = s[2],
                pausedUntil = s[4],
                updated = prefs.getLong("updated", 0),
                cacheHits = if (s.size >= 6) s[5] else 0L,
                poisonBlocked = if (s.size >= 7) s[6] else 0L,
            )
        }
    } catch (e: Exception) {
        SaringStats()
    }

    /** Live 3GPP NAS registration reject cause from TelephonyManager for CEIR visibility. */
    fun ceirStatus(): RadioCeirStatus = try {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val ss = tm?.serviceState ?: return RadioCeirStatus()
        val ps = ss.getNetworkRegistrationInfo(
            NetworkRegistrationInfo.DOMAIN_PS,
            AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
        )
        val cs = ss.getNetworkRegistrationInfo(
            NetworkRegistrationInfo.DOMAIN_CS,
            AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
        )
        val cause = when {
            ps != null && ps.rejectCause != 0 -> ps.rejectCause
            cs != null && cs.rejectCause != 0 -> cs.rejectCause
            else -> 0
        }
        val registered = (ps?.isRegistered == true) || (cs?.isRegistered == true)
        RadioCeirStatus(
            operator = ss.operatorAlphaLong.orEmpty().ifEmpty { ss.operatorAlphaShort.orEmpty() },
            rejectCause = cause,
            inService = registered,
        )
    } catch (e: Exception) {
        RadioCeirStatus()
    }

    fun categories(): List<CategoryState> = Category.entries.map { c ->
        CategoryState(
            c,
            prefs.getBoolean("on:" + c.id, c.default),
            c.sources.sumOf { prefs.getLong("rules:" + it.id, 0) },
            c.sources.sumOf { prefs.getLong("skipped:" + it.id, 0) },
        )
    }

    fun setCategory(category: Category, on: Boolean) {
        prefs.edit().putBoolean("on:" + category.id, on).apply()
    }

    fun ownerRules(): List<OwnerRule> = prefs.getStringSet("owner", emptySet()).orEmpty()
        .map { OwnerRule(it.drop(1), it.startsWith("+")) }
        .sortedBy { it.name }

    /** Stores the owner's decision on a name; null forgets it. Returns false for a bad name. */
    fun setOwnerRule(name: String, allow: Boolean?): Boolean {
        val clean = name.trim().lowercase().removePrefix("*.").removeSuffix(".")
        if (!NAME.matches(clean)) return false
        val rules = prefs.getStringSet("owner", emptySet()).orEmpty()
            .filterTo(HashSet()) { it.drop(1) != clean }
        if (allow != null) rules += (if (allow) "+" else "-") + clean
        prefs.edit().putStringSet("owner", rules).apply()
        return true
    }

    fun sites(): List<String> = try {
        service()?.getList("popups")?.toList().orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    /** Sites that may open windows and ask to notify in a managed browser. */
    fun setSites(sites: List<String>) {
        val service = service() ?: return
        try {
            service.setList("popups", sites.toTypedArray())
            service.setList("notifications", sites.toTypedArray())
        } catch (e: Exception) {
        }
    }

    fun log(): List<Refusal> {
        val lines = try {
            service()?.log.orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        val pm = context.packageManager
        val owners = HashMap<Int, String>()
        return lines.mapNotNull { line ->
            val p = line.split('\t', limit = 4)
            if (p.size < 4) return@mapNotNull null
            val uid = p[1].toIntOrNull() ?: return@mapNotNull null
            val pkg = owners.getOrPut(uid) {
                if (UserHandle.getAppId(uid) < Process.FIRST_APPLICATION_UID) ""
                else pm.getPackagesForUid(uid)?.firstOrNull().orEmpty()
            }
            Refusal(p[0].toLongOrNull() ?: 0, uid, p[2].toIntOrNull() ?: 1, p[3], pkg)
        }
    }

    fun clearLog() {
        try {
            service()?.clearLog()
        } catch (e: Exception) {
        }
    }

    /** Every app that may use the network. */
    fun apps(log: List<Refusal>): List<SaringApp> {
        val pm = context.packageManager
        val exempt = try {
            service()?.getList("exempt")?.toSet().orEmpty()
        } catch (e: Exception) {
            emptySet()
        }
        val refused = log.groupingBy { it.packageName }.fold(0) { sum, r -> sum + r.count }
        return pm.getInstalledApplications(0)
            .filter { pm.checkPermission(Manifest.permission.INTERNET, it.packageName) == PackageManager.PERMISSION_GRANTED }
            .map {
                SaringApp(
                    it.packageName,
                    it.loadLabel(pm).toString(),
                    it.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    it.packageName in exempt,
                    refused[it.packageName] ?: 0,
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun setExempt(packageName: String, exempt: Boolean) {
        val service = service() ?: return
        try {
            val now = service.getList("exempt").toMutableSet()
            if (exempt) now += packageName else now -= packageName
            service.setList("exempt", now.toTypedArray())
        } catch (e: Exception) {
        }
    }

    /** Browsers that read managed configuration, which is every Chromium build. */
    private fun browsers(): List<String> {
        val pm = context.packageManager
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return pm.queryIntentActivities(view, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { pkg ->
                try {
                    pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                        .metaData?.containsKey("android.content.APP_RESTRICTIONS") == true
                } catch (e: PackageManager.NameNotFoundException) {
                    false
                }
            }
    }

    /**
     * Compiles the enabled lists and the owner's rules and installs the table. With
     * [download], fetches what changed first. Returns the names in use, or -1.
     */
    @Synchronized
    fun update(download: Boolean): Long {
        val service = service() ?: return -1
        dir.mkdirs()
        val inputs = ArrayList<String>()
        val ids = ArrayList<String>()
        for (state in categories().filter { it.on }) {
            for (source in state.category.sources) {
                val file = File(dir, source.id + ".txt")
                if (download) fetch(source, file)
                if (file.isFile) {
                    inputs += file.path
                    ids += source.id
                }
            }
        }
        val owner = File(dir, "owner.txt")
        owner.writeText(
            ownerRules().joinToString("\n") {
                if (it.allow) "@@||${it.name}^" else "||${it.name}^\$important"
            },
        )
        inputs += owner.path

        val out = File(context.cacheDir, "names.bin")
        val counts = SaringNative.compile(inputs.toTypedArray(), out.path, System.currentTimeMillis() / 1000)
            ?: return -1
        val edit = prefs.edit()
        for (source in Category.entries.flatMap { it.sources }) {
            edit.remove("rules:" + source.id).remove("skipped:" + source.id)
        }
        ids.forEachIndexed { i, id ->
            val at = 1 + i * SaringNative.COUNTS
            edit.putLong("rules:$id", counts[at] + counts[at + 1] + counts[at + 2] + counts[at + 3] + counts[at + 4])
            edit.putLong("skipped:$id", counts[at + 5] + counts[at + 6])
        }
        return try {
            val names = ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY).use(service::install)
            if (names >= 0 && download) edit.putLong("updated", System.currentTimeMillis())
            names
        } catch (e: Exception) {
            -1
        } finally {
            edit.apply()
            out.delete()
        }
    }

    /** Leaves the previous copy in place when the server has nothing new or cannot be reached. */
    private fun fetch(source: Source, file: File) {
        val tag = File(dir, source.id + ".etag")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                if (file.isFile && tag.isFile) setRequestProperty("If-None-Match", tag.readText())
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return
            val incoming = File(dir, source.id + ".tmp")
            var total = 0L
            connection.inputStream.use { input ->
                incoming.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_LIST_BYTES) throw IllegalStateException("list too large")
                        output.write(buffer, 0, n)
                    }
                }
            }
            if (total > 0 && incoming.renameTo(file)) {
                connection.getHeaderField("ETag")?.let(tag::writeText) ?: tag.delete()
            }
        } catch (e: Exception) {
            // Offline or a broken mirror: the stored copy stays in use.
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val SERVICE = "yasvara_saring"
        const val NAMES = "names"
        const val BROWSER = "browser"
        const val WEB = "web"
        const val DNS_ENCRYPTED = "dns_encrypted"
        const val SAFESEARCH = "safesearch"
        const val RADIO_GUARD = "radio_guard"
        const val BOOST = "boost"
        private const val MAX_LIST_BYTES = 64L * 1024 * 1024
        private val NAME = Regex("^(?=.{4,253}$)([a-z0-9_]([a-z0-9_-]{0,61}[a-z0-9_])?\\.)+[a-z][a-z0-9-]{1,62}$")
    }
}
