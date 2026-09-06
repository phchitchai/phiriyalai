package com.piriyalai.hotspot.network

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import kotlin.math.min

object GatewayResolver {
    fun getGatewayIp(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo: DhcpInfo = wifiManager.dhcpInfo ?: return null
        if (dhcpInfo.gateway == 0) {
            return null
        }
        return intToIp(dhcpInfo.gateway)
    }

    fun getClientIp(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo: DhcpInfo = wifiManager.dhcpInfo ?: return null
        if (dhcpInfo.ipAddress == 0) {
            return null
        }
        return intToIp(dhcpInfo.ipAddress)
    }

    /**
     * From client IP 10.10.222.213, guess likely gateway addresses on the same subnet.
     */
    fun guessGatewayCandidates(clientIp: String): List<String> {
        val parts = clientIp.split(".")
        if (parts.size != 4) {
            return emptyList()
        }

        val prefix = "${parts[0]}.${parts[1]}.${parts[2]}"
        return listOf(
            "$prefix.1",
            "$prefix.254",
            "${parts[0]}.${parts[1]}.0.1",
            "${parts[0]}.0.0.1"
        ).distinct()
    }

    private fun intToIp(value: Int): String {
        return "${value and 0xff}.${value shr 8 and 0xff}.${value shr 16 and 0xff}.${value shr 24 and 0xff}"
    }
}
