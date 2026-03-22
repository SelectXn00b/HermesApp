/**
 * OpenClaw Source Reference:
 * - 无 OpenClaw 对应 (Android 平台独有)
 */
package com.xiaomo.androidforclaw.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.xiaomo.androidforclaw.logging.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.xiaomo.androidforclaw.R
import com.xiaomo.androidforclaw.databinding.ActivityModelConfigBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.xiaomo.androidforclaw.config.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 模型配置页面 — 两页式设计
 *
 * Page 1: 选择 AI 服务商 (Provider)
 * Page 2: 填写服务商参数 + 选择模型
 *
 * 所有 Provider 定义来自 ProviderRegistry，与 OpenClaw 保持一致。
 */
class ModelConfigActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ModelConfigActivity"
    }

    private lateinit var binding: ActivityModelConfigBinding
    private val configLoader by lazy { ConfigLoader(this) }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State
    private var selectedProvider: ProviderDefinition? = null
    private var selectedModelId: String? = null
    private var moreExpanded = false
    private var advancedExpanded = false
    private var configuredProviderIds = setOf<String>()
    private var currentModelRef: String? = null // "provider/modelId"

    // Discovered models (from /v1/models API)
    private val discoveredModels = mutableListOf<PresetModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityModelConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets()
        loadCurrentConfig()
        setupToolbar()
        buildProviderList()
    }

    private fun applySystemBarInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            // Apply bottom inset to save button container so it sits above navigation bar
            binding.btnSave.let { btn ->
                val parent = btn.parent as? android.view.View
                parent?.setPadding(0, 0, 0, insets.bottom)
            }
            windowInsets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ========== Config Loading ==========

    private fun loadCurrentConfig() {
        try {
            val config = configLoader.loadOpenClawConfig()
            val providers = config.resolveProviders()
            configuredProviderIds = providers.filter { (_, v) ->
                !v.apiKey.isNullOrBlank() && !v.apiKey.startsWith("\${") && v.apiKey != "未配置"
            }.keys

            // Resolve current model ref
            currentModelRef = config.agents?.defaults?.model?.primary

            binding.tvCurrentModel.text = currentModelRef ?: "未配置"
            binding.cardCurrentModel.visibility =
                if (currentModelRef != null) View.VISIBLE else View.GONE

        } catch (e: Exception) {
            Log.w(TAG, "Failed to load config", e)
            configuredProviderIds = emptySet()
            currentModelRef = null
            binding.cardCurrentModel.visibility = View.GONE
        }
    }

    // ========== Toolbar ==========

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            if (binding.pageProviderDetail.visibility == View.VISIBLE) {
                showPage1()
            } else {
                finish()
            }
        }
    }

    // ========== Page Navigation ==========

    private fun showPage1() {
        binding.pageProviderList.visibility = View.VISIBLE
        binding.pageProviderDetail.visibility = View.GONE
        binding.toolbar.title = "模型配置"
    }

    private fun showPage2(provider: ProviderDefinition) {
        selectedProvider = provider
        selectedModelId = null

        // Pre-select current model if this is the active provider
        val modelRef = currentModelRef
        if (modelRef != null && modelRef.startsWith("${provider.id}/")) {
            selectedModelId = modelRef.removePrefix("${provider.id}/")
        }

        binding.pageProviderList.visibility = View.GONE
        binding.pageProviderDetail.visibility = View.VISIBLE
        binding.toolbar.title = provider.name

        setupPage2(provider)
    }

    // ========== Page 1: Provider List ==========

    private fun buildProviderList() {
        val inflater = LayoutInflater.from(this)

        // Primary providers
        binding.containerPrimaryProviders.removeAllViews()
        for (provider in ProviderRegistry.PRIMARY_PROVIDERS) {
            addProviderCard(inflater, binding.containerPrimaryProviders, provider)
        }

        // More providers (hidden initially)
        binding.containerMoreProviders.removeAllViews()
        for (provider in ProviderRegistry.MORE_PROVIDERS) {
            addProviderCard(inflater, binding.containerMoreProviders, provider)
        }

        // Custom provider
        binding.containerCustomProviders.removeAllViews()
        for (provider in ProviderRegistry.CUSTOM_PROVIDERS) {
            addProviderCard(inflater, binding.containerCustomProviders, provider)
        }

        // Toggle "more"
        binding.cardMoreToggle.setOnClickListener {
            moreExpanded = !moreExpanded
            binding.containerMoreProviders.visibility =
                if (moreExpanded) View.VISIBLE else View.GONE
            binding.ivMoreArrow.animate()
                .rotation(if (moreExpanded) 180f else 0f)
                .setDuration(200)
                .start()
        }
    }

    private fun addProviderCard(
        inflater: LayoutInflater,
        container: android.widget.LinearLayout,
        provider: ProviderDefinition
    ) {
        val card = inflater.inflate(R.layout.item_provider_card, container, false)

        card.findViewById<TextView>(R.id.tv_provider_name).text = provider.name
        card.findViewById<TextView>(R.id.tv_provider_desc).text = provider.description

        // Status indicator
        val statusView = card.findViewById<View>(R.id.view_status)
        val isConfigured = configuredProviderIds.contains(provider.id)
        val isCurrent = currentModelRef?.startsWith("${provider.id}/") == true
        if (isConfigured || isCurrent) {
            statusView.visibility = View.VISIBLE
            statusView.setBackgroundResource(R.drawable.bg_circle_green)
        }

        // Highlight current provider
        if (isCurrent) {
            (card as? MaterialCardView)?.apply {
                strokeColor = getColor(android.R.color.holo_green_dark)
                strokeWidth = 2
            }
        }

        card.setOnClickListener {
            Log.i(TAG, "Provider card clicked: ${provider.id} / ${provider.name}")
            try {
                showPage2(provider)
            } catch (e: Exception) {
                Log.e(TAG, "showPage2 crashed", e)
            }
        }

        container.addView(card)
    }

    // ========== Page 2: Provider Detail ==========

    private fun setupPage2(provider: ProviderDefinition) {
        // Provider name
        binding.tvProviderName.text = provider.name

        // Status
        val isConfigured = configuredProviderIds.contains(provider.id)
        binding.tvProviderStatus.visibility = if (isConfigured) View.VISIBLE else View.GONE

        // API Key
        binding.tilApiKey.hint = provider.keyHint
        binding.etApiKey.setText("")
        if (!provider.keyRequired) {
            binding.tilApiKey.helperText = "可选（有内置 Key）"
        } else {
            binding.tilApiKey.helperText = null
        }

        // Load existing key if configured
        try {
            val config = configLoader.loadOpenClawConfig()
            val existingProvider = config.resolveProviders()[provider.id]
            if (existingProvider != null) {
                val key = existingProvider.apiKey
                if (!key.isNullOrBlank() && !key.startsWith("\${")) {
                    binding.etApiKey.setText(key)
                }
            }
        } catch (_: Exception) {}

        // Load saved config for this provider (if exists)
        var savedBaseUrl: String? = null
        var savedApi: String? = null
        var savedModels: List<com.xiaomo.androidforclaw.config.ModelDefinition>? = null
        try {
            val config = configLoader.loadOpenClawConfig()
            val existingProvider = config.resolveProviders()[provider.id]
            if (existingProvider != null) {
                savedBaseUrl = existingProvider.baseUrl
                savedApi = existingProvider.api
                savedModels = existingProvider.models
            }
        } catch (_: Exception) {}

        // Tutorial
        if (provider.tutorialSteps.isNotEmpty()) {
            binding.cardTutorial.visibility = View.VISIBLE
            val steps = provider.tutorialSteps.mapIndexed { i, step ->
                "${i + 1}. $step"
            }.joinToString("\n")
            binding.tvTutorialSteps.text = steps

            if (provider.tutorialUrl.isNotBlank()) {
                binding.btnTutorialUrl.visibility = View.VISIBLE
                binding.btnTutorialUrl.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.tutorialUrl)))
                }
            } else {
                binding.btnTutorialUrl.visibility = View.GONE
            }
        } else {
            binding.cardTutorial.visibility = View.GONE
        }

        // Preset models + saved models from config
        discoveredModels.clear()
        val allModels = provider.presetModels.toMutableList()
        if (savedModels != null) {
            val presetIds = allModels.map { it.id }.toSet()
            for (m in savedModels) {
                if (m.id !in presetIds) {
                    allModels.add(PresetModel(
                        id = m.id,
                        name = m.id,
                        reasoning = m.reasoning,
                        free = false
                    ))
                    discoveredModels.add(PresetModel(
                        id = m.id,
                        name = m.id,
                        reasoning = m.reasoning,
                        free = false
                    ))
                }
            }
        }
        buildModelRadioGroup(allModels)

        // Discovery button
        if (provider.supportsDiscovery) {
            binding.btnDiscoverModels.visibility = View.VISIBLE
            binding.btnDiscoverModels.setOnClickListener { discoverModels(provider) }
        } else {
            binding.btnDiscoverModels.visibility = View.GONE
        }

        // Manual add button
        binding.btnAddModel.setOnClickListener { showAddModelDialog() }

        // Advanced section — auto-expand if user customized baseUrl
        advancedExpanded = savedBaseUrl != null && savedBaseUrl != provider.baseUrl
        binding.layoutAdvanced.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        binding.ivAdvancedArrow.rotation = if (advancedExpanded) 180f else 0f

        binding.cardAdvancedToggle.setOnClickListener {
            advancedExpanded = !advancedExpanded
            binding.layoutAdvanced.visibility =
                if (advancedExpanded) View.VISIBLE else View.GONE
            binding.ivAdvancedArrow.animate()
                .rotation(if (advancedExpanded) 180f else 0f)
                .setDuration(200)
                .start()
        }

        // Base URL: prefer saved config over preset
        binding.etBaseUrl.setText(savedBaseUrl ?: provider.baseUrl)

        // API type dropdown: prefer saved config over preset
        val apiTypeLabels = ProviderRegistry.CUSTOM_API_TYPES.map { it.second }
        val apiTypeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, apiTypeLabels)
        binding.dropdownApiType.setAdapter(apiTypeAdapter)
        val effectiveApi = savedApi ?: provider.api
        val currentApiIndex = ProviderRegistry.CUSTOM_API_TYPES.indexOfFirst { it.first == effectiveApi }
        if (currentApiIndex >= 0) {
            binding.dropdownApiType.setText(apiTypeLabels[currentApiIndex], false)
        }

        // Custom model ID field (only for custom provider)
        binding.tilCustomModelId.visibility =
            if (provider.id == "custom") View.VISIBLE else View.GONE

        // Save button
        binding.btnSave.setOnClickListener { saveProviderConfig(provider) }
    }

    private fun buildModelRadioGroup(models: List<PresetModel>) {
        binding.containerPresetModels.removeAllViews()
        val inflater = LayoutInflater.from(this)

        // Auto-select first model
        if (models.isNotEmpty() && selectedModelId == null) {
            selectedModelId = models.first().id
        }

        for (model in models) {
            val view = inflater.inflate(R.layout.item_model_radio, binding.containerPresetModels, false)

            val radio = view.findViewById<RadioButton>(R.id.radio_model)
            val tvName = view.findViewById<TextView>(R.id.tv_model_name)
            val tvId = view.findViewById<TextView>(R.id.tv_model_id)
            val tvBadge = view.findViewById<TextView>(R.id.tv_model_badge)

            tvName.text = model.name
            tvId.text = model.id

            if (model.reasoning) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "推理"
                tvBadge.setTextColor(getColor(android.R.color.holo_blue_dark))
            }

            radio.isChecked = model.id == selectedModelId

            // Click handler on entire row
            val clickHandler = View.OnClickListener {
                selectedModelId = model.id
                // Refresh all radios
                for (i in 0 until binding.containerPresetModels.childCount) {
                    val child = binding.containerPresetModels.getChildAt(i)
                    child.findViewById<RadioButton>(R.id.radio_model)?.isChecked =
                        child.findViewById<TextView>(R.id.tv_model_id)?.text == model.id
                }
            }
            radio.setOnClickListener(clickHandler)
            view.setOnClickListener(clickHandler)

            binding.containerPresetModels.addView(view)
        }
    }

    // ========== Model Discovery ==========

    private fun discoverModels(provider: ProviderDefinition) {
        val apiKey = binding.etApiKey.text?.toString()?.trim()
        val baseUrl = if (advancedExpanded) {
            binding.etBaseUrl.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        } else null
        val effectiveBaseUrl = baseUrl ?: provider.baseUrl

        if (effectiveBaseUrl.isBlank()) {
            Toast.makeText(this, "请填写 Base URL", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnDiscoverModels.isEnabled = false
        binding.btnDiscoverModels.text = "获取中..."

        scope.launch {
            try {
                val models = withContext(Dispatchers.IO) {
                    fetchModels(effectiveBaseUrl, provider.discoveryEndpoint, apiKey)
                }
                discoveredModels.clear()
                discoveredModels.addAll(models)

                // Merge with presets (presets first, then discovered)
                val allModels = (provider.presetModels + models.filter { m ->
                    provider.presetModels.none { it.id == m.id }
                })
                buildModelRadioGroup(allModels)

                Toast.makeText(
                    this@ModelConfigActivity,
                    "发现 ${models.size} 个模型",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Model discovery failed", e)
                Toast.makeText(
                    this@ModelConfigActivity,
                    "获取失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.btnDiscoverModels.isEnabled = true
                binding.btnDiscoverModels.text = "🔍 获取可用模型"
            }
        }
    }

    private fun fetchModels(baseUrl: String, endpoint: String, apiKey: String?): List<PresetModel> {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val url = "${baseUrl.trimEnd('/')}${endpoint}"
        val requestBuilder = Request.Builder().url(url).get()

        if (!apiKey.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body?.string() ?: throw Exception("Empty response")
        val json = JSONObject(body)

        return if (endpoint == "/api/tags") {
            // Ollama format
            parseOllamaModels(json)
        } else {
            // OpenAI /v1/models format
            parseOpenAIModels(json)
        }
    }

    private fun parseOpenAIModels(json: JSONObject): List<PresetModel> {
        val data = json.optJSONArray("data") ?: return emptyList()
        val models = mutableListOf<PresetModel>()
        for (i in 0 until data.length()) {
            val model = data.getJSONObject(i)
            val id = model.optString("id", "").trim()
            if (id.isBlank()) continue
            models.add(
                PresetModel(
                    id = id,
                    name = id, // API usually doesn't provide display names
                    contextWindow = 200000,  // Aligned with OpenClaw DEFAULT_CONTEXT_TOKENS
                    maxTokens = 8192
                )
            )
        }
        return models.sortedBy { it.id }
    }

    private fun parseOllamaModels(json: JSONObject): List<PresetModel> {
        val models = json.optJSONArray("models") ?: return emptyList()
        val result = mutableListOf<PresetModel>()
        for (i in 0 until models.length()) {
            val model = models.getJSONObject(i)
            val name = model.optString("name", "").trim()
            if (name.isBlank()) continue
            result.add(
                PresetModel(
                    id = name,
                    name = name,
                    contextWindow = 200000,  // Aligned with OpenClaw DEFAULT_CONTEXT_TOKENS
                    maxTokens = 8192
                )
            )
        }
        return result.sortedBy { it.id }
    }

    // ========== Manual Add Model ==========

    private fun showAddModelDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_model, null)

        val etModelId = dialogView.findViewById<TextInputEditText>(R.id.et_dialog_model_id)
        val etModelName = dialogView.findViewById<TextInputEditText>(R.id.et_dialog_model_name)
        val etContextWindow = dialogView.findViewById<TextInputEditText>(R.id.et_dialog_context_window)

        etContextWindow.setText("200000")  // Aligned with OpenClaw DEFAULT_CONTEXT_TOKENS

        AlertDialog.Builder(this)
            .setTitle("添加模型")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val modelId = etModelId.text?.toString()?.trim() ?: ""
                if (modelId.isBlank()) {
                    Toast.makeText(this, "模型 ID 不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val modelName = etModelName.text?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: modelId
                val ctxWindow = etContextWindow.text?.toString()?.toIntOrNull() ?: 200000

                val newModel = PresetModel(
                    id = modelId,
                    name = modelName,
                    contextWindow = ctxWindow,
                    maxTokens = 8192
                )
                discoveredModels.add(newModel)

                val provider = selectedProvider ?: return@setPositiveButton
                val allModels = provider.presetModels + discoveredModels.filter { m ->
                    provider.presetModels.none { it.id == m.id }
                }
                selectedModelId = modelId
                buildModelRadioGroup(allModels)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ========== Save ==========

    private fun saveProviderConfig(provider: ProviderDefinition) {
        val apiKey = binding.etApiKey.text?.toString()?.trim()

        // Validate
        if (provider.keyRequired && apiKey.isNullOrBlank()) {
            binding.tilApiKey.error = "请输入 API Key"
            return
        }
        binding.tilApiKey.error = null

        // Resolve model ID
        val modelId = if (provider.id == "custom" && advancedExpanded) {
            binding.etCustomModelId.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                ?: selectedModelId
        } else {
            selectedModelId
        }

        if (modelId.isNullOrBlank()) {
            Toast.makeText(this, "请选择或输入模型", Toast.LENGTH_SHORT).show()
            return
        }

        // Resolve advanced params
        val customBaseUrl = if (advancedExpanded) {
            binding.etBaseUrl.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        } else null

        val customApiType = if (advancedExpanded) {
            val selectedLabel = binding.dropdownApiType.text?.toString()
            ProviderRegistry.CUSTOM_API_TYPES.find { it.second == selectedLabel }?.first
        } else null

        // Resolve selected models for this provider
        val selectedModels = if (provider.id == "custom") {
            listOf(PresetModel(id = modelId, name = modelId))
        } else {
            val allAvailable = provider.presetModels + discoveredModels
            allAvailable.filter { it.id == modelId }
                .ifEmpty { listOf(PresetModel(id = modelId, name = modelId)) }
        }

        try {
            // Build new provider config
            val providerConfig = ProviderRegistry.buildProviderConfig(
                definition = provider,
                apiKey = apiKey,
                baseUrl = customBaseUrl,
                apiType = customApiType,
                selectedModels = selectedModels
            )

            // Load and merge config
            val config = configLoader.loadOpenClawConfig()
            val existingProviders = config.models?.providers?.toMutableMap() ?: mutableMapOf()

            // Determine the provider key to use
            val providerKey = if (provider.id == "custom") {
                // For custom, use user-defined ID or fallback to "custom"
                val customProviderId = binding.etCustomModelId.text?.toString()?.trim()
                    ?.split("/")?.firstOrNull()?.takeIf { it.isNotBlank() }
                customProviderId ?: "custom"
            } else {
                provider.id
            }

            existingProviders[providerKey] = providerConfig

            // Update model ref
            val modelRef = ProviderRegistry.buildModelRef(providerKey, modelId)
            val currentAgents = config.agents ?: AgentsConfig()
            val updatedAgents = currentAgents.copy(
                defaults = currentAgents.defaults.copy(
                    model = ModelSelectionConfig(primary = modelRef)
                )
            )

            val updatedConfig = config.copy(
                models = (config.models ?: ModelsConfig()).copy(
                    providers = existingProviders
                ),
                agents = updatedAgents
            )

            configLoader.saveOpenClawConfig(updatedConfig)

            Toast.makeText(this, "✅ 已保存: $modelRef", Toast.LENGTH_SHORT).show()
            Log.i(TAG, "Saved provider=$providerKey model=$modelRef")

            // Return to list or finish
            setResult(RESULT_OK)
            finish()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config", e)
            Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        if (binding.pageProviderDetail.visibility == View.VISIBLE) {
            showPage1()
        } else {
            super.onBackPressed()
        }
    }
}
