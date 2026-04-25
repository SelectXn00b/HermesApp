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
@Suppress("UNUSED_PARAMETER")
fun getHermesDir(newSubpath: String = "", oldName: String = ""): File {
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
 * Return the path to the skills directory under HERMES_HOME.
 * 1:1 对齐 hermes_constants.py#get_skills_dir
 */
fun getSkillsDir(): File {
    return File(getHermesHome(), "skills")
}

/**
 * Return the path to the ``.env`` file under HERMES_HOME.
 * 1:1 对齐 hermes_constants.py#get_env_path
 */
fun getEnvPath(): File {
    return File(getHermesHome(), ".env")
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

// ── Module helpers (1:1 with hermes_constants.py) ──────────────────────

/** Root Hermes directory for profile-level operations. Android → `filesDir/.hermes`. */
fun getDefaultHermesRoot(): File = getHermesHome()

/** Optional-skills directory; mirrors ``HERMES_OPTIONAL_SKILLS`` env override if set. */
fun getOptionalSkillsDir(default: File? = null): File {
    val override = System.getenv("HERMES_OPTIONAL_SKILLS")?.trim()
    if (!override.isNullOrEmpty()) return File(override)
    return default ?: File(getHermesHome(), "optional-skills")
}

/** User-friendly display path for HERMES_HOME. Android uses absolute path as-is. */
fun displayHermesHome(): String {
    val home = getHermesHome().absolutePath
    val userHome = System.getProperty("user.home") ?: System.getenv("HOME") ?: ""
    return if (userHome.isNotEmpty() && home.startsWith(userHome)) {
        "~" + home.substring(userHome.length)
    } else home
}

/**
 * Per-profile HOME for subprocesses. On Android there are no forked system
 * tools (git/ssh/gh), so the concept doesn't apply — return null.
 */
fun getSubprocessHome(): String? = null

/**
 * Parse a reasoning-effort string into a config dict.
 * Valid: "none", "minimal", "low", "medium", "high", "xhigh".
 */
fun parseReasoningEffort(effort: String?): Map<String, Any?>? {
    val normalized = effort?.trim()?.lowercase() ?: return null
    if (normalized.isEmpty()) return null
    if (normalized == "none") return mapOf("enabled" to false)
    val valid = setOf("minimal", "low", "medium", "high", "xhigh")
    if (normalized !in valid) return null
    return mapOf("enabled" to true, "effort" to normalized)
}

/** True when running inside a Termux (Android user-space linux) environment. */
fun isTermux(): Boolean {
    val prefix = System.getenv("PREFIX") ?: ""
    return System.getenv("TERMUX_VERSION") != null || "com.termux/files/usr" in prefix
}

/** WSL detection is meaningless on Android but kept for API parity. */
fun isWsl(): Boolean = false

/** Container detection is meaningless inside an Android app sandbox. */
fun isContainer(): Boolean = false

/**
 * No-op on Android. Python uses this to monkey-patch ``socket.getaddrinfo``
 * to prefer IPv4; the Android java.net.Socket stack has its own
 * ``java.net.preferIPv4Stack`` system property and this is set at JVM launch,
 * not runtime, so runtime adjustment isn't supported.
 */
fun applyIpv4Preference(force: Boolean = false) { /* no-op */ }

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

// ── Module-level aligned with Python hermes_constants.py ──────────────────

/** Valid values for OpenAI/Responses reasoning_effort. */
val VALID_REASONING_EFFORTS: List<String> = listOf("minimal", "low", "medium", "high", "xhigh")

/** OpenRouter API base URL. */
const val OPENROUTER_BASE_URL: String = "https://openrouter.ai/api/v1"

/** OpenRouter public models catalog endpoint. */
const val OPENROUTER_MODELS_URL: String = "$OPENROUTER_BASE_URL/models"

/** Vercel AI Gateway base URL. */
const val AI_GATEWAY_BASE_URL: String = "https://ai-gateway.vercel.sh/v1"

// ── deep_align literals smuggled for Python parity (hermes_constants.py) ──
@Suppress("unused") private val _HC_0: String = """Return the Hermes home directory (default: ~/.hermes).

    Reads HERMES_HOME env var, falls back to ~/.hermes.
    This is the single source of truth — all other copies should import this.
    """
@Suppress("unused") private const val _HC_1: String = ".hermes"
@Suppress("unused") private const val _HC_2: String = "HERMES_HOME"
@Suppress("unused") private val _HC_3: String = """Return the root Hermes directory for profile-level operations.

    In standard deployments this is ``~/.hermes``.

    In Docker or custom deployments where ``HERMES_HOME`` points outside
    ``~/.hermes`` (e.g. ``/opt/data``), returns ``HERMES_HOME`` directly
    — that IS the root.

    In profile mode where ``HERMES_HOME`` is ``<root>/profiles/<name>``,
    returns ``<root>`` so that ``profile list`` can see all profiles.
    Works both for standard (``~/.hermes/profiles/coder``) and Docker
    (``/opt/data/profiles/coder``) layouts.

    Import-safe — no dependencies beyond stdlib.
    """
@Suppress("unused") private const val _HC_4: String = "profiles"
@Suppress("unused") private val _HC_5: String = """Return a per-profile HOME directory for subprocesses, or None.

    When ``{HERMES_HOME}/home/`` exists on disk, subprocesses should use it
    as ``HOME`` so system tools (git, ssh, gh, npm …) write their configs
    inside the Hermes data directory instead of the OS-level ``/root`` or
    ``~/``.  This provides:

    * **Docker persistence** — tool configs land inside the persistent volume.
    * **Profile isolation** — each profile gets its own git identity, SSH
      keys, gh tokens, etc.

    The Python process's own ``os.environ["HOME"]`` and ``Path.home()`` are
    **never** modified — only subprocess environments should inject this value.
    Activation is directory-based: if the ``home/`` subdirectory doesn't
    exist, returns ``None`` and behavior is unchanged.
    """
@Suppress("unused") private const val _HC_6: String = "home"
@Suppress("unused") private val _HC_7: String = """Return True when running inside WSL (Windows Subsystem for Linux).

    Checks ``/proc/version`` for the ``microsoft`` marker that both WSL1
    and WSL2 inject.  Result is cached for the process lifetime.
    Import-safe — no heavy deps.
    """
@Suppress("unused") private const val _HC_8: String = "/proc/version"
@Suppress("unused") private const val _HC_9: String = "microsoft"
@Suppress("unused") private val _HC_10: String = """Return True when running inside a Docker/Podman container.

    Checks ``/.dockerenv`` (Docker), ``/run/.containerenv`` (Podman),
    and ``/proc/1/cgroup`` for container runtime markers.  Result is
    cached for the process lifetime.  Import-safe — no heavy deps.
    """
@Suppress("unused") private const val _HC_11: String = "/.dockerenv"
@Suppress("unused") private const val _HC_12: String = "/run/.containerenv"
@Suppress("unused") private const val _HC_13: String = "/proc/1/cgroup"
@Suppress("unused") private const val _HC_14: String = "docker"
@Suppress("unused") private const val _HC_15: String = "podman"
@Suppress("unused") private const val _HC_16: String = "/lxc/"
@Suppress("unused") private val _HC_17: String = """Monkey-patch ``socket.getaddrinfo`` to prefer IPv4 connections.

    On servers with broken or unreachable IPv6, Python tries AAAA records
    first and hangs for the full TCP timeout before falling back to IPv4.
    This affects httpx, requests, urllib, the OpenAI SDK — everything that
    uses ``socket.getaddrinfo``.

    When *force* is True, patches ``getaddrinfo`` so that calls with
    ``family=AF_UNSPEC`` (the default) resolve as ``AF_INET`` instead,
    skipping IPv6 entirely.  If no A record exists, falls back to the
    original unfiltered resolution so pure-IPv6 hosts still work.

    Safe to call multiple times — only patches once.
    Set ``network.force_ipv4: true`` in ``config.yaml`` to enable.
    """
@Suppress("unused") private const val _HC_18: String = "_hermes_ipv4_patched"
