/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/gateway/(all)
 *
 * AndroidForClaw adaptation: Android UI layer.
 */
package com.xiaomo.androidforclaw.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.draco.ladb.R
import com.draco.ladb.databinding.ActivityModelSetupBinding
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.ModelDefinition
import com.xiaomo.androidforclaw.config.ModelsConfig
import com.xiaomo.androidforclaw.config.ProviderConfig

/**
 * Model Setup Guide — simplified first-run wizard.
 *
 * Default flow: user only needs to paste an OpenRouter API Key.
 * Advanced: tap "使用其他服务商" to switch to Anthropic/OpenAI/Custom.
 */
class ModelSetupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ModelSetupActivity"
        const val EXTRA_MANUAL = "manual"

        fun isNeeded(context: android.content.Context): Boolean {
            try {
                val configFile = java.io.File("/sdcard/.androidforclaw/openclaw.json")
                if (!configFile.exists() || configFile.length() == 0L) {
                    Log.i(TAG, "openclaw.json missing, model setup is needed")
                    return true
                }

                val configLoader = ConfigLoader(context)
                val config = configLoader.loadOpenClawConfig()
                val providers = config.resolveProviders()
                val hasRealKey = providers.values.any { provider ->
                    val key = provider.apiKey
                    !key.isNullOrBlank() &&
                            !key.startsWith("\${") &&
                            key != "未配置"
                }
                return !hasRealKey
            } catch (e: Exception) {
                Log.w(TAG, "Error checking setup need, assuming needed", e)
                return true  // Config parse error → probably not configured properly → show setup
            }
        }

        // Provider presets
        private val PROVIDERS = mapOf(
            "openrouter" to ProviderPreset(
                name = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                api = "openai-completions",
                hint = "OpenRouter 聚合了 Claude、GPT、Gemini 等多个模型，一个 Key 即可使用全部。\n注册即可免费使用，无需充值！",
                models = listOf(
                    ModelPreset("openrouter/hunter-alpha", "🏹 Hunter Alpha (默认，免费，1M上下文)", reasoning = true, contextWindow = 1048576, maxTokens = 65536),
                    ModelPreset("openrouter/free", "🆓 免费自动路由 (无需充值)"),
                    ModelPreset("qwen/qwen3-coder:free", "🆓 Qwen3 Coder (免费，262K)", contextWindow = 262000),
                    ModelPreset("deepseek/deepseek-r1:free", "🆓 DeepSeek R1 (免费，推理)", reasoning = true, contextWindow = 163840),
                    ModelPreset("anthropic/claude-sonnet-4", "Claude Sonnet 4 (付费，推荐)", contextWindow = 200000, maxTokens = 16384),
                    ModelPreset("anthropic/claude-opus-4", "Claude Opus 4 (付费)", contextWindow = 200000, maxTokens = 32768),
                    ModelPreset("openai/gpt-4.1", "GPT-4.1 (付费)", contextWindow = 1048576, maxTokens = 32768),
                    ModelPreset("google/gemini-2.5-pro", "Gemini 2.5 Pro (付费)", contextWindow = 1048576, maxTokens = 65536)
                ),
                authHeader = true
            ),
            "anthropic" to ProviderPreset(
                name = "Anthropic",
                baseUrl = "https://api.anthropic.com/v1",
                api = "anthropic-messages",
                hint = "Anthropic 官方 API，直连 Claude。注册: console.anthropic.com",
                models = listOf(
                    ModelPreset("claude-sonnet-4-20250514", "Claude Sonnet 4 (推荐)"),
                    ModelPreset("claude-opus-4-20250514", "Claude Opus 4"),
                    ModelPreset("claude-haiku-3-5-20241022", "Claude 3.5 Haiku (快速)")
                )
            ),
            "mimo" to ProviderPreset(
                name = "小米 MiMo",
                baseUrl = "https://api.xiaomimimo.com/anthropic",
                api = "anthropic-messages",
                hint = "小米 MiMo 大模型，免费使用。注册: xiaomimimo.com",
                models = listOf(
                    ModelPreset("mimo-v2-flash", "MiMo V2 Flash (免费，262K)", reasoning = false, contextWindow = 262144, maxTokens = 8192),
                    ModelPreset("mimo-v2-pro", "MiMo V2 Pro (128K)", reasoning = false, contextWindow = 128000, maxTokens = 16384)
                ),
                authHeader = true
            ),
            "openai" to ProviderPreset(
                name = "OpenAI",
                baseUrl = "https://api.openai.com/v1",
                api = "openai-completions",
                hint = "OpenAI 官方 API。注册: platform.openai.com",
                models = listOf(
                    ModelPreset("gpt-4.1", "GPT-4.1 (推荐)"),
                    ModelPreset("gpt-4.1-mini", "GPT-4.1 Mini (快速)"),
                    ModelPreset("o3", "o3 (推理)")
                )
            ),
            "custom" to ProviderPreset(
                name = "自定义",
                baseUrl = "",
                api = "openai-completions",
                hint = "支持任何兼容 OpenAI API 的服务（vLLM、Ollama、OneAPI 等）。",
                models = listOf(
                    ModelPreset("", "手动输入模型 ID")
                )
            )
        )
    }

    private lateinit var binding: ActivityModelSetupBinding
    private val configLoader by lazy { ConfigLoader(this) }
    private var selectedProvider = "mimo"
    private var advancedExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "模型设置"
        }

        setupDefaultMode()
        setupAdvancedToggle()
        setupProviderSelection()
        setupButtons()
    }

    /**
     * Default mode: quick setup only asks for API key.
     */
    private fun setupDefaultMode() {
        binding.tilModel.visibility = View.GONE

        // 默认显示小米 MiMo 提示
        applyProviderPreset("mimo")

        // "打开 xiaomimimo.com" link
        binding.tvOpenOpenrouter.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.xiaomimimo.com/#/console/api-keys")))
            } catch (e: Exception) {
                Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Toggle advanced options (other providers).
     */
    private fun setupAdvancedToggle() {
        binding.tvAdvanced.setOnClickListener {
            advancedExpanded = !advancedExpanded
            binding.layoutAdvanced.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
            binding.tvAdvanced.text = if (advancedExpanded) {
                "⚙️ 收起高级选项"
            } else {
                "⚙️ 使用其他服务商（Anthropic / OpenAI / 自定义）"
            }

            // If collapsing, reset to MiMo
            if (!advancedExpanded && selectedProvider != "mimo") {
                selectedProvider = "mimo"
                applyProviderPreset("mimo")
            }
        }
    }

    private fun setupProviderSelection() {
        binding.chipGroupProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            val provider = when {
                checkedIds.contains(R.id.chip_mimo) -> "mimo"
                checkedIds.contains(R.id.chip_anthropic) -> "anthropic"
                checkedIds.contains(R.id.chip_openai) -> "openai"
                checkedIds.contains(R.id.chip_custom) -> "custom"
                else -> "mimo"
            }
            selectedProvider = provider
            applyProviderPreset(provider)
        }
    }

    private fun applyProviderPreset(providerKey: String) {
        val preset = PROVIDERS[providerKey] ?: return

        binding.apply {
            // API Key hint
            tilApiKey.hint = when (providerKey) {
                "mimo" -> "小米 MiMo API Key"
                "openrouter" -> "OpenRouter API Key"
                "anthropic" -> "Anthropic API Key"
                "openai" -> "OpenAI API Key"
                else -> "API Key"
            }
            (tilApiKey as? com.google.android.material.textfield.TextInputLayout)?.helperText = when (providerKey) {
                "mimo" -> "免费使用，注册: platform.xiaomimimo.com"
                "openrouter" -> "以 sk-or- 开头"
                "anthropic" -> "以 sk-ant- 开头"
                "openai" -> "以 sk- 开头"
                else -> null
            }

            // Base URL
            etSetupApiBase.setText(preset.baseUrl)
            if (providerKey == "custom") {
                tilApiBase.visibility = View.VISIBLE
                etSetupApiBase.isEnabled = true
            } else {
                tilApiBase.visibility = View.GONE
                etSetupApiBase.isEnabled = false
            }

            // Provider hint
            tvProviderHint.text = preset.hint
            tvProviderHint.visibility = if (advancedExpanded) View.VISIBLE else View.GONE

            // Model selection: hidden for built-in providers, only shown for custom provider
            val modelNames = preset.models.map { it.displayName }
            val adapter = ArrayAdapter(this@ModelSetupActivity, android.R.layout.simple_dropdown_item_1line, modelNames)
            actModel.setAdapter(adapter)
            if (modelNames.isNotEmpty()) {
                actModel.setText(modelNames[0], false)
            }

            if (providerKey == "custom") {
                tilModel.visibility = View.VISIBLE
                actModel.inputType = android.text.InputType.TYPE_CLASS_TEXT
                actModel.threshold = 100
            } else {
                tilModel.visibility = View.GONE
                actModel.inputType = android.text.InputType.TYPE_NULL
                actModel.threshold = 1
            }
        }
    }

    private fun setupButtons() {
        binding.btnSkip.setOnClickListener {
            Log.i(TAG, "用户跳过模型配置引导，使用默认配置")
            saveDefaultAndFinish()
        }

        binding.btnStart.setOnClickListener {
            saveAndFinish()
        }
    }

    private fun saveDefaultAndFinish() {
        selectedProvider = "mimo"
        advancedExpanded = false
        applyProviderPreset("mimo")
        binding.etSetupApiKey.setText("")
        saveAndFinish()
    }

    private fun saveAndFinish() {
        val userInputKey = binding.etSetupApiKey.text?.toString()?.trim()
        val selectedModelDisplay = binding.actModel.text?.toString()?.trim()

        // If user provided a key, use it; otherwise use the built-in encrypted key
        val apiKey = if (userInputKey.isNullOrEmpty()) {
            val builtInKey = com.xiaomo.androidforclaw.config.BuiltInKeyProvider.getKey()
            if (builtInKey.isNullOrEmpty()) {
                binding.tilApiKey.error = "请输入 API Key"
                return
            }
            builtInKey
        } else {
            userInputKey
        }
        binding.tilApiKey.error = null

        // For advanced/custom mode
        val apiBase = if (advancedExpanded) {
            binding.etSetupApiBase.text?.toString()?.trim()
        } else {
            null
        }

        if (selectedProvider == "custom" && apiBase.isNullOrEmpty()) {
            binding.tilApiBase.error = "请输入 API Base URL"
            return
        }

        // Resolve model ID
        val preset = PROVIDERS[selectedProvider] ?: return
        val matchedPreset = if (selectedProvider == "custom") {
            null
        } else {
            preset.models.firstOrNull()
        }
        val modelId = if (selectedProvider == "custom") {
            selectedModelDisplay ?: ""
        } else {
            matchedPreset?.id ?: ""
        }

        if (selectedProvider == "custom" && modelId.isBlank()) {
            binding.tilModel.error = "请输入模型 ID"
            return
        }
        binding.tilModel.error = null

        try {
            val config = configLoader.loadOpenClawConfig()

            val providerName = if (selectedProvider == "custom") "custom" else selectedProvider
            val newProvider = ProviderConfig(
                baseUrl = apiBase ?: preset.baseUrl,
                apiKey = apiKey,
                api = preset.api,
                models = listOf(
                    ModelDefinition(
                        id = modelId,
                        name = selectedModelDisplay ?: modelId,
                        reasoning = matchedPreset?.reasoning ?: (modelId.contains("o3") || modelId.contains("r1") || modelId.contains("opus")),
                        contextWindow = matchedPreset?.contextWindow ?: 200000,
                        maxTokens = matchedPreset?.maxTokens ?: 16384
                    )
                ),
                authHeader = preset.authHeader
            )

            val existingModels = config.models ?: ModelsConfig()
            val updatedProviders = existingModels.providers.toMutableMap()
            updatedProviders[providerName] = newProvider

            val defaultModelId = if (selectedProvider == "custom") {
                "custom/$modelId"
            } else if (modelId.startsWith("$providerName/")) {
                // modelId already contains provider prefix (e.g. "openrouter/hunter-alpha")
                modelId
            } else {
                "$providerName/$modelId"
            }

            val modelSelection = com.xiaomo.androidforclaw.config.ModelSelectionConfig(primary = defaultModelId)
            val existingAgents = config.agents ?: com.xiaomo.androidforclaw.config.AgentsConfig()
            val updatedDefaults = existingAgents.defaults.copy(model = modelSelection)
            val updatedAgents = existingAgents.copy(defaults = updatedDefaults)

            val updatedConfig = config.copy(
                models = existingModels.copy(providers = updatedProviders),
                agents = updatedAgents
            )

            configLoader.saveOpenClawConfig(updatedConfig)

            Log.i(TAG, "✅ 模型配置已保存: provider=$providerName, model=$modelId")
            markSetupSeen()
            Toast.makeText(this, "✅ 配置完成！", Toast.LENGTH_SHORT).show()
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "保存配置失败", e)
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun markSetupSeen() {
        try {
            val mmkv = com.tencent.mmkv.MMKV.defaultMMKV()
            mmkv.encode("model_setup_completed", true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark setup as seen", e)
        }
    }

    private data class ProviderPreset(
        val name: String,
        val baseUrl: String,
        val api: String,
        val hint: String,
        val models: List<ModelPreset>,
        val authHeader: Boolean = true
    )

    private data class ModelPreset(
        val id: String,
        val displayName: String,
        val reasoning: Boolean = false,
        val contextWindow: Int = 200000,
        val maxTokens: Int = 16384
    )

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
