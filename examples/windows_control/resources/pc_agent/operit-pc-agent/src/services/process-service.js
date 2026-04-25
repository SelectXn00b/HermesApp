const { spawn } = require("child_process");
const os = require("os");

const MAX_SESSION_COUNT = 32;
const MAX_SESSION_BUFFER_CHARS = 2 * 1024 * 1024;
const DEFAULT_SESSION_READ_CHARS = 16 * 1024;
const MAX_SESSION_READ_CHARS = 200 * 1024;
const DEFAULT_SESSION_COLS = 120;
const DEFAULT_SESSION_ROWS = 30;
const MAX_SESSION_COLS = 400;
const MAX_SESSION_ROWS = 200;
const MAX_SESSION_RUNTIME_MS = 24 * 60 * 60 * 1000;
const EXITED_SESSION_TTL_MS = 60 * 60 * 1000;

function createProcessService({ projectRoot, logger }) {
  const sessions = new Map();
  let cachedNodePty = null;
  let cachedXtermHeadless = null;

  function getNodePty() {
    if (cachedNodePty) {
      return cachedNodePty;
    }

    try {
      cachedNodePty = require("node-pty");
      return cachedNodePty;
    } catch (error) {
      const detail = error && error.message ? ` (${error.message})` : "";
      throw new Error(`PTY backend unavailable: dependency \"node-pty\" is missing${detail}`);
    }
  }

  function getXtermHeadless() {
    if (cachedXtermHeadless) {
      return cachedXtermHeadless;
    }

    try {
      cachedXtermHeadless = require("@xterm/headless");
      return cachedXtermHeadless;
    } catch (error) {
      const detail = error && error.message ? ` (${error.message})` : "";
      throw new Error(`Screen renderer unavailable: dependency "@xterm/headless" is missing${detail}`);
    }
  }

  function withPowerShellUtf8(command) {
    const prelude = [
      "$utf8NoBom = [System.Text.UTF8Encoding]::new($false)",
      "[Console]::InputEncoding = $utf8NoBom",
      "[Console]::OutputEncoding = $utf8NoBom",
      "$OutputEncoding = $utf8NoBom"
    ].join("; ");

    return `${prelude}; ${command}`;
  }

  function normalizeShell(shellInput) {
    const mode = String(shellInput || "powershell").trim().toLowerCase();
    if (mode === "cmd" || mode === "pwsh") {
      return mode;
    }
    return "powershell";
  }

  function parseNonNegativeInt(value, fallback, fieldName) {
    if (value === undefined || value === null || value === "") {
      return fallback;
    }

    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed < 0) {
      throw new Error(`${fieldName} must be a non-negative integer`);
    }

    return Math.floor(parsed);
  }

  function parsePositiveInt(value, fallback, maxValue, fieldName) {
    if (value === undefined || value === null || value === "") {
      return fallback;
    }

    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      throw new Error(`${fieldName} must be a positive integer`);
    }

    const safe = Math.floor(parsed);
    if (safe > maxValue) {
      throw new Error(`${fieldName} too large (max ${maxValue})`);
    }

    return safe;
  }

  function parseBoolean(value, fallback) {
    if (value === undefined || value === null || value === "") {
      return fallback;
    }

    const text = String(value).trim().toLowerCase();
    if (text === "true" || text === "1" || text === "yes" || text === "on") {
      return true;
    }
    if (text === "false" || text === "0" || text === "no" || text === "off") {
      return false;
    }

    throw new Error("Invalid boolean value");
  }

  function createOutputStore() {
    return {
      baseOffset: 0,
      content: ""
    };
  }

  function appendOutput(store, chunk) {
    const text = typeof chunk === "string" ? chunk : String(chunk || "");
    if (!text) {
      return;
    }

    store.content += text;

    const overflow = store.content.length - MAX_SESSION_BUFFER_CHARS;
    if (overflow > 0) {
      store.content = store.content.slice(overflow);
      store.baseOffset += overflow;
    }
  }

  function readOutput(store, requestedOffset, maxChars) {
    const availableFrom = store.baseOffset;
    const latestOffset = store.baseOffset + store.content.length;
    const startOffset = Math.max(requestedOffset, availableFrom);
    const startIndex = startOffset - store.baseOffset;
    const chunk = store.content.slice(startIndex, startIndex + maxChars);

    return {
      chunk,
      fromOffset: startOffset,
      nextOffset: startOffset + chunk.length,
      latestOffset,
      truncated: requestedOffset < availableFrom,
      availableFrom
    };
  }

  function buildOutputMeta(store, requestedOffset) {
    const availableFrom = store.baseOffset;
    const latestOffset = store.baseOffset + store.content.length;
    const fromOffset = Math.max(requestedOffset, availableFrom);

    return {
      fromOffset,
      latestOffset,
      truncated: requestedOffset < availableFrom,
      availableFrom
    };
  }

  function normalizeReadViewMode(value) {
    const mode = String(value || "screen").trim().toLowerCase();
    return mode === "stream" ? "stream" : "screen";
  }

  function normalizeScreenDimension(value, fallback, maxValue) {
    const parsed = Number(value);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      return fallback;
    }

    const safe = Math.floor(parsed);
    return Math.max(1, Math.min(safe, maxValue));
  }

  function createScreenState(colsInput, rowsInput) {
    const cols = normalizeScreenDimension(colsInput, DEFAULT_SESSION_COLS, MAX_SESSION_COLS);
    const rows = normalizeScreenDimension(rowsInput, DEFAULT_SESSION_ROWS, MAX_SESSION_ROWS);
    const xtermHeadless = getXtermHeadless();
    const terminal = new xtermHeadless.Terminal({
      cols,
      rows,
      scrollback: 5000,
      allowProposedApi: true
    });

    return {
      terminal,
      cols,
      rows,
      writeChain: Promise.resolve()
    };
  }

  function enqueueScreenMutation(state, mutator) {
    if (!state || !state.terminal) {
      return;
    }

    state.writeChain = Promise.resolve(state.writeChain)
      .then(() =>
        new Promise((resolve) => {
          try {
            mutator(resolve);
          } catch {
            resolve();
          }
        })
      )
      .catch(() => undefined);
  }

  function applyChunkToScreen(state, chunkInput) {
    if (!state || !state.terminal) {
      return;
    }

    const text = typeof chunkInput === "string" ? chunkInput : String(chunkInput || "");
    if (!text) {
      return;
    }

    enqueueScreenMutation(state, (done) => {
      state.terminal.write(text, done);
    });
  }

  async function flushScreenState(state) {
    if (!state) {
      return;
    }

    try {
      await Promise.resolve(state.writeChain);
    } catch {
      // ignore
    }
  }

  function getViewportStart(buffer) {
    const viewportY = Number(buffer && buffer.viewportY);
    if (Number.isFinite(viewportY) && viewportY >= 0) {
      return Math.floor(viewportY);
    }

    const baseY = Number(buffer && buffer.baseY);
    if (Number.isFinite(baseY) && baseY >= 0) {
      return Math.floor(baseY);
    }

    return 0;
  }

  function isAlternateBuffer(buffer) {
    const bufferType = buffer ? buffer.type : undefined;
    return bufferType === 1 || bufferType === "alternate" || bufferType === "alt";
  }

  function getScreenMeta(state) {
    if (!state || !state.terminal) {
      return {
        cols: DEFAULT_SESSION_COLS,
        rows: DEFAULT_SESSION_ROWS,
        cursorRow: 0,
        cursorCol: 0,
        altScreen: false,
        viewportStart: 0
      };
    }

    const terminal = state.terminal;
    const buffer = terminal.buffer && terminal.buffer.active ? terminal.buffer.active : null;
    const cursorRowRaw = Number(buffer && buffer.cursorY);
    const cursorColRaw = Number(buffer && buffer.cursorX);

    const cols = Number.isFinite(Number(terminal.cols)) ? Number(terminal.cols) : state.cols;
    const rows = Number.isFinite(Number(terminal.rows)) ? Number(terminal.rows) : state.rows;

    return {
      cols,
      rows,
      cursorRow: Number.isFinite(cursorRowRaw) ? cursorRowRaw : 0,
      cursorCol: Number.isFinite(cursorColRaw) ? cursorColRaw : 0,
      altScreen: isAlternateBuffer(buffer),
      viewportStart: getViewportStart(buffer)
    };
  }

  function renderScreenText(state) {
    if (!state || !state.terminal) {
      return "";
    }

    const terminal = state.terminal;
    const buffer = terminal.buffer && terminal.buffer.active ? terminal.buffer.active : null;
    if (!buffer) {
      return "";
    }

    const meta = getScreenMeta(state);
    const rows = [];
    for (let row = 0; row < meta.rows; row += 1) {
      const line = buffer.getLine(meta.viewportStart + row);
      rows.push(line ? line.translateToString(true) : "");
    }

    while (rows.length > 0 && !rows[rows.length - 1]) {
      rows.pop();
    }

    return rows.join("\n");
  }

  function resizeScreenState(state, colsInput, rowsInput) {
    if (!state || !state.terminal) {
      return;
    }

    const cols = normalizeScreenDimension(colsInput, DEFAULT_SESSION_COLS, MAX_SESSION_COLS);
    const rows = normalizeScreenDimension(rowsInput, DEFAULT_SESSION_ROWS, MAX_SESSION_ROWS);

    state.cols = cols;
    state.rows = rows;

    enqueueScreenMutation(state, (done) => {
      state.terminal.resize(cols, rows);
      done();
    });
  }

  function disposeScreenState(state) {
    if (!state || !state.terminal) {
      return;
    }

    try {
      state.terminal.dispose();
    } catch {
      // ignore
    }
  }

  function getSessionOrThrow(sessionIdInput) {
    const sessionId = String(sessionIdInput || "").trim();
    if (!sessionId) {
      throw new Error("Missing sessionId");
    }

    const session = sessions.get(sessionId);
    if (!session) {
      throw new Error("Session not found");
    }

    return session;
  }

  function generateSessionId() {
    const timestamp = Date.now();
    const rand = Math.floor(Math.random() * 1_000_000);
    return `proc_${timestamp}_${rand}`;
  }

  function buildShellLaunch(mode) {
    if (mode === "cmd") {
      return {
        executable: "cmd.exe",
        args: ["/Q", "/K", "chcp 65001>nul"]
      };
    }

    if (mode === "pwsh") {
      return {
        executable: "pwsh",
        args: ["-NoLogo", "-NoProfile", "-NoExit", "-Command", withPowerShellUtf8("$null")]
      };
    }

    return {
      executable: "powershell.exe",
      args: [
        "-NoLogo",
        "-NoProfile",
        "-NoExit",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        withPowerShellUtf8("$null")
      ]
    };
  }

  function terminateChildProcess(session) {
    let signalSent = false;

    if (session.pty && typeof session.pty.kill === "function") {
      try {
        session.pty.kill();
        signalSent = true;
      } catch {
        signalSent = false;
      }
    }

    if (!signalSent && session.child) {
      try {
        signalSent = !!session.child.kill();
      } catch {
        signalSent = false;
      }
    }

    if (!signalSent && Number.isFinite(session.pid) && session.pid > 0) {
      try {
        const killer = spawn("taskkill", ["/PID", String(session.pid), "/T", "/F"], {
          windowsHide: true,
          cwd: projectRoot
        });
        killer.unref();
        signalSent = true;
      } catch {
        signalSent = false;
      }
    }

    return signalSent;
  }

  function cleanupExpiredExitedSessions() {
    const now = Date.now();

    for (const [sessionId, session] of sessions.entries()) {
      if (session.status === "running") {
        continue;
      }

      if (now - session.updatedAt > EXITED_SESSION_TTL_MS) {
        disposeScreenState(session.screenState);
        sessions.delete(sessionId);
      }
    }
  }

  function ensureSessionCapacity() {
    cleanupExpiredExitedSessions();

    if (sessions.size < MAX_SESSION_COUNT) {
      return;
    }

    const exitedSessions = Array.from(sessions.values())
      .filter((item) => item.status !== "running")
      .sort((a, b) => a.updatedAt - b.updatedAt);

    for (const session of exitedSessions) {
      disposeScreenState(session.screenState);
      sessions.delete(session.id);
      if (sessions.size < MAX_SESSION_COUNT) {
        return;
      }
    }

    if (sessions.size >= MAX_SESSION_COUNT) {
      throw new Error(`Too many sessions (max ${MAX_SESSION_COUNT})`);
    }
  }

  function buildSessionSummary(session) {
    const stdoutLatestOffset = session.stdoutStore.baseOffset + session.stdoutStore.content.length;
    const stderrLatestOffset = session.stderrStore.baseOffset + session.stderrStore.content.length;

    return {
      sessionId: session.id,
      shell: session.shell,
      status: session.status,
      pid: session.pid,
      createdAt: new Date(session.createdAt).toISOString(),
      updatedAt: new Date(session.updatedAt).toISOString(),
      exitCode: session.exitCode,
      timedOut: !!session.timedOut,
      commandPreview: session.command.length > 200 ? `${session.command.slice(0, 200)}...` : session.command,
      stdoutLatestOffset,
      stderrLatestOffset
    };
  }

  function runProcess(executable, args, timeoutMs, options = {}) {
    return new Promise((resolve) => {
      const startedAt = Date.now();
      const windowsHide = options.windowsHide === undefined ? true : !!options.windowsHide;
      logger.info("runProcess.start", { executable, args, timeoutMs, windowsHide });

      const child = spawn(executable, args, {
        windowsHide,
        cwd: projectRoot
      });

      let stdout = "";
      let stderr = "";
      let timedOut = false;

      child.stdout.on("data", (chunk) => {
        stdout += chunk.toString();
      });

      child.stderr.on("data", (chunk) => {
        stderr += chunk.toString();
      });

      const timer = setTimeout(() => {
        timedOut = true;
        child.kill();
      }, timeoutMs);

      child.on("close", (code) => {
        clearTimeout(timer);
        const result = {
          exitCode: code === null ? -1 : code,
          stdout,
          stderr,
          timedOut,
          durationMs: Date.now() - startedAt
        };

        logger.info("runProcess.close", {
          executable,
          exitCode: result.exitCode,
          timedOut: result.timedOut,
          durationMs: result.durationMs,
          stdoutLength: result.stdout.length,
          stderrLength: result.stderr.length
        });
        resolve(result);
      });

      child.on("error", (error) => {
        clearTimeout(timer);
        const result = {
          exitCode: -1,
          stdout,
          stderr: (stderr ? `${stderr}\n` : "") + error.message,
          timedOut,
          durationMs: Date.now() - startedAt
        };

        logger.error("runProcess.error", {
          executable,
          error: error.message,
          durationMs: result.durationMs
        });
        resolve(result);
      });
    });
  }

  function runCommand(shell, command, timeoutMs) {
    const mode = normalizeShell(shell);

    if (mode === "cmd") {
      const cmdCommand = `chcp 65001>nul & ${command}`;
      return runProcess("cmd.exe", ["/c", cmdCommand], timeoutMs);
    }

    if (mode === "pwsh") {
      const psCommand = withPowerShellUtf8(command);
      return runProcess("pwsh", ["-NoProfile", "-NonInteractive", "-Command", psCommand], timeoutMs);
    }

    const psCommand = withPowerShellUtf8(command);
    return runProcess(
      "powershell.exe",
      ["-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", psCommand],
      timeoutMs
    );
  }

  function startSession(shellInput, commandInput, options = {}) {
    ensureSessionCapacity();

    const shell = normalizeShell(shellInput);
    const command = typeof commandInput === "string" ? commandInput : "";
    const maxRuntimeMs = parsePositiveInt(
      options.maxRuntimeMs,
      0,
      MAX_SESSION_RUNTIME_MS,
      "maxRuntimeMs"
    );

    const launch = buildShellLaunch(shell);
    const nodePty = getNodePty();
    const startedAt = Date.now();
    const ptyProcess = nodePty.spawn(launch.executable, launch.args, {
      name: shell === "cmd" ? "xterm-color" : "xterm-256color",
      cols: DEFAULT_SESSION_COLS,
      rows: DEFAULT_SESSION_ROWS,
      cwd: projectRoot,
      env: process.env,
      useConpty: true
    });

    const session = {
      id: generateSessionId(),
      shell,
      command,
      pty: ptyProcess,
      child: null,
      pid: Number.isFinite(ptyProcess.pid) ? ptyProcess.pid : null,
      createdAt: startedAt,
      updatedAt: startedAt,
      status: "running",
      exitCode: null,
      timedOut: false,
      stdoutStore: createOutputStore(),
      stderrStore: createOutputStore(),
      screenState: createScreenState(DEFAULT_SESSION_COLS, DEFAULT_SESSION_ROWS),
      runtimeTimer: null,
      removeOnClose: false
    };

    sessions.set(session.id, session);

    ptyProcess.onData((data) => {
      appendOutput(session.stdoutStore, data);
      if (session.screenState) {
        applyChunkToScreen(session.screenState, data);
      }
      session.updatedAt = Date.now();
    });

    ptyProcess.onExit((event) => {
      session.status = "exited";
      session.exitCode =
        event && Number.isFinite(event.exitCode) ? event.exitCode : -1;
      session.updatedAt = Date.now();
      if (session.runtimeTimer) {
        clearTimeout(session.runtimeTimer);
        session.runtimeTimer = null;
      }
      if (session.removeOnClose) {
        disposeScreenState(session.screenState);
        sessions.delete(session.id);
      }
      logger.info("process.session.close", {
        sessionId: session.id,
        shell: session.shell,
        exitCode: session.exitCode,
        timedOut: !!session.timedOut,
        stdoutLatestOffset: session.stdoutStore.baseOffset + session.stdoutStore.content.length,
        stderrLatestOffset: session.stderrStore.baseOffset + session.stderrStore.content.length
      });
    });

    if (typeof ptyProcess.on === "function") {
      ptyProcess.on("error", (error) => {
        appendOutput(session.stderrStore, `${error.message}\n`);
        session.updatedAt = Date.now();
        logger.error("process.session.error", {
          sessionId: session.id,
          shell: session.shell,
          error: error.message
        });
      });
    }

    if (maxRuntimeMs > 0) {
      session.runtimeTimer = setTimeout(() => {
        if (session.status !== "running") {
          return;
        }
        session.timedOut = true;
        session.updatedAt = Date.now();
        terminateChildProcess(session);
      }, maxRuntimeMs);
    }

    if (command.trim()) {
      try {
        ptyProcess.write(`${command}\r`);
      } catch (error) {
        appendOutput(session.stderrStore, `${error.message || String(error)}\n`);
      }
    }

    logger.info("process.session.start", {
      sessionId: session.id,
      shell: session.shell,
      pid: session.pid,
      hasCommand: !!command.trim(),
      maxRuntimeMs
    });

    return {
      ok: true,
      ...buildSessionSummary(session)
    };
  }

  async function readSession(sessionIdInput, options = {}) {
    cleanupExpiredExitedSessions();
    const session = getSessionOrThrow(sessionIdInput);
    const viewMode = normalizeReadViewMode(options.viewMode);
    const stdoutOffset = parseNonNegativeInt(options.stdoutOffset, 0, "stdoutOffset");
    const stderrOffset = parseNonNegativeInt(options.stderrOffset, 0, "stderrOffset");

    session.updatedAt = Date.now();

    if (viewMode === "screen") {
      const stdoutMeta = buildOutputMeta(session.stdoutStore, stdoutOffset);
      const stderrMeta = buildOutputMeta(session.stderrStore, stderrOffset);
      const screenState = session.screenState || createScreenState(DEFAULT_SESSION_COLS, DEFAULT_SESSION_ROWS);
      session.screenState = screenState;

      await flushScreenState(screenState);
      const screenMeta = getScreenMeta(screenState);

      return {
        ok: true,
        ...buildSessionSummary(session),
        viewMode,
        stdout: renderScreenText(screenState),
        stderr: "",
        stdoutOffset: stdoutMeta.latestOffset,
        stderrOffset: stderrMeta.latestOffset,
        stdoutFromOffset: stdoutMeta.fromOffset,
        stderrFromOffset: stderrMeta.fromOffset,
        stdoutAvailableFrom: stdoutMeta.availableFrom,
        stderrAvailableFrom: stderrMeta.availableFrom,
        stdoutTruncated: stdoutMeta.truncated,
        stderrTruncated: stderrMeta.truncated,
        hasMore: false,
        screenCols: screenMeta.cols,
        screenRows: screenMeta.rows,
        cursorRow: screenMeta.cursorRow,
        cursorCol: screenMeta.cursorCol,
        altScreen: screenMeta.altScreen
      };
    }

    const maxChars = parsePositiveInt(
      options.maxChars,
      DEFAULT_SESSION_READ_CHARS,
      MAX_SESSION_READ_CHARS,
      "maxChars"
    );

    const stdoutSlice = readOutput(session.stdoutStore, stdoutOffset, maxChars);
    const stderrSlice = readOutput(session.stderrStore, stderrOffset, maxChars);

    return {
      ok: true,
      ...buildSessionSummary(session),
      viewMode,
      stdout: stdoutSlice.chunk,
      stderr: stderrSlice.chunk,
      stdoutOffset: stdoutSlice.nextOffset,
      stderrOffset: stderrSlice.nextOffset,
      stdoutFromOffset: stdoutSlice.fromOffset,
      stderrFromOffset: stderrSlice.fromOffset,
      stdoutAvailableFrom: stdoutSlice.availableFrom,
      stderrAvailableFrom: stderrSlice.availableFrom,
      stdoutTruncated: stdoutSlice.truncated,
      stderrTruncated: stderrSlice.truncated,
      hasMore:
        stdoutSlice.nextOffset < stdoutSlice.latestOffset ||
        stderrSlice.nextOffset < stderrSlice.latestOffset
    };
  }

  async function writeSession(sessionIdInput, inputText) {
    const session = getSessionOrThrow(sessionIdInput);
    if (session.status !== "running") {
      throw new Error("Session is not running");
    }

    if (typeof inputText !== "string") {
      throw new Error("Missing input");
    }

    if (!session.pty || typeof session.pty.write !== "function") {
      throw new Error("Session stdin is not available");
    }

    session.pty.write(inputText);
    session.updatedAt = Date.now();

    return {
      ok: true,
      ...buildSessionSummary(session),
      acceptedChars: inputText.length
    };
  }

  function resizeSession(sessionIdInput, options = {}) {
    const session = getSessionOrThrow(sessionIdInput);
    if (session.status !== "running") {
      throw new Error("Session is not running");
    }

    const cols = parsePositiveInt(
      options.cols,
      120,
      MAX_SESSION_COLS,
      "cols"
    );
    const rows = parsePositiveInt(
      options.rows,
      30,
      MAX_SESSION_ROWS,
      "rows"
    );

    if (!session.pty || typeof session.pty.resize !== "function") {
      throw new Error("Session resize is not available");
    }

    session.pty.resize(cols, rows);
    if (session.screenState) {
      resizeScreenState(session.screenState, cols, rows);
    }
    session.updatedAt = Date.now();

    return {
      ok: true,
      ...buildSessionSummary(session),
      cols,
      rows
    };
  }

  function terminateSession(sessionIdInput, options = {}) {
    const session = getSessionOrThrow(sessionIdInput);
    const remove = parseBoolean(options.remove, false);

    const wasRunning = session.status === "running";
    const signalSent = wasRunning ? terminateChildProcess(session) : false;

    let removed = false;
    if (remove) {
      if (wasRunning) {
        session.removeOnClose = true;
      } else {
        disposeScreenState(session.screenState);
        sessions.delete(session.id);
        removed = true;
      }
    }

    session.updatedAt = Date.now();

    return {
      ok: true,
      ...buildSessionSummary(session),
      wasRunning,
      signalSent,
      removed
    };
  }

  function listSessions(options = {}) {
    cleanupExpiredExitedSessions();
    const includeExited = parseBoolean(options.includeExited, true);

    const items = Array.from(sessions.values())
      .filter((session) => includeExited || session.status === "running")
      .sort((a, b) => b.createdAt - a.createdAt)
      .map((session) => buildSessionSummary(session));

    return {
      ok: true,
      items
    };
  }

  function terminateAllSessions() {
    for (const session of sessions.values()) {
      if (session.status === "running") {
        terminateChildProcess(session);
      }
      if (session.runtimeTimer) {
        clearTimeout(session.runtimeTimer);
        session.runtimeTimer = null;
      }
      disposeScreenState(session.screenState);
    }
    sessions.clear();
  }

  function isPrivateLanIpv4(ipv4) {
    if (!ipv4 || typeof ipv4 !== "string") {
      return false;
    }

    if (ipv4.startsWith("10.")) {
      return true;
    }

    if (ipv4.startsWith("192.168.")) {
      return true;
    }

    const match = /^172\.(\d+)\./.exec(ipv4);
    if (!match) {
      return false;
    }

    const second = Number(match[1]);
    return Number.isFinite(second) && second >= 16 && second <= 31;
  }

  function isLikelyVirtualInterfaceName(interfaceName) {
    const text = String(interfaceName || "").trim();
    if (!text) {
      return false;
    }

    const virtualTokens = [
      "vEthernet",
      "wsl",
      "hyper-v",
      "vmware",
      "virtualbox",
      "docker",
      "container",
      "tailscale",
      "zerotier",
      "loopback",
      "npcap",
      "hamachi"
    ];

    const lowered = text.toLowerCase();
    return virtualTokens.some((token) => lowered.includes(token));
  }

  function isLikelyPhysicalPreferredInterfaceName(interfaceName) {
    const text = String(interfaceName || "").trim();
    if (!text) {
      return false;
    }

    const preferredTokens = [
      "wifi",
      "wi-fi",
      "wlan",
      "wireless",
      "ethernet",
      "lan"
    ];

    const lowered = text.toLowerCase();
    return preferredTokens.some((token) => lowered.includes(token));
  }

  function scoreIpv4Candidate(address, interfaceName) {
    let score = 0;

    if (isPrivateLanIpv4(address)) {
      score += 100;
    }

    if (isLikelyVirtualInterfaceName(interfaceName)) {
      score -= 200;
    } else {
      score += 70;
    }

    if (isLikelyPhysicalPreferredInterfaceName(interfaceName)) {
      score += 25;
    }

    return score;
  }

  function getNetworkSnapshot() {
    const interfaces = os.networkInterfaces();
    const candidateByAddress = new Map();

    for (const [interfaceName, entries] of Object.entries(interfaces)) {
      for (const item of entries || []) {
        if (!item || item.family !== "IPv4" || item.internal) {
          continue;
        }
        if (typeof item.address === "string" && item.address.startsWith("169.254.")) {
          continue;
        }

        const address = String(item.address || "").trim();
        if (!address) {
          continue;
        }

        const candidate = {
          address,
          interfaceName: String(interfaceName || "").trim(),
          isPrivateLan: isPrivateLanIpv4(address),
          isVirtual: isLikelyVirtualInterfaceName(interfaceName),
          score: scoreIpv4Candidate(address, interfaceName)
        };

        const existing = candidateByAddress.get(address);
        if (!existing || candidate.score > existing.score) {
          candidateByAddress.set(address, candidate);
        }
      }
    }

    const rankedCandidates = Array.from(candidateByAddress.values()).sort((a, b) => {
      if (b.score !== a.score) {
        return b.score - a.score;
      }
      return a.address.localeCompare(b.address);
    });

    const ipv4Candidates = rankedCandidates.map((item) => item.address);
    const preferredNonVirtualLan = rankedCandidates.find((item) => item.isPrivateLan && !item.isVirtual);
    const preferredLanCandidate = preferredNonVirtualLan || rankedCandidates.find((item) => item.isPrivateLan);
    const preferredLan = preferredLanCandidate ? preferredLanCandidate.address : "";
    const recommended = rankedCandidates[0] || null;

    return {
      ipv4Candidates,
      preferredLan,
      recommendedHost: recommended ? recommended.address : "",
      recommendedInterfaceName: recommended ? recommended.interfaceName : "",
      rankedIpv4Candidates: rankedCandidates
    };
  }

  function getUserSnapshot() {
    const username = String(process.env.USERNAME || "").trim();
    const userDomain = String(process.env.USERDOMAIN || "").trim();
    const domainQualified = username && userDomain ? `${userDomain}\\${username}` : username;

    return {
      username,
      domain: userDomain,
      domainQualified
    };
  }

  return {
    runProcess,
    runCommand,
    startSession,
    readSession,
    writeSession,
    resizeSession,
    terminateSession,
    listSessions,
    terminateAllSessions,
    getNetworkSnapshot,
    getUserSnapshot
  };
}

module.exports = {
  createProcessService
};
