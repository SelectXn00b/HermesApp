/**
 * Skills Guard — security scanner for externally-sourced skills.
 *
 * 1:1 对齐 hermes/tools/skills_guard.py (Python 原始)
 *
 * Every skill downloaded from a registry passes through this scanner before
 * installation. It uses regex-based static analysis to detect known-bad
 * patterns (data exfiltration, prompt injection, destructive commands,
 * persistence, etc.) and a trust-aware install policy that determines whether
 * a skill is allowed based on both the scan verdict and the source's trust
 * level.
 *
 * Trust levels:
 *   - builtin:   Ships with Hermes. Never scanned, always trusted.
 *   - trusted:   openai/skills and anthropics/skills only. Caution OK.
 *   - community: Everything else. Any findings = blocked unless --force.
 */
package com.xiaomo.hermes.hermes.tools

import java.io.File
import java.security.MessageDigest
import java.time.Instant

// ── Module-level state & constants ───────────────────────────────────────

val TRUSTED_REPOS: Set<String> = setOf("openai/skills", "anthropics/skills")

// policy tuple order: (safe, caution, dangerous) → decision
val INSTALL_POLICY: Map<String, Triple<String, String, String>> = mapOf(
    "builtin" to Triple("allow", "allow", "allow"),
    "trusted" to Triple("allow", "allow", "block"),
    "community" to Triple("allow", "block", "block"),
    "agent-created" to Triple("allow", "allow", "ask"),
)

val VERDICT_INDEX: Map<String, Int> = mapOf("safe" to 0, "caution" to 1, "dangerous" to 2)


data class Finding(
    val patternId: String,
    val severity: String,
    val category: String,
    val file: String,
    val line: Int,
    val match: String,
    val description: String,
)


data class ScanResult(
    val skillName: String,
    val source: String,
    val trustLevel: String,
    val verdict: String,
    val findings: List<Finding> = emptyList(),
    val scannedAt: String = "",
    val summary: String = "",
)


// ── Threat patterns: (regex, pattern_id, severity, category, description) ─

data class ThreatPattern(
    val regex: Regex,
    val patternId: String,
    val severity: String,
    val category: String,
    val description: String,
)

private fun _threat(
    pattern: String,
    patternId: String,
    severity: String,
    category: String,
    description: String,
): ThreatPattern = ThreatPattern(
    Regex(pattern, RegexOption.IGNORE_CASE),
    patternId, severity, category, description,
)


val THREAT_PATTERNS: List<ThreatPattern> = listOf(
    // ── Exfiltration: shell commands leaking secrets ──
    _threat("""curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)""",
        "env_exfil_curl", "critical", "exfiltration",
        "curl command interpolating secret environment variable"),
    _threat("""wget\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL|API)""",
        "env_exfil_wget", "critical", "exfiltration",
        "wget command interpolating secret environment variable"),
    _threat("""fetch\s*\([^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD|API)""",
        "env_exfil_fetch", "critical", "exfiltration",
        "fetch() call interpolating secret environment variable"),
    _threat("""httpx?\.(get|post|put|patch)\s*\([^\n]*(KEY|TOKEN|SECRET|PASSWORD)""",
        "env_exfil_httpx", "critical", "exfiltration",
        "HTTP library call with secret variable"),
    _threat("""requests\.(get|post|put|patch)\s*\([^\n]*(KEY|TOKEN|SECRET|PASSWORD)""",
        "env_exfil_requests", "critical", "exfiltration",
        "requests library call with secret variable"),

    // ── Exfiltration: reading credential stores ──
    _threat("""base64[^\n]*env""",
        "encoded_exfil", "high", "exfiltration",
        "base64 encoding combined with environment access"),
    _threat("""${'$'}HOME/\.ssh|\~/\.ssh""",
        "ssh_dir_access", "high", "exfiltration",
        "references user SSH directory"),
    _threat("""${'$'}HOME/\.aws|\~/\.aws""",
        "aws_dir_access", "high", "exfiltration",
        "references user AWS credentials directory"),
    _threat("""${'$'}HOME/\.gnupg|\~/\.gnupg""",
        "gpg_dir_access", "high", "exfiltration",
        "references user GPG keyring"),
    _threat("""${'$'}HOME/\.kube|\~/\.kube""",
        "kube_dir_access", "high", "exfiltration",
        "references Kubernetes config directory"),
    _threat("""${'$'}HOME/\.docker|\~/\.docker""",
        "docker_dir_access", "high", "exfiltration",
        "references Docker config (may contain registry creds)"),
    _threat("""${'$'}HOME/\.hermes/\.env|\~/\.hermes/\.env""",
        "hermes_env_access", "critical", "exfiltration",
        "directly references Hermes secrets file"),
    _threat("""cat\s+[^\n]*(\.env|credentials|\.netrc|\.pgpass|\.npmrc|\.pypirc)""",
        "read_secrets_file", "critical", "exfiltration",
        "reads known secrets file"),

    // ── Exfiltration: programmatic env access ──
    _threat("""printenv|env\s*\|""",
        "dump_all_env", "high", "exfiltration",
        "dumps all environment variables"),
    _threat("""os\.environ\b(?!\s*\.get\s*\(\s*["']PATH)""",
        "python_os_environ", "high", "exfiltration",
        "accesses os.environ (potential env dump)"),
    _threat("""os\.getenv\s*\(\s*[^\)]*(?:KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL)""",
        "python_getenv_secret", "critical", "exfiltration",
        "reads secret via os.getenv()"),
    _threat("""process\.env\[""",
        "node_process_env", "high", "exfiltration",
        "accesses process.env (Node.js environment)"),
    _threat("""ENV\[.*(?:KEY|TOKEN|SECRET|PASSWORD)""",
        "ruby_env_secret", "critical", "exfiltration",
        "reads secret via Ruby ENV[]"),

    // ── Exfiltration: DNS and staging ──
    _threat("""\b(dig|nslookup|host)\s+[^\n]*\$""",
        "dns_exfil", "critical", "exfiltration",
        "DNS lookup with variable interpolation (possible DNS exfiltration)"),
    _threat(""">\s*/tmp/[^\s]*\s*&&\s*(curl|wget|nc|python)""",
        "tmp_staging", "critical", "exfiltration",
        "writes to /tmp then exfiltrates"),

    // ── Exfiltration: markdown/link based ──
    _threat("""!\[.*\]\(https?://[^\)]*\$\{?""",
        "md_image_exfil", "high", "exfiltration",
        "markdown image URL with variable interpolation (image-based exfil)"),
    _threat("""\[.*\]\(https?://[^\)]*\$\{?""",
        "md_link_exfil", "high", "exfiltration",
        "markdown link with variable interpolation"),

    // ── Prompt injection ──
    _threat("""ignore\s+(?:\w+\s+)*(previous|all|above|prior)\s+instructions""",
        "prompt_injection_ignore", "critical", "injection",
        "prompt injection: ignore previous instructions"),
    _threat("""you\s+are\s+(?:\w+\s+)*now\s+""",
        "role_hijack", "high", "injection",
        "attempts to override the agent's role"),
    _threat("""do\s+not\s+(?:\w+\s+)*tell\s+(?:\w+\s+)*the\s+user""",
        "deception_hide", "critical", "injection",
        "instructs agent to hide information from user"),
    _threat("""system\s+prompt\s+override""",
        "sys_prompt_override", "critical", "injection",
        "attempts to override the system prompt"),
    _threat("""pretend\s+(?:\w+\s+)*(you\s+are|to\s+be)\s+""",
        "role_pretend", "high", "injection",
        "attempts to make the agent assume a different identity"),
    _threat("""disregard\s+(?:\w+\s+)*(your|all|any)\s+(?:\w+\s+)*(instructions|rules|guidelines)""",
        "disregard_rules", "critical", "injection",
        "instructs agent to disregard its rules"),
    _threat("""output\s+(?:\w+\s+)*(system|initial)\s+prompt""",
        "leak_system_prompt", "high", "injection",
        "attempts to extract the system prompt"),
    _threat("""(when|if)\s+no\s*one\s+is\s+(watching|looking)""",
        "conditional_deception", "high", "injection",
        "conditional instruction to behave differently when unobserved"),
    _threat("""act\s+as\s+(if|though)\s+(?:\w+\s+)*you\s+(?:\w+\s+)*(have\s+no|don't\s+have)\s+(?:\w+\s+)*(restrictions|limits|rules)""",
        "bypass_restrictions", "critical", "injection",
        "instructs agent to act without restrictions"),
    _threat("""translate\s+.*\s+into\s+.*\s+and\s+(execute|run|eval)""",
        "translate_execute", "critical", "injection",
        "translate-then-execute evasion technique"),
    _threat("""<!--[^>]*(?:ignore|override|system|secret|hidden)[^>]*-->""",
        "html_comment_injection", "high", "injection",
        "hidden instructions in HTML comments"),
    _threat("""<\s*div\s+style\s*=\s*["'][\s\S]*?display\s*:\s*none""",
        "hidden_div", "high", "injection",
        "hidden HTML div (invisible instructions)"),

    // ── Destructive operations ──
    _threat("""rm\s+-rf\s+/""",
        "destructive_root_rm", "critical", "destructive",
        "recursive delete from root"),
    _threat("""rm\s+(-[^\s]*)?r.*${'$'}HOME|\brmdir\s+.*${'$'}HOME""",
        "destructive_home_rm", "critical", "destructive",
        "recursive delete targeting home directory"),
    _threat("""chmod\s+777""",
        "insecure_perms", "medium", "destructive",
        "sets world-writable permissions"),
    _threat(""">\s*/etc/""",
        "system_overwrite", "critical", "destructive",
        "overwrites system configuration file"),
    _threat("""\bmkfs\b""",
        "format_filesystem", "critical", "destructive",
        "formats a filesystem"),
    _threat("""\bdd\s+.*if=.*of=/dev/""",
        "disk_overwrite", "critical", "destructive",
        "raw disk write operation"),
    _threat("""shutil\.rmtree\s*\(\s*["'/]""",
        "python_rmtree", "high", "destructive",
        "Python rmtree on absolute or root-relative path"),
    _threat("""truncate\s+-s\s*0\s+/""",
        "truncate_system", "critical", "destructive",
        "truncates system file to zero bytes"),

    // ── Persistence ──
    _threat("""\bcrontab\b""",
        "persistence_cron", "medium", "persistence",
        "modifies cron jobs"),
    _threat("""\.(bashrc|zshrc|profile|bash_profile|bash_login|zprofile|zlogin)\b""",
        "shell_rc_mod", "medium", "persistence",
        "references shell startup file"),
    _threat("""authorized_keys""",
        "ssh_backdoor", "critical", "persistence",
        "modifies SSH authorized keys"),
    _threat("""ssh-keygen""",
        "ssh_keygen", "medium", "persistence",
        "generates SSH keys"),
    _threat("""systemd.*\.service|systemctl\s+(enable|start)""",
        "systemd_service", "medium", "persistence",
        "references or enables systemd service"),
    _threat("""/etc/init\.d/""",
        "init_script", "medium", "persistence",
        "references init.d startup script"),
    _threat("""launchctl\s+load|LaunchAgents|LaunchDaemons""",
        "macos_launchd", "medium", "persistence",
        "macOS launch agent/daemon persistence"),
    _threat("""/etc/sudoers|visudo""",
        "sudoers_mod", "critical", "persistence",
        "modifies sudoers (privilege escalation)"),
    _threat("""git\s+config\s+--global\s+""",
        "git_config_global", "medium", "persistence",
        "modifies global git configuration"),

    // ── Network: reverse shells and tunnels ──
    _threat("""\bnc\s+-[lp]|ncat\s+-[lp]|\bsocat\b""",
        "reverse_shell", "critical", "network",
        "potential reverse shell listener"),
    _threat("""\bngrok\b|\blocaltunnel\b|\bserveo\b|\bcloudflared\b""",
        "tunnel_service", "high", "network",
        "uses tunneling service for external access"),
    _threat("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}:\d{2,5}""",
        "hardcoded_ip_port", "medium", "network",
        "hardcoded IP address with port"),
    _threat("""0\.0\.0\.0:\d+|INADDR_ANY""",
        "bind_all_interfaces", "high", "network",
        "binds to all network interfaces"),
    _threat("""/bin/(ba)?sh\s+-i\s+.*>/dev/tcp/""",
        "bash_reverse_shell", "critical", "network",
        "bash interactive reverse shell via /dev/tcp"),
    _threat("""python[23]?\s+-c\s+["']import\s+socket""",
        "python_socket_oneliner", "critical", "network",
        "Python one-liner socket connection (likely reverse shell)"),
    _threat("""socket\.connect\s*\(\s*\(""",
        "python_socket_connect", "high", "network",
        "Python socket connect to arbitrary host"),
    _threat("""webhook\.site|requestbin\.com|pipedream\.net|hookbin\.com""",
        "exfil_service", "high", "network",
        "references known data exfiltration/webhook testing service"),
    _threat("""pastebin\.com|hastebin\.com|ghostbin\.""",
        "paste_service", "medium", "network",
        "references paste service (possible data staging)"),

    // ── Obfuscation: encoding and eval ──
    _threat("""base64\s+(-d|--decode)\s*\|""",
        "base64_decode_pipe", "high", "obfuscation",
        "base64 decodes and pipes to execution"),
    _threat("""\\x[0-9a-fA-F]{2}.*\\x[0-9a-fA-F]{2}.*\\x[0-9a-fA-F]{2}""",
        "hex_encoded_string", "medium", "obfuscation",
        "hex-encoded string (possible obfuscation)"),
    _threat("""\beval\s*\(\s*["']""",
        "eval_string", "high", "obfuscation",
        "eval() with string argument"),
    _threat("""\bexec\s*\(\s*["']""",
        "exec_string", "high", "obfuscation",
        "exec() with string argument"),
    _threat("""echo\s+[^\n]*\|\s*(bash|sh|python|perl|ruby|node)""",
        "echo_pipe_exec", "critical", "obfuscation",
        "echo piped to interpreter for execution"),
    _threat("""compile\s*\(\s*[^\)]+,\s*["'].*["']\s*,\s*["']exec["']\s*\)""",
        "python_compile_exec", "high", "obfuscation",
        "Python compile() with exec mode"),
    _threat("""getattr\s*\(\s*__builtins__""",
        "python_getattr_builtins", "high", "obfuscation",
        "dynamic access to Python builtins (evasion technique)"),
    _threat("""__import__\s*\(\s*["']os["']\s*\)""",
        "python_import_os", "high", "obfuscation",
        "dynamic import of os module"),
    _threat("""codecs\.decode\s*\(\s*["']""",
        "python_codecs_decode", "medium", "obfuscation",
        "codecs.decode (possible ROT13 or encoding obfuscation)"),
    _threat("""String\.fromCharCode|charCodeAt""",
        "js_char_code", "medium", "obfuscation",
        "JavaScript character code construction (possible obfuscation)"),
    _threat("""atob\s*\(|btoa\s*\(""",
        "js_base64", "medium", "obfuscation",
        "JavaScript base64 encode/decode"),
    _threat("""\[::-1\]""",
        "string_reversal", "low", "obfuscation",
        "string reversal (possible obfuscated payload)"),
    _threat("""chr\s*\(\s*\d+\s*\)\s*\+\s*chr\s*\(\s*\d+""",
        "chr_building", "high", "obfuscation",
        "building string from chr() calls (obfuscation)"),
    _threat("""\\u[0-9a-fA-F]{4}.*\\u[0-9a-fA-F]{4}.*\\u[0-9a-fA-F]{4}""",
        "unicode_escape_chain", "medium", "obfuscation",
        "chain of unicode escapes (possible obfuscation)"),

    // ── Process execution in scripts ──
    _threat("""subprocess\.(run|call|Popen|check_output)\s*\(""",
        "python_subprocess", "medium", "execution",
        "Python subprocess execution"),
    _threat("""os\.system\s*\(""",
        "python_os_system", "high", "execution",
        "os.system() — unguarded shell execution"),
    _threat("""os\.popen\s*\(""",
        "python_os_popen", "high", "execution",
        "os.popen() — shell pipe execution"),
    _threat("""child_process\.(exec|spawn|fork)\s*\(""",
        "node_child_process", "high", "execution",
        "Node.js child_process execution"),
    _threat("""Runtime\.getRuntime\(\)\.exec\(""",
        "java_runtime_exec", "high", "execution",
        "Java Runtime.exec() — shell execution"),
    _threat("""`[^`]*\$\([^)]+\)[^`]*`""",
        "backtick_subshell", "medium", "execution",
        "backtick string with command substitution"),

    // ── Path traversal ──
    _threat("""\.\./\.\./\.\.""",
        "path_traversal_deep", "high", "traversal",
        "deep relative path traversal (3+ levels up)"),
    _threat("""\.\./\.\.""",
        "path_traversal", "medium", "traversal",
        "relative path traversal (2+ levels up)"),
    _threat("""/etc/passwd|/etc/shadow""",
        "system_passwd_access", "critical", "traversal",
        "references system password files"),
    _threat("""/proc/self|/proc/\d+/""",
        "proc_access", "high", "traversal",
        "references /proc filesystem (process introspection)"),
    _threat("""/dev/shm/""",
        "dev_shm", "medium", "traversal",
        "references shared memory (common staging area)"),

    // ── Crypto mining ──
    _threat("""xmrig|stratum\+tcp|monero|coinhive|cryptonight""",
        "crypto_mining", "critical", "mining",
        "cryptocurrency mining reference"),
    _threat("""hashrate|nonce.*difficulty""",
        "mining_indicators", "medium", "mining",
        "possible cryptocurrency mining indicators"),

    // ── Supply chain: curl/wget pipe to shell ──
    _threat("""curl\s+[^\n]*\|\s*(ba)?sh""",
        "curl_pipe_shell", "critical", "supply_chain",
        "curl piped to shell (download-and-execute)"),
    _threat("""wget\s+[^\n]*-O\s*-\s*\|\s*(ba)?sh""",
        "wget_pipe_shell", "critical", "supply_chain",
        "wget piped to shell (download-and-execute)"),
    _threat("""curl\s+[^\n]*\|\s*python""",
        "curl_pipe_python", "critical", "supply_chain",
        "curl piped to Python interpreter"),

    // ── Supply chain: unpinned/deferred dependencies ──
    _threat("""#\s*///\s*script.*dependencies""",
        "pep723_inline_deps", "medium", "supply_chain",
        "PEP 723 inline script metadata with dependencies (verify pinning)"),
    _threat("""pip\s+install\s+(?!-r\s)(?!.*==)""",
        "unpinned_pip_install", "medium", "supply_chain",
        "pip install without version pinning"),
    _threat("""npm\s+install\s+(?!.*@\d)""",
        "unpinned_npm_install", "medium", "supply_chain",
        "npm install without version pinning"),
    _threat("""uv\s+run\s+""",
        "uv_run", "medium", "supply_chain",
        "uv run (may auto-install unpinned dependencies)"),

    // ── Supply chain: remote resource fetching ──
    _threat("""(curl|wget|httpx?\.get|requests\.get|fetch)\s*[\(]?\s*["']https?://""",
        "remote_fetch", "medium", "supply_chain",
        "fetches remote resource at runtime"),
    _threat("""git\s+clone\s+""",
        "git_clone", "medium", "supply_chain",
        "clones a git repository at runtime"),
    _threat("""docker\s+pull\s+""",
        "docker_pull", "medium", "supply_chain",
        "pulls a Docker image at runtime"),

    // ── Privilege escalation ──
    _threat("""^allowed-tools\s*:""",
        "allowed_tools_field", "high", "privilege_escalation",
        "skill declares allowed-tools (pre-approves tool access)"),
    _threat("""\bsudo\b""",
        "sudo_usage", "high", "privilege_escalation",
        "uses sudo (privilege escalation)"),
    _threat("""setuid|setgid|cap_setuid""",
        "setuid_setgid", "critical", "privilege_escalation",
        "setuid/setgid (privilege escalation mechanism)"),
    _threat("""NOPASSWD""",
        "nopasswd_sudo", "critical", "privilege_escalation",
        "NOPASSWD sudoers entry (passwordless privilege escalation)"),
    _threat("""chmod\s+[u+]?s""",
        "suid_bit", "critical", "privilege_escalation",
        "sets SUID/SGID bit on a file"),

    // ── Agent config persistence ──
    _threat("""AGENTS\.md|CLAUDE\.md|\.cursorrules|\.clinerules""",
        "agent_config_mod", "critical", "persistence",
        "references agent config files (could persist malicious instructions across sessions)"),
    _threat("""\.hermes/config\.yaml|\.hermes/SOUL\.md""",
        "hermes_config_mod", "critical", "persistence",
        "references Hermes configuration files directly"),
    _threat("""\.claude/settings|\.codex/config""",
        "other_agent_config", "high", "persistence",
        "references other agent configuration files"),

    // ── Hardcoded secrets (credentials embedded in the skill itself) ──
    _threat("""(?:api[_-]?key|token|secret|password)\s*[=:]\s*["'][A-Za-z0-9+/=_-]{20,}""",
        "hardcoded_secret", "critical", "credential_exposure",
        "possible hardcoded API key, token, or secret"),
    _threat("""-----BEGIN\s+(RSA\s+)?PRIVATE\s+KEY-----""",
        "embedded_private_key", "critical", "credential_exposure",
        "embedded private key"),
    _threat("""ghp_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{80,}""",
        "github_token_leaked", "critical", "credential_exposure",
        "GitHub personal access token in skill content"),
    _threat("""sk-[A-Za-z0-9]{20,}""",
        "openai_key_leaked", "critical", "credential_exposure",
        "possible OpenAI API key in skill content"),
    _threat("""sk-ant-[A-Za-z0-9_-]{90,}""",
        "anthropic_key_leaked", "critical", "credential_exposure",
        "possible Anthropic API key in skill content"),
    _threat("""AKIA[0-9A-Z]{16}""",
        "aws_access_key_leaked", "critical", "credential_exposure",
        "AWS access key ID in skill content"),

    // ── Additional prompt injection: jailbreak patterns ──
    _threat("""\bDAN\s+mode\b|Do\s+Anything\s+Now""",
        "jailbreak_dan", "critical", "injection",
        "DAN (Do Anything Now) jailbreak attempt"),
    _threat("""\bdeveloper\s+mode\b.*\benabled?\b""",
        "jailbreak_dev_mode", "critical", "injection",
        "developer mode jailbreak attempt"),
    _threat("""hypothetical\s+scenario.*(?:ignore|bypass|override)""",
        "hypothetical_bypass", "high", "injection",
        "hypothetical scenario used to bypass restrictions"),
    _threat("""for\s+educational\s+purposes?\s+only""",
        "educational_pretext", "medium", "injection",
        "educational pretext often used to justify harmful content"),
    _threat("""(respond|answer|reply)\s+without\s+(?:\w+\s+)*(restrictions|limitations|filters|safety)""",
        "remove_filters", "critical", "injection",
        "instructs agent to respond without safety filters"),
    _threat("""you\s+have\s+been\s+(?:\w+\s+)*(updated|upgraded|patched)\s+to""",
        "fake_update", "high", "injection",
        "fake update/patch announcement (social engineering)"),
    _threat("""new\s+policy|updated\s+guidelines|revised\s+instructions""",
        "fake_policy", "medium", "injection",
        "claims new policy/guidelines (may be social engineering)"),

    // ── Context window exfiltration ──
    _threat("""(include|output|print|send|share)\s+(?:\w+\s+)*(conversation|chat\s+history|previous\s+messages|context)""",
        "context_exfil", "high", "exfiltration",
        "instructs agent to output/share conversation history"),
    _threat("""(send|post|upload|transmit)\s+.*\s+(to|at)\s+https?://""",
        "send_to_url", "high", "exfiltration",
        "instructs agent to send data to a URL"),
)

// Structural limits for skill directories
const val MAX_FILE_COUNT: Int = 50
const val MAX_TOTAL_SIZE_KB: Int = 1024
const val MAX_SINGLE_FILE_KB: Int = 256

// File extensions to scan (text files only — skip binary)
val SCANNABLE_EXTENSIONS: Set<String> = setOf(
    ".md", ".txt", ".py", ".sh", ".bash", ".js", ".ts", ".rb",
    ".yaml", ".yml", ".json", ".toml", ".cfg", ".ini", ".conf",
    ".html", ".css", ".xml", ".tex", ".r", ".jl", ".pl", ".php",
)

// Known binary extensions that should NOT be in a skill
val SUSPICIOUS_BINARY_EXTENSIONS: Set<String> = setOf(
    ".exe", ".dll", ".so", ".dylib", ".bin", ".dat", ".com",
    ".msi", ".dmg", ".app", ".deb", ".rpm",
)

// Zero-width and invisible unicode characters used for injection
val INVISIBLE_CHARS: Set<Char> = setOf(
    '\u200b', '\u200c', '\u200d', '\u2060', '\u2062', '\u2063', '\u2064',
    '\ufeff', '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',
    '\u2066', '\u2067', '\u2068', '\u2069',
)


// ── Scanning functions ────────────────────────────────────────────────────

/** Scan a single file for threat patterns and invisible unicode characters. */
fun scanFile(filePath: File, relPath: String = ""): List<Finding> {
    val actualRel = relPath.ifEmpty { filePath.name }
    val suffixLower = "." + filePath.extension.lowercase()
    if (suffixLower !in SCANNABLE_EXTENSIONS && filePath.name != "SKILL.md") return emptyList()

    val content = try {
        filePath.readText(Charsets.UTF_8)
    } catch (_: Exception) {
        return emptyList()
    }

    val findings = mutableListOf<Finding>()
    val lines = content.split("\n")
    val seen = mutableSetOf<Pair<String, Int>>()  // (pattern_id, line_number) for dedup

    // Regex pattern matching
    for (threat in THREAT_PATTERNS) {
        for ((idx, line) in lines.withIndex()) {
            val i = idx + 1
            if (Pair(threat.patternId, i) in seen) continue
            if (threat.regex.containsMatchIn(line)) {
                seen.add(Pair(threat.patternId, i))
                var matched = line.trim()
                if (matched.length > 120) matched = matched.substring(0, 117) + "..."
                findings.add(Finding(
                    patternId = threat.patternId,
                    severity = threat.severity,
                    category = threat.category,
                    file = actualRel,
                    line = i,
                    match = matched,
                    description = threat.description,
                ))
            }
        }
    }

    // Invisible unicode character detection — one finding per line
    for ((idx, line) in lines.withIndex()) {
        val i = idx + 1
        for (char in INVISIBLE_CHARS) {
            if (char in line) {
                val charName = _unicodeCharName(char)
                findings.add(Finding(
                    patternId = "invisible_unicode",
                    severity = "high",
                    category = "injection",
                    file = actualRel,
                    line = i,
                    match = String.format("U+%04X (%s)", char.code, charName),
                    description = "invisible unicode character $charName (possible text hiding/injection)",
                ))
                break
            }
        }
    }

    return findings
}


/**
 * Scan all files in a skill directory for security threats.
 *
 * Performs: structural checks (file count, total size, binary files, symlinks),
 * regex pattern matching on all text files, invisible unicode char detection.
 */
fun scanSkill(skillPath: File, source: String = "community"): ScanResult {
    val skillName = skillPath.name
    val trustLevel = _resolveTrustLevel(source)

    val allFindings = mutableListOf<Finding>()

    if (skillPath.isDirectory) {
        allFindings.addAll(_checkStructure(skillPath))
        skillPath.walkTopDown()
            .filter { it.isFile }
            .forEach { f ->
                val rel = try {
                    f.relativeTo(skillPath).path
                } catch (_: Exception) {
                    f.name
                }
                allFindings.addAll(scanFile(f, rel))
            }
    } else if (skillPath.isFile) {
        allFindings.addAll(scanFile(skillPath, skillPath.name))
    }

    val verdict = _determineVerdict(allFindings)
    val summary = _buildSummary(skillName, source, trustLevel, verdict, allFindings)

    return ScanResult(
        skillName = skillName,
        source = source,
        trustLevel = trustLevel,
        verdict = verdict,
        findings = allFindings,
        scannedAt = Instant.now().toString(),
        summary = summary,
    )
}


/**
 * Determine whether a skill should be installed based on scan result and trust.
 *
 * Returns (allowed, reason) — allowed is null for "needs user confirmation".
 */
fun shouldAllowInstall(result: ScanResult, force: Boolean = false): Pair<Boolean?, String> {
    val policy = INSTALL_POLICY[result.trustLevel] ?: INSTALL_POLICY["community"]!!
    val vi = VERDICT_INDEX[result.verdict] ?: 2
    val decision = when (vi) {
        0 -> policy.first
        1 -> policy.second
        else -> policy.third
    }

    if (decision == "allow") {
        return Pair(true, "Allowed (${result.trustLevel} source, ${result.verdict} verdict)")
    }

    if (force) {
        return Pair(true, "Force-installed despite ${result.verdict} verdict (${result.findings.size} findings)")
    }

    if (decision == "ask") {
        return Pair(null, "Requires confirmation (${result.trustLevel} source + ${result.verdict} verdict, ${result.findings.size} findings)")
    }

    return Pair(false, "Blocked (${result.trustLevel} source + ${result.verdict} verdict, ${result.findings.size} findings). Use --force to override.")
}


/** Format a scan result as a human-readable report string. */
fun formatScanReport(result: ScanResult): String {
    val lines = mutableListOf<String>()
    val verdictDisplay = result.verdict.uppercase()
    lines.add("Scan: ${result.skillName} (${result.source}/${result.trustLevel})  Verdict: $verdictDisplay")

    if (result.findings.isNotEmpty()) {
        val severityOrder = mapOf("critical" to 0, "high" to 1, "medium" to 2, "low" to 3)
        val sortedFindings = result.findings.sortedBy { severityOrder[it.severity] ?: 4 }
        for (f in sortedFindings) {
            val sev = f.severity.uppercase().padEnd(8)
            val cat = f.category.padEnd(14)
            val loc = "${f.file}:${f.line}".padEnd(30)
            val shortMatch = if (f.match.length > 60) f.match.substring(0, 60) else f.match
            lines.add("  $sev $cat $loc \"$shortMatch\"")
        }
        lines.add("")
    }

    val (allowed, reason) = shouldAllowInstall(result)
    val status = when (allowed) {
        true -> "ALLOWED"
        null -> "NEEDS CONFIRMATION"
        false -> "BLOCKED"
    }
    lines.add("Decision: $status — $reason")

    return lines.joinToString("\n")
}


/** Compute a SHA-256 hash of all files in a skill directory for integrity tracking. */
fun contentHash(skillPath: File): String {
    val h = MessageDigest.getInstance("SHA-256")
    if (skillPath.isDirectory) {
        skillPath.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.absolutePath }
            .forEach { f ->
                try {
                    h.update(f.readBytes())
                } catch (_: Exception) {}
            }
    } else if (skillPath.isFile) {
        h.update(skillPath.readBytes())
    }
    val hex = h.digest().joinToString("") { String.format("%02x", it) }
    return "sha256:${hex.substring(0, 16)}"
}


// ── Structural checks ────────────────────────────────────────────────────

/**
 * Check the skill directory for structural anomalies: too many files,
 * suspiciously large total size, binary/executable files that shouldn't be in
 * a skill, symlinks pointing outside the skill directory, oversized single
 * files.
 */
fun _checkStructure(skillDir: File): List<Finding> {
    val findings = mutableListOf<Finding>()
    var fileCount = 0
    var totalSize = 0L

    val skillDirResolved = try {
        skillDir.canonicalFile
    } catch (_: Exception) {
        skillDir.absoluteFile
    }

    skillDir.walkTopDown().forEach { f ->
        // Skip root dir itself
        if (f == skillDir) return@forEach

        val isSymlink = try {
            java.nio.file.Files.isSymbolicLink(f.toPath())
        } catch (_: Exception) {
            false
        }
        if (!f.isFile && !isSymlink) return@forEach

        val rel = try {
            f.relativeTo(skillDir).path
        } catch (_: Exception) {
            f.name
        }
        fileCount += 1

        if (isSymlink) {
            try {
                val resolved = f.canonicalFile
                if (!resolved.absolutePath.startsWith(skillDirResolved.absolutePath)) {
                    findings.add(Finding(
                        patternId = "symlink_escape",
                        severity = "critical",
                        category = "traversal",
                        file = rel,
                        line = 0,
                        match = "symlink -> $resolved",
                        description = "symlink points outside the skill directory",
                    ))
                }
            } catch (_: Exception) {
                findings.add(Finding(
                    patternId = "broken_symlink",
                    severity = "medium",
                    category = "traversal",
                    file = rel,
                    line = 0,
                    match = "broken symlink",
                    description = "broken or circular symlink",
                ))
            }
            return@forEach
        }

        val size = try {
            f.length()
        } catch (_: Exception) {
            return@forEach
        }
        totalSize += size

        if (size > MAX_SINGLE_FILE_KB * 1024L) {
            findings.add(Finding(
                patternId = "oversized_file",
                severity = "medium",
                category = "structural",
                file = rel,
                line = 0,
                match = "${size / 1024}KB",
                description = "file is ${size / 1024}KB (limit: ${MAX_SINGLE_FILE_KB}KB)",
            ))
        }

        val ext = "." + f.extension.lowercase()
        if (ext in SUSPICIOUS_BINARY_EXTENSIONS) {
            findings.add(Finding(
                patternId = "binary_file",
                severity = "critical",
                category = "structural",
                file = rel,
                line = 0,
                match = "binary: $ext",
                description = "binary/executable file ($ext) should not be in a skill",
            ))
        }

        // Executable permission on non-script files
        if (ext !in setOf(".sh", ".bash", ".py", ".rb", ".pl") && f.canExecute()) {
            findings.add(Finding(
                patternId = "unexpected_executable",
                severity = "medium",
                category = "structural",
                file = rel,
                line = 0,
                match = "executable bit set",
                description = "file has executable permission but is not a recognized script type",
            ))
        }
    }

    if (fileCount > MAX_FILE_COUNT) {
        findings.add(Finding(
            patternId = "too_many_files",
            severity = "medium",
            category = "structural",
            file = "(directory)",
            line = 0,
            match = "$fileCount files",
            description = "skill has $fileCount files (limit: $MAX_FILE_COUNT)",
        ))
    }

    if (totalSize > MAX_TOTAL_SIZE_KB * 1024L) {
        findings.add(Finding(
            patternId = "oversized_skill",
            severity = "high",
            category = "structural",
            file = "(directory)",
            line = 0,
            match = "${totalSize / 1024}KB total",
            description = "skill is ${totalSize / 1024}KB total (limit: ${MAX_TOTAL_SIZE_KB}KB)",
        ))
    }

    return findings
}


/** Get a readable name for an invisible unicode character. */
fun _unicodeCharName(char: Char): String {
    val names = mapOf(
        '\u200b' to "zero-width space",
        '\u200c' to "zero-width non-joiner",
        '\u200d' to "zero-width joiner",
        '\u2060' to "word joiner",
        '\u2062' to "invisible times",
        '\u2063' to "invisible separator",
        '\u2064' to "invisible plus",
        '\ufeff' to "BOM/zero-width no-break space",
        '\u202a' to "LTR embedding",
        '\u202b' to "RTL embedding",
        '\u202c' to "pop directional",
        '\u202d' to "LTR override",
        '\u202e' to "RTL override",
        '\u2066' to "LTR isolate",
        '\u2067' to "RTL isolate",
        '\u2068' to "first strong isolate",
        '\u2069' to "pop directional isolate",
    )
    return names[char] ?: String.format("U+%04X", char.code)
}


// ── Internal helpers ─────────────────────────────────────────────────────

/** Map a source identifier to a trust level. */
fun _resolveTrustLevel(source: String): String {
    val prefixAliases = listOf("skills-sh/", "skills.sh/", "skils-sh/", "skils.sh/")
    var normalizedSource = source
    for (prefix in prefixAliases) {
        if (normalizedSource.startsWith(prefix)) {
            normalizedSource = normalizedSource.substring(prefix.length)
            break
        }
    }
    if (normalizedSource == "agent-created") return "agent-created"
    if (normalizedSource.startsWith("official/") || normalizedSource == "official") return "builtin"
    for (trusted in TRUSTED_REPOS) {
        if (normalizedSource.startsWith(trusted) || normalizedSource == trusted) return "trusted"
    }
    return "community"
}


/** Determine the overall verdict from a list of findings. */
fun _determineVerdict(findings: List<Finding>): String {
    if (findings.isEmpty()) return "safe"
    val hasCritical = findings.any { it.severity == "critical" }
    val hasHigh = findings.any { it.severity == "high" }
    if (hasCritical) return "dangerous"
    if (hasHigh) return "caution"
    return "caution"
}


/** Build a one-line summary of the scan result. */
fun _buildSummary(name: String, source: String, trust: String, verdict: String, findings: List<Finding>): String {
    if (findings.isEmpty()) return "$name: clean scan, no threats detected"
    val categories = findings.map { it.category }.toSortedSet()
    return "$name: $verdict — ${findings.size} finding(s) in ${categories.joinToString(", ")}"
}
