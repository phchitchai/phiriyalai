package com.piriyalai.hotspot.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.piriyalai.hotspot.data.CredentialStore
import com.piriyalai.hotspot.service.HotspotLoginService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val store = CredentialStore(context)
        if (store.isAutoLoginEnabled() && store.hasCredentials()) {
            HotspotLoginService.start(context)
        }
    }
}
