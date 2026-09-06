package com.piriyalai.hotspot.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalDiscoveryTest {
    @Test
    fun buildCandidatesForNetwork_skipsPublicHostnameOnPrivateNetwork() {
        val urls = PortalDiscovery.buildCandidatesForNetwork(
            configuredUrl = "https://login.piriyalaihotspot.com:1003/",
            clientIp = "10.10.222.213",
            gatewayIp = "10.10.222.1"
        )

        assertTrue(urls.any { it.contains("10.10.222.1") })
        assertFalse(urls.any { it.contains("login.piriyalaihotspot.com") })
    }
}
