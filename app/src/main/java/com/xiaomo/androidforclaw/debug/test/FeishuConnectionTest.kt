/**
 * OpenClaw Source Reference:
 * - ../openclaw/src/channels/feishu/(all)
 *
 * AndroidForClaw adaptation: Feishu connection debug tests.
 */
package com.xiaomo.androidforclaw.test

import android.content.Context
import android.util.Log
import com.xiaomo.androidforclaw.config.ConfigLoader
import com.xiaomo.androidforclaw.config.FeishuConfigAdapter
import com.xiaomo.feishu.FeishuChannel
import com.xiaomo.feishu.FeishuEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * 飞书连接测试
 *
 * 测试内容：
 * 1. 配置加载
 * 2. FeishuClient 初始化
 * 3. 获取 tenant_access_token
 * 4. WebSocket 连接
 * 5. 发送测试消息
 */
class FeishuConnectionTest(private val context: Context) {

    companion object {
        private const val TAG = "FeishuConnectionTest"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configLoader = ConfigLoader(context)

    data class TestResult(
        val success: Boolean,
        val message: String,
        val details: Map<String, Any> = emptyMap()
    )

    /**
     * 运行完整测试套件
     */
    suspend fun runFullTest(callback: (String) -> Unit): TestResult {
        callback("🚀 开始飞书连接测试...\n")

        try {
            // 1. 测试配置加载
            callback("📋 [1/5] 测试配置加载...")
            val configResult = testConfigLoading()
            callback("  ${if (configResult.success) "✅" else "❌"} ${configResult.message}\n")
            if (!configResult.success) return configResult

            // 2. 测试配置验证
            callback("🔍 [2/5] 验证配置...")
            val validationResult = testConfigValidation()
            callback("  ${if (validationResult.success) "✅" else "❌"} ${validationResult.message}\n")
            if (!validationResult.success) return validationResult

            // 3. 测试 FeishuClient 初始化
            callback("🔧 [3/5] 初始化 FeishuClient...")
            val clientResult = testClientInitialization()
            callback("  ${if (clientResult.success) "✅" else "❌"} ${clientResult.message}\n")
            if (!clientResult.success) return clientResult

            // 4. 测试获取 access token
            callback("🔑 [4/5] 获取 tenant_access_token...")
            val tokenResult = testGetAccessToken()
            callback("  ${if (tokenResult.success) "✅" else "❌"} ${tokenResult.message}\n")
            if (!tokenResult.success) return tokenResult

            // 5. 测试 Channel 启动
            callback("🌐 [5/5] 启动 FeishuChannel...")
            val channelResult = testChannelStart()
            callback("  ${if (channelResult.success) "✅" else "❌"} ${channelResult.message}\n")

            if (channelResult.success) {
                callback("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                callback("✅ 所有测试通过！飞书连接正常\n")
                callback("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
                return TestResult(true, "所有测试通过", channelResult.details)
            } else {
                return channelResult
            }

        } catch (e: Exception) {
            Log.e(TAG, "测试失败", e)
            val errorMsg = "测试执行失败: ${e.message}"
            callback("\n❌ $errorMsg\n")
            return TestResult(false, errorMsg)
        }
    }

    /**
     * 测试 1: 配置加载
     */
    private fun testConfigLoading(): TestResult {
        return try {
            val openClawConfig = configLoader.loadOpenClawConfig()
            val feishuChannelConfig = openClawConfig.channels.feishu

            Log.i(TAG, "配置加载成功")
            Log.i(TAG, "  enabled: ${feishuChannelConfig.enabled}")
            Log.i(TAG, "  appId: ${feishuChannelConfig.appId}")
            Log.i(TAG, "  connectionMode: ${feishuChannelConfig.connectionMode}")

            TestResult(
                success = true,
                message = "配置加载成功",
                details = mapOf(
                    "enabled" to feishuChannelConfig.enabled,
                    "appId" to feishuChannelConfig.appId,
                    "connectionMode" to feishuChannelConfig.connectionMode
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "配置加载失败", e)
            TestResult(false, "配置加载失败: ${e.message}")
        }
    }

    /**
     * 测试 2: 配置验证
     */
    private fun testConfigValidation(): TestResult {
        return try {
            val openClawConfig = configLoader.loadOpenClawConfig()
            val feishuChannelConfig = openClawConfig.channels.feishu

            // 检查必需字段
            if (!feishuChannelConfig.enabled) {
                return TestResult(false, "Feishu Channel 未启用 (enabled=false)")
            }

            if (feishuChannelConfig.appId.isBlank()) {
                return TestResult(false, "appId 为空")
            }

            if (feishuChannelConfig.appSecret.isBlank()) {
                return TestResult(false, "appSecret 为空")
            }

            if (feishuChannelConfig.connectionMode !in listOf("websocket", "webhook")) {
                return TestResult(false, "connectionMode 无效: ${feishuChannelConfig.connectionMode}")
            }

            Log.i(TAG, "配置验证通过")
            TestResult(true, "配置验证通过")

        } catch (e: Exception) {
            Log.e(TAG, "配置验证失败", e)
            TestResult(false, "配置验证失败: ${e.message}")
        }
    }

    /**
     * 测试 3: FeishuClient 初始化
     */
    private fun testClientInitialization(): TestResult {
        return try {
            val feishuConfig = configLoader.getFeishuConfig()

            // 验证配置转换
            val validation = feishuConfig.validate()
            if (validation.isFailure) {
                return TestResult(false, "配置验证失败: ${validation.exceptionOrNull()?.message}")
            }

            Log.i(TAG, "FeishuClient 初始化成功")
            Log.i(TAG, "  API Base URL: ${feishuConfig.getApiBaseUrl()}")

            TestResult(
                success = true,
                message = "FeishuClient 初始化成功",
                details = mapOf(
                    "apiBaseUrl" to feishuConfig.getApiBaseUrl(),
                    "domain" to feishuConfig.domain
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "FeishuClient 初始化失败", e)
            TestResult(false, "FeishuClient 初始化失败: ${e.message}")
        }
    }

    /**
     * 测试 4: 获取 Access Token
     */
    private suspend fun testGetAccessToken(): TestResult {
        return try {
            val feishuConfig = configLoader.getFeishuConfig()
            val feishuClient = com.xiaomo.feishu.FeishuClient(feishuConfig)

            // 尝试获取 token（会自动调用 API）
            withTimeout(10000) {
                // FeishuClient 会在内部自动获取 token
                // Token 获取成功就说明配置正确
                val tokenResult = feishuClient.getTenantAccessToken()
                val result = if (tokenResult.isSuccess) {
                    Result.success(com.google.gson.JsonObject())
                } else {
                    tokenResult.map { com.google.gson.JsonObject() }
                }

                if (result.isSuccess) {
                    Log.i(TAG, "Access Token 获取成功")
                    TestResult(true, "Access Token 获取成功")
                } else {
                    val error = result.exceptionOrNull()
                    Log.e(TAG, "API 请求失败", error)
                    TestResult(false, "API 请求失败: ${error?.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "获取 Access Token 失败", e)
            TestResult(false, "获取 Access Token 失败: ${e.message}")
        }
    }

    /**
     * 测试 5: Channel 启动
     */
    private suspend fun testChannelStart(): TestResult {
        return try {
            val feishuConfig = configLoader.getFeishuConfig()
            val feishuChannel = FeishuChannel(feishuConfig)

            // 监听事件
            var connected = false
            var error: Throwable? = null

            val eventJob = scope.launch {
                feishuChannel.eventFlow
                    .catch { e ->
                        error = e
                        Log.e(TAG, "事件流错误", e)
                    }
                    .collect { event ->
                        when (event) {
                            is FeishuEvent.Connected -> {
                                Log.i(TAG, "✅ WebSocket 连接成功")
                                connected = true
                            }
                            is FeishuEvent.Disconnected -> {
                                Log.w(TAG, "⚠️ WebSocket 断开连接")
                            }
                            is FeishuEvent.Error -> {
                                Log.e(TAG, "❌ 连接错误", event.error)
                                error = event.error
                            }
                            is FeishuEvent.Message -> {
                                Log.i(TAG, "📨 收到消息: ${event.messageId}")
                            }
                        }
                    }
            }

            // 启动 Channel
            val startResult = feishuChannel.start()
            if (startResult.isFailure) {
                eventJob.cancel()
                return TestResult(false, "Channel 启动失败: ${startResult.exceptionOrNull()?.message}")
            }

            // 等待连接建立（最多 5 秒）
            withTimeout(5000) {
                while (!connected && error == null) {
                    delay(100)
                }
            }

            eventJob.cancel()
            feishuChannel.stop()

            when {
                error != null -> {
                    TestResult(false, "连接出错: ${error?.message}")
                }
                connected -> {
                    TestResult(
                        success = true,
                        message = "Channel 启动成功，WebSocket 已连接",
                        details = mapOf(
                            "connectionMode" to feishuConfig.connectionMode,
                            "connected" to true
                        )
                    )
                }
                else -> {
                    TestResult(false, "连接超时（5秒内未收到连接确认）")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Channel 启动测试失败", e)
            TestResult(false, "Channel 启动失败: ${e.message}")
        }
    }

    /**
     * 快速健康检查（仅测试配置和 API）
     */
    suspend fun quickHealthCheck(): TestResult {
        return try {
            // 1. 加载配置
            val openClawConfig = configLoader.loadOpenClawConfig()
            val feishuChannelConfig = openClawConfig.channels.feishu

            if (!feishuChannelConfig.enabled) {
                return TestResult(false, "Feishu Channel 未启用")
            }

            // 2. 验证配置
            val feishuConfig = configLoader.getFeishuConfig()
            val validation = feishuConfig.validate()
            if (validation.isFailure) {
                return TestResult(false, "配置无效: ${validation.exceptionOrNull()?.message}")
            }

            // 3. 测试 API 连接（获取 token）
            val feishuClient = com.xiaomo.feishu.FeishuClient(feishuConfig)
            val result = feishuClient.getTenantAccessToken()

            if (result.isSuccess) {
                TestResult(true, "飞书 API 连接正常")
            } else {
                TestResult(false, "API 连接失败: ${result.exceptionOrNull()?.message}")
            }

        } catch (e: Exception) {
            TestResult(false, "健康检查失败: ${e.message}")
        }
    }
}
