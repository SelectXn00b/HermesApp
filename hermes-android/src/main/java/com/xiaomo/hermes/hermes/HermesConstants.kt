package com.xiaomo.hermes.hermes

import android.content.Context
import java.io.File

/**
 * Hermes 常量定义和全局配置
 * 1:1 对齐 hermes-agent/hermes_constants.py
 *
 * 所有路径默认在 Android 内部存储中。
 * HERMES_HOME = context.filesDir/.hermes
 */

// ── 版本号（硬编码，Python 版本从 __init__ 读取）─────────────────────
const val HERMES_VERSION = "0.4.1"

// ── Provider 常量 ──────────────────────────────────────────────────────
const val PROVIDER_OPENAI = "openai"
const val PROVIDER_ANTHROPIC = "anthropic"
const val PROVIDER_OPENROUTER = "openrouter"
const val PROVIDER_AIMLAPI = "aimlapi"
const val PROVIDER_DEEPSEEK = "deepseek"
const val PROVIDER_TOGETHERAI = "togetherai"
const val PROVIDER_GEMINI = "gemini"
const val PROVIDER_OLLAMA = "ollama"
const val PROVIDER_VLLM = "vllm"
const val PROVIDER_LITELLM = "litellm"
const val PROVIDER_SGLANG = "sglang"

// ── 默认值 ──────────────────────────────────────────────────────────────
const val DEFAULT_PROVIDER = PROVIDER_OPENROUTER
const val DEFAULT_MODEL = "anthropic/claude-sonnet-4"
const val DEFAULT_TEMPERATURE = 0.0
const val DEFAULT_MAX_TOKENS = 8192
const val DEFAULT_TOP_P = 1.0

// ── 引导常量 ────────────────────────────────────────────────────────────
const val BOOTSTRAP_SESSION_DIR = "auto-setup"
const val BOOTSTRAP_SESSION_TITLE = "bootstrap"
const val BOOTSTRAP_SYSTEM = (
    "You are a terminal assistant helping bootstrap a coding agent. " +
    "Your job is to do FIRST RUN SETUP, which means: " +
    "1. Discover the user's system and capabilities. " +
    "2. Set up their configuration including models, paths, and plugins. " +
    "3. Get them into a state where they can run coding tasks. " +
    "Be direct, ask questions when needed, and set things up efficiently."
)

// ── 未知参数修复 ─────────────────────────────────────────────────────────
const val FIX_UNKNOWN_PARAMS_SYSTEM = (
    "You are a system that fixes unknown model parameters. " +
    "The user is using a provider/model that doesn't accept certain parameters. " +
    "Look at the error message and the model settings, then fix the issue. " +
    "The goal is to update the configuration so it works for the user's provider."
)

// ── 保存对话标题生成 ─────────────────────────────────────────────────────
const val SAVE_TITLE_SYSTEM = (
    "Generate a short title for this conversation. " +
    "Respond with just the title text, 5 words max, no quotes, no period, no prefix."
)

// ── Mode 常量 ────────────────────────────────────────────────────────────
const val MODE_CHAT = "chat"
const val MODE_PLAN = "plan"
const val MODE_CODE = "code"
const val MODE_GIT = "git"
const val MODE_RUN = "run"
const val MODE_TALK = "talk"
val VALID_MODES = setOf(MODE_CHAT, MODE_PLAN, MODE_CODE, MODE_GIT, MODE_RUN, MODE_TALK)

// ── Session 状态常量 ────────────────────────────────────────────────────
const val SESSION_STATE_ACTIVE = "active"
const val SESSION_STATE_PAUSED = "paused"
const val SESSION_STATE_COMPLETED = "completed"
const val SESSION_STATE_ARCHIVED = "archived"
val VALID_SESSION_STATES = setOf(SESSION_STATE_ACTIVE, SESSION_STATE_PAUSED, SESSION_STATE_COMPLETED, SESSION_STATE_ARCHIVED)

// ── Provider 信息 ────────────────────────────────────────────────────────
data class ProviderInfo(
    val name: String,
    val displayName: String,
    val description: String,
    val envVars: List<String>,
    val baseUrlTemplate: String,
    val authHeader: String,
    val defaultModel: String,
    val requiresApiKey: Boolean = true)

val PROVIDERS: Map<String, ProviderInfo> = mapOf(
    PROVIDER_OPENAI to ProviderInfo(
        name = PROVIDER_OPENAI,
        displayName = "OpenAI",
        description = "OpenAI GPT-4o, o1, o3",
        envVars = listOf("OPENAI_API_KEY"),
        baseUrlTemplate = "https://api.openai.com/v1",
        authHeader = "Authorization",
        defaultModel = "gpt-4o"),
    PROVIDER_ANTHROPIC to ProviderInfo(
        name = PROVIDER_ANTHROPIC,
        displayName = "Anthropic",
        description = "Anthropic Claude (Sonnet, Opus, Haiku)",
        envVars = listOf("ANTHROPIC_API_KEY"),
        baseUrlTemplate = "https://api.anthropic.com/v1",
        authHeader = "x-api-key",
        defaultModel = "claude-sonnet-4-20250514"),
    PROVIDER_OPENROUTER to ProviderInfo(
        name = PROVIDER_OPENROUTER,
        displayName = "OpenRouter",
        description = "OpenRouter (multi-model proxy, recommended default)",
        envVars = listOf("OPENROUTER_API_KEY"),
        baseUrlTemplate = "https://openrouter.ai/api/v1",
        authHeader = "Authorization",
        defaultModel = DEFAULT_MODEL),
    PROVIDER_AIMLAPI to ProviderInfo(
        name = PROVIDER_AIMLAPI,
        displayName = "AIMLAPI",
        description = "AIMLAPI (multi-model proxy)",
        envVars = listOf("AIMLAPI_API_KEY"),
        baseUrlTemplate = "https://api.aimlapi.com/v1",
        authHeader = "Authorization",
        defaultModel = "gpt-4o"),
    PROVIDER_DEEPSEEK to ProviderInfo(
        name = PROVIDER_DEEPSEEK,
        displayName = "DeepSeek",
        description = "DeepSeek V3, R1",
        envVars = listOf("DEEPSEEK_API_KEY"),
        baseUrlTemplate = "https://api.deepseek.com/v1",
        authHeader = "Authorization",
        defaultModel = "deepseek-chat"),
    PROVIDER_TOGETHERAI to ProviderInfo(
        name = PROVIDER_TOGETHERAI,
        displayName = "Together AI",
        description = "Together AI (open models)",
        envVars = listOf("TOGETHER_API_KEY"),
        baseUrlTemplate = "https://api.together.xyz/v1",
        authHeader = "Authorization",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
    PROVIDER_GEMINI to ProviderInfo(
        name = PROVIDER_GEMINI,
        displayName = "Gemini",
        description = "Google Gemini Pro / Flash",
        envVars = listOf("GEMINI_API_KEY"),
        baseUrlTemplate = "https://generativelanguage.googleapis.com/v1beta/openai",
        authHeader = "Authorization",
        defaultModel = "gemini-2.0-flash"),
    PROVIDER_OLLAMA to ProviderInfo(
        name = PROVIDER_OLLAMA,
        displayName = "Ollama",
        description = "Ollama (local models)",
        envVars = listOf(),
        baseUrlTemplate = "http://localhost:11434/v1",
        authHeader = "Authorization",
        defaultModel = "qwen3",
        requiresApiKey = false),
    PROVIDER_VLLM to ProviderInfo(
        name = PROVIDER_VLLM,
        displayName = "vLLM",
        description = "vLLM (self-hosted)",
        envVars = listOf("VLLM_API_KEY"),
        baseUrlTemplate = "http://localhost:8000/v1",
        authHeader = "Authorization",
        defaultModel = "qwen3",
        requiresApiKey = false),
    PROVIDER_LITELLM to ProviderInfo(
        name = PROVIDER_LITELLM,
        displayName = "LiteLLM",
        description = "LiteLLM (local proxy)",
        envVars = listOf("LITELLM_API_KEY"),
        baseUrlTemplate = "http://localhost:4000/v1",
        authHeader = "Authorization",
        defaultModel = "gpt-4o",
        requiresApiKey = false),
    PROVIDER_SGLANG to ProviderInfo(
        name = PROVIDER_SGLANG,
        displayName = "SGLang",
        description = "SGLang (self-hosted)",
        envVars = listOf("SGLANG_API_KEY"),
        baseUrlTemplate = "http://localhost:30000/v1",
        authHeader = "Authorization",
        defaultModel = "qwen3",
        requiresApiKey = false))

// ── 全局 Android Context（需要在 Application.onCreate 中初始化）─────────
private var _appContext: Context? = null

fun initHermesConstants(context: Context) {
    _appContext = context.applicationContext
}

fun getAppContext(): Context {
    return _appContext ?: throw IllegalStateException(
        "Hermes constants not initialized. Call initHermesConstants(context) in Application.onCreate()."
    )
}

/**
 * 获取 HERMES_HOME 目录
 * Python: Path.home() / ".hermes"
 * Android: context.filesDir / ".hermes"
 */
fun getHermesHome(): File {
    val home = File(getAppContext().filesDir, ".hermes")
    if (!home.exists()) {
        home.mkdirs()
    }
    return home
}

/**
 * 获取 HEROES_DIR (inside HERMES_HOME)
 */
fun getHermesDir(): File {
    return getHermesHome()
}

/**
 * 获取 memories 目录
 */
fun getMemoriesDir(): File {
    val dir = File(getHermesHome(), "memories")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

/**
 * 获取 sessions 目录
 */
fun getSessionsDir(): File {
    val dir = File(getHermesHome(), "sessions")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

/**
 * 获取 workspace 目录
 */
fun getWorkspaceDir(): File {
    val dir = File(getHermesHome(), "workspace")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

/**
 * 获取 exports 目录
 */
fun getExportsDir(): File {
    val dir = File(getHermesHome(), "exports")
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

/**
 * 获取 state 数据库路径
 */
fun getStateDbPath(): File {
    return File(getHermesHome(), "state.db")
}

/**
 * 获取 config.yaml 路径
 */
fun getConfigPath(): File {
    return File(getHermesHome(), "config.yaml")
}

/**
 * 获取配置
 * Python: load_config() + merge with defaults
 */
fun getHermesConfig(): Map<String, Any> {
    val defaults = mutableMapOf<String, Any>(
        "provider" to DEFAULT_PROVIDER,
        "model" to DEFAULT_MODEL,
        "temperature" to DEFAULT_TEMPERATURE,
        "mode" to MODE_CHAT,
        "max_tokens" to DEFAULT_MAX_TOKENS,
        "top_p" to DEFAULT_TOP_P)

    val configFile = getConfigPath()
    if (configFile.exists()) {
        try {
            // yaml 配置加载（Android 简化版，使用 snakeyaml 或手动解析）
            // 这里简化为返回 defaults，实际使用时需要集成 snakeyaml
            return defaults
        } catch (e: Exception) {
            return defaults
        }
    }
    return defaults
}

/**
 * 验证 provider 名称
 */
fun isValidProvider(provider: String): Boolean {
    return provider in PROVIDERS
}

/**
 * 验证 mode 名称
 */
fun isValidMode(mode: String): Boolean {
    return mode in VALID_MODES
}

/**
 * 验证 session 状态
 */
fun isValidSessionState(state: String): Boolean {
    return state in VALID_SESSION_STATES


}

// ── Logger helper ──────────────────────────────────────────────────

/** Simple logger wrapper using Android Log. */
class HermesLogger(private val tag: String) {
    fun debug(msg: String) { android.util.Log.d(tag, msg) }
    fun info(msg: String) { android.util.Log.i(tag, msg) }
    fun warning(msg: String) { android.util.Log.w(tag, msg) }
    fun error(msg: String) { android.util.Log.e(tag, msg) }
    fun debug(format: String, vararg args: Any?) { android.util.Log.d(tag, format.format()) }
    fun info(format: String, vararg args: Any?) { android.util.Log.i(tag, format.format()) }
    fun warning(format: String, vararg args: Any?) { android.util.Log.w(tag, format.format()) }
    fun error(format: String, vararg args: Any?) { android.util.Log.e(tag, format.format()) }
}

/** Get a logger instance. */
fun getLogger(tag: String): HermesLogger = HermesLogger(tag)
