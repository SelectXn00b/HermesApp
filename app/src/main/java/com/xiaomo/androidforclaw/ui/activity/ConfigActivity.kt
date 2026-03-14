/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.util.AppConstants
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivityConfigBinding
import com.tencent.mmkv.MMKV

/**
 * Configuration Activity
 * Maps to OpenClaw CLI: openclaw config
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private val mmkv by lazy { MMKV.defaultMMKV() }
    private val configLoader by lazy { ConfigLoader(this) }

    companion object {
        private const val TAG = "ConfigActivity"
        private const val REQUEST_MANAGE_STORAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 禁止截屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "配置"
        }

        // Check storage permission first
        if (!checkStoragePermission()) {
            requestStoragePermission()
        } else {
            loadConfig()
        }

        setupListeners()
    }

    /**
     * Check if app has MANAGE_EXTERNAL_STORAGE permission
     */
    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Old Android versions don't need this permission
        }
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission
     */
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open storage permission settings", e)
                Toast.makeText(this, "无法打开权限设置页面", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (checkStoragePermission()) {
                Log.i(TAG, "✅ Storage permission granted")
                loadConfig()
            } else {
                Log.w(TAG, "Storage permission not granted")
                Toast.makeText(this, "需要文件管理权限才能读取配置", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadConfig() {
        try {
            Log.d(TAG, "Starting to load config...")

            // Load config from openclaw.json
            val config = configLoader.loadOpenClawConfig()
            Log.d(TAG, "Config loaded successfully")

            binding.apply {
                // Display model configuration - use resolveProviders() method
                val providers = config.resolveProviders()
                Log.d(TAG, "providers: $providers")
                Log.d(TAG, "providers.size: ${providers.size}")

                // Show current model in model config card
                val primary = config.agents?.defaults?.model?.primary
                if (!primary.isNullOrBlank()) {
                    tvCurrentModelSummary.text = "当前: $primary"
                }

                if (providers.isNotEmpty()) {
                    val firstProvider = providers.entries.first()
                    val providerConfig = firstProvider.value
                    Log.d(TAG, "First provider: ${firstProvider.key}, baseUrl: ${providerConfig.baseUrl}")

                    etApiBase.setText(providerConfig.baseUrl)
                    // Show API key (password masked by inputType)
                    etApiKey.setText(providerConfig.apiKey)

                    // Display first model
                    if (providerConfig.models.isNotEmpty()) {
                        val firstModel = providerConfig.models.first()
                        Log.d(TAG, "First model: ${firstModel.name} (${firstModel.id})")
                    }

                    Log.d(TAG, "Model config displayed successfully")
                } else {
                    Log.w(TAG, "providers is empty")
                    etApiBase.setText("未配置")
                    etApiKey.setText("未配置")
                }

                // Feature switches - read from openclaw.json
                Log.d(TAG, "thinking.enabled: ${config.thinking.enabled}")
                Log.d(TAG, "agent.mode: ${config.agent.mode}")

                switchReasoning.isChecked = config.thinking.enabled
                switchExploration.isChecked = config.agent.mode == "exploration"

                // Gateway configuration
                Log.d(TAG, "gateway.port: ${config.gateway.port}")
                etGatewayPort.setText(config.gateway.port.toString())

                Log.d(TAG, "All config loaded successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config", e)
            Log.e(TAG, "Error details: ${e.stackTraceToString()}")
            Toast.makeText(this, "加载配置失败: ${e.message}", Toast.LENGTH_SHORT).show()

            // Use default values on failure
            binding.apply {
                etApiKey.setText("")
                etApiBase.setText("")
                switchReasoning.isChecked = true
                switchExploration.isChecked = false
                etGatewayPort.setText("8080")
            }
        }
    }

    private fun setupListeners() {
        binding.apply {
            // Save button
            btnSave.setOnClickListener {
                saveConfig()
            }

            // Reset to default button
            btnReset.setOnClickListener {
                resetToDefault()
            }

            // Model configuration entry
            cardModelConfig.setOnClickListener {
                startActivity(Intent(this@ConfigActivity, ModelConfigActivity::class.java))
            }

            // Skills management entry
            cardSkills.setOnClickListener {
                startActivity(Intent(this@ConfigActivity, SkillsActivity::class.java))
            }

            // Channels management entry
            cardChannels.setOnClickListener {
                startActivity(Intent(this@ConfigActivity, ChannelListActivity::class.java))
            }
        }
    }

    private fun saveConfig() {
        try {
            // Load current config
            val config = configLoader.loadOpenClawConfig()

            // Get UI values
            val apiKey = binding.etApiKey.text.toString()
            val apiBase = binding.etApiBase.text.toString()
            val gatewayPort = binding.etGatewayPort.text.toString().toIntOrNull() ?: 8080
            val reasoningEnabled = binding.switchReasoning.isChecked
            val explorationMode = binding.switchExploration.isChecked

            // Create updated config with new values
            val updatedThinking = config.thinking.copy(enabled = reasoningEnabled)
            val updatedAgent = config.agent.copy(
                mode = if (explorationMode) "exploration" else "planning"
            )
            val updatedGateway = config.gateway.copy(port = gatewayPort)

            // Update first provider's configuration
            val updatedModels = config.models?.let { models ->
                val updatedProviders = models.providers.toMutableMap()
                if (updatedProviders.isNotEmpty()) {
                    val firstProviderKey = updatedProviders.keys.first()
                    val oldProvider = updatedProviders[firstProviderKey]!!
                    updatedProviders[firstProviderKey] = oldProvider.copy(
                        apiKey = apiKey,
                        baseUrl = apiBase
                    )
                }
                models.copy(providers = updatedProviders)
            }

            // Create new config object with updated values
            val updatedConfig = config.copy(
                thinking = updatedThinking,
                agent = updatedAgent,
                gateway = updatedGateway,
                models = updatedModels
            )

            // Save to openclaw.json only (not MMKV)
            configLoader.saveOpenClawConfig(updatedConfig)

            Toast.makeText(this, "配置已保存到 openclaw.json", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
            Toast.makeText(this, "保存配置失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetToDefault() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("恢复默认")
            .setMessage("确定要恢复所有配置为默认值吗？")
            .setPositiveButton("确定") { _, _ ->
                // Delete current openclaw.json, will be recreated with defaults on next load
                try {
                    val configFile = java.io.File("/sdcard/.androidforclaw/openclaw.json")
                    if (configFile.exists()) {
                        configFile.delete()
                    }
                    loadConfig()
                    Toast.makeText(this, "已恢复默认配置", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to reset config", e)
                    Toast.makeText(this, "恢复默认配置失败", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
