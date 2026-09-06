package com.piriyalai.hotspot.auth

import android.content.Context
import com.piriyalai.hotspot.network.LinkAddressResolver
import com.piriyalai.hotspot.network.NetworkClientFactory
import com.piriyalai.hotspot.network.PortalDiscovery
import okhttp3.OkHttpClient

object HotspotAuthFacade {
    fun login(
        context: Context,
        username: String,
        password: String,
        configuredPortalUrl: String,
        trustCert: Boolean
    ): LoginResult {
        val httpClient = NetworkClientFactory.create(context, trustCert)
        val clientIp = LinkAddressResolver.getClientIp(context)
        val gatewayIp = LinkAddressResolver.getGatewayIp(context)
        val candidates = PortalDiscovery.buildCandidateUrls(context, configuredPortalUrl, clientIp, gatewayIp)

        val errors = mutableListOf<String>()
        if (gatewayIp != null) {
            errors.add("Gateway: $gatewayIp")
        }
        if (clientIp != null) {
            errors.add("Client IP: $clientIp")
        }

        val mikrotik = MikrotikAuthClient(httpClient)
        val fortigate = FortiGateAuthClient(candidates, httpClient)

        for (candidate in candidates) {
            val mikrotikResult = runCatching { mikrotik.login(candidate, username, password) }.getOrNull()
            if (mikrotikResult?.success == true) {
                return mikrotikResult
            }
            mikrotikResult?.let { errors.add(it.message) }
        }

        val fortigateResult = fortigate.login(username, password)
        if (fortigateResult.success) {
            return fortigateResult
        }

        errors.add(fortigateResult.message)
        return LoginResult(
            success = false,
            message = errors.take(6).joinToString("\n")
        )
    }
}
