package com.piriyalai.hotspot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.piriyalai.hotspot.auth.FortiGateAuthClient
import com.piriyalai.hotspot.data.CredentialStore
import com.piriyalai.hotspot.databinding.ActivityMainBinding
import com.piriyalai.hotspot.network.NetworkClientFactory
import com.piriyalai.hotspot.network.PortalDiscovery
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
            Toast.makeText(this, "ต้องอนุญาตสิทธิ์เพื่อ auto-login", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialStore = CredentialStore(this)
        loadSavedSettings()
        requestRuntimePermissions()

        binding.saveButton.setOnClickListener { saveSettings() }
        binding.testLoginButton.setOnClickListener { testLogin() }
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
    }

    private fun saveSettings() {
        val username = binding.usernameInput.text?.toString()?.trim() ?: ""
        val password = binding.passwordInput.text?.toString() ?: ""
        val portalUrl = binding.portalUrlInput.text?.toString()?.trim()
            ?: BuildConfig.DEFAULT_PORTAL_URL
        val wifiSsid = binding.wifiSsidInput.text?.toString()?.trim() ?: ""
        val autoLogin = binding.autoLoginSwitch.isChecked
        val trustCert = binding.trustCertSwitch.isChecked

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, "กรุณากรอก username และ password", Toast.LENGTH_SHORT).show()
            return
        }

        credentialStore.save(username, password, portalUrl, wifiSsid, autoLogin, trustCert)

        if (autoLogin) {
            HotspotLoginService.start(this)
            Toast.makeText(this, "บันทึกแล้ว — เปิด auto-login", Toast.LENGTH_SHORT).show()
        } else {
            HotspotLoginService.stop(this)
            Toast.makeText(this, "บันทึกแล้ว — ปิด auto-login", Toast.LENGTH_SHORT).show()
        }
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

        binding.testLoginButton.isEnabled = false
        binding.statusText.text = "กำลังค้นหา portal และทดสอบ login..."

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                performLogin(username, password, portalUrl, trustCert)
            }

            binding.testLoginButton.isEnabled = true
            binding.statusText.text = result.message
            Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun performLogin(
            context: android.content.Context,
            username: String,
            password: String,
            configuredPortalUrl: String,
            trustCert: Boolean
        ): com.piriyalai.hotspot.auth.LoginResult {
            val httpClient = NetworkClientFactory.create(context, trustCert)
            val candidates = PortalDiscovery.buildCandidateUrls(context, configuredPortalUrl)
            val discovered = PortalDiscovery.discoverWorkingPortal(httpClient, candidates)
            val portalList = if (discovered != null) {
                listOf(discovered) + candidates
            } else {
                candidates
            }

            val client = FortiGateAuthClient(portalList, httpClient)
            return runCatching { client.login(username, password) }
                .getOrElse { error ->
                    com.piriyalai.hotspot.auth.LoginResult(false, error.message ?: "error")
                }
        }
    }

    private fun performLogin(
        username: String,
        password: String,
        portalUrl: String,
        trustCert: Boolean
    ): com.piriyalai.hotspot.auth.LoginResult {
        return performLogin(this, username, password, portalUrl, trustCert)
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            permissions += Manifest.permission.ACCESS_FINE_LOCATION
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
