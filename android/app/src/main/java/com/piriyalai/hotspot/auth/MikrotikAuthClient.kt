package com.piriyalai.hotspot.auth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.regex.Pattern

class MikrotikAuthClient(
    private val httpClient: OkHttpClient
) {
    private val chapIdPattern = Pattern.compile("""['"]([0-9a-fA-F]+)['"]\s*\+\s*document\.login\.password\.value\s*\+\s*['"]([0-9a-fA-F]+)['"]""")
    private val loginOnlyPattern = Pattern.compile("""action=["']([^"']*login[^"']*)["']""", Pattern.CASE_INSENSITIVE)

    fun login(portalBaseUrl: String, username: String, password: String): LoginResult? {
        val loginPage = fetchPage(portalBaseUrl) ?: return null
        if (!isMikrotikPage(loginPage.body)) {
            return null
        }

        val postUrl = resolvePostUrl(portalBaseUrl, loginPage.finalUrl, loginPage.body)
        val chap = extractChap(loginPage.body)
        val hashedPassword = if (chap != null) {
            md5Hex(chap.id + password + chap.challenge)
        } else {
            password
        }

        val formBuilder = FormBody.Builder()
            .add("username", username)
            .add("password", hashedPassword)
            .add("dst", "")
            .add("popup", "true")

        val request = Request.Builder()
            .url(postUrl)
            .post(formBuilder.build())
            .header("User-Agent", USER_AGENT)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            val success = response.isSuccessful &&
                !body.contains("invalid", ignoreCase = true) &&
                !body.contains("authentication failed", ignoreCase = true)

            if (success) {
                LoginResult(true, "Login สำเร็จ (MikroTik $portalBaseUrl)", postUrl, portalBaseUrl)
            } else {
                LoginResult(false, "MikroTik login ไม่สำเร็จที่ $portalBaseUrl")
            }
        }
    }

    private data class PageResult(val body: String, val finalUrl: String)
    private data class ChapPair(val id: String, val challenge: String)

    private fun fetchPage(url: String): PageResult? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                PageResult(response.body?.string() ?: "", response.request.url.toString())
            }
        }.getOrNull()
    }

    private fun isMikrotikPage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("mikrotik") ||
            lower.contains("link-login-only") ||
            lower.contains("chap-id") ||
            lower.contains("hexmd5")
    }

    private fun extractChap(html: String): ChapPair? {
        val matcher = chapIdPattern.matcher(html)
        if (matcher.find()) {
            return ChapPair(matcher.group(1) ?: return null, matcher.group(2) ?: return null)
        }
        return null
    }

    private fun resolvePostUrl(baseUrl: String, finalUrl: String, html: String): String {
        val matcher = loginOnlyPattern.matcher(html)
        if (matcher.find()) {
            val action = matcher.group(1) ?: "/login"
            return resolveRelativeUrl(finalUrl, action)
        }
        return joinUrl(baseUrl, "login")
    }

    private fun resolveRelativeUrl(pageUrl: String, action: String): String {
        if (action.startsWith("http://") || action.startsWith("https://")) {
            return action
        }
        val page = java.net.URL(pageUrl)
        val path = if (action.startsWith("/")) action else "/$action"
        val portPart = if (page.port == -1) "" else ":${page.port}"
        return "${page.protocol}://${page.host}$portPart$path"
    }

    private fun joinUrl(base: String, path: String): String {
        return base.removeSuffix("/") + "/" + path.removePrefix("/")
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 PiriyalaiHotspot/1.0"
    }
}
