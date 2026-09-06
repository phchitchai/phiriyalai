package com.piriyalai.hotspot.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.piriyalai.hotspot.auth.PortalSsl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object NetworkClientFactory {
    fun create(context: Context, trustPortalCertificate: Boolean): OkHttpClient {
        val wifiNetwork = findWifiNetwork(context)
        val builder = OkHttpClient.Builder()
            .dns(CaptivePortalDns)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (wifiNetwork != null) {
            builder.socketFactory(wifiNetwork.socketFactory)
        }

        if (trustPortalCertificate) {
            builder.sslSocketFactory(
                PortalSsl.trustAllSslSocketFactory(),
                PortalSsl.trustAllManager()
            )
            builder.hostnameVerifier { _, _ -> true }
        }

        return builder.build()
    }

    fun findWifiNetwork(context: Context): Network? {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networks = connectivityManager.allNetworks
        return networks.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } ?: networks.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@firstOrNull false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        } ?: connectivityManager.activeNetwork
    }
}
