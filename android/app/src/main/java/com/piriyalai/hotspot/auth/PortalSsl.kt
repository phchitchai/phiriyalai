package com.piriyalai.hotspot.auth

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * FortiGate captive portals often use self-signed certificates on port 1003.
 * This trusts only the portal connection initiated by our OkHttp client.
 * Credentials are still sent over TLS when the portal supports HTTPS.
 */
object PortalSsl {
    fun trustAllSslSocketFactory(): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(trustAllManager()), SecureRandom())
        return context.socketFactory
    }

    fun trustAllManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
    }
}
