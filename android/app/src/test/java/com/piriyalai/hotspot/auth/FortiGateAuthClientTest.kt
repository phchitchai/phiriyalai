package com.piriyalai.hotspot.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FortiGateAuthClientTest {
    private val client = FortiGateAuthClient(
        portalBaseUrl = "https://login.piriyalaihotspot.com:1003/",
        httpClient = FortiGateAuthClient.createDefaultClient(true)
    )

    @Test
    fun extractMagic_fromStandardForm() {
        val html = """
            <form action="/fgtauth" method="post">
              <input type="hidden" name="magic" value="abc123session" />
              <input type="hidden" name="4Tredir" value="http://example.com" />
            </form>
        """.trimIndent()

        assertEquals("abc123session", client.extractMagic(html))
    }

    @Test
    fun extractMagic_returnsNullWhenMissing() {
        assertNull(client.extractMagic("<html><body>no token</body></html>"))
    }
}
