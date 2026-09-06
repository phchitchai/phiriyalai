package com.piriyalai.hotspot.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Captive portals often block DNS for the open internet.
 * If system DNS fails, fall back to public IPs so HTTP probes still leave the device
 * and can be intercepted by FortiGate.
 */
object CaptivePortalDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
            return listOf(InetAddress.getByName(hostname))
        }

        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (_: UnknownHostException) {
            FALLBACK_IPS.map { InetAddress.getByAddress(hostname, it) }
        }
    }

    private val FALLBACK_IPS = listOf(
        byteArrayOf(1, 1, 1, 1),
        byteArrayOf(8, 8, 8, 8),
        byteArrayOf(1, 0, 0, 1)
    )
}
