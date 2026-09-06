package com.piriyalai.hotspot.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import java.net.Inet4Address

object LinkAddressResolver {
    fun getGatewayIp(context: Context): String? {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = NetworkClientFactory.findWifiNetwork(context)
            ?: connectivityManager.activeNetwork
            ?: return GatewayResolver.getGatewayIp(context)

        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        return linkProperties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway
            ?.hostAddress
            ?: GatewayResolver.getGatewayIp(context)
    }

    fun getClientIp(context: Context): String? {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = NetworkClientFactory.findWifiNetwork(context)
            ?: connectivityManager.activeNetwork
            ?: return GatewayResolver.getClientIp(context)

        val linkProperties = connectivityManager.getLinkProperties(network) ?: return null
        val fromLink = linkProperties.linkAddresses
            .mapNotNull { it.toIpv4() }
            .firstOrNull()

        return fromLink ?: GatewayResolver.getClientIp(context)
    }

    fun isPrivateNetworkIp(ip: String?): Boolean {
        if (ip.isNullOrBlank()) {
            return false
        }
        return ip.startsWith("10.") ||
            ip.startsWith("192.168.") ||
            ip.startsWith("172.16.") ||
            ip.startsWith("172.17.") ||
            ip.startsWith("172.18.") ||
            ip.startsWith("172.19.") ||
            ip.startsWith("172.2") ||
            ip.startsWith("172.30.") ||
            ip.startsWith("172.31.")
    }

    private fun LinkAddress.toIpv4(): String? {
        val address = this.address
        return if (address is Inet4Address && !address.isLoopbackAddress) {
            address.hostAddress
        } else {
            null
        }
    }
}
