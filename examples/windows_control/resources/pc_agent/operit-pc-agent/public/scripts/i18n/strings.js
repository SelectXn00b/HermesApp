const STORAGE_KEY = "operit.pc-agent.locale";

const RESOURCES = {
  en: {
    language: {
      english: "English",
      chinese: "中文"
    },
    ui: {
      title: "Operit PC Agent Console",
      subtitle: "Windows bridge for mobile integration, secure HTTP relay, and command validation"
    },
    nav: {
      wizard: "Setup Wizard",
      commands: "Commands",
      manage: "Manage",
      settings: "Settings"
    },
    action: {
      refreshAll: "Refresh All",
      refreshHealth: "Refresh Health",
      runPreset: "Run Preset",
      runRaw: "Run Raw",
      refreshSessions: "Refresh Sessions",
      createSession: "New Session",
      creating: "Creating...",
      closeSession: "Close",
      closing: "Closing...",
      sendInput: "Send",
      sendCtrlC: "Send Ctrl+C",
      sending: "Sending...",
      saveConfig: "Save Config",
      runOpenSshSetup: "Run Verification",
      saveAndNext: "Save and Next",
      generateMobileSnippet: "Generate Mobile Snippet",
      copyJson: "Copy JSON",
      copyEnv: "Copy ENV",
      oneClickFill: "One-click Fill",
      copyPayload: "Copy",
      toggleAdvancedShow: "Advanced",
      toggleAdvancedHide: "Hide Advanced",
      toCommands: "Go to Commands",
      toSettings: "Go to Settings",
      applyRecommendedBind: "Use Recommended IPv4 and Restart",
      applyingRecommendedBind: "Applying and Restarting...",
      dismiss: "Dismiss",
      working: "Working...",
      running: "Running...",
      refreshing: "Refreshing..."
    },
    status: {
      host: "Host",
      lanIp: "LAN IPv4",
      pid: "PID",
      uptime: "Uptime",
      ssh: "Mode",
      version: "Version",
      unknown: "unknown",
      ready: "Ready",
      loading: "Loading config and health data...",
      healthRefreshed: "Health refreshed",
      healthRefreshFailed: "Health refresh failed: {error}",
      dataRefreshed: "Data refreshed",
      refreshFailed: "Refresh failed: {error}",
      initializationFailed: "Initialization failed: {error}"
    },
    startup: {
      title: "Startup Recovery",
      bindUnavailableMessage:
        "Configured bind address {configuredBind} is unavailable. Service is temporarily running on {runtimeBind}. Recommended IPv4: {recommendedBind}.",
      bindUnavailableMeta: "Detected IPv4 candidates: {ipv4Candidates}"
    },
    card: {
      healthTitle: "Health Status",
      healthSubtitle: "Agent runtime, bind target and HTTP relay status",
      commandTitle: "Command Runner",
      commandSubtitle: "Run preset commands or raw commands",
      manageTitle: "Session Management",
      manageSubtitle: "View all remote process sessions and close running ones",
      configTitle: "Configuration",
      configSubtitle: "Manage bind address, token, timeout and allowed presets",
      openSshTitle: "Verification",
      openSshSubtitle: "Use command presets to verify HTTP relay"
    },
    panel: {
      presetMode: "Preset Mode",
      rawMode: "Raw Mode",
      allowedPresets: "Allowed Presets"
    },
    wizard: {
      title: "Mobile Integration Wizard",
      subtitle: "Use this guided flow to configure PC-side bridge and generate mobile-side env values.",
      stepNav1: "1. Base Config",
      stepNav2: "2. Mobile Fill",
      step1Title: "Step 1: Make PC reachable from phone",
      step1Desc: "Use LAN IPv4 for bind address (for example 192.168.x.x). Keep port default unless needed. API token is required.",
      step1Hint: "Do not use 127.0.0.1 for phone access. Restart service after changing bind address or port.",
      step2Title: "Step 2: Mobile Paste Config",
      step2Desc: "Use one-click fill, then copy the config text to mobile app.",
      oneClickTitle: "Config Text",
      oneClickDesc: "Auto-fills available values and generates text for mobile paste.",
      advancedTitle: "Advanced override",
      mobileJsonTitle: "Configuration Text",
      mobileEnvTitle: "Configuration Text"
    },
    field: {
      token: "Token",
      preset: "Preset",
      shell: "Shell",
      command: "Command",
      pid: "PID",
      createdAt: "Created At",
      updatedAt: "Updated At",
      exitCode: "Exit Code",
      commandPreview: "Command",
      includeExitedSessions: "Include exited sessions",
      startShell: "Shell",
      startCommand: "Startup Command",
      currentSession: "Current Session",
      status: "Status",
      bindAddress: "Bind Address",
      port: "Port",
      maxCommandTimeoutMs: "Max Command Timeout (ms)",
      apiToken: "API Token",
      windowsAgentBaseUrl: "WINDOWS_AGENT_BASE_URL",
      windowsAgentToken: "WINDOWS_AGENT_TOKEN",
      windowsAgentDefaultShell: "WINDOWS_AGENT_DEFAULT_SHELL",
      windowsAgentTimeoutMs: "WINDOWS_AGENT_TIMEOUT_MS"
    },
    placeholder: {
      tokenRequiredWhenApiTokenEnabled: "Required",
      rawCommandExample: "Example: Get-Process | Select-Object -First 5 Name,Id",
      apiTokenEmptyDisable: "Leave empty to auto-generate a new token",
      noPresetsAvailable: "(no presets available)",
      loadingPresets: "(loading...)",
      mobileHost: "LAN IP or reachable host, not 127.0.0.1 for phone",
      mobileBaseUrl: "Example: http://192.168.1.8:58321",
      passwordOrPrivateKey: "Provide password or private key",
      startCommand: "Optional. Leave empty to open plain shell",
      terminalInput: "Type command input and submit"
    },
    preset: {
      disabledSuffix: " (disabled)"
    },
    message: {
      configSaved: "Configuration saved",
      configSavedRestartRequired: "Configuration saved. Restart service is required because bind address or port changed.",
      configSaveFailed: "Failed to save config: {error}",
      openSshFinished: "Verification finished",
      openSshWarn: "Verification returned warnings",
      openSshFailed: "Verification failed: {error}",
      openSshRequiresAdmin: "",
      openSshElevationCancelled: "",
      openSshBlockedByPolicy: "",
      presetFinished: "Preset command finished",
      presetWarn: "Preset command returned warnings",
      presetFailed: "Preset command failed: {error}",
      rawFinished: "Raw command finished",
      rawWarn: "Raw command returned warnings",
      rawFailed: "Raw command failed: {error}",
      sessionsRefreshed: "Session list refreshed ({count})",
      sessionsRefreshFailed: "Session list refresh failed: {error}",
      sessionCreated: "Session created: {sessionId}",
      sessionCreateFailed: "Session create failed: {error}",
      sessionClosed: "Session closed: {sessionId}",
      sessionCloseWarn: "Session close finished with warning: {sessionId}",
      sessionCloseFailed: "Session close failed: {error}",
      inputSent: "Input sent",
      inputSendFailed: "Input send failed: {error}",
      ctrlCSent: "Ctrl+C sent",
      terminalInitFailed: "Terminal init failed: {error}",
      terminalInteractive: "Interactive mode: type directly in terminal window",
      terminalReadonlyExited: "Session is not running, terminal is read-only",
      selectSessionFirst: "Select a session first",
      selectSessionHint: "Select a session from the left list to start interaction",
      terminalNoOutput: "(no output yet)",
      outputTruncated: "Earlier output was truncated by server buffer",
      noSessions: "No sessions",
      wizardStep1Done: "Step 1 completed",
      mobileSnippetGenerated: "Mobile snippet generated",
      oneClickFilled: "Auto fill completed",
      copyPayloadSuccess: "Copied",
      copyJsonSuccess: "Copied",
      copyEnvSuccess: "Copied",
      copyFailed: "Copy failed: {error}",
      startupApplyRestarting: "Applied bind address {bindAddress}. Restarting service...",
      startupApplyFailed: "Failed to apply recommended IPv4: {error}"
    },
    error: {
      unknown: "Unknown error"
    }
  },
  zh: {
    language: {
      english: "English",
      chinese: "中文"
    },
    ui: {
      title: "Operit PC Agent 控制台",
      subtitle: "面向移动端对接的 Windows 桥接服务：安全 HTTP 中转与命令验证"
    },
    nav: {
      wizard: "配置向导",
      commands: "命令执行",
      manage: "管理",
      settings: "高级设置"
    },
    action: {
      refreshAll: "刷新全部",
      refreshHealth: "刷新健康状态",
      runPreset: "运行预设",
      runRaw: "运行 Raw",
      refreshSessions: "刷新会话",
      createSession: "新建会话",
      creating: "创建中...",
      closeSession: "关闭",
      closing: "关闭中...",
      sendInput: "发送",
      sendCtrlC: "发送 Ctrl+C",
      sending: "发送中...",
      saveConfig: "保存配置",
      runOpenSshSetup: "执行连通验证",
      saveAndNext: "保存并下一步",
      generateMobileSnippet: "生成移动端片段",
      copyJson: "复制",
      copyEnv: "复制",
      oneClickFill: "一键填写",
      copyPayload: "复制",
      toggleAdvancedShow: "高级配置",
      toggleAdvancedHide: "收起高级",
      toCommands: "前往命令页",
      toSettings: "前往设置页",
      applyRecommendedBind: "使用推荐 IPv4 并重启",
      applyingRecommendedBind: "应用并重启中...",
      dismiss: "暂不处理",
      working: "处理中...",
      running: "执行中...",
      refreshing: "刷新中..."
    },
    status: {
      host: "主机",
      lanIp: "局域网IPv4",
      pid: "PID",
      uptime: "运行时长",
      ssh: "模式",
      version: "版本",
      unknown: "未知",
      ready: "就绪",
      loading: "正在加载配置与健康状态...",
      healthRefreshed: "健康状态已刷新",
      healthRefreshFailed: "健康状态刷新失败: {error}",
      dataRefreshed: "数据刷新完成",
      refreshFailed: "刷新失败: {error}",
      initializationFailed: "初始化失败: {error}"
    },
    startup: {
      title: "启动恢复",
      bindUnavailableMessage:
        "配置的绑定地址 {configuredBind} 当前不可用。服务已临时运行在 {runtimeBind}。推荐 IPv4: {recommendedBind}。",
      bindUnavailableMeta: "检测到的 IPv4 候选: {ipv4Candidates}"
    },
    card: {
      healthTitle: "健康状态",
      healthSubtitle: "查看 Agent 运行状态、绑定信息与 HTTP 中转状态",
      commandTitle: "命令执行",
      commandSubtitle: "支持预设命令与 Raw 命令",
      manageTitle: "会话管理",
      manageSubtitle: "查看所有远程进程会话，并可关闭运行中的会话",
      configTitle: "配置中心",
      configSubtitle: "管理监听地址、令牌、超时与允许预设",
      openSshTitle: "连通验证",
      openSshSubtitle: "使用预设命令验证 HTTP 中转"
    },
    panel: {
      presetMode: "预设模式",
      rawMode: "Raw 模式",
      allowedPresets: "允许的预设"
    },
    wizard: {
      title: "移动端对接配置向导",
      subtitle: "按步骤完成电脑端桥接配置，并生成移动端一键配置可直接填写的环境变量。",
      stepNav1: "1. 基础配置",
      stepNav2: "2. 移动端填写",
      step1Title: "步骤 1：让手机能访问这台电脑",
      step1Desc: "绑定地址用局域网 IPv4（如 192.168.x.x）。端口默认即可。API 令牌必填。",
      step1Hint: "给手机访问时不要用 127.0.0.1。改了绑定地址或端口后要重启服务。",
      step2Title: "步骤 2：移动端粘贴配置",
      step2Desc: "先一键填写，再复制配置文本到移动端粘贴。",
      oneClickTitle: "配置文本",
      oneClickDesc: "自动填充可获取项，并生成给移动端粘贴的配置文本。",
      advancedTitle: "高级覆盖",
      mobileJsonTitle: "配置文本",
      mobileEnvTitle: "配置文本"
    },
    field: {
      token: "令牌",
      preset: "预设",
      shell: "Shell",
      command: "命令",
      pid: "PID",
      createdAt: "创建时间",
      updatedAt: "更新时间",
      exitCode: "退出码",
      commandPreview: "命令预览",
      includeExitedSessions: "包含已退出会话",
      startShell: "Shell",
      startCommand: "启动命令",
      currentSession: "当前会话",
      status: "状态",
      bindAddress: "绑定地址",
      port: "端口",
      maxCommandTimeoutMs: "最大命令超时 (ms)",
      apiToken: "API 令牌",
      windowsAgentBaseUrl: "WINDOWS_AGENT_BASE_URL",
      windowsAgentToken: "WINDOWS_AGENT_TOKEN",
      windowsAgentDefaultShell: "WINDOWS_AGENT_DEFAULT_SHELL",
      windowsAgentTimeoutMs: "WINDOWS_AGENT_TIMEOUT_MS"
    },
    placeholder: {
      tokenRequiredWhenApiTokenEnabled: "必填",
      rawCommandExample: "示例: Get-Process | Select-Object -First 5 Name,Id",
      apiTokenEmptyDisable: "留空会自动生成新令牌",
      noPresetsAvailable: "（暂无可用预设）",
      loadingPresets: "（加载中...）",
      mobileHost: "填写局域网 IP 或可达主机，不要给手机端写 127.0.0.1",
      mobileBaseUrl: "示例: http://192.168.1.8:58321",
      passwordOrPrivateKey: "填写密码或私钥其中之一",
      startCommand: "可选，不填则仅启动空 shell",
      terminalInput: "输入命令并发送"
    },
    preset: {
      disabledSuffix: "（已禁用）"
    },
    message: {
      configSaved: "配置保存成功",
      configSavedRestartRequired: "配置已保存。由于绑定地址或端口变化，需要重启服务。",
      configSaveFailed: "配置保存失败: {error}",
      openSshFinished: "验证完成",
      openSshWarn: "验证返回告警",
      openSshFailed: "验证失败: {error}",
      openSshRequiresAdmin: "",
      openSshElevationCancelled: "",
      openSshBlockedByPolicy: "",
      presetFinished: "预设命令执行完成",
      presetWarn: "预设命令返回告警",
      presetFailed: "预设命令执行失败: {error}",
      rawFinished: "Raw 命令执行完成",
      rawWarn: "Raw 命令返回告警",
      rawFailed: "Raw 命令执行失败: {error}",
      sessionsRefreshed: "会话列表已刷新（{count}）",
      sessionsRefreshFailed: "会话列表刷新失败: {error}",
      sessionCreated: "会话已创建: {sessionId}",
      sessionCreateFailed: "会话创建失败: {error}",
      sessionClosed: "会话已关闭: {sessionId}",
      sessionCloseWarn: "会话关闭返回告警: {sessionId}",
      sessionCloseFailed: "会话关闭失败: {error}",
      inputSent: "输入已发送",
      inputSendFailed: "输入发送失败: {error}",
      ctrlCSent: "Ctrl+C 已发送",
      terminalInitFailed: "终端初始化失败: {error}",
      terminalInteractive: "交互模式：直接在终端窗口输入即可",
      terminalReadonlyExited: "会话未运行，当前终端为只读",
      selectSessionFirst: "请先选择会话",
      selectSessionHint: "请先在左侧选择会话，再进行交互",
      terminalNoOutput: "（暂无输出）",
      outputTruncated: "更早的输出已被服务端缓冲区截断",
      noSessions: "暂无会话",
      wizardStep1Done: "步骤 1 已完成",
      mobileSnippetGenerated: "移动端片段已生成",
      oneClickFilled: "一键填写完成",
      copyPayloadSuccess: "已复制",
      copyJsonSuccess: "已复制",
      copyEnvSuccess: "已复制",
      copyFailed: "复制失败: {error}",
      startupApplyRestarting: "已应用绑定地址 {bindAddress}，正在重启服务...",
      startupApplyFailed: "应用推荐 IPv4 失败: {error}"
    },
    error: {
      unknown: "未知错误"
    }
  }
};

function normalizeLocale(raw) {
  const value = String(raw || "").trim().toLowerCase();
  if (value.startsWith("zh")) {
    return "zh";
  }
  return "en";
}

function getByPath(target, path) {
  return String(path)
    .split(".")
    .reduce((current, key) => (current && Object.prototype.hasOwnProperty.call(current, key) ? current[key] : undefined), target);
}

function interpolate(template, values) {
  if (!values) {
    return template;
  }

  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (match, key) => {
    if (Object.prototype.hasOwnProperty.call(values, key)) {
      return String(values[key]);
    }
    return match;
  });
}

function readStoredLocale() {
  try {
    return window.localStorage.getItem(STORAGE_KEY) || "";
  } catch {
    return "";
  }
}

function writeStoredLocale(locale) {
  try {
    window.localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // ignore storage errors
  }
}

export function createI18n() {
  const browserLocale = typeof navigator !== "undefined" ? navigator.language : "en";
  let locale = normalizeLocale(readStoredLocale() || browserLocale);

  function t(key, values) {
    const primary = getByPath(RESOURCES[locale], key);
    const fallback = getByPath(RESOURCES.en, key);
    const text = primary !== undefined ? primary : fallback;
    if (typeof text === "string") {
      return interpolate(text, values);
    }
    return key;
  }

  function setLocale(nextLocale) {
    locale = normalizeLocale(nextLocale);
    writeStoredLocale(locale);
    return locale;
  }

  function getLocale() {
    return locale;
  }

  return {
    t,
    setLocale,
    getLocale
  };
}
