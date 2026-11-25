/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.yasvara.laras.data.root.RootRepository
import java.io.ByteArrayInputStream

/**
 * Sandboxed WebUI X host for Akar modules (`<mod>/webroot/index.html`).
 *
 * Serves static assets from a virtual HTTPS origin (`https://mui.kernelsu.org/`) via
 * `YasvaraRootService.execModuleShell` (so `/data/misc/yasvara_root/modules` stays 0700 root:root)
 * and injects the standard `window.ksu` JavaScript bridge (`exec`, `fullScreen`, `toast`,
 * `moduleInfo`, `listPackages`).
 */
class AkarWebUiActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var webView: WebView
    private lateinit var repo: RootRepository
    private var moduleId: String = ""
    private var moduleName: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        moduleId = intent.getStringExtra(EXTRA_MODULE_ID).orEmpty()
        moduleName = intent.getStringExtra(EXTRA_MODULE_NAME).orEmpty().ifBlank { moduleId }
        if (!isSafeId(moduleId)) {
            finish()
            return
        }
        title = moduleName
        repo = RootRepository(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            addJavascriptInterface(KsuJsBridge(), "ksu")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val uri = request.url
                    if (uri.host != VIRTUAL_HOST) return null
                    val rawPath = (uri.path ?: "/index.html").removePrefix("/")
                        .ifBlank { "index.html" }
                    if (rawPath.contains("..") || rawPath.contains('\\') || rawPath.contains('\'')) {
                        return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    val res = runBlocking(Dispatchers.IO) {
                        repo.execModuleShell(moduleId, "base64 \"webroot/$rawPath\"")
                    }
                    if (res.exitCode != 0) {
                        return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                    val bytes = runCatching {
                        Base64.decode(res.output.replace("\n", "").replace("\r", ""), Base64.DEFAULT)
                    }.getOrNull() ?: return null
                    val ext = rawPath.substringAfterLast('.', "").lowercase()
                    val mime = when (ext) {
                        "html", "htm" -> "text/html"
                        "js", "mjs" -> "application/javascript"
                        "css" -> "text/css"
                        "json" -> "application/json"
                        "svg" -> "image/svg+xml"
                        "png" -> "image/png"
                        "jpg", "jpeg" -> "image/jpeg"
                        "webp" -> "image/webp"
                        "wasm" -> "application/wasm"
                        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
                    }
                    return WebResourceResponse(mime, "utf-8", ByteArrayInputStream(bytes))
                }
            }
        }
        setContentView(webView)
        webView.loadUrl("https://$VIRTUAL_HOST/index.html")
    }

    override fun onDestroy() {
        scope.cancel()
        webView.destroy()
        super.onDestroy()
    }

    private fun isSafeId(id: String): Boolean =
        id.isNotEmpty() && id.length <= 64 && !id.contains("..") &&
            id.matches(Regex("^[a-zA-Z][a-zA-Z0-9._-]+$"))

    inner class KsuJsBridge {
        @JavascriptInterface
        fun exec(cmd: String, optionsJson: String?, callbackFunc: String?) {
            val cb = callbackFunc?.takeIf { it.matches(Regex("^[a-zA-Z0-9_$.]+$")) } ?: return
            scope.launch {
                val out = repo.execModuleShell(moduleId, cmd)
                val jsStdout = JSONObject.quote(out.output)
                val jsStderr = JSONObject.quote("")
                webView.evaluateJavascript("window.$cb(${out.exitCode}, $jsStdout, $jsStderr);", null)
            }
        }

        @JavascriptInterface
        fun toast(msg: String) {
            scope.launch { Toast.makeText(this@AkarWebUiActivity, msg, Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun fullScreen(enable: Boolean) {
            scope.launch {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (enable) {
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                } else {
                    View.SYSTEM_UI_FLAG_VISIBLE
                }
            }
        }

        @JavascriptInterface
        fun moduleInfo(): String = JSONObject().apply {
            put("id", moduleId)
            put("name", moduleName)
            put("moduleDir", "/data/adb/modules/$moduleId")
        }.toString()

        @JavascriptInterface
        fun listPackages(type: String?): String {
            val pm = packageManager
            val arr = JSONArray()
            val sysMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            for (ai in pm.getInstalledApplications(0)) {
                val isSys = (ai.flags and sysMask) != 0
                val keep = when (type) {
                    "user" -> !isSys
                    "system" -> isSys
                    else -> true
                }
                if (keep) arr.put(ai.packageName)
            }
            return arr.toString()
        }
    }

    companion object {
        const val EXTRA_MODULE_ID = "module_id"
        const val EXTRA_MODULE_NAME = "module_name"
        private const val VIRTUAL_HOST = "mui.kernelsu.org"
    }
}
