const http = require("http");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const {
  PROJECT_ROOT,
  PUBLIC_DIR,
  DATA_DIR,
  SCRIPTS_DIR,
  LOGS_DIR,
  CONFIG_PATH,
  RUNTIME_PATH,
  STARTUP_STATE_PATH,
  RUNTIME_LOG_PATH
} = require("./config/paths");
const {
  DEFAULT_CONFIG,
  PRESET_COMMANDS,
  STATIC_CONTENT_TYPES
} = require("./config/constants");
const { AGENT_VERSION } = require("./config/version");
const { createRuntimeLogger } = require("./lib/logger");
const { createStaticFileServer, sendNotFound } = require("./lib/http-utils");
const { createConfigStore } = require("./stores/config-store");
const { createRuntimeStore } = require("./stores/runtime-store");
const { createStartupStateStore } = require("./stores/startup-state-store");
const { createProcessService } = require("./services/process-service");
const { createFileService } = require("./services/file-service");
const { createApiHandler } = require("./handlers/api-handler");

const logger = createRuntimeLogger({
  logsDir: LOGS_DIR,
  runtimeLogPath: RUNTIME_LOG_PATH
});

const configStore = createConfigStore({
  dataDir: DATA_DIR,
  configPath: CONFIG_PATH,
  defaultConfig: DEFAULT_CONFIG,
  presetCommands: PRESET_COMMANDS
});

const runtimeStore = createRuntimeStore({
  dataDir: DATA_DIR,
  runtimePath: RUNTIME_PATH
});

const startupStateStore = createStartupStateStore({
  dataDir: DATA_DIR,
  startupStatePath: STARTUP_STATE_PATH
});

const processService = createProcessService({
  projectRoot: PROJECT_ROOT,
  logger
});

const fileService = createFileService({
  projectRoot: PROJECT_ROOT
});

const staticFileServer = createStaticFileServer({
  publicDir: PUBLIC_DIR,
  staticContentTypes: STATIC_CONTENT_TYPES
});

const state = {
  config: configStore.loadConfig()
};

function resolveRuntimeBindAddress(config) {
  const override = String(process.env.OPERIT_BIND_ADDRESS_OVERRIDE || "").trim();
  if (override) {
    return {
      runtimeBindAddress: override,
      bindOverrideActive: true
    };
  }

  return {
    runtimeBindAddress: config.bindAddress,
    bindOverrideActive: false
  };
}

const runtimeBinding = resolveRuntimeBindAddress(state.config);
let restartScheduled = false;

logger.info("config.loaded", {
  agentVersion: AGENT_VERSION,
  bindAddress: state.config.bindAddress,
  runtimeBindAddress: runtimeBinding.runtimeBindAddress,
  bindOverrideActive: runtimeBinding.bindOverrideActive,
  port: state.config.port,
  maxCommandMs: state.config.maxCommandMs,
  allowedPresets: state.config.allowedPresets
});

function scheduleRestart(reason) {
  if (restartScheduled) {
    logger.warn("server.restart.duplicate_request", { reason: reason || "unknown" });
    return false;
  }

  restartScheduled = true;
  logger.info("server.restart.requested", { reason: reason || "unknown" });

  setTimeout(() => {
    try {
      const launcherScript = path.join(SCRIPTS_DIR, "launch_agent.ps1");
      const psExe = process.env.SystemRoot
        ? path.join(process.env.SystemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
        : "powershell.exe";

      const child = spawn(psExe, ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", launcherScript], {
        cwd: PROJECT_ROOT,
        detached: true,
        stdio: "ignore",
        windowsHide: true
      });

      child.unref();
      logger.info("server.restart.launcher_spawned", {
        launcherScript,
        pid: child.pid || null
      });
    } catch (error) {
      logger.error("server.restart.spawn_failed", {
        reason: reason || "unknown",
        error: error && error.message ? error.message : String(error)
      });
      restartScheduled = false;
      return;
    }

    setTimeout(() => shutdown("RESTART"), 120);
  }, 120);

  return true;
}

const apiHandler = createApiHandler({
  state,
  configStore,
  startupStateStore,
  restartAgent: scheduleRestart,
  processService,
  fileService,
  logger,
  presetCommands: PRESET_COMMANDS,
  runtimeInfo: {
    pid: () => process.pid,
    host: () => os.hostname(),
    uptimeSec: () => Math.floor(process.uptime()),
    runtimeBindAddress: () => runtimeBinding.runtimeBindAddress,
    bindOverrideActive: () => runtimeBinding.bindOverrideActive
  },
  versionInfo: {
    agentVersion: AGENT_VERSION
  }
});

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || "127.0.0.1"}`);
  logger.info("http.request", { method: req.method, path: url.pathname });

  if (req.method === "GET" && !url.pathname.startsWith("/api/")) {
    if (staticFileServer.tryServePublicFile(res, url.pathname)) {
      return;
    }
  }

  if (await apiHandler.handleApiRequest(req, res, url)) {
    return;
  }

  sendNotFound(res);
  logger.warn("http.404", { method: req.method, path: url.pathname });
});

server.listen(state.config.port, runtimeBinding.runtimeBindAddress, () => {
  runtimeStore.writeRuntimeFile({
    port: state.config.port,
    pid: process.pid,
    host: os.hostname()
  });

  logger.info("server.listening", {
    url: `http://${runtimeBinding.runtimeBindAddress}:${state.config.port}`,
    configBindAddress: state.config.bindAddress,
    runtimeBindAddress: runtimeBinding.runtimeBindAddress,
    bindOverrideActive: runtimeBinding.bindOverrideActive,
    pid: process.pid,
    host: os.hostname(),
    agentVersion: AGENT_VERSION
  });
});

function shutdown(signal) {
  logger.info("server.shutdown", { signal: signal || "unknown" });

  try {
    processService.terminateAllSessions();
  } catch (error) {
    logger.error("server.shutdown.terminateAllSessions.error", {
      error: error && error.message ? error.message : String(error)
    });
  }

  runtimeStore.removeRuntimeFile();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 2000);
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("uncaughtException", (error) => {
  logger.error("process.uncaughtException", {
    error: error.message,
    stack: error.stack || ""
  });
});
process.on("unhandledRejection", (reason) => {
  logger.error("process.unhandledRejection", { reason: String(reason) });
});
process.on("exit", () => {
  logger.info("process.exit", {});
  runtimeStore.removeRuntimeFile();
});
