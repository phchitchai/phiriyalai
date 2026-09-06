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
import com.piriyalai.hotspot.auth.HotspotAuthFacade
import com.piriyalai.hotspot.data.CredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class HotspotLoginService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loginJob: Job? = null
    private var watchJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastSuccessAt = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Auto-login พร้อมแล้ว รอ WiFi..."))
        registerNetworkCallback()
        startWatchLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reason = when (intent?.action) {
            ACTION_LOGIN_NOW -> "manual"
            ACTION_WIFI_CHANGED -> "wifi_changed"
            else -> "service_start"
        }
        attemptLogin(reason)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatchLoop() {
        watchJob?.cancel()
        watchJob = serviceScope.launch {
            while (isActive) {
                delay(45_000)
                if (isCaptivePortalPresent()) {
                    attemptLogin("watch_captive")
                }
            }
        }
    }

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
                val captive = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
                )
                val notValidated = !networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
                )
                if (captive || notValidated) {
                    attemptLogin(if (captive) "captive_portal" else "wifi_unvalidated")
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
            updateNotification("Auto-login ยังไม่พร้อม — เปิดสวิตช์แล้วกดบันทึก")
            return
        }

        if (!isTargetWifiConnected(store.getWifiSsid())) {
            updateNotification("รอเชื่อมต่อ WiFi โรงเรียน...")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastSuccessAt < 60_000 && reason != "manual") {
            updateNotification("Login สำเร็จแล้ว (รอ session ใหม่)")
            return
        }

        loginJob?.cancel()
        loginJob = serviceScope.launch {
            delay(2_500)
            updateNotification("กำลัง auto-login... ($reason)")

            val portalUrl = store.getPortalUrl().ifBlank { BuildConfig.DEFAULT_PORTAL_URL }
            val result = runCatching {
                HotspotAuthFacade.login(
                    context = this@HotspotLoginService,
                    username = store.getUsername(),
                    password = store.getPassword(),
                    configuredPortalUrl = portalUrl,
                    trustCert = store.trustPortalCertificate()
                )
            }.getOrElse { error ->
                com.piriyalai.hotspot.auth.LoginResult(false, error.message ?: "เกิดข้อผิดพลาด")
            }

            if (result.success) {
                lastSuccessAt = System.currentTimeMillis()
                updateNotification("Auto-login สำเร็จ")
            } else {
                updateNotification("Auto-login ไม่สำเร็จ — จะลองใหม่")
            }
        }
    }

    private fun isCaptivePortalPresent(): Boolean {
        val cm = connectivityManager ?: return false
        val network = NetworkClientFactoryCompat.activeWifi(cm) ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun isTargetWifiConnected(targetSsid: String): Boolean {
        if (targetSsid.isBlank()) {
            return true
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val currentSsid = wifiManager.connectionInfo?.ssid?.trim('"') ?: return false
        if (currentSsid == "<unknown ssid>" || currentSsid == "0x") {
            return true
        }
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
            .setContentTitle("Piriyalai Auto-login")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_LOGIN_NOW = "com.piriyalai.hotspot.LOGIN_NOW"
        const val ACTION_WIFI_CHANGED = "com.piriyalai.hotspot.WIFI_CHANGED"
        private const val CHANNEL_ID = "piriyalai_hotspot_login"
        private const val NOTIFICATION_ID = 1003

        fun start(context: Context) {
            startInternal(context, null)
        }

        fun loginNow(context: Context) {
            startInternal(context, ACTION_LOGIN_NOW)
        }

        private fun startInternal(context: Context, action: String?) {
            val intent = Intent(context, HotspotLoginService::class.java).apply {
                if (action != null) {
                    this.action = action
                }
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HotspotLoginService::class.java))
        }
    }
}

private object NetworkClientFactoryCompat {
    fun activeWifi(cm: ConnectivityManager): Network? {
        return cm.allNetworks.firstOrNull { network ->
            cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }
}
