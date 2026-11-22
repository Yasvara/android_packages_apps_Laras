/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.kait

import android.content.Context
import android.text.Html
import android.util.Log
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** A module version parsed from an online repository release tag (`"<versionCode>-<versionName>"`). */
data class RepoVersion(val versionCode: Long, val versionName: String) {
    fun upgradableOver(installedCode: Long, installedName: String): Boolean =
        versionCode > installedCode ||
            (versionCode == installedCode &&
                installedName.isNotBlank() &&
                installedName.replace(' ', '_') != versionName)

    companion object {
        fun parse(raw: String?): RepoVersion? {
            val text = raw?.takeIf { it.isNotBlank() } ?: return null
            val split = text.split('-', limit = 2)
            if (split.size < 2) return null
            val code = split[0].toLongOrNull() ?: return null
            return RepoVersion(code, split[1])
        }
    }
}

data class OnlineReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val downloadCount: Int,
)

data class OnlineRelease(
    val id: String,
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val descriptionText: String,
    val version: RepoVersion?,
    val apkAssets: List<OnlineReleaseAsset>,
)

data class OnlineModule(
    val packageName: String,
    val title: String,
    val summary: String,
    val authors: String,
    val repoUrl: String,
    val homepageUrl: String,
    val stargazerCount: Int,
    val updatedAt: String,
    val scope: List<String>,
    val latestVersion: RepoVersion?,
    val latestRelease: OnlineRelease?,
    val releases: List<OnlineRelease>,
    val readmeText: String,
)

data class OnlineCatalog(
    val modules: List<OnlineModule> = emptyList(),
    val loaded: Boolean = false,
    val fromCache: Boolean = false,
    val loadedAtMillis: Long = 0L,
)

/**
 * Fetches and caches the official Xposed Module Repository (`modules.lsposed.org`) without
 * third-party network dependencies.
 *
 * Uses `https://backup.modules.lsposed.org/modules.json` for the full catalogue (800+ modules)
 * and `module/<package>.json` on both mirrors for full release/README details, with a local disk
 * cache in [Context.getCacheDir] for instant offline browsing.
 */
class KaitOnlineRepository(private val context: Context) {

    private val cacheFile = File(context.cacheDir, "kait_online_modules.json")
    private val refreshMutex = Mutex()

    suspend fun fetchCatalog(forceNetwork: Boolean = false): OnlineCatalog =
        withContext(Dispatchers.IO) {
            if (!forceNetwork && cacheFile.isFile) {
                val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
                if (ageMs in 0..CACHE_FRESH_MS) {
                    parseCatalogText(cacheFile.readText())?.let { list ->
                        return@withContext OnlineCatalog(
                            modules = list,
                            loaded = true,
                            fromCache = false,
                            loadedAtMillis = cacheFile.lastModified(),
                        )
                    }
                }
            }

            refreshMutex.lock()
            try {
                for (base in LIST_MIRRORS) {
                    val raw = httpGet("${base}modules.json") ?: continue
                    val parsed = parseCatalogText(raw)
                    if (!parsed.isNullOrEmpty()) {
                        runCatching { cacheFile.writeText(raw) }
                        return@withContext OnlineCatalog(
                            modules = parsed,
                            loaded = true,
                            fromCache = false,
                            loadedAtMillis = System.currentTimeMillis(),
                        )
                    }
                }

                // Offline fallback to cached catalogue on disk.
                if (cacheFile.isFile) {
                    val cached = runCatching { parseCatalogText(cacheFile.readText()) }.getOrNull()
                    if (!cached.isNullOrEmpty()) {
                        return@withContext OnlineCatalog(
                            modules = cached,
                            loaded = true,
                            fromCache = true,
                            loadedAtMillis = cacheFile.lastModified(),
                        )
                    }
                }

                OnlineCatalog(modules = emptyList(), loaded = true, fromCache = false)
            } finally {
                refreshMutex.unlock()
            }
        }

    suspend fun fetchModuleDetail(packageName: String, fallback: OnlineModule?): OnlineModule? =
        withContext(Dispatchers.IO) {
            for (base in DETAIL_MIRRORS) {
                val raw = httpGet("${base}module/$packageName.json") ?: continue
                val detailed = runCatching { parseModuleObject(JSONObject(raw)) }.getOrNull()
                if (detailed != null) return@withContext detailed
            }
            fallback
        }

    private fun httpGet(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("User-Agent", "Yasvara-Kait/2.0 (Android 17)")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP ${conn.responseCode} from $urlString")
                return null
            }
            val stream: InputStream =
                if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                    GZIPInputStream(conn.inputStream)
                } else {
                    conn.inputStream
                }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching $urlString: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseCatalogText(jsonText: String): List<OnlineModule>? {
        return try {
            val arr = JSONArray(jsonText)
            val out = ArrayList<OnlineModule>(arr.length())
            val seen = HashSet<String>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optBoolean("hide", false)) continue
                val parsed = runCatching { parseModuleObject(obj) }.getOrNull() ?: continue
                if (parsed.releases.isEmpty() && parsed.latestRelease == null) continue
                if (seen.add(parsed.packageName)) {
                    out.add(parsed)
                }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing module catalog", e)
            null
        }
    }

    private fun parseModuleObject(obj: JSONObject): OnlineModule? {
        val pkg = obj.optString("name", "").trim()
        if (pkg.isEmpty()) return null
        val title = obj.optString("description", "").trim().ifEmpty { pkg }
        val summary = obj.optString("summary", "").trim()
        val repoUrl = obj.optString("url", "").trim()
            .ifEmpty { "https://github.com/Xposed-Modules-Repo/$pkg" }
        val homepageUrl = obj.optString("homepageUrl", "").trim()
        val stars = obj.optInt("stargazerCount", 0)
        val updatedAt = obj.optString("latestReleaseTime", "")
            .ifEmpty { obj.optString("pushedAt", obj.optString("updatedAt", "")) }

        // Collaborators & additionalAuthors
        val authorList = mutableListOf<String>()
        val collabs = obj.optJSONArray("collaborators")
        if (collabs != null) {
            for (i in 0 until collabs.length()) {
                val c = collabs.optJSONObject(i) ?: continue
                val n = c.optString("name", "").trim().ifEmpty { c.optString("login", "").trim() }
                if (n.isNotEmpty() && n !in authorList) authorList.add(n)
            }
        }
        val addAuthors = obj.optJSONArray("additionalAuthors")
        if (addAuthors != null) {
            for (i in 0 until addAuthors.length()) {
                val a = addAuthors.optJSONObject(i) ?: continue
                val n = a.optString("name", "").trim()
                if (n.isNotEmpty() && n !in authorList) authorList.add(n)
            }
        }

        // Scope list
        val scope = mutableListOf<String>()
        val scopeArr = obj.optJSONArray("scope")
        if (scopeArr != null) {
            for (i in 0 until scopeArr.length()) {
                val s = scopeArr.optString(i, "").trim()
                if (s.isNotEmpty()) scope.add(s)
            }
        }

        // Releases
        val releases = mutableListOf<OnlineRelease>()
        val relArr = obj.optJSONArray("releases")
        if (relArr != null) {
            for (i in 0 until relArr.length()) {
                val r = relArr.optJSONObject(i) ?: continue
                parseRelease(r, i)?.let(releases::add)
            }
        }

        val latestRel = releases.firstOrNull()
        val latestVer = RepoVersion.parse(obj.optString("latestRelease", "")) ?: latestRel?.version
        val readmeHtml = obj.optString("readmeHTML", "")
        val readmeText = if (readmeHtml.isNotBlank()) htmlToPlainText(readmeHtml) else ""

        return OnlineModule(
            packageName = pkg,
            title = title,
            summary = summary,
            authors = authorList.joinToString(", "),
            repoUrl = repoUrl,
            homepageUrl = homepageUrl,
            stargazerCount = stars,
            updatedAt = updatedAt.take(10),
            scope = scope,
            latestVersion = latestVer,
            latestRelease = latestRel,
            releases = releases,
            readmeText = readmeText,
        )
    }

    private fun parseRelease(r: JSONObject, index: Int): OnlineRelease? {
        val tag = r.optString("tagName", "").trim()
        val name = r.optString("name", "").trim().ifEmpty { tag.ifEmpty { "Release #${index + 1}" } }
        val id = r.optString("id", "").trim().ifEmpty { tag.ifEmpty { "rel-$index" } }
        val published = r.optString("publishedAt", r.optString("createdAt", "")).take(10)
        val descHtml = r.optString("descriptionHTML", "")
        val descText = if (descHtml.isNotBlank()) htmlToPlainText(descHtml) else ""

        val assets = mutableListOf<OnlineReleaseAsset>()
        val assetsArr = r.optJSONArray("releaseAssets")
        if (assetsArr != null) {
            for (i in 0 until assetsArr.length()) {
                val a = assetsArr.optJSONObject(i) ?: continue
                val assetName = a.optString("name", "").trim()
                val dlUrl = a.optString("downloadUrl", "").trim()
                val contentType = a.optString("contentType", "").trim()
                val isApk = dlUrl.isNotEmpty() && (
                    contentType == "application/vnd.android.package-archive" ||
                        assetName.endsWith(".apk", ignoreCase = true)
                    )
                if (isApk) {
                    assets.add(
                        OnlineReleaseAsset(
                            name = assetName.ifEmpty { "module.apk" },
                            downloadUrl = dlUrl,
                            sizeBytes = a.optLong("size", 0L),
                            downloadCount = a.optInt("downloadCount", 0),
                        )
                    )
                }
            }
        }
        return OnlineRelease(
            id = id,
            name = name,
            tagName = tag,
            publishedAt = published,
            descriptionText = descText,
            version = RepoVersion.parse(tag),
            apkAssets = assets,
        )
    }

    private fun htmlToPlainText(html: String): String {
        return runCatching {
            Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                .toString()
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }.getOrDefault("")
    }

    companion object {
        private const val TAG = "KaitOnlineRepo"
        private const val CACHE_FRESH_MS = 10 * 60 * 1000L // 10 minutes
        private val LIST_MIRRORS = listOf("https://backup.modules.lsposed.org/")
        private val DETAIL_MIRRORS = listOf(
            "https://backup.modules.lsposed.org/",
            "https://modules.lsposed.org/",
        )
    }
}
