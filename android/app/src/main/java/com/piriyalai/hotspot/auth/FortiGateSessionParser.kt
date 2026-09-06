package com.piriyalai.hotspot.auth

import java.util.regex.Pattern

data class FortiGateSession(
    val loginPageUrl: String,
    val magic: String,
    val postUrl: String
)

object FortiGateSessionParser {
    private val fgtauthPathPattern = Pattern.compile(
        """/fgtauth[/?]([0-9a-fA-F]+)""",
        Pattern.CASE_INSENSITIVE
    )

    fun extractMagicFromUrl(url: String): String? {
        val query = runCatching {
            java.net.URI(url).rawQuery
        }.getOrNull()

        if (!query.isNullOrBlank()) {
            if (query.contains("=")) {
                query.split("&").forEach { part ->
                    val pieces = part.split("=", limit = 2)
                    if (pieces[0].equals("magic", ignoreCase = true)) {
                        return pieces.getOrNull(1)
                    }
                }
            } else if (query.matches(Regex("[0-9a-fA-F]+"))) {
                return query
            }
        }

        val matcher = fgtauthPathPattern.matcher(url)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    fun buildPostUrl(loginPageUrl: String): String {
        val uri = java.net.URI(loginPageUrl)
        val portPart = if (uri.port == -1) "" else ":${uri.port}"
        return "${uri.scheme}://${uri.host}$portPart/fgtauth"
    }

    fun isFortiGateLoginPage(html: String, pageUrl: String): Boolean {
        return FortiGateAuthClient.containsMagicToken(html) ||
            extractMagicFromUrl(pageUrl) != null ||
            (html.contains("username", ignoreCase = true) &&
                html.contains("password", ignoreCase = true) &&
                (html.contains("fgtauth", ignoreCase = true) ||
                    html.contains("fortinet", ignoreCase = true) ||
                    pageUrl.contains("fgtauth", ignoreCase = true)))
    }
}
