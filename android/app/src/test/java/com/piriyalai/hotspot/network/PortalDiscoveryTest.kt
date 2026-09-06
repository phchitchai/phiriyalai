package com.piriyalai.hotspot.network

import org.junit.Assert.assertTrue
import org.junit.Test

class PortalDiscoveryTest {
    @Test
    fun buildCandidatesForNetwork_includesPiriyalaiHostnameOnPrivateNetwork() {
        val urls = PortalDiscovery.buildCandidatesForNetwork(
            configuredUrl = "",
            clientIp = "172.17.1.76",
            gatewayIp = "172.17.0.1"
        )

        assertTrue(urls.any { it.contains("login.piriyalaihotspot.com:1003") })
    }
}
