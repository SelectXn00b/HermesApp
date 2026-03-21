package com.xiaomo.androidforclaw.config

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ProviderRegistry 单元测试
 *
 * 验证 provider 从 JSON 加载、normalize 映射和注册完整性。
 */
class ProviderRegistryTest {

    @Before
    fun setup() {
        // 单元测试中没有 Android Context，直接从文件加载 JSON
        ProviderRegistry.reset()
        // Gradle test working dir is the module root (app/)
        val candidates = listOf(
            "src/main/assets/providers.json",
            "app/src/main/assets/providers.json"
        )
        val jsonFile = candidates.map { File(it) }.firstOrNull { it.exists() }
        if (jsonFile != null) {
            ProviderRegistry.initFromJson(jsonFile.readText())
        } else {
            // Fallback: use minimal inline JSON so tests don't NPE
            ProviderRegistry.initFromJson("""{"providers":[]}""")
        }
    }

    // ========== normalizeProviderId 测试 ==========

    @Test
    fun `normalizeProviderId maps kimi to moonshot`() {
        assertEquals("moonshot", ProviderRegistry.normalizeProviderId("kimi"))
        assertEquals("moonshot", ProviderRegistry.normalizeProviderId("KIMI"))
        assertEquals("moonshot", ProviderRegistry.normalizeProviderId("  kimi  "))
    }

    @Test
    fun `normalizeProviderId maps kimi-code and kimi-coding`() {
        assertEquals("kimi-coding", ProviderRegistry.normalizeProviderId("kimi-code"))
        assertEquals("kimi-coding", ProviderRegistry.normalizeProviderId("kimi-coding"))
        assertEquals("kimi-coding", ProviderRegistry.normalizeProviderId("KIMI-CODE"))
    }

    @Test
    fun `normalizeProviderId maps moonshot-cn to moonshot`() {
        assertEquals("moonshot", ProviderRegistry.normalizeProviderId("moonshot-cn"))
    }

    @Test
    fun `normalizeProviderId maps other known aliases`() {
        assertEquals("zai", ProviderRegistry.normalizeProviderId("z.ai"))
        assertEquals("qwen-portal", ProviderRegistry.normalizeProviderId("qwen"))
        assertEquals("amazon-bedrock", ProviderRegistry.normalizeProviderId("bedrock"))
        assertEquals("volcengine", ProviderRegistry.normalizeProviderId("doubao"))
    }

    @Test
    fun `normalizeProviderId passes through unknown IDs`() {
        assertEquals("openai", ProviderRegistry.normalizeProviderId("openai"))
        assertEquals("custom-xyz", ProviderRegistry.normalizeProviderId("custom-xyz"))
    }

    // ========== JSON 加载测试 ==========

    @Test
    fun `ALL list loads from providers_json`() {
        assertTrue("Should have providers loaded", ProviderRegistry.ALL.isNotEmpty())
        assertTrue("Should have at least 10 providers", ProviderRegistry.ALL.size >= 10)
    }

    @Test
    fun `ALL list is sorted by order`() {
        val orders = ProviderRegistry.ALL.map { it.order }
        assertEquals("Providers should be sorted by order", orders, orders.sorted())
    }

    @Test
    fun `PRIMARY group is non-empty`() {
        assertTrue(ProviderRegistry.PRIMARY_PROVIDERS.isNotEmpty())
    }

    @Test
    fun `findById works after JSON load`() {
        val openrouter = ProviderRegistry.findById("openrouter")
        assertNotNull(openrouter)
        assertEquals("OpenRouter", openrouter!!.name)
    }

    // ========== Provider 内容测试 ==========

    @Test
    fun `ALL list contains moonshot provider`() {
        val moonshot = ProviderRegistry.ALL.find { it.id == "moonshot" }
        assertNotNull("moonshot provider must exist in ALL", moonshot)
        assertEquals("Moonshot (Kimi)", moonshot!!.name)
        assertEquals("https://api.moonshot.ai/v1", moonshot.baseUrl)
        assertEquals(ModelApi.OPENAI_COMPLETIONS, moonshot.api)
    }

    @Test
    fun `ALL list contains kimi-coding provider`() {
        val kimiCoding = ProviderRegistry.ALL.find { it.id == "kimi-coding" }
        assertNotNull("kimi-coding provider must exist in ALL", kimiCoding)
        assertEquals("Kimi for Coding", kimiCoding!!.name)
        assertEquals("https://api.kimi.com/coding/", kimiCoding.baseUrl)
        assertEquals(ModelApi.ANTHROPIC_MESSAGES, kimiCoding.api)
    }

    @Test
    fun `moonshot has preset models from JSON`() {
        val moonshot = ProviderRegistry.ALL.find { it.id == "moonshot" }!!
        assertTrue("moonshot should have preset models", moonshot.presetModels.isNotEmpty())
        val model = moonshot.presetModels.first()
        assertEquals("kimi-k2.5", model.id)
        assertEquals(256000, model.contextWindow)
    }

    @Test
    fun `kimi-coding has preset models from JSON`() {
        val kimiCoding = ProviderRegistry.ALL.find { it.id == "kimi-coding" }!!
        assertTrue("kimi-coding should have preset models", kimiCoding.presetModels.isNotEmpty())
        val model = kimiCoding.presetModels.first()
        assertEquals("kimi-code", model.id)
        assertEquals(262144, model.contextWindow)
        assertEquals(32768, model.maxTokens)
    }

    @Test
    fun `anthropic uses anthropic-messages API`() {
        val anthropic = ProviderRegistry.ALL.find { it.id == "anthropic" }
        assertNotNull(anthropic)
        assertEquals(ModelApi.ANTHROPIC_MESSAGES, anthropic!!.api)
        assertFalse("Anthropic authHeader should be false", anthropic.authHeader)
    }

    @Test
    fun `openrouter is discovery-capable`() {
        val openrouter = ProviderRegistry.ALL.find { it.id == "openrouter" }
        assertNotNull(openrouter)
        assertTrue(openrouter!!.supportsDiscovery)
    }

    @Test
    fun `ollama has custom discovery endpoint`() {
        val ollama = ProviderRegistry.ALL.find { it.id == "ollama" }
        assertNotNull(ollama)
        assertEquals("/api/tags", ollama!!.discoveryEndpoint)
    }

    // ========== buildProviderConfig 测试 ==========

    @Test
    fun `buildProviderConfig uses definition defaults`() {
        val anthropic = ProviderRegistry.ALL.find { it.id == "anthropic" }!!
        val config = ProviderRegistry.buildProviderConfig(
            definition = anthropic,
            apiKey = "sk-ant-test"
        )
        assertEquals("https://api.anthropic.com", config.baseUrl)
        assertEquals(ModelApi.ANTHROPIC_MESSAGES, config.api)
        assertEquals("sk-ant-test", config.apiKey)
        assertFalse(config.authHeader)
    }

    @Test
    fun `buildProviderConfig allows baseUrl override`() {
        val openai = ProviderRegistry.ALL.find { it.id == "openai" }!!
        val config = ProviderRegistry.buildProviderConfig(
            definition = openai,
            apiKey = "sk-test",
            baseUrl = "https://my-proxy.com/v1"
        )
        assertEquals("https://my-proxy.com/v1", config.baseUrl)
    }

    @Test
    fun `buildModelRef creates correct format`() {
        assertEquals("openrouter/auto", ProviderRegistry.buildModelRef("openrouter", "auto"))
        assertEquals("anthropic/claude-sonnet-4-6", ProviderRegistry.buildModelRef("anthropic", "claude-sonnet-4-6"))
    }

    // ========== initFromJson 测试 ==========

    @Test
    fun `initFromJson parses minimal JSON correctly`() {
        ProviderRegistry.reset()
        ProviderRegistry.initFromJson("""
        {
            "providers": [
                {
                    "id": "test-provider",
                    "name": "Test",
                    "baseUrl": "https://test.com/v1",
                    "api": "openai-completions",
                    "keyRequired": true,
                    "keyHint": "Key",
                    "order": 1,
                    "group": "primary",
                    "models": [
                        {"id": "test-model", "name": "Test Model", "contextWindow": 4096}
                    ]
                }
            ]
        }
        """.trimIndent())

        assertEquals(1, ProviderRegistry.ALL.size)
        assertEquals("test-provider", ProviderRegistry.ALL[0].id)
        assertEquals(1, ProviderRegistry.ALL[0].presetModels.size)
        assertEquals(4096, ProviderRegistry.ALL[0].presetModels[0].contextWindow)
    }
}
