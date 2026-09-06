package com.piriyalai.hotspot.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build

object PortalDiscovery {
    const val DEFAULT_PORTAL_URL = "https://login.piriyalaihotspot.com:1003/"

    fun buildCandidateUrls(
        context: Context,
        configuredUrl: String?,
        clientIp: String? = LinkAddressResolver.getClientIp(context),
        gatewayIp: String? = LinkAddressResolver.getGatewayIp(context)
    ): List<String> {
        val candidates = linkedSetOf<String>()
        candidates.addAll(buildCandidatesForNetwork(configuredUrl, clientIp, gatewayIp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            runCatching {
                connectivityManager::class.java
                    .getMethod("getCaptivePortalLoginUrl")
                    .invoke(connectivityManager) as? android.net.Uri
            }.getOrNull()?.toString()?.let { url ->
                candidates.add(url)
            }
        }

        return candidates.toList()
    }

    internal fun buildCandidatesForNetwork(
        configuredUrl: String?,
        clientIp: String?,
        gatewayIp: String?
    ): List<String> {
        val candidates = linkedSetOf<String>()

        configuredUrl?.takeIf { it.isNotBlank() }?.let { candidates.add(it) }

        candidates.add(DEFAULT_PORTAL_URL)
        candidates.add("https://login.piriyalaihotspot.com:1003/fgtauth")
        candidates.add("http://login.piriyalaihotspot.com:1000/")

        return candidates.toList()
    }
}
