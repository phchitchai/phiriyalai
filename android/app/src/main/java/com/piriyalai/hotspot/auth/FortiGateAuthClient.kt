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
    val postUrl: String? = null
)

/**
 * FortiGate / FortiAP captive portal client.
 *
 * Flow:
 * 1. GET the portal page (or follow redirects) to obtain a session "magic" token.
 * 2. POST username + password + magic to /fgtauth (or the form action URL).
 */
class FortiGateAuthClient(
    private val portalBaseUrl: String,
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

    fun login(username: String, password: String): LoginResult {
        val normalizedBase = normalizeBaseUrl(portalBaseUrl)
        val loginPageResult = fetchLoginPage(normalizedBase)
            ?: return LoginResult(false, "ไม่สามารถเปิดหน้า login ได้ กรุณาเชื่อมต่อ WiFi โรงเรียนก่อน")

        val magic = extractMagic(loginPageResult.body)
            ?: return LoginResult(
                false,
                "ไม่พบ magic token ในหน้า login (อาจไม่ใช่ FortiGate portal)"
            )

        val redir = extractRedir(loginPageResult.body)
        val postUrl = resolvePostUrl(normalizedBase, loginPageResult.finalUrl, loginPageResult.body)

        val formBuilder = FormBody.Builder()
            .add("magic", magic)
            .add("username", username)
            .add("password", password)

        if (redir != null) {
            formBuilder.add("4Tredir", redir)
        }

        val postRequest = Request.Builder()
            .url(postUrl)
            .post(formBuilder.build())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        httpClient.newCall(postRequest).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            val success = response.isSuccessful &&
                !responseBody.contains("auth_failed", ignoreCase = true) &&
                !responseBody.contains("login failed", ignoreCase = true) &&
                !responseBody.contains("invalid", ignoreCase = true)

            return if (success) {
                LoginResult(true, "Login สำเร็จ", postUrl)
            } else {
                LoginResult(false, "Login ไม่สำเร็จ (HTTP ${response.code})")
            }
        }
    }

    private data class LoginPageResult(
        val body: String,
        val finalUrl: String
    )

    private fun fetchLoginPage(baseUrl: String): LoginPageResult? {
        val candidates = listOf(
            baseUrl,
            joinUrl(baseUrl, "fgtauth"),
            joinUrl(baseUrl, "login")
        ).distinct()

        for (candidate in candidates) {
            val request = Request.Builder()
                .url(candidate)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (body.contains("magic", ignoreCase = true)) {
                    return LoginPageResult(body, response.request.url.toString())
                }
            }
        }
        return null
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
            val action = formMatcher.group(1) ?: "/"
            return resolveRelativeUrl(finalUrl, action)
        }

        val baseHost = URL(baseUrl)
        return "${baseHost.protocol}://${baseHost.host}:${baseHost.port}/fgtauth"
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

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun joinUrl(base: String, path: String): String {
        return base.removeSuffix("/") + "/" + path.removePrefix("/")
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 PiriyalaiHotspot/1.0"

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
