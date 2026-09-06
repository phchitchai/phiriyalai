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
        connectivityManager.allNetworks.forEach { network ->
            val gateway = connectivityManager.getLinkProperties(network)
                ?.routes
                ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
                ?.gateway
                ?.hostAddress
            if (!gateway.isNullOrBlank() && gateway != "0.0.0.0") {
                return gateway
            }
        }
        return GatewayResolver.getGatewayIp(context)
    }

    fun getClientIp(context: Context): String? {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.allNetworks.forEach { network ->
            val ip = connectivityManager.getLinkProperties(network)
                ?.linkAddresses
                ?.mapNotNull { it.toIpv4() }
                ?.firstOrNull()
            if (!ip.isNullOrBlank()) {
                return ip
            }
        }
        return GatewayResolver.getClientIp(context)
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
