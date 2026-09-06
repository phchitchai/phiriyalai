package com.piriyalai.hotspot.auth

import android.content.Context
import com.piriyalai.hotspot.network.LinkAddressResolver
import com.piriyalai.hotspot.network.NetworkClientFactory
import com.piriyalai.hotspot.network.PortalDiscovery

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
        val portalUrl = configuredPortalUrl.ifBlank { PortalDiscovery.DEFAULT_PORTAL_URL }
        val candidates = PortalDiscovery.buildCandidateUrls(context, portalUrl, clientIp, gatewayIp)

        val errors = mutableListOf<String>()
        if (gatewayIp != null) {
            errors.add("Gateway: $gatewayIp")
        }
        if (clientIp != null) {
            errors.add("Client IP: $clientIp")
        }

        val fortigate = FortiGateAuthClient(candidates, httpClient)
        val fortigateResult = fortigate.login(username, password)
        if (fortigateResult.success) {
            return fortigateResult
        }

        errors.add(fortigateResult.message)
        return LoginResult(
            success = false,
            message = errors.joinToString("\n")
        )
    }
}
