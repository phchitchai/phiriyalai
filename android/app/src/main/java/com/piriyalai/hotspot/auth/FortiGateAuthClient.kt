package com.piriyalai.hotspot.auth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class LoginResult(
    val success: Boolean,
    val message: String,
    val postUrl: String? = null,
    val portalUrl: String? = null
)

/**
 * FortiGate external captive portal client for login.piriyalaihotspot.com:1003.
 *
 * Session magic is in the redirect URL: /fgtauth?0601019c0b956a96
 */
class FortiGateAuthClient(
    private val portalCandidates: List<String>,
    private val httpClient: OkHttpClient
) {
    private val magicPattern = Pattern.compile(
        """name=["']magic["']\s+value=["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val magicPatternAlt = Pattern.compile(
        """value=["']([^"']+)["']\s+name=["']magic["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val redirPattern = Pattern.compile(
        """name=["']4Tredir["']\s+value=["']([^"']*)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val formActionPattern = Pattern.compile(
        """<form[^>]*action=["']([^"']+)["']""",
        Pattern.CASE_INSENSITIVE
    )

    private val captiveProbes = listOf(
        "http://connectivitycheck.gstatic.com/generate_204",
        "http://www.google.com/generate_204",
        "http://captive.apple.com/hotspot-detect.html",
        "http://www.msftconnecttest.com/connecttest.txt"
    )

    constructor(portalBaseUrl: String, httpClient: OkHttpClient) : this(
        portalCandidates = listOf(portalBaseUrl),
        httpClient = httpClient
    )

    fun loginWithSession(session: FortiGateSession, username: String, password: String): LoginResult {
        return postCredentials(session, username, password)
    }

    fun login(username: String, password: String): LoginResult {
        val errors = mutableListOf<String>()

        discoverSessionFromCaptivePortal()?.let { session ->
            errors.add("พบ portal: ${session.loginPageUrl}")
            val result = postCredentials(session, username, password)
            if (result.success) {
                return result
            }
            errors.add(result.message)
        }

        for (candidate in portalCandidates.distinct()) {
            val session = discoverSessionAt(candidate)
            if (session == null) {
                errors.add("$candidate → ไม่พบหน้า login")
                continue
            }

            errors.add("พบ session: ${session.loginPageUrl}")
            val result = postCredentials(session, username, password)
            if (result.success) {
                return result
            }
            errors.add(result.message)
        }

        return LoginResult(
            success = false,
            message = errors.take(8).joinToString("\n")
        )
    }

    private fun discoverSessionFromCaptivePortal(): FortiGateSession? {
        for (probeUrl in captiveProbes) {
            val session = runCatching {
                val request = Request.Builder()
                    .url(probeUrl)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    sessionFromResponse(response.request.url.toString(), response.body?.string() ?: "")
                }
            }.getOrNull()

            if (session != null) {
                return session
            }
        }
        return null
    }

    private fun discoverSessionAt(baseUrl: String): FortiGateSession? {
        val magicInUrl = FortiGateSessionParser.extractMagicFromUrl(baseUrl)
        if (magicInUrl != null) {
            val postUrl = FortiGateSessionParser.buildPostUrl(baseUrl)
            return FortiGateSession(baseUrl, magicInUrl, postUrl)
        }

        val urls = listOf(
            baseUrl,
            joinUrl(baseUrl, "fgtauth"),
            joinUrl(baseUrl, "login")
        ).distinct()

        for (url in urls) {
            val session = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    sessionFromResponse(response.request.url.toString(), response.body?.string() ?: "")
                }
            }.getOrNull()

            if (session != null) {
                return session
            }
        }
        return null
    }

    private fun sessionFromResponse(finalUrl: String, body: String): FortiGateSession? {
        val magicFromUrl = FortiGateSessionParser.extractMagicFromUrl(finalUrl)
        if (magicFromUrl != null) {
            return FortiGateSession(
                loginPageUrl = finalUrl,
                magic = magicFromUrl,
                postUrl = FortiGateSessionParser.buildPostUrl(finalUrl)
            )
        }

        if (!FortiGateSessionParser.isFortiGateLoginPage(body, finalUrl)) {
            return null
        }

        val magicFromHtml = extractMagic(body) ?: return null
        val postUrl = resolvePostUrl(finalUrl, finalUrl, body)
        return FortiGateSession(finalUrl, magicFromHtml, postUrl)
    }

    private fun postCredentials(session: FortiGateSession, username: String, password: String): LoginResult {
        val loginPage = runCatching { fetchPage(session.loginPageUrl) }.getOrNull()
        val redir = loginPage?.let { extractRedir(it) }

        val formBuilder = FormBody.Builder()
            .add("magic", session.magic)
            .add("username", username)
            .add("password", password)

        if (redir != null) {
            formBuilder.add("4Tredir", redir)
        }

        val postUrls = listOf(session.postUrl, session.loginPageUrl).distinct()
        var lastError = "POST failed"
        for (postUrl in postUrls) {
            val postRequest = Request.Builder()
                .url(postUrl)
                .post(formBuilder.build())
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", session.loginPageUrl)
                .build()

            val result = runCatching {
                httpClient.newCall(postRequest).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""
                    val success = response.isSuccessful &&
                        !responseBody.contains("auth_failed", ignoreCase = true) &&
                        !responseBody.contains("login failed", ignoreCase = true) &&
                        !responseBody.contains("invalid username", ignoreCase = true)

                    if (success) {
                        LoginResult(true, "Login สำเร็จ\n$postUrl", postUrl, session.loginPageUrl)
                    } else {
                        LoginResult(false, "Login ไม่สำเร็จ (HTTP ${response.code}) ที่ $postUrl")
                    }
                }
            }.getOrElse { error ->
                LoginResult(false, "$postUrl → ${error.message}")
            }

            if (result.success) {
                return result
            }
            lastError = result.message
        }
        return LoginResult(false, lastError)
    }

    private fun fetchPage(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            response.body?.string()
        }
    }

    internal fun extractMagic(html: String): String? {
        return magicPattern.matcher(html).let { matcher ->
            if (matcher.find()) matcher.group(1) else null
        } ?: magicPatternAlt.matcher(html).let { matcher ->
            if (matcher.find()) matcher.group(1) else null
        }
    }

    private fun extractRedir(html: String): String? {
        val matcher = redirPattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun resolvePostUrl(baseUrl: String, finalUrl: String, html: String): String {
        val formMatcher = formActionPattern.matcher(html)
        if (formMatcher.find()) {
            val action = formMatcher.group(1) ?: "/fgtauth"
            return resolveRelativeUrl(finalUrl, action)
        }
        return FortiGateSessionParser.buildPostUrl(finalUrl)
    }

    private fun resolveRelativeUrl(pageUrl: String, action: String): String {
        if (action.startsWith("http://") || action.startsWith("https://")) {
            return action
        }
        val page = URL(pageUrl)
        val path = if (action.startsWith("/")) action else "/$action"
        val portPart = if (page.port == -1) "" else ":${page.port}"
        return "${page.protocol}://${page.host}$portPart$path"
    }

    private fun joinUrl(base: String, path: String): String {
        return base.removeSuffix("/") + "/" + path.removePrefix("/")
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 PiriyalaiHotspot/1.0"

        fun containsMagicToken(html: String): Boolean {
            return html.contains("name=\"magic\"", ignoreCase = true) ||
                html.contains("name='magic'", ignoreCase = true)
        }

        fun createDefaultClient(trustPortalCertificate: Boolean): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            if (trustPortalCertificate) {
                builder.sslSocketFactory(
                    PortalSsl.trustAllSslSocketFactory(),
                    PortalSsl.trustAllManager()
                )
                builder.hostnameVerifier { _, _ -> true }
            }

            return builder.build()
        }
    }
}
