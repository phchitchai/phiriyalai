package com.piriyalai.hotspot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import com.piriyalai.hotspot.data.CredentialStore
import com.piriyalai.hotspot.service.HotspotLoginService

class WifiStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != WifiManager.NETWORK_STATE_CHANGED_ACTION &&
            action != WifiManager.WIFI_STATE_CHANGED_ACTION
        ) {
            return
        }

        val store = CredentialStore(context)
        if (store.isAutoLoginEnabled() && store.hasCredentials()) {
            runCatching { HotspotLoginService.loginNow(context) }
        }
    }
}
