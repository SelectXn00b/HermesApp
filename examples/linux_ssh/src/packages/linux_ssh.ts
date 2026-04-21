/* METADATA
{
    "name": "linux_ssh",
    "display_name": {
        "zh": "Linux SSH",
        "en": "Linux SSH"
    },
    "description": {
        "zh": "基于 terminal 集成能力提供 Linux SSH 连接、tmux 长任务与远程文件操作。",
        "en": "Linux SSH tools powered by terminal integration, including tmux long jobs and remote file operations."
    },
    "enabledByDefault": true,
    "category": "System",
    "env": [
        {
            "name": "LINUX_SSH_HOST",
            "description": { "zh": "SSH 主机地址", "en": "SSH host" },
            "required": false
        },
        {
            "name": "LINUX_SSH_PORT",
            "description": { "zh": "SSH 端口，默认 22", "en": "SSH port, default 22" },
            "required": false
        },
        {
            "name": "LINUX_SSH_USERNAME",
            "description": { "zh": "SSH 用户名", "en": "SSH username" },
            "required": false
        },
        {
            "name": "LINUX_SSH_PASSWORD",
            "description": { "zh": "SSH 密码（可选，使用密钥时可留空）", "en": "SSH password (optional when key auth is used)" },
            "required": false
        },
        {
            "name": "LINUX_SSH_PRIVATE_KEY_PATH",
            "description": { "zh": "SSH 私钥路径（可选）", "en": "SSH private key path (optional)" },
            "required": false
        },
        {
            "name": "LINUX_SSH_TIMEOUT_MS",
            "description": { "zh": "默认命令超时毫秒，默认 20000", "en": "Default timeout in ms, default 20000" },
            "required": false
        }
    ],
    "tools": [
        {
            "name": "usage_advice",
            "description": {
                "zh": "Linux SSH 使用建议：\\n- 长任务优先用 tmux（linux_ssh_tmux_run），不要直接前台阻塞。\\n- 先用 linux_ssh_configure 配置连接参数，后续工具不再接收认证参数。\\n- 修改远程文件前优先先读后改，避免覆盖错误。\\n- 避免破坏性命令（如 rm -rf /、强制重置），除非得到明确授权。",
                "en": "Linux SSH usage advice:\\n- Prefer tmux for long-running tasks (linux_ssh_tmux_run).\\n- Configure SSH fields with linux_ssh_configure first; other tools no longer accept auth fields.\\n- Read-before-write when editing remote files.\\n- Avoid destructive commands unless explicitly authorized."
            },
            "parameters": [],
            "advice": true
        },
        {
            "name": "linux_ssh_configure",
            "description": {
                "zh": "设置并持久化 Linux SSH 连接参数，可选测试连通性。",
                "en": "Set and persist Linux SSH connection parameters, with optional connectivity test."
            },
            "parameters": [
                { "name": "host", "description": { "zh": "主机地址", "en": "Host" }, "type": "string", "required": false },
                { "name": "port", "description": { "zh": "端口，默认 22", "en": "Port, default 22" }, "type": "number", "required": false },
                { "name": "username", "description": { "zh": "用户名", "en": "Username" }, "type": "string", "required": false },
                { "name": "password", "description": { "zh": "密码（可选）", "en": "Password (optional)" }, "type": "string", "required": false },
                { "name": "private_key_path", "description": { "zh": "私钥路径（可选）", "en": "Private key path (optional)" }, "type": "string", "required": false },
                { "name": "timeout_ms", "description": { "zh": "默认超时毫秒（可选）", "en": "Default timeout ms (optional)" }, "type": "number", "required": false },
                { "name": "test_connection", "description": { "zh": "是否立即测试连接", "en": "Whether to test connection immediately" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "linux_ssh_test_connection",
            "description": { "zh": "测试 SSH 连接并返回远端用户和主机信息。", "en": "Test SSH connection and return remote user/host info." },
            "parameters": [
                { "name": "timeout_ms", "description": { "zh": "超时毫秒（可选）", "en": "Timeout ms (optional)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_exec",
            "description": { "zh": "通过 SSH 在远程 Linux 执行一条命令。", "en": "Execute one command on remote Linux through SSH." },
            "parameters": [
                { "name": "command", "description": { "zh": "要执行的命令", "en": "Command to execute" }, "type": "string", "required": true },
                { "name": "timeout_ms", "description": { "zh": "超时毫秒（可选）", "en": "Timeout ms (optional)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_ensure_tmux",
            "description": { "zh": "在远程 Linux 自动安装并校验 tmux。", "en": "Auto install and verify tmux on remote Linux." },
            "parameters": []
        },
        {
            "name": "linux_ssh_tmux_run",
            "description": { "zh": "在远程 tmux 中启动长任务（断线不影响任务）。", "en": "Start a long-running task in remote tmux (survives disconnects)." },
            "parameters": [
                { "name": "command", "description": { "zh": "要执行的长任务命令", "en": "Long-running command" }, "type": "string", "required": true },
                { "name": "workdir", "description": { "zh": "远程工作目录（可选）", "en": "Remote working directory (optional)" }, "type": "string", "required": false },
                { "name": "window_name", "description": { "zh": "tmux 窗口名（可选，不传则自动创建 task-N）", "en": "tmux window name (optional, auto-create task-N when omitted)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_tmux_capture",
            "description": { "zh": "抓取远程 tmux 会话窗口输出。", "en": "Capture output from remote tmux session/window." },
            "parameters": [
                { "name": "window_name", "description": { "zh": "tmux 窗口名（可选）", "en": "tmux window name (optional)" }, "type": "string", "required": false },
                { "name": "max_lines", "description": { "zh": "最多抓取行数，默认 200", "en": "Max lines, default 200" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_tmux_list_windows",
            "description": { "zh": "列出远程 tmux 会话中的窗口列表。", "en": "List windows in a remote tmux session." },
            "parameters": []
        },
        {
            "name": "linux_ssh_tmux_input",
            "description": { "zh": "向远程 tmux 指定窗口发送文本或控制键。", "en": "Send text or control keys into a remote tmux window." },
            "parameters": [
                { "name": "window_name", "description": { "zh": "tmux 窗口名", "en": "tmux window name" }, "type": "string", "required": true },
                { "name": "input", "description": { "zh": "文本输入（可选）", "en": "Input text (optional)" }, "type": "string", "required": false },
                { "name": "control", "description": { "zh": "控制键（如 enter/tab/ctrl+c）", "en": "Control key (e.g. enter/tab/ctrl+c)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_tmux_close",
            "description": { "zh": "关闭远程 tmux 指定窗口。", "en": "Close a remote tmux window." },
            "parameters": [
                { "name": "window_name", "description": { "zh": "tmux 窗口名", "en": "tmux window name" }, "type": "string", "required": true }
            ]
        },
        {
            "name": "linux_ssh_terminal_input",
            "description": { "zh": "向交互 SSH 终端输入文本或控制键。", "en": "Write text/control keys into interactive SSH terminal." },
            "parameters": [
                { "name": "input", "description": { "zh": "文本输入（可选）", "en": "Input text (optional)" }, "type": "string", "required": false },
                { "name": "control", "description": { "zh": "控制键（如 enter/tab/ctrl）", "en": "Control key (e.g. enter/tab/ctrl)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_terminal_screen",
            "description": { "zh": "获取交互 SSH 终端当前可见屏幕。", "en": "Get current visible screen of interactive SSH terminal." },
            "parameters": []
        },
        {
            "name": "linux_ssh_ls",
            "description": { "zh": "列出远程目录。", "en": "List remote directory." },
            "parameters": [
                { "name": "path", "description": { "zh": "目录路径，默认 ~", "en": "Directory path, default ~" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_read",
            "description": { "zh": "读取远程文件（支持按行范围）。", "en": "Read remote file (supports line range)." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "line_start", "description": { "zh": "起始行（可选）", "en": "Start line (optional)" }, "type": "number", "required": false },
                { "name": "line_end", "description": { "zh": "结束行（可选）", "en": "End line (optional)" }, "type": "number", "required": false },
                { "name": "sudo", "description": { "zh": "是否使用 sudo -n 读取，默认 false", "en": "Whether to read with sudo -n, default false" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "linux_ssh_write",
            "description": { "zh": "写入远程文件（默认覆盖，可选追加）。", "en": "Write remote file (overwrite by default, append optional)." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "content", "description": { "zh": "写入内容", "en": "Content to write" }, "type": "string", "required": true },
                { "name": "append", "description": { "zh": "是否追加，默认 false", "en": "Append mode, default false" }, "type": "boolean", "required": false },
                { "name": "sudo", "description": { "zh": "是否使用 sudo -n 写入，默认 false", "en": "Whether to write with sudo -n, default false" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "linux_ssh_edit",
            "description": { "zh": "按精确字符串替换远程文件内容。", "en": "Edit remote file by exact string replacement." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "old_text", "description": { "zh": "旧文本", "en": "Old text" }, "type": "string", "required": true },
                { "name": "new_text", "description": { "zh": "新文本", "en": "New text" }, "type": "string", "required": true },
                { "name": "expected_replacements", "description": { "zh": "期望替换次数，默认 1", "en": "Expected replacements, default 1" }, "type": "number", "required": false },
                { "name": "sudo", "description": { "zh": "是否使用 sudo -n 读取和写回，默认 false", "en": "Whether to read and write back with sudo -n, default false" }, "type": "boolean", "required": false }
            ]
        }
    ]
}
*/

const linuxSshTools = (function () {
    const PACKAGE_VERSION = "0.1.0";
    const DEFAULT_PORT = 22;
    const DEFAULT_TIMEOUT_MS = 20_000;
    const DEFAULT_TERMINAL_SESSION_NAME = "linux_ssh_terminal";
    const DEFAULT_HIDDEN_EXECUTOR_NAME = "linux_ssh";
    const DEFAULT_TMUX_SESSION_NAME = "operit_ai";
    const MAX_INLINE_TERMINAL_OUTPUT_CHARS = 12_000;
    const LARGE_OUTPUT_HINT = "Output is large and saved to file. Use read_file_part or grep_code to inspect it.";

    const ENV_KEYS = {
        host: "LINUX_SSH_HOST",
        port: "LINUX_SSH_PORT",
        username: "LINUX_SSH_USERNAME",
        password: "LINUX_SSH_PASSWORD",
        privateKeyPath: "LINUX_SSH_PRIVATE_KEY_PATH",
        timeoutMs: "LINUX_SSH_TIMEOUT_MS"
    };

    const CONFIGURE_SSH_CONFIG_OPTIONS = {
        persistIfProvided: true,
        requireAuth: false,
        allowParamConnection: true,
        allowParamAuth: true
    };

    const STORED_SSH_CONFIG_OPTIONS = {
        persistIfProvided: false,
        requireAuth: true,
        allowParamConnection: false,
        allowParamAuth: false
    };

    /**
     * Tool params are pre-converted by Kotlin based on metadata.
     * Keep only business-rule validation in TS.
     */
    /**
     * @typedef {{
     *   host?: string;
     *   port?: number;
     *   username?: string;
     *   password?: string;
     *   private_key_path?: string;
     *   timeout_ms?: number;
     *   test_connection?: boolean;
     * }} LinuxSshConfigureParams
     */

    /**
     * @typedef {{ timeout_ms?: number }} LinuxSshTestConnectionParams
     * @typedef {{ command: string; timeout_ms?: number }} LinuxSshExecParams
     * @typedef {{ command: string; workdir?: string; window_name?: string }} LinuxSshTmuxRunParams
     * @typedef {{ window_name?: string; max_lines?: number }} LinuxSshTmuxCaptureParams
     * @typedef {{ window_name: string; input?: string; control?: string }} LinuxSshTmuxInputParams
     * @typedef {{ window_name: string }} LinuxSshTmuxCloseParams
     * @typedef {{ input?: string; control?: string }} LinuxSshTerminalInputParams
     * @typedef {{ path?: string }} LinuxSshLsParams
     * @typedef {{ path: string; line_start?: number; line_end?: number; sudo?: boolean }} LinuxSshReadParams
     * @typedef {{ path: string; content: string; append?: boolean; sudo?: boolean }} LinuxSshWriteParams
     * @typedef {{ path: string; old_text: string; new_text: string; expected_replacements?: number; sudo?: boolean }} LinuxSshEditParams
     */

    function asText(value) {
        return String(value == null ? "" : value);
    }

    function firstNonBlank(...values) {
        for (let i = 0; i < values.length; i += 1) {
            const value = values[i];
            if (typeof value === "string" && value.trim()) {
                return value.trim();
            }
        }
        return "";
    }

    function parsePositiveInt(value, fallbackValue) {
        const raw = asText(value).trim();
        if (!raw) {
            return fallbackValue;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed <= 0) {
            return fallbackValue;
        }
        return Math.floor(parsed);
    }

    async function persistResultOutputIfTooLong(data, fileLabel) {
        const outputStr = typeof data?.output === "string"
            ? data.output
            : String(data?.output ?? "");

        if (outputStr.length <= MAX_INLINE_TERMINAL_OUTPUT_CHARS) {
            return data;
        }

        await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);

        const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
        const rand = Math.floor(Math.random() * 1_000_000);
        const safeLabel = firstNonBlank(fileLabel, "linux_ssh_output")
            .replace(/[^a-zA-Z0-9._-]+/g, "_");
        const filePath = `${OPERIT_CLEAN_ON_EXIT_DIR}/${safeLabel}_${timestamp}_${rand}.log`;

        await Tools.Files.write(filePath, outputStr, false);

        return {
            ...data,
            output: "(saved_to_file)",
            output_saved_to: filePath,
            output_chars: outputStr.length,
            operit_clean_on_exit_dir: OPERIT_CLEAN_ON_EXIT_DIR,
            hint: LARGE_OUTPUT_HINT
        };
    }

    function mergePersistedOutput(target, source) {
        if (!source || !source.output_saved_to) {
            return target;
        }

        return {
            ...target,
            output: source.output,
            output_saved_to: source.output_saved_to,
            output_chars: source.output_chars,
            operit_clean_on_exit_dir: source.operit_clean_on_exit_dir,
            hint: source.hint || LARGE_OUTPUT_HINT
        };
    }

    function readEnv(name) {
        if (typeof getEnv !== "function") {
            return "";
        }
        const value = getEnv(name);
        return value == null ? "" : asText(value).trim();
    }

    function shellQuote(value) {
        return `'${asText(value).replace(/'/g, `'"'"'`)}'`;
    }

    function stripWrappingQuotes(value) {
        let text = asText(value).trim();
        while (text.length >= 2) {
            const first = text.charAt(0);
            const last = text.charAt(text.length - 1);
            const wrappedBySingleQuote = first === "'" && last === "'";
            const wrappedByDoubleQuote = first === "\"" && last === "\"";
            if (!wrappedBySingleQuote && !wrappedByDoubleQuote) {
                break;
            }
            text = text.slice(1, -1).trim();
        }
        return text;
    }

    function createErrorResult(error) {
        return {
            success: false,
            packageVersion: PACKAGE_VERSION,
            error: error && error.message ? error.message : String(error)
        };
    }

    function createSuccessResult(data) {
        return {
            success: true,
            packageVersion: PACKAGE_VERSION,
            ...(data || {})
        };
    }

    async function runTool(action) {
        try {
            return await action();
        } catch (error) {
            return createErrorResult(error);
        }
    }

    async function persistToolResult(fileLabel, data) {
        return await persistResultOutputIfTooLong({
            packageVersion: PACKAGE_VERSION,
            ...(data || {})
        }, fileLabel);
    }

    function mergeToolResult(data, source) {
        return mergePersistedOutput({
            packageVersion: PACKAGE_VERSION,
            ...(data || {})
        }, source);
    }

    function extractStringResult(result) {
        if (typeof result === "string") {
            return result;
        }
        if (!result || typeof result !== "object" || Array.isArray(result)) {
            return "";
        }
        if (typeof result.value === "string") {
            return result.value;
        }
        if (result.value != null) {
            return String(result.value);
        }
        if (typeof result.data === "string") {
            return result.data;
        }
        if (result.data != null) {
            return String(result.data);
        }
        return "";
    }

    async function writeEnvVar(key, value) {
        try {
            await Tools.SoftwareSettings.writeEnvironmentVariable(key, asText(value));
            return true;
        } catch (_error) {
            return false;
        }
    }

    async function persistProvidedConfig(params) {
        if (!params || typeof params !== "object") {
            return [];
        }
        const entries = [
            ["host", ENV_KEYS.host],
            ["port", ENV_KEYS.port],
            ["username", ENV_KEYS.username],
            ["password", ENV_KEYS.password],
            ["private_key_path", ENV_KEYS.privateKeyPath],
            ["timeout_ms", ENV_KEYS.timeoutMs]
        ];

        const persisted: string[] = [];
        for (let i = 0; i < entries.length; i += 1) {
            const paramKey = entries[i][0];
            const envKey = entries[i][1];
            if (params[paramKey] !== undefined && params[paramKey] !== null) {
                const ok = await writeEnvVar(envKey, params[paramKey]);
                if (ok) {
                    persisted.push(envKey);
                }
            }
        }
        return persisted;
    }

    async function resolveSshConfig(params, options) {
        const opts = options || {};
        if (opts.persistIfProvided !== false) {
            await persistProvidedConfig(params);
        }

        const allowParamConnection = opts.allowParamConnection !== false;
        const allowParamAuth = opts.allowParamAuth !== false;

        const host = firstNonBlank(
            allowParamConnection && params ? asText(params.host) : "",
            readEnv(ENV_KEYS.host)
        );
        const username = firstNonBlank(
            allowParamConnection && params ? asText(params.username) : "",
            readEnv(ENV_KEYS.username)
        );
        const password = firstNonBlank(
            allowParamAuth && params ? asText(params.password) : "",
            readEnv(ENV_KEYS.password)
        );
        const privateKeyPath = firstNonBlank(
            allowParamAuth && params ? stripWrappingQuotes(params.private_key_path) : "",
            stripWrappingQuotes(readEnv(ENV_KEYS.privateKeyPath))
        );

        const portRaw = firstNonBlank(
            allowParamConnection && params ? asText(params.port) : "",
            readEnv(ENV_KEYS.port),
            String(DEFAULT_PORT)
        );
        const timeoutRaw = firstNonBlank(params && asText(params.timeout_ms), readEnv(ENV_KEYS.timeoutMs), String(DEFAULT_TIMEOUT_MS));

        const port = parsePositiveInt(portRaw, DEFAULT_PORT);
        const timeoutMs = parsePositiveInt(timeoutRaw, DEFAULT_TIMEOUT_MS);

        if (!host) {
            throw new Error("Missing SSH host. Configure with linux_ssh_configure or set LINUX_SSH_HOST");
        }
        if (!username) {
            throw new Error("Missing SSH username. Configure with linux_ssh_configure or set LINUX_SSH_USERNAME");
        }

        if (opts.requireAuth !== false) {
            if (!password && !privateKeyPath) {
                throw new Error("Missing SSH auth. Configure password/private_key_path via linux_ssh_configure");
            }
        }

        return {
            host,
            port,
            username,
            password,
            privateKeyPath,
            timeoutMs
        };
    }

    async function resolveConfiguredSshConfig(params) {
        return await resolveSshConfig(params, CONFIGURE_SSH_CONFIG_OPTIONS);
    }

    async function resolveStoredSshConfig(params) {
        return await resolveSshConfig(params, STORED_SSH_CONFIG_OPTIONS);
    }

    async function createLocalTerminalSession() {
        return await Tools.System.terminal.create(DEFAULT_TERMINAL_SESSION_NAME);
    }

    function buildHiddenExecutorKey(scope) {
        const normalizedScope = firstNonBlank(scope, "default")
            .replace(/[^a-zA-Z0-9._-]+/g, "_");
        return `${DEFAULT_HIDDEN_EXECUTOR_NAME}:${normalizedScope}`;
    }

    async function runLocalHiddenCommand(command, timeoutMs, scope) {
        const effectiveTimeout = parsePositiveInt(timeoutMs, DEFAULT_TIMEOUT_MS);
        const executorKey = buildHiddenExecutorKey(scope);
        const result = await Tools.System.terminal.hiddenExec(command, {
            executorKey,
            timeoutMs: effectiveTimeout
        });
        return {
            sessionId: "",
            executorKey,
            exitCode: Number(result.exitCode || 0),
            timedOut: !!result.timedOut,
            output: asText(result.output)
        };
    }

    async function ensureLocalCommand(runner, commandName, installScript) {
        const check = await runner(
            `if command -v ${commandName} >/dev/null 2>&1; then echo '__FOUND__'; else echo '__MISSING__'; fi`,
            DEFAULT_TIMEOUT_MS
        );
        if (check.output.includes("__FOUND__")) {
            return { success: true, installed: false, output: check.output };
        }

        const install = await runner(installScript, 180_000);
        const verify = await runner(
            `if command -v ${commandName} >/dev/null 2>&1; then echo '__FOUND__'; else echo '__MISSING__'; fi`,
            DEFAULT_TIMEOUT_MS
        );

        if (!verify.output.includes("__FOUND__")) {
            throw new Error(
                `Failed to install ${commandName}.\nInstall output:\n${install.output}\nVerify output:\n${verify.output}`
            );
        }
        return { success: true, installed: true, output: install.output };
    }

    async function ensureLocalSshDependencies(config, runner) {
        await ensureLocalCommand(
            runner,
            "ssh",
            [
                "if command -v apt-get >/dev/null 2>&1; then",
                "  (sudo -n apt-get update && sudo -n apt-get install -y openssh-client) || (apt-get update && apt-get install -y openssh-client)",
                "elif command -v dnf >/dev/null 2>&1; then",
                "  (sudo -n dnf install -y openssh-clients) || dnf install -y openssh-clients",
                "elif command -v yum >/dev/null 2>&1; then",
                "  (sudo -n yum install -y openssh-clients) || yum install -y openssh-clients",
                "elif command -v pacman >/dev/null 2>&1; then",
                "  (sudo -n pacman -Sy --noconfirm openssh) || pacman -Sy --noconfirm openssh",
                "else",
                "  echo '__NO_PACKAGE_MANAGER__'",
                "fi"
            ].join("\n")
        );

        if (config.password && !config.privateKeyPath) {
            await ensureLocalCommand(
                runner,
                "sshpass",
                [
                    "if command -v apt-get >/dev/null 2>&1; then",
                    "  (sudo -n apt-get update && sudo -n apt-get install -y sshpass) || (apt-get update && apt-get install -y sshpass)",
                    "elif command -v dnf >/dev/null 2>&1; then",
                    "  (sudo -n dnf install -y sshpass) || dnf install -y sshpass",
                    "elif command -v yum >/dev/null 2>&1; then",
                    "  (sudo -n yum install -y sshpass) || yum install -y sshpass",
                    "elif command -v pacman >/dev/null 2>&1; then",
                    "  (sudo -n pacman -Sy --noconfirm sshpass) || pacman -Sy --noconfirm sshpass",
                    "else",
                    "  echo '__NO_PACKAGE_MANAGER__'",
                    "fi"
                ].join("\n")
            );
        }
    }

    function buildSshOptions(config) {
        const connectTimeoutSeconds = Math.max(5, Math.floor(config.timeoutMs / 1000));
        const options = [
            "-o StrictHostKeyChecking=no",
            "-o UserKnownHostsFile=/dev/null",
            "-o LogLevel=ERROR",
            "-o ServerAliveInterval=30",
            "-o ServerAliveCountMax=120",
            `-o ConnectTimeout=${connectTimeoutSeconds}`
        ];

        if (config.password && !config.privateKeyPath) {
            options.push("-o PreferredAuthentications=password", "-o PubkeyAuthentication=no");
        }

        return options.join(" ");
    }

    function buildSshCommand(config, remoteCommand, interactive) {
        const authPrefix = (config.password && !config.privateKeyPath)
            ? `SSHPASS=${shellQuote(config.password)} sshpass -e `
            : "";
        const keyPart = config.privateKeyPath ? ` -i ${shellQuote(config.privateKeyPath)}` : "";
        const target = `${config.username}@${config.host}`;
        const options = buildSshOptions(config);
        const tty = interactive ? " -tt" : "";
        const base = `${authPrefix}ssh${tty}${keyPart} ${options} -p ${config.port} ${shellQuote(target)}`;
        if (remoteCommand === undefined || remoteCommand === null) {
            return base;
        }
        return `${base} ${shellQuote(remoteCommand)}`;
    }

    function ensureTrailingNewline(value) {
        const text = asText(value);
        if (!text) {
            return "\n";
        }
        return text.endsWith("\n") ? text : `${text}\n`;
    }

    function buildLocalPipeCommand(stdinText, downstreamCommand, appendTrailingNewline) {
        const normalizedInput = appendTrailingNewline
            ? ensureTrailingNewline(stdinText)
            : asText(stdinText);
        return `printf '%s' ${shellQuote(normalizedInput)} | ${downstreamCommand}`;
    }

    async function runRemoteCommandHidden(config, remoteCommand, timeoutMs, scope) {
        const effectiveScope = firstNonBlank(scope, "remote");
        const runner = async function execute(command, commandTimeoutMs) {
            return await runLocalHiddenCommand(command, commandTimeoutMs, effectiveScope);
        };
        await ensureLocalSshDependencies(config, runner);
        const command = buildSshCommand(config, remoteCommand, false);
        const result = await runner(command, timeoutMs || config.timeoutMs);
        return result;
    }

    async function runRemoteCommandWithLocalStdinHidden(config, stdinText, remoteCommand, timeoutMs, scope, appendTrailingNewline) {
        const effectiveScope = firstNonBlank(scope, "remote");
        const runner = async function execute(command, commandTimeoutMs) {
            return await runLocalHiddenCommand(command, commandTimeoutMs, effectiveScope);
        };
        await ensureLocalSshDependencies(config, runner);
        const sshCommand = buildSshCommand(config, remoteCommand, false);
        const command = buildLocalPipeCommand(stdinText, sshCommand, appendTrailingNewline === true);
        const result = await runner(command, timeoutMs || config.timeoutMs);
        return result;
    }

    function extractBlock(output, beginToken, endToken) {
        const start = output.indexOf(beginToken);
        if (start < 0) {
            return "";
        }
        const from = start + beginToken.length;
        const end = output.indexOf(endToken, from);
        if (end < 0) {
            return output.slice(from).trim();
        }
        return output.slice(from, end).trim();
    }

    function hasExactMarkerLine(output, marker) {
        return asText(output)
            .split(/\r?\n/)
            .some((line) => asText(line).trim() === marker);
    }

    function extractOutputLineValue(output, prefix) {
        const lines = asText(output).split(/\r?\n/);
        for (let i = 0; i < lines.length; i += 1) {
            const line = asText(lines[i]).trim();
            if (line.startsWith(prefix)) {
                return line.slice(prefix.length).trim();
            }
        }
        return "";
    }

    function normalizeTmuxControlKey(control) {
        const raw = asText(control).trim();
        if (!raw) {
            return "";
        }

        const lower = raw.toLowerCase();
        const aliases = {
            enter: "Enter",
            return: "Enter",
            tab: "Tab",
            escape: "Escape",
            esc: "Escape",
            space: "Space",
            backspace: "BSpace",
            bs: "BSpace",
            delete: "Delete",
            del: "Delete",
            insert: "Insert",
            up: "Up",
            down: "Down",
            left: "Left",
            right: "Right",
            home: "Home",
            end: "End",
            pageup: "PageUp",
            pagedown: "PageDown"
        };
        if (Object.prototype.hasOwnProperty.call(aliases, lower)) {
            return aliases[lower];
        }

        const ctrlMatch = lower.match(/^ctrl[+-]([a-z])$/);
        if (ctrlMatch) {
            return `C-${ctrlMatch[1]}`;
        }

        const tmuxCtrlMatch = raw.match(/^C-([a-zA-Z])$/);
        if (tmuxCtrlMatch) {
            return `C-${tmuxCtrlMatch[1].toLowerCase()}`;
        }

        const functionMatch = lower.match(/^f([1-9]|1[0-2])$/);
        if (functionMatch) {
            return `F${functionMatch[1]}`;
        }

        return raw;
    }

    function buildRemoteShellCommand(script, args, useSudo) {
        const prefix = useSudo ? "sudo -n " : "";
        const argv = Array.isArray(args) ? args.map((arg) => shellQuote(arg)).join(" ") : "";
        return `${prefix}sh -c ${shellQuote(script)} sh${argv ? ` ${argv}` : ""}`;
    }

    function buildRemoteUserShellCommand() {
        const script = [
            'user_shell="${SHELL:-/bin/sh}"',
            'exec "$user_shell" -s --'
        ].join("\n");
        return buildRemoteShellCommand(script, [], false);
    }

    function buildRemotePathResolveLines(rawVarName, resolvedVarName, options) {
        const opts = options || {};
        const fallbackToHome = !!opts.fallbackToHome;
        return [
            `${resolvedVarName}="$${rawVarName}"`,
            `if [ -z "$${resolvedVarName}" ]; then`,
            fallbackToHome ? `  ${resolvedVarName}="$HOME"` : `  ${resolvedVarName}=""`,
            "fi",
            `case "$${resolvedVarName}" in`,
            '  "~")',
            `    ${resolvedVarName}="$HOME"`,
            "    ;;",
            '  "~/"*)',
            `    ${resolvedVarName}="$HOME/\${${resolvedVarName}#~/}"`,
            "    ;;",
            "esac"
        ];
    }

    async function ensureRemoteTmux(config) {
        const installScript = [
            "if command -v tmux >/dev/null 2>&1; then",
            "  echo '__TMUX_READY__'",
            "  exit 0",
            "fi",
            "if command -v apt-get >/dev/null 2>&1; then",
            "  (sudo -n apt-get update && sudo -n apt-get install -y tmux) || (apt-get update && apt-get install -y tmux)",
            "elif command -v dnf >/dev/null 2>&1; then",
            "  (sudo -n dnf install -y tmux) || dnf install -y tmux",
            "elif command -v yum >/dev/null 2>&1; then",
            "  (sudo -n yum install -y tmux) || yum install -y tmux",
            "elif command -v pacman >/dev/null 2>&1; then",
            "  (sudo -n pacman -Sy --noconfirm tmux) || pacman -Sy --noconfirm tmux",
            "else",
            "  echo '__NO_PACKAGE_MANAGER__'",
            "fi",
            "if command -v tmux >/dev/null 2>&1; then",
            "  echo '__TMUX_READY__'",
            "  exit 0",
            "fi",
            "echo '__TMUX_INSTALL_FAILED__'",
            "exit 7"
        ].join("\n");

        const result = await runRemoteCommandHidden(
            config,
            buildRemoteShellCommand(installScript, [], false),
            240_000,
            "tmux"
        );
        const success = result.exitCode === 0 && result.output.includes("__TMUX_READY__");
        return await persistResultOutputIfTooLong({
            success,
            exitCode: result.exitCode,
            timedOut: result.timedOut,
            output: result.output
        }, "linux_ssh_tmux_setup_output");
    }

    async function ensureRemoteTmuxWindow(config, requestedWindowName) {
        const script = [
            `session_name="${DEFAULT_TMUX_SESSION_NAME}"`,
            "requested_window=\"$1\"",
            "window_name=\"$requested_window\"",
            "window_exists() {",
            "  tmux list-windows -t \"$session_name\" -F '#{window_name}' | grep -Fx -- \"$1\" >/dev/null 2>&1",
            "}",
            "if [ -z \"$window_name\" ]; then",
            "  seq=1",
            "  while tmux has-session -t \"$session_name\" 2>/dev/null && window_exists \"task-$seq\"; do",
            "    seq=$((seq + 1))",
            "  done",
            "  window_name=\"task-$seq\"",
            "fi",
            "if tmux has-session -t \"$session_name\" 2>/dev/null; then",
            "  if ! window_exists \"$window_name\"; then",
            "    tmux new-window -d -t \"$session_name\" -n \"$window_name\"",
            "  fi",
            "else",
            "  tmux new-session -d -s \"$session_name\" -n \"$window_name\"",
            "fi",
            "printf '__OPERIT_TMUX_WINDOW_READY__\\n'",
            "echo \"window=$window_name\""
        ].join("\n");

        const result = await runRemoteCommandHidden(
            config,
            buildRemoteShellCommand(script, [requestedWindowName], false),
            config.timeoutMs,
            "tmux"
        );
        const success = result.exitCode === 0 && !result.timedOut && result.output.includes("__OPERIT_TMUX_WINDOW_READY__");
        const windowName = extractOutputLineValue(result.output, "window=");
        return await persistResultOutputIfTooLong({
            success,
            windowName,
            exitCode: result.exitCode,
            timedOut: result.timedOut,
            output: result.output
        }, "linux_ssh_tmux_window_output");
    }

    async function readRemoteFileContent(config, path, lineStart, lineEnd, useSudo) {
        let readCmd = "cat \"$resolved_path\"";

        if (lineStart !== undefined || lineEnd !== undefined) {
            const start = lineStart === undefined ? 1 : lineStart;
            const end = lineEnd === undefined ? "$" : String(lineEnd);
            readCmd = `sed -n '${start},${end}p' "$resolved_path"`;
        }

        const script = [
            "raw_path=\"$1\"",
            ...buildRemotePathResolveLines("raw_path", "resolved_path", { fallbackToHome: false }),
            "if [ ! -f \"$resolved_path\" ]; then",
            "  echo '__OPERIT_FILE_NOT_FOUND__'",
            "  exit 4",
            "fi",
            "printf '__OPERIT_BEGIN__\\n'",
            readCmd,
            "printf '\\n__OPERIT_END__\\n'"
        ].join("\n");

        const command = buildRemoteShellCommand(script, [path], useSudo);
        const result = await runRemoteCommandHidden(config, command, config.timeoutMs, "fs");
        if (result.exitCode !== 0 || result.timedOut) {
            throw new Error(`Failed to read remote file: ${result.output}`);
        }

        const content = extractBlock(result.output, "__OPERIT_BEGIN__", "__OPERIT_END__");
        return {
            output: result.output,
            content
        };
    }

    async function writeRemoteFileContent(config, path, content, appendMode, useSudo) {
        const redirectOperator = appendMode ? ">>" : ">";
        const script = [
            "raw_path=\"$1\"",
            ...buildRemotePathResolveLines("raw_path", "resolved_path", { fallbackToHome: false }),
            "mkdir -p \"$(dirname -- \"$resolved_path\")\"",
            `cat ${redirectOperator} \"$resolved_path\"`
        ].join("\n");

        const command = buildRemoteShellCommand(script, [path], useSudo);
        const result = await runRemoteCommandWithLocalStdinHidden(
            config,
            content,
            command,
            config.timeoutMs,
            "fs",
            false
        );
        if (result.exitCode !== 0 || result.timedOut) {
            throw new Error(`Failed to write remote file: ${result.output}`);
        }
    }

    async function linux_ssh_configure(params) {
        return await runTool(async () => {
            await resolveConfiguredSshConfig(params);
            const testConnection = params?.test_connection === true;
            const connection = testConnection ? await linux_ssh_test_connection(params) : null;
            return createSuccessResult({
                testConnection,
                connection
            });
        });
    }

    async function linux_ssh_test_connection(params) {
        return await runTool(async () => {
            const config = await resolveStoredSshConfig(params);
            const timeoutMs = params?.timeout_ms ?? config.timeoutMs;
            const command = [
                "printf '__OPERIT_CONNECT_BEGIN__\\n'",
                "echo \"user=$(whoami)\"",
                "echo \"host=$(hostname)\"",
                "if command -v tmux >/dev/null 2>&1; then echo 'tmux=present'; else echo 'tmux=missing'; fi",
                "printf '__OPERIT_CONNECT_END__\\n'"
            ].join("\n");

            const result = await runRemoteCommandHidden(
                config,
                buildRemoteShellCommand(command, [], false),
                timeoutMs,
                "remote"
            );
            const success = result.exitCode === 0 && !result.timedOut;
            const block = extractBlock(result.output, "__OPERIT_CONNECT_BEGIN__", "__OPERIT_CONNECT_END__");

            return await persistToolResult("linux_ssh_test_connection_output", {
                success,
                timeoutMs,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: block || result.output,
                error: success ? "" : `SSH connection failed, exitCode=${result.exitCode}`
            });
        });
    }

    async function linux_ssh_exec(params) {
        return await runTool(async () => {
            const command = asText(params?.command).trim();
            if (!command) {
                throw new Error("command cannot be empty");
            }

            const config = await resolveStoredSshConfig(params);
            const timeoutMs = params?.timeout_ms ?? config.timeoutMs;
            const result = await runRemoteCommandWithLocalStdinHidden(
                config,
                command,
                buildRemoteUserShellCommand(),
                timeoutMs,
                "remote",
                true
            );
            const success = result.exitCode === 0 && !result.timedOut;

            return await persistToolResult("linux_ssh_exec_output", {
                success,
                timeoutMs,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                sessionId: result.sessionId,
                error: success ? "" : `Remote command failed, exitCode=${result.exitCode}`
            });
        });
    }

    async function linux_ssh_ensure_tmux(params) {
        return await runTool(async () => {
            const config = await resolveStoredSshConfig(params);
            const tmuxResult = await ensureRemoteTmux(config);

            return mergeToolResult({
                success: !!tmuxResult.success,
                exitCode: tmuxResult.exitCode,
                timedOut: tmuxResult.timedOut,
                output: tmuxResult.output,
                error: tmuxResult.success ? "" : "Failed to install or verify tmux on remote host"
            }, tmuxResult);
        });
    }

    async function linux_ssh_tmux_run(params) {
        return await runTool(async () => {
            const command = asText(params?.command).trim();
            if (!command) {
                throw new Error("command cannot be empty");
            }

            const config = await resolveStoredSshConfig(params);
            const tmuxSessionName = DEFAULT_TMUX_SESSION_NAME;
            const requestedWindowName = asText(params?.window_name).trim();
            const workdir = stripWrappingQuotes(params?.workdir);

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                return mergeToolResult({
                    success: false,
                    tmuxSessionName,
                    requestedWindowName,
                    workdir,
                    exitCode: tmuxReady.exitCode,
                    timedOut: tmuxReady.timedOut,
                    output: tmuxReady.output,
                    error: "tmux setup failed"
                }, tmuxReady);
            }

            const targetWindowReady = await ensureRemoteTmuxWindow(config, requestedWindowName);
            if (!targetWindowReady.success || !targetWindowReady.windowName) {
                return mergeToolResult({
                    success: false,
                    tmuxSessionName,
                    requestedWindowName,
                    workdir,
                    exitCode: targetWindowReady.exitCode,
                    timedOut: targetWindowReady.timedOut,
                    output: targetWindowReady.output,
                    error: "tmux window setup failed"
                }, targetWindowReady);
            }

            const windowName = targetWindowReady.windowName;
            const targetWindow = `${tmuxSessionName}:${windowName}`;

            const script = [
                "target_window=\"$1\"",
                "raw_workdir=\"$2\"",
                "user_shell=\"${SHELL:-/bin/sh}\"",
                "payload_file=$(mktemp)",
                "launcher_file=$(mktemp)",
                "cleanup_now() {",
                "  rm -f -- \"$payload_file\" \"$launcher_file\"",
                "}",
                "trap cleanup_now EXIT INT TERM HUP",
                "cat > \"$payload_file\"",
                ...buildRemotePathResolveLines("raw_workdir", "resolved_workdir", { fallbackToHome: false }),
                "escaped_user_shell=$(printf '%s' \"$user_shell\" | sed \"s/'/'\\\"'\\\"'/g\")",
                "escaped_payload_file=$(printf '%s' \"$payload_file\" | sed \"s/'/'\\\"'\\\"'/g\")",
                "escaped_launcher_file=$(printf '%s' \"$launcher_file\" | sed \"s/'/'\\\"'\\\"'/g\")",
                "launcher_cleanup_line=\"  rm -f -- '$escaped_payload_file' '$escaped_launcher_file'\"",
                "if [ -n \"$raw_workdir\" ]; then",
                "  escaped_workdir=$(printf '%s' \"$resolved_workdir\" | sed \"s/'/'\\\"'\\\"'/g\")",
                "  printf '%s\\n' '#!/bin/sh' 'cleanup() {' \"$launcher_cleanup_line\" '}' 'trap cleanup EXIT' \"cd -- '$escaped_workdir' || exit 1\" \"'$escaped_user_shell' '$escaped_payload_file'\" > \"$launcher_file\"",
                "fi",
                "if [ -z \"$raw_workdir\" ]; then",
                "  printf '%s\\n' '#!/bin/sh' 'cleanup() {' \"$launcher_cleanup_line\" '}' 'trap cleanup EXIT' \"'$escaped_user_shell' '$escaped_payload_file'\" > \"$launcher_file\"",
                "fi",
                "tmux send-keys -t \"$target_window\" \"sh '$escaped_launcher_file'\" C-m",
                "trap - EXIT INT TERM HUP",
                "printf '__OPERIT_TMUX_RUN_OK__\\n'",
                `echo "session=${tmuxSessionName}"`,
                `echo "window=${windowName}"`
            ].join("\n");

            const result = await runRemoteCommandWithLocalStdinHidden(
                config,
                command,
                buildRemoteShellCommand(script, [targetWindow, workdir], false),
                config.timeoutMs,
                "tmux",
                true
            );
            const success = result.exitCode === 0 && !result.timedOut && result.output.includes("__OPERIT_TMUX_RUN_OK__");

            return await persistToolResult("linux_ssh_tmux_run_output", {
                success,
                tmuxSessionName,
                windowName,
                tmuxTarget: targetWindow,
                workdir,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                error: success ? "" : `tmux run failed, exitCode=${result.exitCode}`
            });
        });
    }

    async function linux_ssh_tmux_capture(params) {
        return await runTool(async () => {
            const config = await resolveStoredSshConfig(params);
            const tmuxSessionName = DEFAULT_TMUX_SESSION_NAME;
            const windowName = asText(params?.window_name).trim();
            const maxLines = params?.max_lines ?? 200;

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                return mergeToolResult({
                    success: false,
                    tmuxSessionName,
                    windowName,
                    maxLines,
                    exitCode: tmuxReady.exitCode,
                    timedOut: tmuxReady.timedOut,
                    output: tmuxReady.output,
                    error: "tmux setup failed"
                }, tmuxReady);
            }

            const target = windowName ? `${tmuxSessionName}:${windowName}` : tmuxSessionName;
            const script = [
                `tmux has-session -t ${shellQuote(tmuxSessionName)} 2>/dev/null || { echo '__OPERIT_TMUX_NOT_FOUND__'; exit 4; }`,
                "printf '__OPERIT_TMUX_CAPTURE_BEGIN__\\n'",
                `tmux capture-pane -t ${shellQuote(target)} -p -S -${maxLines}`,
                "printf '\\n__OPERIT_TMUX_CAPTURE_END__\\n'"
            ].join("\n");

            const result = await runRemoteCommandHidden(
                config,
                buildRemoteShellCommand(script, [], false),
                config.timeoutMs,
                "tmux"
            );
            const success = result.exitCode === 0 && !result.timedOut;
            const content = extractBlock(result.output, "__OPERIT_TMUX_CAPTURE_BEGIN__", "__OPERIT_TMUX_CAPTURE_END__");

            return await persistToolResult("linux_ssh_tmux_capture_output", {
                success,
                tmuxSessionName,
                windowName,
                maxLines,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: content || result.output,
                error: success ? "" : `tmux capture failed, exitCode=${result.exitCode}`
            });
        });
    }

    async function linux_ssh_tmux_list_windows(params) {
        return await runTool(async () => {
            const config = await resolveStoredSshConfig(params);
            const tmuxSessionName = DEFAULT_TMUX_SESSION_NAME;

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                return mergeToolResult({
                    success: false,
                    tmuxSessionName,
                    sessionExists: false,
                    windows: [],
                    count: 0,
                    exitCode: tmuxReady.exitCode,
                    timedOut: tmuxReady.timedOut,
                    output: tmuxReady.output,
                    error: "tmux setup failed"
                }, tmuxReady);
            }

            const script = [
                `tmux has-session -t ${shellQuote(tmuxSessionName)} 2>/dev/null || { echo '__OPERIT_TMUX_NOT_FOUND__'; exit 4; }`,
                "printf '__OPERIT_TMUX_WINDOWS_BEGIN__\\n'",
                `tmux list-windows -t ${shellQuote(tmuxSessionName)} -F '#{window_index}:#{window_name}'`,
                "printf '__OPERIT_TMUX_WINDOWS_END__\\n'"
            ].join("; ");

            const result = await runRemoteCommandHidden(
                config,
                buildRemoteShellCommand(script, [], false),
                config.timeoutMs,
                "tmux"
            );
            const notFound = hasExactMarkerLine(result.output, "__OPERIT_TMUX_NOT_FOUND__");
            const success = result.exitCode === 0 && !result.timedOut && !notFound;
            const block = extractBlock(result.output, "__OPERIT_TMUX_WINDOWS_BEGIN__", "__OPERIT_TMUX_WINDOWS_END__");
            const windows = block
                .split(/\r?\n/)
                .map((line) => asText(line).trim())
                .filter((line) => !!line && /^\d+:.+/.test(line))
                .map((line) => {
                    const firstColon = line.indexOf(":");
                    const index = firstColon >= 0 ? line.slice(0, firstColon).trim() : "";
                    const name = firstColon >= 0 ? line.slice(firstColon + 1).trim() : line;
                    return {
                        index,
                        name,
                        key: index ? `${index}:${name}` : name,
                        label: index ? `#${index} ${name}` : name
                    };
                });

            return await persistToolResult("linux_ssh_tmux_list_windows_output", {
                success,
                tmuxSessionName,
                sessionExists: !notFound,
                windows,
                count: windows.length,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: block || result.output,
                error: notFound
                    ? `tmux session not found: ${tmuxSessionName}`
                    : (success ? "" : `tmux list windows failed, exitCode=${result.exitCode}`)
            });
        });
    }

    async function linux_ssh_tmux_input(params) {
        return await runTool(async () => {
            const requestedWindowName = asText(params?.window_name).trim();
            const inputText = params?.input !== undefined && params?.input !== null
                ? asText(params.input)
                : "";
            const controlKey = params?.control !== undefined && params?.control !== null
                ? normalizeTmuxControlKey(params.control)
                : "";

            if (!requestedWindowName) {
                throw new Error("window_name cannot be empty");
            }
            if (!inputText && !controlKey) {
                throw new Error("At least one of input/control is required");
            }

            const config = await resolveStoredSshConfig(params);
            const tmuxSessionName = DEFAULT_TMUX_SESSION_NAME;

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                return mergeToolResult({
                    success: false,
                    tmuxSessionName,
                    windowName: requestedWindowName,
                    input: inputText,
                    control: controlKey,
                    exitCode: tmuxReady.exitCode,
                    timedOut: tmuxReady.timedOut,
                    output: tmuxReady.output,
                    error: "tmux setup failed"
                }, tmuxReady);
            }

            const targetWindowReady = await ensureRemoteTmuxWindow(config, requestedWindowName);
            if (!targetWindowReady.success || !targetWindowReady.windowName) {
                return mergeToolResult({
                    success: false,
                    tmuxSessionName,
                    windowName: requestedWindowName,
                    input: inputText,
                    control: controlKey,
                    exitCode: targetWindowReady.exitCode,
                    timedOut: targetWindowReady.timedOut,
                    output: targetWindowReady.output,
                    error: "tmux window setup failed"
                }, targetWindowReady);
            }

            const windowName = targetWindowReady.windowName;
            const targetWindow = `${tmuxSessionName}:${windowName}`;
            const scriptLines: string[] = [];

            if (inputText) {
                scriptLines.push(`tmux send-keys -t ${shellQuote(targetWindow)} ${shellQuote(inputText)}`);
            }
            if (controlKey) {
                scriptLines.push(`tmux send-keys -t ${shellQuote(targetWindow)} ${shellQuote(controlKey)}`);
            }
            scriptLines.push("printf '__OPERIT_TMUX_INPUT_OK__\\n'");

            const result = await runRemoteCommandHidden(
                config,
                buildRemoteShellCommand(scriptLines.join("\n"), [], false),
                config.timeoutMs,
                "tmux"
            );
            const success = result.exitCode === 0 && !result.timedOut && result.output.includes("__OPERIT_TMUX_INPUT_OK__");

            return await persistToolResult("linux_ssh_tmux_input_output", {
                success,
                tmuxSessionName,
                windowName,
                tmuxTarget: targetWindow,
                input: inputText,
                control: controlKey,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                error: success ? "" : `tmux input failed, exitCode=${result.exitCode}`
            });
        });
    }

    async function linux_ssh_tmux_close(params) {
        return await runTool(async () => {
            const tmuxSessionName = DEFAULT_TMUX_SESSION_NAME;
            const windowName = asText(params?.window_name).trim();
            if (!windowName) {
                throw new Error("window_name cannot be empty");
            }

            const config = await resolveStoredSshConfig(params);
            const targetWindow = `${tmuxSessionName}:${windowName}`;

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                return mergeToolResult({
                    success: false,
                    tmuxSessionName,
                    windowName,
                    tmuxTarget: targetWindow,
                    sessionExists: false,
                    windowExists: false,
                    exitCode: tmuxReady.exitCode,
                    timedOut: tmuxReady.timedOut,
                    output: tmuxReady.output,
                    error: "tmux setup failed"
                }, tmuxReady);
            }

            const script = [
                `tmux has-session -t ${shellQuote(tmuxSessionName)} 2>/dev/null || { echo '__OPERIT_TMUX_NOT_FOUND__'; exit 4; }`,
                `tmux list-windows -t ${shellQuote(tmuxSessionName)} -F '#{window_name}' | grep -Fx -- ${shellQuote(windowName)} >/dev/null || { echo '__OPERIT_TMUX_WINDOW_NOT_FOUND__'; exit 5; }`,
                `tmux kill-window -t ${shellQuote(targetWindow)}`,
                "printf '__OPERIT_TMUX_CLOSE_OK__\\n'"
            ].join("\n");

            const result = await runRemoteCommandHidden(
                config,
                buildRemoteShellCommand(script, [], false),
                config.timeoutMs,
                "tmux"
            );
            const sessionExists = !hasExactMarkerLine(result.output, "__OPERIT_TMUX_NOT_FOUND__");
            const windowExists = !hasExactMarkerLine(result.output, "__OPERIT_TMUX_WINDOW_NOT_FOUND__");
            const success = result.exitCode === 0 && !result.timedOut && sessionExists && windowExists && result.output.includes("__OPERIT_TMUX_CLOSE_OK__");

            return await persistToolResult("linux_ssh_tmux_close_output", {
                success,
                tmuxSessionName,
                windowName,
                tmuxTarget: targetWindow,
                sessionExists,
                windowExists,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                error: !sessionExists
                    ? `tmux session not found: ${tmuxSessionName}`
                    : (!windowExists
                        ? `tmux window not found: ${windowName}`
                        : (success ? "" : `tmux close failed, exitCode=${result.exitCode}`))
            });
        });
    }

    async function linux_ssh_terminal_input(params) {
        return await runTool(async () => {
            const input = params?.input;
            const control = params?.control;
            if (input === undefined && control === undefined) {
                throw new Error("At least one of input/control is required");
            }

            const session = await createLocalTerminalSession();
            const result = await Tools.System.terminal.input(session.sessionId, {
                input: input === undefined ? undefined : asText(input),
                control: control === undefined ? undefined : asText(control)
            });

            return createSuccessResult({
                sessionId: session.sessionId,
                result: extractStringResult(result)
            });
        });
    }

    async function linux_ssh_terminal_screen(params) {
        return await runTool(async () => {
            const session = await createLocalTerminalSession();
            const result = await Tools.System.terminal.screen(session.sessionId);
            return createSuccessResult({
                sessionId: result.sessionId || session.sessionId,
                rows: Number(result.rows || 0),
                cols: Number(result.cols || 0),
                content: asText(result.content)
            });
        });
    }

    async function linux_ssh_ls(params) {
        return await runTool(async () => {
            const config = await resolveStoredSshConfig(params);
            const path = stripWrappingQuotes(params?.path);
            const displayPath = firstNonBlank(path, "~");
            const script = [
                "raw_path=\"$1\"",
                ...buildRemotePathResolveLines("raw_path", "resolved_path", { fallbackToHome: true }),
                "ls -la -- \"$resolved_path\""
            ].join("\n");
            const result = await runRemoteCommandHidden(
                config,
                buildRemoteShellCommand(script, [path], false),
                config.timeoutMs,
                "fs"
            );
            const success = result.exitCode === 0 && !result.timedOut;

            return await persistToolResult("linux_ssh_ls_output", {
                success,
                path: displayPath,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                error: success ? "" : `ls failed, exitCode=${result.exitCode}`
            });
        });
    }

    async function linux_ssh_read(params) {
        return await runTool(async () => {
            const path = stripWrappingQuotes(params?.path);
            if (!path) {
                throw new Error("path cannot be empty");
            }

            const config = await resolveStoredSshConfig(params);
            const lineStart = params?.line_start;
            const lineEnd = params?.line_end;
            const useSudo = params?.sudo === true;
            if (lineStart !== undefined && lineEnd !== undefined && lineEnd < lineStart) {
                throw new Error("line_end must be greater than or equal to line_start");
            }

            const readResult = await readRemoteFileContent(config, path, lineStart, lineEnd, useSudo);
            return createSuccessResult({
                path,
                lineStart: lineStart === undefined ? null : lineStart,
                lineEnd: lineEnd === undefined ? null : lineEnd,
                content: readResult.content
            });
        });
    }

    async function linux_ssh_write(params) {
        return await runTool(async () => {
            const path = stripWrappingQuotes(params?.path);
            if (!path) {
                throw new Error("path cannot be empty");
            }
            if (params?.content === undefined || params?.content === null) {
                throw new Error("content cannot be null");
            }

            const config = await resolveStoredSshConfig(params);
            const content = asText(params.content);
            const append = params?.append === true;
            const useSudo = params?.sudo === true;
            await writeRemoteFileContent(config, path, content, append, useSudo);

            return createSuccessResult({
                path,
                append,
                contentLength: content.length
            });
        });
    }

    async function linux_ssh_edit(params) {
        return await runTool(async () => {
            const path = stripWrappingQuotes(params?.path);
            if (!path) {
                throw new Error("path cannot be empty");
            }
            const oldText = asText(params?.old_text);
            if (!oldText) {
                throw new Error("old_text cannot be empty");
            }
            if (params?.new_text === undefined || params?.new_text === null) {
                throw new Error("new_text cannot be null");
            }

            const config = await resolveStoredSshConfig(params);
            const newText = asText(params.new_text);
            const expectedReplacements = params?.expected_replacements ?? 1;
            const useSudo = params?.sudo === true;

            const readResult = await readRemoteFileContent(config, path, undefined, undefined, useSudo);
            const source = readResult.content;
            const parts = source.split(oldText);
            const replacements = parts.length - 1;

            if (replacements !== expectedReplacements) {
                throw new Error(
                    `Replacement count mismatch: expected ${expectedReplacements}, actual ${replacements}`
                );
            }

            const updated = parts.join(newText);
            await writeRemoteFileContent(config, path, updated, false, useSudo);

            return createSuccessResult({
                path,
                replacements,
                expectedReplacements,
                beforeLength: source.length,
                afterLength: updated.length
            });
        });
    }

    return {
        linux_ssh_configure,
        linux_ssh_test_connection,
        linux_ssh_exec,
        linux_ssh_ensure_tmux,
        linux_ssh_tmux_run,
        linux_ssh_tmux_capture,
        linux_ssh_tmux_list_windows,
        linux_ssh_tmux_input,
        linux_ssh_tmux_close,
        linux_ssh_terminal_input,
        linux_ssh_terminal_screen,
        linux_ssh_ls,
        linux_ssh_read,
        linux_ssh_write,
        linux_ssh_edit
    };
})();

exports.linux_ssh_configure = linuxSshTools.linux_ssh_configure;
exports.linux_ssh_test_connection = linuxSshTools.linux_ssh_test_connection;
exports.linux_ssh_exec = linuxSshTools.linux_ssh_exec;
exports.linux_ssh_ensure_tmux = linuxSshTools.linux_ssh_ensure_tmux;
exports.linux_ssh_tmux_run = linuxSshTools.linux_ssh_tmux_run;
exports.linux_ssh_tmux_capture = linuxSshTools.linux_ssh_tmux_capture;
exports.linux_ssh_tmux_list_windows = linuxSshTools.linux_ssh_tmux_list_windows;
exports.linux_ssh_tmux_input = linuxSshTools.linux_ssh_tmux_input;
exports.linux_ssh_tmux_close = linuxSshTools.linux_ssh_tmux_close;
exports.linux_ssh_terminal_input = linuxSshTools.linux_ssh_terminal_input;
exports.linux_ssh_terminal_screen = linuxSshTools.linux_ssh_terminal_screen;
exports.linux_ssh_ls = linuxSshTools.linux_ssh_ls;
exports.linux_ssh_read = linuxSshTools.linux_ssh_read;
exports.linux_ssh_write = linuxSshTools.linux_ssh_write;
exports.linux_ssh_edit = linuxSshTools.linux_ssh_edit;
