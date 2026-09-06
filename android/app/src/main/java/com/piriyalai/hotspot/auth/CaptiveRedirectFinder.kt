package com.piriyalai.hotspot.auth

import okhttp3.OkHttpClient
import okhttp3.Request

data class DiscoveryTrace(
    val steps: MutableList<String> = mutableListOf()
) {
    fun add(message: String) {
        steps.add(message)
    }

    fun toMessage(): String = steps.take(12).joinToString("\n")
}

/**
 * FortiGate only issues a magic token when an HTTP request is intercepted.
 * Opening /fgtauth without that token does not show a login form.
 */
object CaptiveRedirectFinder {
    private val probeUrls = listOf(
        "http://neverssl.com/",
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.gstatic.com/generate_204",
        "http://clients3.google.com/generate_204",
        "http://detectportal.firefox.com/canonical.html",
        "http://www.msftconnecttest.com/connecttest.txt",
        "http://captive.apple.com/hotspot-detect.html",
        "http://example.com/"
    )

    fun findSession(httpClient: OkHttpClient, trace: DiscoveryTrace = DiscoveryTrace()): FortiGateSession? {
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

        for (probeUrl in probeUrls.take(4)) {
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
