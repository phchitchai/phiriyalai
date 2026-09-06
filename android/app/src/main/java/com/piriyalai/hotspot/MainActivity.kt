package com.piriyalai.hotspot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.piriyalai.hotspot.auth.HotspotAuthFacade
import com.piriyalai.hotspot.data.CredentialStore
import com.piriyalai.hotspot.databinding.ActivityMainBinding
import com.piriyalai.hotspot.service.HotspotLoginService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (!granted) {
            Toast.makeText(this, "ต้องอนุญาต Notification และ Location เพื่อ auto-login", Toast.LENGTH_LONG).show()
        }
        requestBatteryExemption()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialStore = CredentialStore(this)
        HotspotAuthFacade.webViewProvider = { binding.portalWebView }
        loadSavedSettings()
        requestRuntimePermissions()

        binding.saveButton.setOnClickListener { saveSettings(startService = true) }
        binding.testLoginButton.setOnClickListener { testLogin() }
        binding.autoLoginSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                saveSettings(startService = true)
            } else {
                saveSettings(startService = false)
                HotspotLoginService.stop(this)
                binding.autoStatusText.text = "Auto-login: ปิดอยู่"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAutoStatus()
        if (credentialStore.isAutoLoginEnabled() && credentialStore.hasCredentials()) {
            HotspotLoginService.start(this)
        }
    }

    private fun loadSavedSettings() {
        binding.usernameInput.setText(credentialStore.getUsername())
        binding.passwordInput.setText(credentialStore.getPassword())
        binding.portalUrlInput.setText(
            credentialStore.getPortalUrl().ifBlank { BuildConfig.DEFAULT_PORTAL_URL }
        )
        binding.wifiSsidInput.setText(credentialStore.getWifiSsid())
        binding.autoLoginSwitch.isChecked = credentialStore.isAutoLoginEnabled()
        binding.trustCertSwitch.isChecked = credentialStore.trustPortalCertificate()
        refreshAutoStatus()
    }

    private fun saveSettings(startService: Boolean): Boolean {
        val username = binding.usernameInput.text?.toString()?.trim() ?: ""
        val password = binding.passwordInput.text?.toString() ?: ""
        val portalUrl = binding.portalUrlInput.text?.toString()?.trim()
            ?: BuildConfig.DEFAULT_PORTAL_URL
        val wifiSsid = binding.wifiSsidInput.text?.toString()?.trim() ?: ""
        val autoLogin = binding.autoLoginSwitch.isChecked
        val trustCert = binding.trustCertSwitch.isChecked

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, "กรุณากรอก username และ password", Toast.LENGTH_SHORT).show()
            return false
        }

        credentialStore.save(username, password, portalUrl, wifiSsid, autoLogin, trustCert)

        if (autoLogin && startService) {
            HotspotLoginService.start(this)
            requestBatteryExemption()
            binding.autoStatusText.text = "Auto-login: กำลังทำงาน (ดู notification)"
            Toast.makeText(this, "บันทึกแล้ว — Auto-login เปิดอยู่", Toast.LENGTH_SHORT).show()
        } else {
            HotspotLoginService.stop(this)
            binding.autoStatusText.text = "Auto-login: ปิดอยู่"
            Toast.makeText(this, "บันทึกแล้ว", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun testLogin() {
        val username = binding.usernameInput.text?.toString()?.trim() ?: ""
        val password = binding.passwordInput.text?.toString() ?: ""
        val portalUrl = binding.portalUrlInput.text?.toString()?.trim()
            ?: BuildConfig.DEFAULT_PORTAL_URL
        val trustCert = binding.trustCertSwitch.isChecked

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, "กรุณากรอก username และ password", Toast.LENGTH_SHORT).show()
            return
        }

        if (!binding.autoLoginSwitch.isChecked) {
            binding.autoLoginSwitch.isChecked = true
        }

        binding.testLoginButton.isEnabled = false
        binding.statusText.text = "กำลัง login..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                HotspotAuthFacade.login(
                    context = this@MainActivity,
                    username = username,
                    password = password,
                    configuredPortalUrl = portalUrl,
                    trustCert = trustCert
                )
            }

            binding.testLoginButton.isEnabled = true
            binding.statusText.text = result.message
            Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()

            if (result.success) {
                saveSettings(startService = true)
                HotspotLoginService.loginNow(this@MainActivity)
            }
        }
    }

    private fun refreshAutoStatus() {
        binding.autoStatusText.text = if (credentialStore.isAutoLoginEnabled() && credentialStore.hasCredentials()) {
            "Auto-login: เปิดอยู่ — จะ login เองเมื่อต่อ WiFi"
        } else {
            "Auto-login: ยังไม่ทำงาน (เปิดสวิตช์แล้วกดบันทึก)"
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        permissions += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestBatteryExemption()
        }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            return
        }
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        runCatching { startActivity(intent) }
    }
}
