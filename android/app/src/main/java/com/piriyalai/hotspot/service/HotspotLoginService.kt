package com.piriyalai.hotspot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.piriyalai.hotspot.BuildConfig
import com.piriyalai.hotspot.MainActivity
import com.piriyalai.hotspot.R
import com.piriyalai.hotspot.data.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HotspotLoginService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loginJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("รอเชื่อมต่อ WiFi..."))
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOGIN_NOW -> attemptLogin("manual")
            else -> attemptLogin("service_start")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                attemptLogin("wifi_available")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).not()
                ) {
                    attemptLogin("wifi_capabilities")
                }
            }
        }

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        networkCallback = null
    }

    private fun attemptLogin(reason: String) {
        val store = CredentialStore(this)
        if (!store.isAutoLoginEnabled() || !store.hasCredentials()) {
            updateNotification("Auto-login ปิดอยู่")
            return
        }

        if (!isTargetWifiConnected(store.getWifiSsid())) {
            updateNotification("ยังไม่ได้เชื่อมต่อ WiFi เป้าหมาย")
            return
        }

        loginJob?.cancel()
        loginJob = serviceScope.launch {
            delay(2_000)
            updateNotification("กำลัง login... ($reason)")

            val portalUrl = store.getPortalUrl().ifBlank { BuildConfig.DEFAULT_PORTAL_URL }
            val result = runCatching {
                MainActivity.performLogin(
                    context = this@HotspotLoginService,
                    username = store.getUsername(),
                    password = store.getPassword(),
                    configuredPortalUrl = portalUrl,
                    trustCert = store.trustPortalCertificate()
                )
            }.getOrElse { error ->
                com.piriyalai.hotspot.auth.LoginResult(false, error.message ?: "เกิดข้อผิดพลาด")
            }

            updateNotification(result.message)
        }
    }

    private fun isTargetWifiConnected(targetSsid: String): Boolean {
        if (targetSsid.isBlank()) {
            return true
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val currentSsid = wifiManager.connectionInfo?.ssid?.trim('"') ?: return false
        return currentSsid.equals(targetSsid, ignoreCase = true)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Piriyalai Hotspot Auto Login",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Piriyalai Hotspot")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_LOGIN_NOW = "com.piriyalai.hotspot.LOGIN_NOW"
        private const val CHANNEL_ID = "piriyalai_hotspot_login"
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            val intent = Intent(context, HotspotLoginService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HotspotLoginService::class.java))
        }
    }
}
