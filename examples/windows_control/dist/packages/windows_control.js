/* METADATA
{
    "name": "windows_control",
    "display_name": {
        "zh": "Windows 控制",
        "en": "Windows Control"
    },
    "description": {
        "zh": "通过 HTTP 调用 Operit PC Agent 控制 Windows 电脑，支持执行 PowerShell/CMD 命令并返回输出。",
        "en": "Control a Windows PC through Operit PC Agent over HTTP, execute PowerShell/CMD commands, and return output."
    },
    "enabledByDefault": false,
    "category": "System",
    "env": [
        {
            "name": "WINDOWS_AGENT_BASE_URL",
            "description": { "zh": "Operit PC Agent 地址，例如 http://192.168.1.8:58321", "en": "Operit PC Agent URL, e.g. http://192.168.1.8:58321" },
            "required": true
        },
        {
            "name": "WINDOWS_AGENT_TOKEN",
            "description": { "zh": "Agent API 令牌（必填）", "en": "Agent API token (required)" },
            "required": true
        },
        {
            "name": "WINDOWS_AGENT_DEFAULT_SHELL",
            "description": { "zh": "默认 shell：powershell/pwsh/cmd，默认 powershell", "en": "Default shell: powershell/pwsh/cmd, default powershell" },
            "required": false
        },
        {
            "name": "WINDOWS_AGENT_TIMEOUT_MS",
            "description": { "zh": "默认命令超时毫秒数，默认 30000", "en": "Default command timeout in ms, default 30000" },
            "required": false
        }
    ],
    "tools": [
        {
            "name": "usage_advice",
            "description": {
                "zh": "Windows 控制建议：\\n- 查找优先使用快速工具（如 rg），其次使用 PowerShell 原生命令。\\n- 修改文件优先使用文件工具（read/edit/write），采用小步、可追踪修改。\\n- 不做破坏性操作（如 git reset --hard、强制回滚），除非你明确授权。\\n- 涉及权限风险或越界操作时，先申请授权再执行。",
                "en": "Windows control advice:\\n- Prefer fast search tools first (e.g., rg), then PowerShell native commands.\\n- For file changes, prefer file tools (read/edit/write) with small, traceable steps.\\n- Do not perform destructive operations (e.g., git reset --hard or forced rollback) unless explicitly authorized.\\n- For permission-risk or out-of-bound actions, request authorization before execution."
            },
            "parameters": [],
            "advice": true
        },
        {
            "name": "windows_exec",
            "description": {
                "zh": "通过 Operit PC Agent 的 HTTP 接口在 Windows 上执行命令。",
                "en": "Execute commands on Windows via Operit PC Agent HTTP API."
            },
            "parameters": [
                {
                    "name": "command",
                    "description": { "zh": "要执行的命令内容", "en": "Command content to execute" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "shell",
                    "description": { "zh": "shell：powershell/pwsh/cmd，默认读取环境变量或 powershell", "en": "Shell: powershell/pwsh/cmd, default from env or powershell" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次命令超时毫秒数（可选）", "en": "Timeout for this command in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "windows_process_start",
            "description": {
                "zh": "启动一个可持续交互的 Windows 进程会话，可用于长时间命令（如 codex）。",
                "en": "Start a persistent interactive Windows process session for long-running commands (e.g., codex)."
            },
            "parameters": [
                {
                    "name": "command",
                    "description": { "zh": "启动后立即执行的命令（可选，不传则仅打开 shell 会话）", "en": "Command to run immediately after start (optional; if omitted, opens shell session only)." },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "shell",
                    "description": { "zh": "shell：powershell/pwsh/cmd，默认读取环境变量或 powershell", "en": "Shell: powershell/pwsh/cmd, default from env or powershell" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "max_runtime_ms",
                    "description": { "zh": "会话最大运行毫秒数（可选，不传表示不主动超时）", "en": "Max session runtime in ms (optional; no forced timeout if omitted)." },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次 API 调用超时毫秒数（可选）", "en": "Timeout for this API call in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "windows_process_read",
            "description": {
                "zh": "按偏移增量读取进程会话输出（stdout/stderr），适合轮询长任务输出。",
                "en": "Read process session stdout/stderr incrementally by offsets, suitable for polling long-running output."
            },
            "parameters": [
                {
                    "name": "session_id",
                    "description": { "zh": "会话 ID", "en": "Process session ID" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "stdout_offset",
                    "description": { "zh": "stdout 读取偏移（字符）", "en": "Read offset for stdout (characters)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "stderr_offset",
                    "description": { "zh": "stderr 读取偏移（字符）", "en": "Read offset for stderr (characters)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "max_chars",
                    "description": { "zh": "单次返回的最大字符数（每个流）", "en": "Maximum characters returned per stream in one call" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次 API 调用超时毫秒数（可选）", "en": "Timeout for this API call in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "windows_process_write",
            "description": {
                "zh": "向进程会话写入输入（stdin）。通常应先发送一次 input，再紧跟一次 control=enter 提交内容；如果 control 和 input 同时传入，则按组合键处理（例如 control=ctrl, input=c 表示 Ctrl+C）。",
                "en": "Write input to a process session (stdin). In normal usage, send input first, then send control=enter to submit. If control and input are provided together, they are treated as a key combination (for example, control=ctrl with input=c means Ctrl+C)."
            },
            "parameters": [
                {
                    "name": "session_id",
                    "description": { "zh": "会话 ID", "en": "Process session ID" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "input",
                    "description": { "zh": "要写入的文本（可包含换行）", "en": "Text to write (can include newlines)" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "control",
                    "description": { "zh": "控制键或修饰键。单独传可发送控制键（如 enter/tab/esc/up/down）；与 input 同传时按组合键处理（如 control=ctrl, input=c）。", "en": "Control key or modifier. Used alone, it sends a control key (e.g. enter/tab/esc/up/down). When sent together with input, it is treated as a key combination (e.g. control=ctrl with input=c)." },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "repeat",
                    "description": { "zh": "将本次输入重复 N 次（1..1000）", "en": "Repeat this write payload N times (1..1000)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次 API 调用超时毫秒数（可选）", "en": "Timeout for this API call in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "windows_process_terminate",
            "description": {
                "zh": "终止进程会话，可选移除会话记录。",
                "en": "Terminate a process session, with optional session removal."
            },
            "parameters": [
                {
                    "name": "session_id",
                    "description": { "zh": "会话 ID", "en": "Process session ID" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "remove",
                    "description": { "zh": "是否在终止后移除会话记录，默认 false", "en": "Whether to remove session record after termination, default false" },
                    "type": "boolean",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次 API 调用超时毫秒数（可选）", "en": "Timeout for this API call in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "windows_process_list",
            "description": {
                "zh": "列出进程会话。",
                "en": "List process sessions."
            },
            "parameters": [
                {
                    "name": "include_exited",
                    "description": { "zh": "是否包含已退出会话，默认 true", "en": "Whether to include exited sessions, default true" },
                    "type": "boolean",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次 API 调用超时毫秒数（可选）", "en": "Timeout for this API call in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "windows_test_connection",
            "description": {
                "zh": "测试 Agent HTTP 连通性，并执行 whoami 预设验证命令通道。",
                "en": "Test Agent HTTP connectivity and run whoami preset to verify command channel."
            },
            "parameters": [
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次测试超时毫秒数（可选）", "en": "Timeout for this test in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "read",
            "description": {
                "zh": "读取 Windows 文件。支持整文件读取、按 offset/length 字节分段读取、按 line_start/line_end 行范围读取。",
                "en": "Read a file on Windows. Supports full read, byte-range read by offset/length, and line-range read by line_start/line_end."
            },
            "parameters": [
                {
                    "name": "path",
                    "description": { "zh": "文件路径（绝对路径或相对 Agent 工作目录）", "en": "File path (absolute path or relative to agent working directory)" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "encoding",
                    "description": { "zh": "文本编码：utf8/utf16le/ascii/latin1，默认 utf8", "en": "Text encoding: utf8/utf16le/ascii/latin1, default utf8" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "offset",
                    "description": { "zh": "分段读取起始字节偏移（可选）", "en": "Start byte offset for segmented read (optional)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "length",
                    "description": { "zh": "分段读取长度（字节，可选）", "en": "Segment length in bytes (optional)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "line_start",
                    "description": { "zh": "按行读取时的起始行号（从 1 开始，可选）", "en": "Start line number for line-range read (1-based, optional)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "line_end",
                    "description": { "zh": "按行读取时的结束行号（包含该行，可选）", "en": "End line number for line-range read (inclusive, optional)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次操作超时毫秒数（可选）", "en": "Timeout for this operation in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "edit",
            "description": {
                "zh": "读取文件后执行精确字符串替换，再覆盖写回。默认要求只替换 1 处。",
                "en": "Read file, perform exact string replacement, and overwrite the file. Default expects exactly 1 replacement."
            },
            "parameters": [
                {
                    "name": "path",
                    "description": { "zh": "文件路径", "en": "File path" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "old_text",
                    "description": { "zh": "待替换的原始文本（精确匹配）", "en": "Original text to replace (exact match)" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "new_text",
                    "description": { "zh": "替换后的文本", "en": "Replacement text" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "expected_replacements",
                    "description": { "zh": "期望替换次数，默认 1", "en": "Expected number of replacements, default 1" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "encoding",
                    "description": { "zh": "文本编码，默认 utf8", "en": "Text encoding, default utf8" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次操作超时毫秒数（可选）", "en": "Timeout for this operation in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "write",
            "description": {
                "zh": "覆盖写入文件内容（会替换原文件全部内容）。",
                "en": "Overwrite file content (replaces the entire file)."
            },
            "parameters": [
                {
                    "name": "path",
                    "description": { "zh": "文件路径", "en": "File path" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "content",
                    "description": { "zh": "写入内容", "en": "Content to write" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "encoding",
                    "description": { "zh": "文本编码，默认 utf8", "en": "Text encoding, default utf8" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次操作超时毫秒数（可选）", "en": "Timeout for this operation in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        }
    ]
}
*/
const windowsControl = (function () {
    const WINDOWS_CONTROL_PACKAGE_VERSION = "0.2.2";
    const MAX_INLINE_WINDOWS_EXEC_OUTPUT_CHARS = 12000;
    const CONNECTION_TEST_TIMEOUT_MS = 5000;
    const ENV_KEYS = {
        baseUrl: "WINDOWS_AGENT_BASE_URL",
        token: "WINDOWS_AGENT_TOKEN",
        defaultShell: "WINDOWS_AGENT_DEFAULT_SHELL",
        timeoutMs: "WINDOWS_AGENT_TIMEOUT_MS"
    };
    function asText(value) {
        return String(value == null ? "" : value);
    }
    function buildVersionMismatchMessage(remoteVersion) {
        return [
            `Version mismatch: package=${WINDOWS_CONTROL_PACKAGE_VERSION}, agent=${remoteVersion || "unknown"}.`,
            "请前往 Windows 一键配置，重新上传最新 operit-pc-agent.zip 到电脑并运行，然后再粘贴最新配置。"
        ].join(" ");
    }
    function readEnv(name) {
        if (typeof getEnv === "function") {
            const value = getEnv(name);
            if (value !== undefined && value !== null) {
                return asText(value).trim();
            }
        }
        return "";
    }
    function normalizeShell(value, fallback) {
        const raw = asText(value || fallback).trim().toLowerCase();
        if (raw === "pwsh" || raw === "cmd") {
            return raw;
        }
        return "powershell";
    }
    function parseTimeout(value, fallback) {
        const raw = asText(value).trim();
        if (!raw) {
            return fallback;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed < 1000 || parsed > 600000) {
            throw new Error("Invalid timeout_ms, expected 1000..600000");
        }
        return Math.floor(parsed);
    }
    function toHttpTimeoutSeconds(timeoutMs) {
        const seconds = Math.floor(timeoutMs / 1000);
        return seconds >= 1 ? seconds : 1;
    }
    function validateOptionalNonNegativeInt(value, fieldName) {
        if (value === undefined) {
            return undefined;
        }
        if (!Number.isFinite(value) || value < 0) {
            throw new Error(`Invalid ${fieldName}, expected non-negative integer`);
        }
        return Math.floor(value);
    }
    function validateOptionalPositiveInt(value, fieldName) {
        if (value === undefined) {
            return undefined;
        }
        if (!Number.isFinite(value) || value < 1) {
            throw new Error(`Invalid ${fieldName}, expected integer >= 1`);
        }
        return Math.floor(value);
    }
    function requireSessionId(value) {
        const sessionId = asText(value).trim();
        if (!sessionId) {
            throw new Error("session_id cannot be empty");
        }
        return sessionId;
    }
    function parseExpectedReplacements(value) {
        const raw = asText(value).trim();
        if (!raw) {
            return 1;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed < 1) {
            throw new Error("Invalid expected_replacements, expected integer >= 1");
        }
        return Math.floor(parsed);
    }
    function normalizeBaseUrl(rawValue) {
        let base = asText(rawValue).trim();
        if (!base) {
            throw new Error("Missing env: WINDOWS_AGENT_BASE_URL");
        }
        if (!/^https?:\/\//i.test(base)) {
            base = `http://${base}`;
        }
        base = base.replace(/\/+$/, "");
        return base;
    }
    function parseJson(content) {
        const text = asText(content).trim();
        if (!text) {
            return {};
        }
        try {
            return JSON.parse(text);
        }
        catch (error) {
            throw new Error(`Agent response is not valid JSON: ${error && error.message ? error.message : String(error)}`);
        }
    }
    function resolveAgentConfig() {
        const baseUrl = normalizeBaseUrl(readEnv(ENV_KEYS.baseUrl));
        const token = readEnv(ENV_KEYS.token);
        if (!token) {
            throw new Error("Missing env: WINDOWS_AGENT_TOKEN");
        }
        const defaultShell = normalizeShell(readEnv(ENV_KEYS.defaultShell), "powershell");
        const timeoutMs = parseTimeout(readEnv(ENV_KEYS.timeoutMs), 30000);
        return {
            baseUrl,
            token,
            defaultShell,
            timeoutMs
        };
    }
    async function httpRequest(config, path, method, body, timeoutMs, strictTimeout = false) {
        const timeoutSeconds = toHttpTimeoutSeconds(timeoutMs);
        const response = await Tools.Net.http({
            url: `${config.baseUrl}${path}`,
            method,
            headers: {
                Accept: "application/json"
            },
            body: body || undefined,
            connect_timeout: strictTimeout ? timeoutSeconds : Math.min(timeoutSeconds, 10),
            read_timeout: strictTimeout ? timeoutSeconds : timeoutSeconds + 5,
            validateStatus: false
        });
        return {
            statusCode: response.statusCode,
            content: response.content,
            url: response.url
        };
    }
    async function ensureVersionCompatible(config, timeoutMs, strictTimeout = false) {
        const healthResponse = await httpRequest(config, "/api/health", "GET", null, timeoutMs, strictTimeout);
        const health = parseJson(healthResponse.content);
        if (healthResponse.statusCode >= 400 || !health.ok) {
            throw new Error(`Health check failed: HTTP ${healthResponse.statusCode}`);
        }
        const remoteVersion = asText(health.version || health.agentVersion).trim();
        if (!remoteVersion) {
            throw new Error("Agent version is missing. 请前往 Windows 一键配置重新部署电脑端。");
        }
        if (remoteVersion !== WINDOWS_CONTROL_PACKAGE_VERSION) {
            throw new Error(buildVersionMismatchMessage(remoteVersion));
        }
        return {
            health,
            remoteVersion
        };
    }
    async function postCommand(config, payload, timeoutMs, strictTimeout = false) {
        const requestBody = {
            ...payload,
            token: config.token || undefined,
            timeoutMs
        };
        const response = await httpRequest(config, "/api/command/execute", "POST", requestBody, timeoutMs, strictTimeout);
        const data = parseJson(response.content);
        if (response.statusCode >= 400) {
            const message = data && data.error ? asText(data.error) : `HTTP ${response.statusCode}`;
            throw new Error(`Agent command request failed: ${message}`);
        }
        return data;
    }
    async function postTextFileApi(config, path, payload, timeoutMs) {
        const requestBody = {
            ...payload,
            token: config.token || undefined,
            timeoutMs
        };
        const response = await httpRequest(config, path, "POST", requestBody, timeoutMs);
        const data = parseJson(response.content);
        if (response.statusCode >= 400 || !data.ok) {
            const message = data && data.error ? asText(data.error) : `HTTP ${response.statusCode}`;
            throw new Error(`Agent text file request failed: ${message}`);
        }
        return data;
    }
    async function postProcessApi(config, path, payload, timeoutMs) {
        const requestBody = {
            ...payload,
            token: config.token || undefined
        };
        const response = await httpRequest(config, path, "POST", requestBody, timeoutMs);
        const data = parseJson(response.content);
        if (response.statusCode >= 400 || data.ok === false) {
            const message = data && data.error ? asText(data.error) : `HTTP ${response.statusCode}`;
            throw new Error(`Agent process request failed: ${message}`);
        }
        return data;
    }
    async function persistWindowsExecOutputIfTooLong(command, shell, data, config, versionCheck) {
        const stdout = asText(data.stdout);
        const stderr = asText(data.stderr);
        const outputChars = stdout.length + stderr.length;
        if (outputChars <= MAX_INLINE_WINDOWS_EXEC_OUTPUT_CHARS) {
            return null;
        }
        await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);
        const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
        const rand = Math.floor(Math.random() * 1000000);
        const filePath = `${OPERIT_CLEAN_ON_EXIT_DIR}/windows_exec_output_${timestamp}_${rand}.log`;
        const content = [
            `command: ${command}`,
            `shell: ${shell}`,
            `exitCode: ${asText(data.exitCode)}`,
            `timedOut: ${asText(!!data.timedOut)}`,
            `durationMs: ${asText(data.durationMs)}`,
            "",
            "--- stdout ---",
            stdout,
            "",
            "--- stderr ---",
            stderr
        ].join("\n");
        await Tools.Files.write(filePath, content, false);
        return {
            success: !!data.ok,
            agentBaseUrl: config.baseUrl,
            shell,
            command,
            exitCode: data.exitCode,
            timedOut: !!data.timedOut,
            durationMs: data.durationMs,
            output: "(saved_to_file)",
            stderr: "(saved_to_file)",
            outputSavedTo: filePath,
            outputChars,
            operitCleanOnExitDir: OPERIT_CLEAN_ON_EXIT_DIR,
            hint: "Output is large and saved to file. Use read_file_part or grep_code to inspect it.",
            packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
            agentVersion: versionCheck.remoteVersion,
            error: data.ok ? "" : "Command failed. See outputSavedTo for details."
        };
    }
    async function windows_exec(params) {
        try {
            const config = resolveAgentConfig();
            const command = asText(params?.command).trim();
            if (!command) {
                throw new Error("command cannot be empty");
            }
            const shell = normalizeShell(params?.shell, config.defaultShell);
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const data = await postCommand(config, { command, shell }, timeoutMs);
            const persistedResult = await persistWindowsExecOutputIfTooLong(command, shell, data, config, versionCheck);
            if (persistedResult) {
                return persistedResult;
            }
            return {
                success: !!data.ok,
                agentBaseUrl: config.baseUrl,
                shell,
                command,
                exitCode: data.exitCode,
                timedOut: !!data.timedOut,
                durationMs: data.durationMs,
                output: asText(data.stdout),
                stderr: asText(data.stderr),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion,
                error: data.ok ? "" : asText(data.error || data.stderr || "Command failed")
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function windows_test_connection(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = Math.min(parseTimeout(params?.timeout_ms, CONNECTION_TEST_TIMEOUT_MS), CONNECTION_TEST_TIMEOUT_MS);
            const startAt = Date.now();
            const versionCheck = await ensureVersionCompatible(config, timeoutMs, true);
            const elapsedMs = Date.now() - startAt;
            const remainingMs = timeoutMs - elapsedMs;
            if (remainingMs <= 0) {
                throw new Error(`Connection test timed out after ${timeoutMs}ms`);
            }
            const commandData = await postCommand(config, { preset: "whoami" }, remainingMs, true);
            return {
                success: !!commandData.ok,
                agentBaseUrl: config.baseUrl,
                shell: "cmd",
                command: "preset:whoami",
                exitCode: commandData.exitCode,
                timedOut: !!commandData.timedOut,
                durationMs: commandData.durationMs,
                output: asText(commandData.stdout),
                stderr: asText(commandData.stderr),
                health: versionCheck.health,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion,
                error: commandData.ok ? "" : asText(commandData.error || commandData.stderr || "Command channel failed")
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    function mapProcessResult(config, versionCheck, data) {
        const result = {
            success: true,
            agentBaseUrl: config.baseUrl,
            packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
            agentVersion: versionCheck.remoteVersion
        };
        const sessionId = asText(data && data.sessionId).trim();
        if (sessionId) {
            result.sessionId = sessionId;
        }
        const shellValue = asText(data && data.shell).trim();
        if (shellValue) {
            result.shell = normalizeShell(shellValue, config.defaultShell);
        }
        const status = asText(data && data.status).trim();
        if (status) {
            result.status = status;
        }
        if (data && data.pid !== undefined && data.pid !== null && data.pid !== "") {
            result.pid = Number(data.pid);
        }
        const createdAt = asText(data && data.createdAt).trim();
        if (createdAt) {
            result.createdAt = createdAt;
        }
        const updatedAt = asText(data && data.updatedAt).trim();
        if (updatedAt) {
            result.updatedAt = updatedAt;
        }
        if (data && data.exitCode !== undefined && data.exitCode !== null && data.exitCode !== "") {
            result.exitCode = Number(data.exitCode);
        }
        if (data && data.timedOut !== undefined) {
            result.timedOut = !!data.timedOut;
        }
        const commandPreview = asText(data && data.commandPreview).trim();
        if (commandPreview) {
            result.commandPreview = commandPreview;
        }
        if (data && data.stdout !== undefined) {
            result.stdout = asText(data.stdout);
        }
        if (data && data.stderr !== undefined) {
            result.stderr = asText(data.stderr);
        }
        if (data && data.stdoutOffset !== undefined && data.stdoutOffset !== null && data.stdoutOffset !== "") {
            result.stdoutOffset = Number(data.stdoutOffset);
        }
        if (data && data.stderrOffset !== undefined && data.stderrOffset !== null && data.stderrOffset !== "") {
            result.stderrOffset = Number(data.stderrOffset);
        }
        if (data && data.stdoutLatestOffset !== undefined && data.stdoutLatestOffset !== null && data.stdoutLatestOffset !== "") {
            result.stdoutLatestOffset = Number(data.stdoutLatestOffset);
        }
        if (data && data.stderrLatestOffset !== undefined && data.stderrLatestOffset !== null && data.stderrLatestOffset !== "") {
            result.stderrLatestOffset = Number(data.stderrLatestOffset);
        }
        if (data && data.stdoutFromOffset !== undefined && data.stdoutFromOffset !== null && data.stdoutFromOffset !== "") {
            result.stdoutFromOffset = Number(data.stdoutFromOffset);
        }
        if (data && data.stderrFromOffset !== undefined && data.stderrFromOffset !== null && data.stderrFromOffset !== "") {
            result.stderrFromOffset = Number(data.stderrFromOffset);
        }
        if (data && data.stdoutAvailableFrom !== undefined && data.stdoutAvailableFrom !== null && data.stdoutAvailableFrom !== "") {
            result.stdoutAvailableFrom = Number(data.stdoutAvailableFrom);
        }
        if (data && data.stderrAvailableFrom !== undefined && data.stderrAvailableFrom !== null && data.stderrAvailableFrom !== "") {
            result.stderrAvailableFrom = Number(data.stderrAvailableFrom);
        }
        if (data && data.stdoutTruncated !== undefined) {
            result.stdoutTruncated = !!data.stdoutTruncated;
        }
        if (data && data.stderrTruncated !== undefined) {
            result.stderrTruncated = !!data.stderrTruncated;
        }
        if (data && data.hasMore !== undefined) {
            result.hasMore = !!data.hasMore;
        }
        if (data && data.acceptedChars !== undefined && data.acceptedChars !== null && data.acceptedChars !== "") {
            result.acceptedChars = Number(data.acceptedChars);
        }
        if (data && data.wasRunning !== undefined) {
            result.wasRunning = !!data.wasRunning;
        }
        if (data && data.signalSent !== undefined) {
            result.signalSent = !!data.signalSent;
        }
        if (data && data.removed !== undefined) {
            result.removed = !!data.removed;
        }
        if (Array.isArray(data && data.items)) {
            result.items = data.items;
        }
        return result;
    }
    async function windows_process_start(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const shell = normalizeShell(params?.shell, config.defaultShell);
            const command = asText(params?.command);
            const maxRuntimeMs = validateOptionalPositiveInt(params?.max_runtime_ms, "max_runtime_ms");
            const data = await postProcessApi(config, "/api/process/start", {
                shell,
                command: command || undefined,
                max_runtime_ms: maxRuntimeMs === undefined ? undefined : maxRuntimeMs
            }, timeoutMs);
            return mapProcessResult(config, versionCheck, data);
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function windows_process_read(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const sessionId = requireSessionId(asText(params?.session_id));
            const stdoutOffset = validateOptionalNonNegativeInt(params?.stdout_offset, "stdout_offset");
            const stderrOffset = validateOptionalNonNegativeInt(params?.stderr_offset, "stderr_offset");
            const maxChars = validateOptionalPositiveInt(params?.max_chars, "max_chars");
            const data = await postProcessApi(config, "/api/process/read", {
                session_id: sessionId,
                stdout_offset: stdoutOffset === undefined ? 0 : stdoutOffset,
                stderr_offset: stderrOffset === undefined ? 0 : stderrOffset,
                max_chars: maxChars === undefined ? undefined : maxChars
            }, timeoutMs);
            return mapProcessResult(config, versionCheck, data);
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function windows_process_write(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const sessionId = requireSessionId(asText(params?.session_id));
            const hasInput = !!(params && params.input !== undefined && params.input !== null);
            const hasControl = !!(params && params.control !== undefined && params.control !== null && String(params.control).trim() !== "");
            if (!hasInput && !hasControl) {
                throw new Error("Missing required input: provide input or control");
            }
            const input = hasInput ? asText(params && params.input) : undefined;
            const control = hasControl ? asText(params && params.control) : undefined;
            const repeat = validateOptionalPositiveInt(params?.repeat, "repeat");
            const data = await postProcessApi(config, "/api/process/write", {
                session_id: sessionId,
                input,
                control,
                repeat: repeat === undefined ? undefined : repeat
            }, timeoutMs);
            return mapProcessResult(config, versionCheck, data);
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function windows_process_terminate(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const sessionId = requireSessionId(asText(params?.session_id));
            const remove = params?.remove;
            const data = await postProcessApi(config, "/api/process/terminate", {
                session_id: sessionId,
                remove: remove === undefined ? undefined : remove
            }, timeoutMs);
            return mapProcessResult(config, versionCheck, data);
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function windows_process_list(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const includeExited = params?.include_exited;
            const data = await postProcessApi(config, "/api/process/list", {
                include_exited: includeExited === undefined ? true : includeExited
            }, timeoutMs);
            return mapProcessResult(config, versionCheck, data);
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function read(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const path = asText(params?.path).trim();
            if (!path) {
                throw new Error("path cannot be empty");
            }
            const encoding = asText(params?.encoding).trim();
            const offset = validateOptionalNonNegativeInt(params?.offset, "offset");
            const length = validateOptionalNonNegativeInt(params?.length, "length");
            const lineStart = validateOptionalPositiveInt(params?.line_start, "line_start");
            const lineEnd = validateOptionalPositiveInt(params?.line_end, "line_end");
            const useSegment = offset !== undefined || length !== undefined;
            const useLineRange = lineStart !== undefined || lineEnd !== undefined;
            if (useSegment && useLineRange) {
                throw new Error("offset/length cannot be used together with line_start/line_end");
            }
            if (lineStart !== undefined && lineEnd !== undefined && lineEnd < lineStart) {
                throw new Error("line_end must be greater than or equal to line_start");
            }
            const data = useLineRange
                ? await postTextFileApi(config, "/api/file/read_lines", {
                    path,
                    encoding: encoding || undefined,
                    line_start: lineStart === undefined ? 1 : lineStart,
                    line_end: lineEnd === undefined ? undefined : lineEnd
                }, timeoutMs)
                : useSegment
                    ? await postTextFileApi(config, "/api/file/read_segment", {
                        path,
                        encoding: encoding || undefined,
                        offset: offset === undefined ? 0 : offset,
                        length: length === undefined ? undefined : length
                    }, timeoutMs)
                    : await postTextFileApi(config, "/api/file/read", {
                        path,
                        encoding: encoding || undefined
                    }, timeoutMs);
            const result = {
                success: true,
                agentBaseUrl: config.baseUrl,
                path: asText(data.path),
                encoding: asText(data.encoding),
                content: asText(data.content),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion
            };
            if (data.offset !== undefined) {
                result.offset = Number(data.offset);
            }
            if (data.length !== undefined) {
                result.length = Number(data.length);
            }
            if (data.totalBytes !== undefined) {
                result.totalBytes = Number(data.totalBytes);
            }
            if (data.lineStart !== undefined) {
                result.lineStart = Number(data.lineStart);
            }
            if (data.lineEnd !== undefined) {
                result.lineEnd = Number(data.lineEnd);
            }
            if (data.totalLines !== undefined) {
                result.totalLines = Number(data.totalLines);
            }
            if (data.eof !== undefined) {
                result.eof = !!data.eof;
            }
            return result;
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function write(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const path = asText(params?.path).trim();
            if (!path) {
                throw new Error("path cannot be empty");
            }
            const content = asText(params?.content);
            const encoding = asText(params?.encoding).trim();
            const data = await postTextFileApi(config, "/api/file/write", {
                path,
                content,
                encoding: encoding || undefined
            }, timeoutMs);
            return {
                success: true,
                agentBaseUrl: config.baseUrl,
                path: asText(data.path),
                encoding: asText(data.encoding),
                sizeBytes: Number(data.sizeBytes),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function edit(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params?.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const path = asText(params?.path).trim();
            if (!path) {
                throw new Error("path cannot be empty");
            }
            const oldText = asText(params?.old_text);
            if (!oldText) {
                throw new Error("old_text cannot be empty");
            }
            const newText = asText(params?.new_text);
            const expectedReplacements = parseExpectedReplacements(params?.expected_replacements);
            const encoding = asText(params?.encoding).trim();
            const data = await postTextFileApi(config, "/api/file/edit", {
                path,
                old_text: oldText,
                new_text: newText,
                expected_replacements: expectedReplacements,
                encoding: encoding || undefined
            }, timeoutMs);
            return {
                success: true,
                agentBaseUrl: config.baseUrl,
                path: asText(data.path),
                encoding: asText(data.encoding),
                sizeBytes: Number(data.sizeBytes),
                replacements: Number(data.replacements),
                expectedReplacements: Number(data.expectedReplacements),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    return {
        windows_exec,
        windows_process_start,
        windows_process_read,
        windows_process_write,
        windows_process_terminate,
        windows_process_list,
        windows_test_connection,
        windows_check_connection: windows_test_connection,
        read,
        edit,
        write
    };
})();
exports.windows_exec = windowsControl.windows_exec;
exports.windows_process_start = windowsControl.windows_process_start;
exports.windows_process_read = windowsControl.windows_process_read;
exports.windows_process_write = windowsControl.windows_process_write;
exports.windows_process_terminate = windowsControl.windows_process_terminate;
exports.windows_process_list = windowsControl.windows_process_list;
exports.windows_test_connection = windowsControl.windows_test_connection;
exports.windows_check_connection = windowsControl.windows_check_connection;
exports.read = windowsControl.read;
exports.edit = windowsControl.edit;
exports.write = windowsControl.write;
