package com.piriyalai.hotspot.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""
    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""
    fun getPortalUrl(): String = prefs.getString(KEY_PORTAL_URL, "") ?: ""
    fun getWifiSsid(): String = prefs.getString(KEY_WIFI_SSID, "") ?: ""
    fun isAutoLoginEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_LOGIN, false)
    fun trustPortalCertificate(): Boolean = prefs.getBoolean(KEY_TRUST_CERT, true)

    fun save(
        username: String,
        password: String,
        portalUrl: String,
        wifiSsid: String,
        autoLogin: Boolean,
        trustPortalCertificate: Boolean
    ) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_PORTAL_URL, portalUrl)
            .putString(KEY_WIFI_SSID, wifiSsid)
            .putBoolean(KEY_AUTO_LOGIN, autoLogin)
            .putBoolean(KEY_TRUST_CERT, trustPortalCertificate)
            .apply()
    }

    fun hasCredentials(): Boolean {
        return getUsername().isNotBlank() && getPassword().isNotBlank()
    }

    companion object {
        private const val FILE_NAME = "piriyalai_hotspot_credentials"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PORTAL_URL = "portal_url"
        private const val KEY_WIFI_SSID = "wifi_ssid"
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_TRUST_CERT = "trust_cert"
    }
}
