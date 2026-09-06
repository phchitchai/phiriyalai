package com.piriyalai.hotspot.auth

import org.junit.Assert.assertTrue
import org.junit.Test

class CaptiveRedirectFinderTest {
    @Test
    fun buildProbeUrls_startsWithRawIp() {
        val urls = CaptiveRedirectFinder.buildProbeUrls("172.17.0.1")
        assertTrue(urls.first().startsWith("http://1.1.1.1"))
        assertTrue(urls.any { it.contains("172.17.0.1") })
        assertTrue(urls.any { it.contains("login.piriyalaihotspot.com:1003") })
    }
}
