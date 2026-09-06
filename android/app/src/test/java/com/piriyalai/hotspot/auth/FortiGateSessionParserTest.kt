package com.piriyalai.hotspot.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FortiGateSessionParserTest {
    @Test
    fun extractMagicFromUrl_bareQueryToken() {
        val magic = FortiGateSessionParser.extractMagicFromUrl(
            "https://login.piriyalaihotspot.com:1003/fgtauth?0601019c0b956a96"
        )
        assertEquals("0601019c0b956a96", magic)
    }

    @Test
    fun extractMagicFromUrl_namedQueryParam() {
        val magic = FortiGateSessionParser.extractMagicFromUrl(
            "https://login.piriyalaihotspot.com:1003/fgtauth?magic=abc123def"
        )
        assertEquals("abc123def", magic)
    }

    @Test
    fun buildPostUrl_stripsQuery() {
        val postUrl = FortiGateSessionParser.buildPostUrl(
            "https://login.piriyalaihotspot.com:1003/fgtauth?0601019c0b956a96"
        )
        assertEquals("https://login.piriyalaihotspot.com:1003/fgtauth", postUrl)
    }

    @Test
    fun isFortiGateLoginPage_detectsUsernamePasswordForm() {
        val html = """<form><input name="username"/><input name="password"/></form>"""
        assertTrue(
            FortiGateSessionParser.isFortiGateLoginPage(
                html,
                "https://login.piriyalaihotspot.com:1003/fgtauth?0601019c0b956a96"
            )
        )
    }
}
