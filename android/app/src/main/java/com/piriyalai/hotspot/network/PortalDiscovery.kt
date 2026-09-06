package com.piriyalai.hotspot.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import com.piriyalai.hotspot.auth.FortiGateAuthClient
import okhttp3.OkHttpClient
import okhttp3.Request

object PortalDiscovery {
    private val connectivityProbes = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.google.com/generate_204",
        "http://captive.apple.com/hotspot-detect.html",
        "http://www.msftconnecttest.com/connecttest.txt"
    )

    fun buildCandidateUrls(context: Context, configuredUrl: String?): List<String> {
        val candidates = linkedSetOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            runCatching {
                connectivityManager::class.java
                    .getMethod("getCaptivePortalLoginUrl")
                    .invoke(connectivityManager) as? android.net.Uri
            }.getOrNull()?.toString()?.let { url ->
                candidates.add(normalizeBase(url))
            }
        }

        val dhcpGateway = GatewayResolver.getGatewayIp(context)
        val clientIp = GatewayResolver.getClientIp(context)

        val gatewayHosts = buildList {
            dhcpGateway?.let { add(it) }
            clientIp?.let { addAll(GatewayResolver.guessGatewayCandidates(it)) }
        }.distinct()

        for (host in gatewayHosts) {
            candidates.add("http://$host:1000/")
            candidates.add("https://$host:1003/")
            candidates.add("http://$host/")
            candidates.add("http://$host:80/")
        }

        configuredUrl?.takeIf { it.isNotBlank() }?.let { candidates.add(normalizeBase(it)) }

        // Port 1000 (HTTP) often works when 1003 (HTTPS public IP) times out on local WiFi.
        candidates.add("http://login.piriyalaihotspot.com:1000/")
        candidates.add("https://login.piriyalaihotspot.com:1003/")
        candidates.add("http://login.piriyalaihotspot.com/")

        return candidates.toList()
    }

    fun discoverWorkingPortal(
        httpClient: OkHttpClient,
        candidates: List<String>
    ): String? {
        for (probeUrl in connectivityProbes) {
            findPortalFromProbe(httpClient, probeUrl)?.let { return it }
        }

        for (candidate in candidates) {
            if (hasLoginPage(httpClient, candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun findPortalFromProbe(httpClient: OkHttpClient, probeUrl: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url(probeUrl)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val body = response.body?.string() ?: ""

                if (FortiGateAuthClient.containsMagicToken(body)) {
                    return normalizeBase(finalUrl)
                }

                if (!finalUrl.equals(probeUrl, ignoreCase = true) && isLikelyPortalUrl(finalUrl)) {
                    val portalBase = normalizeBase(finalUrl)
                    if (hasLoginPage(httpClient, portalBase)) {
                        return portalBase
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun hasLoginPage(httpClient: OkHttpClient, baseUrl: String): Boolean {
        val paths = listOf("", "fgtauth", "login")
        for (path in paths) {
            val url = if (path.isEmpty()) baseUrl else joinUrl(baseUrl, path)
            val found = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    FortiGateAuthClient.containsMagicToken(body)
                }
            }.getOrDefault(false)

            if (found) {
                return true
            }
        }
        return false
    }

    private fun isLikelyPortalUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("hotspot") ||
            lower.contains("fgtauth") ||
            lower.contains("login") ||
            lower.contains(":1000") ||
            lower.contains(":1003")
    }

    private fun normalizeBase(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return "$trimmed/"
    }

    private fun joinUrl(base: String, path: String): String {
        return base.removeSuffix("/") + "/" + path.removePrefix("/")
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 PiriyalaiHotspot/1.0"
}
