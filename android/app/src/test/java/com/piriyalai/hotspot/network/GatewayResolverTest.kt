package com.piriyalai.hotspot.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayResolverTest {
    @Test
    fun guessGatewayCandidates_fromClientIp() {
        val candidates = GatewayResolver.guessGatewayCandidates("10.10.222.213")
        assertTrue(candidates.contains("10.10.222.1"))
        assertTrue(candidates.contains("10.10.222.254"))
        assertEquals("10.10.222.1", candidates.first())
    }
}
