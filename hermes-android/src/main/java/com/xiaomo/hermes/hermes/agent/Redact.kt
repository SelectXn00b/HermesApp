/**
 * Regex-based secret redaction for logs and tool output.
 *
 * 1:1 对齐 hermes/agent/redact.py (Python 原始)
 *
 * Applies pattern matching to mask API keys, tokens, and credentials
 * before they reach log files, verbose output, or gateway logs.
 *
 * Short tokens (< 18 chars) are fully masked. Longer tokens preserve
 * the first 6 and last 4 characters for debuggability.
 */
package com.xiaomo.hermes.hermes.agent

import java.util.logging.Formatter
import java.util.logging.LogRecord

// Sensitive query-string parameter names (case-insensitive exact match).
// Ported from nearai/ironclaw#2529 — catches tokens whose values don't match
// any known vendor prefix regex (e.g. opaque tokens, short OAuth codes).
val _SENSITIVE_QUERY_PARAMS: Set<String> = setOf(
    "access_token",
    "refresh_token",
    "id_token",
    "token",
    "api_key",
    "apikey",
    "client_secret",
    "password",
    "auth",
    "jwt",
    "session",
    "secret",
    "key",
    "code",           // OAuth authorization codes
    "signature",      // pre-signed URL signatures
    "x-amz-signature",
)

// Sensitive form-urlencoded / JSON body key names (case-insensitive exact match).
// Exact match, NOT substring — "token_count" and "session_id" must NOT match.
// Ported from nearai/ironclaw#2529.
val _SENSITIVE_BODY_KEYS: Set<String> = setOf(
    "access_token",
    "refresh_token",
    "id_token",
    "token",
    "api_key",
    "apikey",
    "client_secret",
    "password",
    "auth",
    "jwt",
    "secret",
    "private_key",
    "authorization",
    "key",
)

// Snapshot at import time so runtime env mutations (e.g. LLM-generated
// `export HERMES_REDACT_SECRETS=false`) cannot disable redaction mid-session.
val _REDACT_ENABLED: Boolean = (System.getenv("HERMES_REDACT_SECRETS") ?: "").lowercase() !in
    setOf("0", "false", "no", "off")

// Known API key prefixes -- match the prefix + contiguous token chars
val _PREFIX_PATTERNS: List<String> = listOf(
    "sk-[A-Za-z0-9_-]{10,}",           // OpenAI / OpenRouter / Anthropic (sk-ant-*)
    "ghp_[A-Za-z0-9]{10,}",            // GitHub PAT (classic)
    "github_pat_[A-Za-z0-9_]{10,}",    // GitHub PAT (fine-grained)
    "gho_[A-Za-z0-9]{10,}",            // GitHub OAuth access token
    "ghu_[A-Za-z0-9]{10,}",            // GitHub user-to-server token
    "ghs_[A-Za-z0-9]{10,}",            // GitHub server-to-server token
    "ghr_[A-Za-z0-9]{10,}",            // GitHub refresh token
    "xox[baprs]-[A-Za-z0-9-]{10,}",    // Slack tokens
    "AIza[A-Za-z0-9_-]{30,}",          // Google API keys
    "pplx-[A-Za-z0-9]{10,}",           // Perplexity
    "fal_[A-Za-z0-9_-]{10,}",          // Fal.ai
    "fc-[A-Za-z0-9]{10,}",             // Firecrawl
    "bb_live_[A-Za-z0-9_-]{10,}",      // BrowserBase
    "gAAAA[A-Za-z0-9_=-]{20,}",        // Codex encrypted tokens
    "AKIA[A-Z0-9]{16}",                // AWS Access Key ID
    "sk_live_[A-Za-z0-9]{10,}",        // Stripe secret key (live)
    "sk_test_[A-Za-z0-9]{10,}",        // Stripe secret key (test)
    "rk_live_[A-Za-z0-9]{10,}",        // Stripe restricted key
    "SG\\.[A-Za-z0-9_-]{10,}",         // SendGrid API key
    "hf_[A-Za-z0-9]{10,}",             // HuggingFace token
    "r8_[A-Za-z0-9]{10,}",             // Replicate API token
    "npm_[A-Za-z0-9]{10,}",            // npm access token
    "pypi-[A-Za-z0-9_-]{10,}",         // PyPI API token
    "dop_v1_[A-Za-z0-9]{10,}",         // DigitalOcean PAT
    "doo_v1_[A-Za-z0-9]{10,}",         // DigitalOcean OAuth
    "am_[A-Za-z0-9_-]{10,}",           // AgentMail API key
    "sk_[A-Za-z0-9_]{10,}",            // ElevenLabs TTS key (sk_ underscore, not sk- dash)
    "tvly-[A-Za-z0-9]{10,}",           // Tavily search API key
    "exa_[A-Za-z0-9]{10,}",            // Exa search API key
    "gsk_[A-Za-z0-9]{10,}",            // Groq Cloud API key
    "syt_[A-Za-z0-9]{10,}",            // Matrix access token
    "retaindb_[A-Za-z0-9]{10,}",       // RetainDB API key
    "hsk-[A-Za-z0-9]{10,}",            // Hindsight API key
    "mem0_[A-Za-z0-9]{10,}",           // Mem0 Platform API key
    "brv_[A-Za-z0-9]{10,}",            // ByteRover API key
)

// ENV assignment patterns: KEY=value where KEY contains a secret-like name
const val _SECRET_ENV_NAMES: String = "(?:API_?KEY|TOKEN|SECRET|PASSWORD|PASSWD|CREDENTIAL|AUTH)"
val _ENV_ASSIGN_RE: Regex = Regex(
    "([A-Z0-9_]{0,50}$_SECRET_ENV_NAMES[A-Z0-9_]{0,50})\\s*=\\s*(['\"]?)(\\S+)\\2"
)

// JSON field patterns: "apiKey": "value", "token": "value", etc.
const val _JSON_KEY_NAMES: String =
    "(?:api_?[Kk]ey|token|secret|password|access_token|refresh_token|auth_token|bearer|secret_value|raw_secret|secret_input|key_material)"
val _JSON_FIELD_RE: Regex = Regex(
    "(\"$_JSON_KEY_NAMES\")\\s*:\\s*\"([^\"]+)\"",
    RegexOption.IGNORE_CASE,
)

// Authorization headers
val _AUTH_HEADER_RE: Regex = Regex(
    "(Authorization:\\s*Bearer\\s+)(\\S+)",
    RegexOption.IGNORE_CASE,
)

// Telegram bot tokens: bot<digits>:<token> or <digits>:<token>,
// where token part is restricted to [-A-Za-z0-9_] and length >= 30
val _TELEGRAM_RE: Regex = Regex(
    "(bot)?(\\d{8,}):([-A-Za-z0-9_]{30,})"
)

// Private key blocks: -----BEGIN RSA PRIVATE KEY----- ... -----END RSA PRIVATE KEY-----
val _PRIVATE_KEY_RE: Regex = Regex(
    "-----BEGIN[A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END[A-Z ]*PRIVATE KEY-----"
)

// Database connection strings: protocol://user:PASSWORD@host
// Catches postgres, mysql, mongodb, redis, amqp URLs and redacts the password
val _DB_CONNSTR_RE: Regex = Regex(
    "((?:postgres(?:ql)?|mysql|mongodb(?:\\+srv)?|redis|amqp)://[^:]+:)([^@]+)(@)",
    RegexOption.IGNORE_CASE,
)

// JWT tokens: header.payload[.signature] — always start with "eyJ" (base64 for "{")
// Matches 1-part (header only), 2-part (header.payload), and full 3-part JWTs.
val _JWT_RE: Regex = Regex(
    "eyJ[A-Za-z0-9_-]{10,}" +               // Header (always starts with eyJ)
        "(?:\\.[A-Za-z0-9_=-]{4,}){0,2}"    // Optional payload and/or signature
)

// Discord user/role mentions: <@123456789012345678> or <@!123456789012345678>
// Snowflake IDs are 17-20 digit integers that resolve to specific Discord accounts.
val _DISCORD_MENTION_RE: Regex = Regex("<@!?(\\d{17,20})>")

// E.164 phone numbers: +<country><number>, 7-15 digits
// Negative lookahead prevents matching hex strings or identifiers
val _SIGNAL_PHONE_RE: Regex = Regex("(\\+[1-9]\\d{6,14})(?![A-Za-z0-9])")

// URLs containing query strings — matches `scheme://...?...[# or end]`.
// Used to scan text for URLs whose query params may contain secrets.
// Ported from nearai/ironclaw#2529.
val _URL_WITH_QUERY_RE: Regex = Regex(
    "(https?|wss?|ftp)://" +       // scheme
        "([^\\s/?#]+)" +           // authority (may include userinfo)
        "([^\\s?#]*)" +            // path
        "\\?([^\\s#]+)" +          // query (required)
        "(#\\S*)?"                 // optional fragment
)

// URLs containing userinfo — `scheme://user:password@host` for ANY scheme
// (not just DB protocols already covered by _DB_CONNSTR_RE above).
// Catches things like `https://user:token@api.example.com/v1/foo`.
val _URL_USERINFO_RE: Regex = Regex(
    "(https?|wss?|ftp)://([^/\\s:@]+):([^/\\s@]+)@"
)

// Form-urlencoded body detection: conservative — only applies when the entire
// text looks like a query string (k=v&k=v pattern with no newlines).
val _FORM_BODY_RE: Regex = Regex(
    "^[A-Za-z_][A-Za-z0-9_.-]*=[^&\\s]*(?:&[A-Za-z_][A-Za-z0-9_.-]*=[^&\\s]*)+$"
)

// Compile known prefix patterns into one alternation
val _PREFIX_RE: Regex = Regex(
    "(?<![A-Za-z0-9_-])(" + _PREFIX_PATTERNS.joinToString("|") + ")(?![A-Za-z0-9_-])"
)


/** Mask a token, preserving prefix for long tokens. */
fun _maskToken(token: String): String {
    if (token.length < 18) return "***"
    return "${token.substring(0, 6)}...${token.substring(token.length - 4)}"
}


/**
 * Redact sensitive parameter values in a URL query string.
 *
 * Handles `k=v&k=v` format. Sensitive keys (case-insensitive) have values
 * replaced with `***`. Non-sensitive keys pass through unchanged.
 * Empty or malformed pairs are preserved as-is.
 */
fun _redactQueryString(query: String): String {
    if (query.isEmpty()) return query
    val parts = mutableListOf<String>()
    for (pair in query.split("&")) {
        if ("=" !in pair) {
            parts.add(pair)
            continue
        }
        val idx = pair.indexOf('=')
        val key = pair.substring(0, idx)
        if (key.lowercase() in _SENSITIVE_QUERY_PARAMS) {
            parts.add("$key=***")
        } else {
            parts.add(pair)
        }
    }
    return parts.joinToString("&")
}


/**
 * Scan text for URLs with query strings and redact sensitive params.
 *
 * Catches opaque tokens that don't match vendor prefix regexes, e.g.
 * `https://example.com/cb?code=ABC123&state=xyz` → `...?code=***&state=xyz`.
 */
fun _redactUrlQueryParams(text: String): String {
    return _URL_WITH_QUERY_RE.replace(text) { m ->
        val scheme = m.groupValues[1]
        val authority = m.groupValues[2]
        val path = m.groupValues[3]
        val query = _redactQueryString(m.groupValues[4])
        val fragment = m.groupValues.getOrNull(5) ?: ""
        "$scheme://$authority$path?$query$fragment"
    }
}


/**
 * Strip `user:password@` from HTTP/WS/FTP URLs.
 *
 * DB protocols (postgres, mysql, mongodb, redis, amqp) are handled
 * separately by `_DB_CONNSTR_RE`.
 */
fun _redactUrlUserinfo(text: String): String {
    return _URL_USERINFO_RE.replace(text) { m ->
        "${m.groupValues[1]}://${m.groupValues[2]}:***@"
    }
}


/**
 * Redact sensitive values in a form-urlencoded body.
 *
 * Only applies when the entire input looks like a pure form body
 * (k=v&k=v with no newlines, no other text). Single-line non-form
 * text passes through unchanged. This is a conservative pass — the
 * `_redactUrlQueryParams` function handles embedded query strings.
 */
fun _redactFormBody(text: String): String {
    if (text.isEmpty() || "\n" in text || "&" !in text) return text
    // The body-body form check is strict: only trigger on clean k=v&k=v.
    if (!_FORM_BODY_RE.matches(text.trim())) return text
    return _redactQueryString(text.trim())
}


/**
 * Apply all redaction patterns to a block of text.
 *
 * Safe to call on any string -- non-matching text passes through unchanged.
 * Disabled when security.redact_secrets is false in config.yaml.
 */
fun redactSensitiveText(text: Any?): String? {
    if (text == null) return null
    var s: String = if (text is String) text else text.toString()
    if (s.isEmpty()) return s
    if (!_REDACT_ENABLED) return s

    // Known prefixes (sk-, ghp_, etc.)
    s = _PREFIX_RE.replace(s) { m -> _maskToken(m.groupValues[1]) }

    // ENV assignments: OPENAI_API_KEY=sk-abc...
    s = _ENV_ASSIGN_RE.replace(s) { m ->
        val name = m.groupValues[1]
        val quote = m.groupValues[2]
        val value = m.groupValues[3]
        "$name=$quote${_maskToken(value)}$quote"
    }

    // JSON fields: "apiKey": "value"
    s = _JSON_FIELD_RE.replace(s) { m ->
        val key = m.groupValues[1]
        val value = m.groupValues[2]
        "$key: \"${_maskToken(value)}\""
    }

    // Authorization headers
    s = _AUTH_HEADER_RE.replace(s) { m ->
        m.groupValues[1] + _maskToken(m.groupValues[2])
    }

    // Telegram bot tokens
    s = _TELEGRAM_RE.replace(s) { m ->
        val prefix = m.groupValues[1]
        val digits = m.groupValues[2]
        "$prefix$digits:***"
    }

    // Private key blocks
    s = _PRIVATE_KEY_RE.replace(s, "[REDACTED PRIVATE KEY]")

    // Database connection string passwords
    s = _DB_CONNSTR_RE.replace(s) { m -> "${m.groupValues[1]}***${m.groupValues[3]}" }

    // JWT tokens (eyJ... — base64-encoded JSON headers)
    s = _JWT_RE.replace(s) { m -> _maskToken(m.value) }

    // URL userinfo (http(s)://user:pass@host) — redact for non-DB schemes.
    // DB schemes are handled above by _DB_CONNSTR_RE.
    s = _redactUrlUserinfo(s)

    // URL query params containing opaque tokens (?access_token=…&code=…)
    s = _redactUrlQueryParams(s)

    // Form-urlencoded bodies (only triggers on clean k=v&k=v inputs).
    s = _redactFormBody(s)

    // Discord user/role mentions (<@snowflake_id>)
    s = _DISCORD_MENTION_RE.replace(s) { m ->
        "<@${if ("!" in m.value) "!" else ""}***>"
    }

    // E.164 phone numbers (Signal, WhatsApp)
    s = _SIGNAL_PHONE_RE.replace(s) { m ->
        val phone = m.groupValues[1]
        if (phone.length <= 8) {
            phone.substring(0, 2) + "****" + phone.substring(phone.length - 2)
        } else {
            phone.substring(0, 4) + "****" + phone.substring(phone.length - 4)
        }
    }

    return s
}


/** Log formatter that redacts secrets from all log messages. */
class RedactingFormatter : Formatter() {
    override fun format(record: LogRecord): String {
        val original = formatMessage(record)
        return redactSensitiveText(original) ?: ""
    }
}
