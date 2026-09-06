package com.piriyalai.hotspot.auth

import android.annotation.SuppressLint
import android.content.Context
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class WebViewPortalLogin(
    private val webView: WebView
) {
    @SuppressLint("SetJavaScriptEnabled")
    fun waitForSession(timeoutSeconds: Long = 25): FortiGateSession? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("Call from a background thread")
        }

        val latch = CountDownLatch(1)
        val found = AtomicReference<FortiGateSession?>(null)
        val done = AtomicBoolean(false)

        Handler(Looper.getMainLooper()).post {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"

            webView.webViewClient = object : WebViewClient() {
                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.proceed()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    capture(request?.url?.toString())
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    capture(url)
                    view?.evaluateJavascript(
                        "(function(){return document.documentElement ? document.documentElement.outerHTML : '';})();"
                    ) { html ->
                        val decoded = html?.let { JSONObject.quote(it).let { quoted -> html } }
                        val source = html?.trim('"')?.replace("\\u003C", "<")?.replace("\\n", "\n") ?: ""
                        FortiGateSessionParser.extractFgtauthUrl(source)?.let { capture(it) }
                        FortiGateSessionParser.extractMagicFromText(source)?.let { magic ->
                            if (url != null) {
                                capture("https://login.piriyalaihotspot.com:1003/fgtauth?$magic")
                            }
                        }
                    }
                }

                private fun capture(url: String?) {
                    if (url.isNullOrBlank() || done.get()) {
                        return
                    }
                    val magic = FortiGateSessionParser.extractMagicFromUrl(url)
                        ?: FortiGateSessionParser.extractMagicFromText(url)
                    if (magic != null && done.compareAndSet(false, true)) {
                        found.set(
                            FortiGateSession(
                                loginPageUrl = url,
                                magic = magic,
                                postUrl = FortiGateSessionParser.buildPostUrl(url)
                            )
                        )
                        latch.countDown()
                    }
                }
            }

            webView.loadUrl(START_URL)
        }

        latch.await(timeoutSeconds, TimeUnit.SECONDS)
        return found.get()
    }

    companion object {
        const val START_URL = "http://1.1.1.1/"

        fun createOffscreen(context: Context): WebView {
            return WebView(context.applicationContext)
        }
    }
}
