package com.piriyalai.hotspot.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptivePortalDnsTest {
    @Test
    fun lookup_literalIp() {
        val addresses = CaptivePortalDns.lookup("1.1.1.1")
        assertEquals("1.1.1.1", addresses.first().hostAddress)
    }

    @Test
    fun lookup_unknownHostFallsBackToPublicIp() {
        val addresses = CaptivePortalDns.lookup("neverssl-does-not-resolve-on-captive.invalid")
        assertTrue(addresses.any { it.hostAddress == "1.1.1.1" })
    }
}
