package com.piriyalai.hotspot.auth

import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoveryTrace(
    val steps: MutableList<String> = mutableListOf()
) {
    fun add(message: String) {
        steps.add(message)
    }

    fun toMessage(): String = steps.take(16).joinToString("\n")
}

/**
 * FortiGate issues magic only when an HTTP request is intercepted.
 * School DNS often blocks hostnames like neverssl.com, so probes use raw IPs first.
 */
object CaptiveRedirectFinder {
    private val ipProbes = listOf(
        "http://1.1.1.1/",
        "http://1.1.1.1/generate_204",
        "http://8.8.8.8/",
        "http://8.8.8.8/generate_204",
        "http://1.0.0.1/",
        "http://9.9.9.9/"
    )

    private val hostnameProbes = listOf(
        "http://login.piriyalaihotspot.com:1003/",
        "https://login.piriyalaihotspot.com:1003/fgtauth",
        "http://login.piriyalaihotspot.com:1000/",
        "http://neverssl.com/",
        "http://connectivitycheck.gstatic.com/generate_204"
    )

    fun buildProbeUrls(gatewayIp: String?): List<String> {
        val urls = linkedSetOf<String>()
        urls.addAll(ipProbes)
        if (!gatewayIp.isNullOrBlank()) {
            urls.add("http://$gatewayIp/")
            urls.add("http://$gatewayIp:1000/")
            urls.add("http://$gatewayIp:1003/")
        }
        urls.add("http://172.17.0.1/")
        urls.addAll(hostnameProbes)
        return urls.toList()
    }

    fun findSession(
        httpClient: OkHttpClient,
        gatewayIp: String? = null,
        trace: DiscoveryTrace = DiscoveryTrace()
    ): FortiGateSession? {
        val probeUrls = buildProbeUrls(gatewayIp)
        val noFollowClient = httpClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        for (probeUrl in probeUrls) {
            val session = inspect(noFollowClient, probeUrl, trace, followOnce = true)
            if (session != null) {
                return session
            }
        }

        val followClient = httpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        for (probeUrl in ipProbes.take(3)) {
            val session = inspect(followClient, probeUrl, trace, followOnce = false)
            if (session != null) {
                return session
            }
        }
        return null
    }

    private fun inspect(
        client: OkHttpClient,
        url: String,
        trace: DiscoveryTrace,
        followOnce: Boolean
    ): FortiGateSession? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                val location = response.header("Location")
                val body = response.body?.string() ?: ""
                val snippet = body.replace("\n", " ").take(80)

                trace.add("${response.code} $url → $finalUrl")
                if (!location.isNullOrBlank()) {
                    trace.add("Location: $location")
                }
                if (snippet.isNotBlank()) {
                    trace.add("body: $snippet")
                }

                sessionFrom(finalUrl, location, body)
                    ?: if (followOnce && !location.isNullOrBlank()) {
                        inspect(client, location, trace, followOnce = false)
                    } else {
                        null
                    }
            }
        }.getOrElse { error ->
            trace.add("fail $url → ${error.message}")
            null
        }
    }

    private fun sessionFrom(finalUrl: String, location: String?, body: String): FortiGateSession? {
        val candidates = listOfNotNull(finalUrl, location, FortiGateSessionParser.extractFgtauthUrl(body))
        for (candidate in candidates) {
            val magic = FortiGateSessionParser.extractMagicFromUrl(candidate)
                ?: FortiGateSessionParser.extractMagicFromText(candidate)
            if (magic != null) {
                val loginUrl = if (candidate.startsWith("http")) candidate else finalUrl
                return FortiGateSession(
                    loginPageUrl = loginUrl,
                    magic = magic,
                    postUrl = FortiGateSessionParser.buildPostUrl(loginUrl)
                )
            }
        }

        FortiGateSessionParser.extractMagicFromText(body)?.let { magic ->
            val loginUrl = FortiGateSessionParser.extractFgtauthUrl(body) ?: finalUrl
            return FortiGateSession(
                loginPageUrl = loginUrl,
                magic = magic,
                postUrl = FortiGateSessionParser.buildPostUrl(loginUrl)
            )
        }
        return null
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 PiriyalaiHotspot/1.0"
}
