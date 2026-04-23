package com.ai.assistance.operit.integrations.intent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ai.assistance.operit.data.collects.ApiProviderConfigs
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收 adb 广播，把 API key 直接写入默认模型配置。
 *
 * 典型用法：
 *   adb shell am broadcast \
 *     -n com.xiaomo.androidforclaw/com.ai.assistance.operit.integrations.intent.ApiConfigReceiver \
 *     -a com.ai.assistance.operit.SET_API_KEY \
 *     --es key 'sk-or-v1-xxxxxxxx' \
 *     --es provider OPENROUTER
 *
 * 可选 extras:
 *   --es key       API key（必填）
 *   --es provider  ApiProviderType 枚举名，默认 OPENROUTER；不识别时也默认 OPENROUTER
 *   --es endpoint  自定义 endpoint，缺省时按 provider 默认
 *   --es model     自定义 modelName，缺省时按 provider 默认
 *   --es config_id 要更新的配置 ID，默认 "default"
 *
 * 成功/失败都会以 logcat tag `ApiConfigReceiver` 打印，可用 `adb logcat -v time -s ApiConfigReceiver` 观察。
 */
class ApiConfigReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ApiConfigReceiver"

        const val ACTION_SET_API_KEY = "com.ai.assistance.operit.SET_API_KEY"

        const val EXTRA_KEY = "key"
        const val EXTRA_PROVIDER = "provider"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_MODEL = "model"
        const val EXTRA_CONFIG_ID = "config_id"

        private const val DEFAULT_CONFIG_ID = "default"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_API_KEY) return

        val key = intent.getStringExtra(EXTRA_KEY)?.trim().orEmpty()
        if (key.isEmpty()) {
            AppLogger.e(TAG, "Missing --es key, ignored. action=${intent.action}")
            return
        }

        val providerArg = intent.getStringExtra(EXTRA_PROVIDER)?.trim().orEmpty()
        val provider = runCatching { ApiProviderType.valueOf(providerArg.uppercase()) }
            .getOrElse {
                if (providerArg.isNotEmpty()) {
                    AppLogger.w(TAG, "Unknown provider '$providerArg', falling back to OPENROUTER")
                }
                ApiProviderType.OPENROUTER
            }

        val endpoint = intent.getStringExtra(EXTRA_ENDPOINT)?.takeIf { it.isNotBlank() }
            ?: ApiProviderConfigs.getDefaultApiEndpoint(provider)
        val model = intent.getStringExtra(EXTRA_MODEL)?.takeIf { it.isNotBlank() }
            ?: ApiProviderConfigs.getDefaultModelName(provider)
        val configId = intent.getStringExtra(EXTRA_CONFIG_ID)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_CONFIG_ID

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = ModelConfigManager(context.applicationContext)
                manager.initializeIfNeeded()
                manager.updateModelConfig(
                    configId = configId,
                    apiKey = key,
                    apiEndpoint = endpoint,
                    modelName = model,
                    apiProviderType = provider
                )
                AppLogger.i(
                    TAG,
                    "Updated config '$configId': provider=$provider endpoint=$endpoint model=$model keyLen=${key.length}"
                )
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Failed to update config via broadcast", e)
            } finally {
                pending.finish()
            }
        }
    }
}
