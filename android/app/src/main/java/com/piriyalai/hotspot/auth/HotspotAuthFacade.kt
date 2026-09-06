package com.piriyalai.hotspot.auth

import android.content.Context
import android.net.ConnectivityManager
import android.webkit.WebView
import com.piriyalai.hotspot.network.LinkAddressResolver
import com.piriyalai.hotspot.network.NetworkClientFactory

object HotspotAuthFacade {
    @Volatile
    var webViewProvider: (() -> WebView)? = null

    fun login(
        context: Context,
        username: String,
        password: String,
        configuredPortalUrl: String,
        trustCert: Boolean
    ): LoginResult {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiNetwork = NetworkClientFactory.findWifiNetwork(context)
        if (wifiNetwork != null) {
            connectivityManager.bindProcessToNetwork(wifiNetwork)
        }

        try {
            return loginInternal(context, username, password, configuredPortalUrl, trustCert)
        } finally {
            connectivityManager.bindProcessToNetwork(null)
        }
    }

    private fun loginInternal(
        context: Context,
        username: String,
        password: String,
        configuredPortalUrl: String,
        trustCert: Boolean
    ): LoginResult {
        val httpClient = NetworkClientFactory.create(context, trustCert)
        val client = FortiGateAuthClient(listOf(configuredPortalUrl), httpClient)
        val clientIp = LinkAddressResolver.getClientIp(context)
        val gatewayIp = LinkAddressResolver.getGatewayIp(context)
        val errors = mutableListOf<String>()
        errors.add("Gateway: ${gatewayIp ?: "-"}")
        errors.add("Client IP: ${clientIp ?: "-"}")

        val trace = DiscoveryTrace()
        val intercepted = CaptiveRedirectFinder.findSession(httpClient, trace)
        if (intercepted != null) {
            errors.add("พบ magic จาก redirect: ${intercepted.loginPageUrl}")
            val result = client.loginWithSession(intercepted, username, password)
            if (result.success) {
                return result
            }
            errors.add(result.message)
        } else {
            errors.add("ยังไม่ถูก redirect จาก HTTP probe")
            errors.add(trace.toMessage())
        }

        val webView = webViewProvider?.invoke() ?: WebViewPortalLogin.createOffscreen(context)
        val webSession = runCatching {
            WebViewPortalLogin(webView).waitForSession()
        }.getOrElse { error ->
            errors.add("WebView: ${error.message}")
            null
        }

        if (webSession != null) {
            errors.add("พบ magic จาก WebView: ${webSession.loginPageUrl}")
            val result = client.loginWithSession(webSession, username, password)
            if (result.success) {
                return result
            }
            errors.add(result.message)
        } else {
            errors.add("WebView ไม่พบ /fgtauth?magic")
        }

        return LoginResult(false, errors.joinToString("\n"))
    }
}
