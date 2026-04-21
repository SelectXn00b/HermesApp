const OUTPUT_LIMIT_CHARS = 600000;
const POLL_INTERVAL_MS = 180;
const POLL_READ_CHARS = 12000;
const MAX_POLL_READ_ROUNDS = 4;
const INPUT_FLUSH_DELAY_MS = 32;
const TERMINAL_WRITE_CHUNK_SIZE = 12000;
const TERMINAL_RESIZE_DEBOUNCE_MS = 120;
const XTERM_MODULE_URL = "https://cdn.jsdelivr.net/npm/xterm@5.3.0/+esm";
const XTERM_FIT_MODULE_URL = "https://cdn.jsdelivr.net/npm/@xterm/addon-fit@0.10.0/+esm";

let xtermModulesPromise = null;

function loadXtermModules() {
  if (!xtermModulesPromise) {
    xtermModulesPromise = Promise.all([
      import(XTERM_MODULE_URL),
      import(XTERM_FIT_MODULE_URL)
    ]).then(([xtermMod, fitMod]) => {
      const TerminalCtor = xtermMod && xtermMod.Terminal;
      const FitAddonCtor = fitMod && fitMod.FitAddon;
      if (!TerminalCtor || !FitAddonCtor) {
        throw new Error("xterm modules missing exports");
      }
      return {
        Terminal: TerminalCtor,
        FitAddon: FitAddonCtor
      };
    });
  }

  return xtermModulesPromise;
}

function toText(value, fallback = "-") {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }
  return String(value);
}

function formatTimestamp(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return String(value);
  }

  return date.toLocaleString();
}

function normalizeStatus(status) {
  return String(status || "").trim().toLowerCase();
}

function sortSessions(items) {
  const safeItems = Array.isArray(items) ? [...items] : [];

  safeItems.sort((a, b) => {
    const statusA = normalizeStatus(a && a.status);
    const statusB = normalizeStatus(b && b.status);

    const rankA = statusA === "running" ? 0 : 1;
    const rankB = statusB === "running" ? 0 : 1;
    if (rankA !== rankB) {
      return rankA - rankB;
    }

    const timeA = Date.parse(String(a && a.updatedAt ? a.updatedAt : ""));
    const timeB = Date.parse(String(b && b.updatedAt ? b.updatedAt : ""));
    if (Number.isFinite(timeA) && Number.isFinite(timeB) && timeA !== timeB) {
      return timeB - timeA;
    }

    return String(b && b.sessionId ? b.sessionId : "").localeCompare(String(a && a.sessionId ? a.sessionId : ""));
  });

  return safeItems;
}

function trimOutput(text) {
  const safeText = String(text || "");
  if (safeText.length <= OUTPUT_LIMIT_CHARS) {
    return safeText;
  }
  return safeText.slice(safeText.length - OUTPUT_LIMIT_CHARS);
}

function createSessionNavButton(session, isActive, t, onSelect) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = `session-nav-item${isActive ? " is-active" : ""}`;

  const header = document.createElement("div");
  header.className = "session-nav-item__header";

  const idNode = document.createElement("code");
  idNode.className = "session-nav-item__id";
  idNode.textContent = toText(session.sessionId);

  const statusNode = document.createElement("span");
  const status = normalizeStatus(session.status);
  statusNode.className = `session-nav-item__status ${status === "running" ? "is-running" : "is-exited"}`;
  statusNode.textContent = toText(session.status);

  header.append(idNode, statusNode);

  const meta = document.createElement("div");
  meta.className = "session-nav-item__meta";
  meta.textContent = `${t("field.shell")}: ${toText(session.shell)} · ${t("field.updatedAt")}: ${formatTimestamp(session.updatedAt)}`;

  button.append(header, meta);
  button.addEventListener("click", () => onSelect(toText(session.sessionId, "")));

  return button;
}

export function createProcessesPage({ t, W, on = {} }) {
  return W.Box(
    { className: "route-page", ref: "managePage" },
    W.Card(
      {
        title: t("card.manageTitle"),
        subtitle: t("card.manageSubtitle")
      },
      W.Grid2(
        {},
        W.Field({ label: t("field.token") }, W.Input({ ref: "manageTokenInput", type: "password", placeholder: t("placeholder.tokenRequiredWhenApiTokenEnabled") })),
        W.Toggle({ inputRef: "manageIncludeExitedInput", text: t("field.includeExitedSessions"), checked: true })
      ),
      W.Panel(
        {},
        W.Grid2(
          {},
          W.Field(
            { label: t("field.startShell") },
            W.Select({
              ref: "manageStartShellSelect",
              options: [
                { value: "powershell", label: "powershell" },
                { value: "cmd", label: "cmd" },
                { value: "pwsh", label: "pwsh" }
              ]
            })
          ),
          W.Field({ label: t("field.startCommand") }, W.Input({ ref: "manageStartCommandInput", placeholder: t("placeholder.startCommand") }))
        ),
        W.ButtonGroup(
          {},
          W.Button({ text: t("action.createSession"), ref: "createSessionButton", on: { click: on.createSession } }),
          W.Button({ text: t("action.refreshSessions"), variant: "soft", ref: "refreshSessionsButton", on: { click: on.refreshSessions } })
        )
      ),
      W.Box(
        { className: "terminal-layout" },
        W.Box(
          { className: "terminal-sidebar" },
          W.Box({ className: "session-nav", ref: "manageSessionList" })
        ),
        W.Box(
          { className: "terminal-main" },
          W.Box(
            { className: "terminal-main__header" },
            W.Box(
              { className: "terminal-current" },
              W.Box({ className: "terminal-current__row" }, W.Text({ className: "terminal-current__label", text: `${t("field.currentSession")}:` }), W.Text({ className: "terminal-current__value", ref: "currentSessionIdValue", text: "-" })),
              W.Box({ className: "terminal-current__row" }, W.Text({ className: "terminal-current__label", text: `${t("field.shell")}:` }), W.Text({ className: "terminal-current__value", ref: "currentSessionShellValue", text: "-" })),
              W.Box({ className: "terminal-current__row" }, W.Text({ className: "terminal-current__label", text: `${t("field.status")}:` }), W.Text({ className: "terminal-current__value", ref: "currentSessionStatusValue", text: "-" })),
              W.Text({ className: "terminal-hint", ref: "terminalHint", text: t("message.selectSessionHint") })
            ),
            W.ButtonGroup(
              {},
              W.Button({ text: t("action.sendCtrlC"), variant: "soft", ref: "sendCtrlCButton", on: { click: on.sendCtrlC } }),
              W.Button({ text: t("action.closeSession"), variant: "danger", ref: "closeSessionButton", on: { click: on.closeSelectedSession } })
            )
          ),
          W.Box({ className: "terminal-host", ref: "terminalHost" })
        )
      )
    )
  );
}

export function createProcessesController({ api, refs, t, helpers }) {
  const { setBusy, setNotice, asErrorMessage } = helpers;

  let sessions = [];
  let selectedSessionId = "";

  const offsetsBySessionId = new Map();
  const outputBySessionId = new Map();
  const truncationNotedSessionIds = new Set();

  const inputQueueBySessionId = new Map();
  const inputFlushTimerBySessionId = new Map();
  const flushingSessionIds = new Set();

  let pollTimer = null;
  let polling = false;
  let lastSessionListRenderAt = 0;

  let terminal = null;
  let fitAddon = null;
  let terminalReady = false;
  let terminalInitPromise = null;
  let terminalInitFailed = false;
  let resizeObserver = null;
  let terminalWheelStopHandler = null;

  let terminalWriteQueue = "";
  let terminalWriteInFlight = false;
  let terminalWriteRafId = 0;

  let pendingResize = null;
  let resizeInFlight = false;
  let resizeDebounceTimer = null;
  let lastAppliedResize = { sessionId: "", cols: 0, rows: 0 };

  function pageVisible() {
    return !!(refs.managePage && !refs.managePage.hidden);
  }

  function getToken() {
    return refs.manageTokenInput ? refs.manageTokenInput.value : "";
  }

  function ensureOffset(sessionId) {
    if (!offsetsBySessionId.has(sessionId)) {
      offsetsBySessionId.set(sessionId, { stdoutOffset: 0, stderrOffset: 0 });
    }
    return offsetsBySessionId.get(sessionId);
  }

  function findSessionById(sessionId) {
    return sessions.find((item) => toText(item && item.sessionId, "") === sessionId) || null;
  }

  function setTerminalHintText(text) {
    if (refs.terminalHint) {
      refs.terminalHint.textContent = text;
    }
  }

  function updateTerminalHint() {
    if (!selectedSessionId) {
      setTerminalHintText(t("message.selectSessionHint"));
      return;
    }

    const selected = findSessionById(selectedSessionId);
    if (!selected) {
      setTerminalHintText(t("message.selectSessionHint"));
      return;
    }

    if (normalizeStatus(selected.status) === "running") {
      setTerminalHintText(t("message.terminalInteractive"));
      return;
    }

    setTerminalHintText(t("message.terminalReadonlyExited"));
  }

  function isTerminalNearBottom() {
    if (!terminal || !terminal.buffer || !terminal.buffer.active) {
      return true;
    }

    const active = terminal.buffer.active;
    return active.baseY - active.viewportY <= 1;
  }

  function resetTerminalWriteQueue() {
    terminalWriteQueue = "";
    terminalWriteInFlight = false;
    if (terminalWriteRafId) {
      window.cancelAnimationFrame(terminalWriteRafId);
      terminalWriteRafId = 0;
    }
  }

  function scheduleTerminalWriteFlush() {
    if (terminalWriteRafId) {
      return;
    }

    terminalWriteRafId = window.requestAnimationFrame(() => {
      terminalWriteRafId = 0;
      if (!terminalReady || !terminal || terminalWriteInFlight || !terminalWriteQueue) {
        return;
      }

      const keepBottom = isTerminalNearBottom();
      const chunk = terminalWriteQueue.slice(0, TERMINAL_WRITE_CHUNK_SIZE);
      terminalWriteQueue = terminalWriteQueue.slice(chunk.length);
      terminalWriteInFlight = true;

      terminal.write(chunk, () => {
        terminalWriteInFlight = false;
        if (keepBottom) {
          try {
            terminal.scrollToBottom();
          } catch {
            // ignore
          }
        }

        if (terminalWriteQueue) {
          scheduleTerminalWriteFlush();
        }
      });
    });
  }

  function queueTerminalOutput(chunk) {
    if (!chunk || !terminalReady || !terminal) {
      return;
    }

    terminalWriteQueue += chunk;
    scheduleTerminalWriteFlush();
  }

  function scheduleResizeSync() {
    if (!terminalReady || !terminal || !selectedSessionId) {
      return;
    }

    const selected = findSessionById(selectedSessionId);
    if (!selected || normalizeStatus(selected.status) !== "running") {
      return;
    }

    const cols = Number(terminal.cols);
    const rows = Number(terminal.rows);
    if (!Number.isFinite(cols) || !Number.isFinite(rows) || cols <= 0 || rows <= 0) {
      return;
    }

    const safeCols = Math.floor(cols);
    const safeRows = Math.floor(rows);

    if (
      lastAppliedResize.sessionId === selectedSessionId &&
      lastAppliedResize.cols === safeCols &&
      lastAppliedResize.rows === safeRows
    ) {
      return;
    }

    if (
      pendingResize &&
      pendingResize.sessionId === selectedSessionId &&
      pendingResize.cols === safeCols &&
      pendingResize.rows === safeRows
    ) {
      return;
    }

    pendingResize = {
      sessionId: selectedSessionId,
      cols: safeCols,
      rows: safeRows
    };

    if (resizeDebounceTimer) {
      clearTimeout(resizeDebounceTimer);
    }

    resizeDebounceTimer = setTimeout(() => {
      resizeDebounceTimer = null;
      void flushPendingResize();
    }, TERMINAL_RESIZE_DEBOUNCE_MS);
  }

  async function flushPendingResize() {
    if (resizeInFlight || !pendingResize) {
      return;
    }

    const task = pendingResize;
    pendingResize = null;

    if (task.sessionId !== selectedSessionId) {
      return;
    }

    const selected = findSessionById(task.sessionId);
    if (!selected || normalizeStatus(selected.status) !== "running") {
      return;
    }

    resizeInFlight = true;
    try {
      await api.resizeProcessSession({
        token: getToken(),
        session_id: task.sessionId,
        cols: task.cols,
        rows: task.rows
      });
      lastAppliedResize = {
        sessionId: task.sessionId,
        cols: task.cols,
        rows: task.rows
      };
    } catch {
      // keep silent during polling-style updates
    } finally {
      resizeInFlight = false;
      if (pendingResize) {
        void flushPendingResize();
      }
    }
  }

  function fitTerminal() {
    if (!terminalReady || !fitAddon || !terminal) {
      return;
    }

    try {
      fitAddon.fit();
      scheduleResizeSync();
    } catch {
      // ignore
    }
  }

  function queueSessionInput(sessionId, chunk) {
    const sid = String(sessionId || "").trim();
    if (!sid || !chunk) {
      return;
    }

    const previous = toText(inputQueueBySessionId.get(sid), "");
    inputQueueBySessionId.set(sid, previous + chunk);
    scheduleInputFlush(sid);
  }

  function scheduleInputFlush(sessionId) {
    const sid = String(sessionId || "").trim();
    if (!sid) {
      return;
    }

    if (inputFlushTimerBySessionId.has(sid)) {
      return;
    }

    const timer = setTimeout(() => {
      inputFlushTimerBySessionId.delete(sid);
      void flushSessionInput(sid);
    }, INPUT_FLUSH_DELAY_MS);

    inputFlushTimerBySessionId.set(sid, timer);
  }

  async function flushSessionInput(sessionId) {
    const sid = String(sessionId || "").trim();
    if (!sid) {
      return;
    }

    const queued = toText(inputQueueBySessionId.get(sid), "");
    if (!queued) {
      return;
    }

    if (flushingSessionIds.has(sid)) {
      scheduleInputFlush(sid);
      return;
    }

    inputQueueBySessionId.delete(sid);
    flushingSessionIds.add(sid);

    try {
      await api.writeProcessSession({
        token: getToken(),
        session_id: sid,
        input: queued
      });
    } catch (error) {
      const tail = toText(inputQueueBySessionId.get(sid), "");
      inputQueueBySessionId.set(sid, queued + tail);
      setNotice("error", t("message.inputSendFailed", { error: asErrorMessage(error) }));
    } finally {
      flushingSessionIds.delete(sid);
      if (toText(inputQueueBySessionId.get(sid), "")) {
        scheduleInputFlush(sid);
      }
    }
  }

  async function ensureTerminal() {
    if (terminalReady) {
      return true;
    }

    if (terminalInitFailed) {
      return false;
    }

    if (terminalInitPromise) {
      return terminalInitPromise;
    }

    terminalInitPromise = (async () => {
      try {
        const host = refs.terminalHost;
        if (!host) {
          throw new Error("terminal host unavailable");
        }

        const modules = await loadXtermModules();
        const TerminalCtor = modules.Terminal;
        const FitAddonCtor = modules.FitAddon;

        terminal = new TerminalCtor({
          convertEol: false,
          cursorBlink: true,
          scrollback: 5000,
          fontFamily: "Consolas, 'Courier New', monospace",
          fontSize: 13,
          theme: {
            background: "#091327",
            foreground: "#d7e9ff",
            cursor: "#d7e9ff"
          }
        });

        fitAddon = new FitAddonCtor();
        terminal.loadAddon(fitAddon);
        terminal.open(host);
        resetTerminalWriteQueue();

        if (terminalWheelStopHandler) {
          host.removeEventListener("wheel", terminalWheelStopHandler);
        }
        terminalWheelStopHandler = (event) => {
          event.stopPropagation();
        };
        host.addEventListener("wheel", terminalWheelStopHandler, { passive: true });

        fitTerminal();

        terminal.onData((chunk) => {
          if (!selectedSessionId) {
            return;
          }

          const selected = findSessionById(selectedSessionId);
          if (!selected || normalizeStatus(selected.status) !== "running") {
            return;
          }

          queueSessionInput(selectedSessionId, chunk);
        });

        if (typeof ResizeObserver === "function") {
          resizeObserver = new ResizeObserver(() => {
            fitTerminal();
          });
          resizeObserver.observe(host);
        } else {
          window.addEventListener("resize", fitTerminal);
        }

        terminalReady = true;
        terminalInitFailed = false;
        updateTerminalHint();
        await renderTerminalSnapshot();

        return true;
      } catch (error) {
        terminalInitFailed = true;
        const message = asErrorMessage(error);
        if (refs.terminalHost) {
          refs.terminalHost.textContent = t("message.terminalInitFailed", { error: message });
        }
        setNotice("error", t("message.terminalInitFailed", { error: message }));
        return false;
      } finally {
        terminalInitPromise = null;
      }
    })();

    return terminalInitPromise;
  }

  async function renderTerminalSnapshot() {
    const ready = await ensureTerminal();
    if (!ready || !terminal) {
      return;
    }

    resetTerminalWriteQueue();
    terminal.reset();

    if (!selectedSessionId) {
      return;
    }

    const selected = findSessionById(selectedSessionId);
    if (!selected) {
      return;
    }

    const output = toText(outputBySessionId.get(selectedSessionId), "");
    if (output) {
      terminalWriteInFlight = true;
      terminal.write(output, () => {
        terminalWriteInFlight = false;
        try {
          terminal.scrollToBottom();
        } catch {
          // ignore
        }
        if (terminalWriteQueue) {
          scheduleTerminalWriteFlush();
        }
      });
    }

    scheduleResizeSync();
  }

  function appendTerminalOutputIfSelected(sessionId, chunk) {
    if (!chunk || !terminalReady || !terminal) {
      return;
    }

    if (selectedSessionId !== sessionId) {
      return;
    }

    queueTerminalOutput(chunk);
  }

  function syncCurrentSessionMeta() {
    const selected = findSessionById(selectedSessionId);

    if (refs.currentSessionIdValue) {
      refs.currentSessionIdValue.textContent = selected ? toText(selected.sessionId) : "-";
    }
    if (refs.currentSessionShellValue) {
      refs.currentSessionShellValue.textContent = selected ? toText(selected.shell) : "-";
    }
    if (refs.currentSessionStatusValue) {
      refs.currentSessionStatusValue.textContent = selected ? toText(selected.status) : "-";
    }
    if (refs.closeSessionButton) {
      refs.closeSessionButton.disabled = !selected || normalizeStatus(selected.status) !== "running";
    }
    if (refs.sendCtrlCButton) {
      refs.sendCtrlCButton.disabled = !selected || normalizeStatus(selected.status) !== "running";
    }

    updateTerminalHint();

    if (selected && normalizeStatus(selected.status) === "running") {
      scheduleResizeSync();
    }
  }

  function renderSessionList() {
    const container = refs.manageSessionList;
    if (!container) {
      return;
    }

    if (!sessions.length) {
      const empty = document.createElement("div");
      empty.className = "session-empty";
      empty.textContent = t("message.noSessions");
      container.replaceChildren(empty);
      lastSessionListRenderAt = Date.now();
      return;
    }

    const nodes = sessions.map((session) =>
      createSessionNavButton(session, toText(session.sessionId, "") === selectedSessionId, t, selectSession)
    );

    container.replaceChildren(...nodes);
    lastSessionListRenderAt = Date.now();
  }

  function pickNextSelection() {
    if (!sessions.length) {
      return "";
    }

    if (selectedSessionId && findSessionById(selectedSessionId)) {
      return selectedSessionId;
    }

    const running = sessions.find((item) => normalizeStatus(item && item.status) === "running");
    if (running) {
      return toText(running.sessionId, "");
    }

    return toText(sessions[0].sessionId, "");
  }

  function patchSessionSummary(summary) {
    const sessionId = toText(summary && summary.sessionId, "");
    if (!sessionId) {
      return;
    }

    const index = sessions.findIndex((item) => toText(item && item.sessionId, "") === sessionId);
    if (index < 0) {
      return;
    }

    sessions[index] = {
      ...sessions[index],
      status: summary.status !== undefined ? summary.status : sessions[index].status,
      shell: summary.shell !== undefined ? summary.shell : sessions[index].shell,
      pid: summary.pid !== undefined ? summary.pid : sessions[index].pid,
      exitCode: summary.exitCode !== undefined ? summary.exitCode : sessions[index].exitCode,
      updatedAt: summary.updatedAt !== undefined ? summary.updatedAt : sessions[index].updatedAt,
      commandPreview: summary.commandPreview !== undefined ? summary.commandPreview : sessions[index].commandPreview
    };
  }

  function appendSessionOutput(sessionId, chunk) {
    if (!chunk) {
      return;
    }

    const previous = toText(outputBySessionId.get(sessionId), "");
    outputBySessionId.set(sessionId, trimOutput(previous + chunk));
  }

  async function pollSelectedSession() {
    if (polling || !selectedSessionId || !pageVisible()) {
      return;
    }

    polling = true;

    try {
      const sessionId = selectedSessionId;
      const offsets = ensureOffset(sessionId);
      const selectedBefore = findSessionById(sessionId);
      const statusBefore = normalizeStatus(selectedBefore && selectedBefore.status);

      let combinedChunk = "";
      let latestResult = null;
      let rounds = 0;

      while (rounds < MAX_POLL_READ_ROUNDS) {
        rounds += 1;

        const result = await api.readProcessSession({
          token: getToken(),
          session_id: sessionId,
          view_mode: "stream",
          stdout_offset: offsets.stdoutOffset,
          stderr_offset: offsets.stderrOffset,
          max_chars: POLL_READ_CHARS
        });

        if (toText(result && result.sessionId, "") !== sessionId) {
          break;
        }

        latestResult = result;

        if (Number.isFinite(Number(result && result.stdoutOffset))) {
          offsets.stdoutOffset = Number(result.stdoutOffset);
        }
        if (Number.isFinite(Number(result && result.stderrOffset))) {
          offsets.stderrOffset = Number(result.stderrOffset);
        }

        if ((result && result.stdoutTruncated) || (result && result.stderrTruncated)) {
          if (!truncationNotedSessionIds.has(sessionId)) {
            combinedChunk += `\r\n[${t("message.outputTruncated")}]\r\n`;
            truncationNotedSessionIds.add(sessionId);
          }
        }

        const stdoutText = toText(result && result.stdout, "");
        const stderrText = toText(result && result.stderr, "");
        combinedChunk += stdoutText;
        combinedChunk += stderrText;

        if (!result || !result.hasMore || selectedSessionId !== sessionId || !pageVisible()) {
          break;
        }
      }

      if (!latestResult) {
        return;
      }

      patchSessionSummary(latestResult);

      if (combinedChunk) {
        appendSessionOutput(sessionId, combinedChunk);
        appendTerminalOutputIfSelected(sessionId, combinedChunk);
      }

      const selectedAfter = findSessionById(sessionId);
      const statusAfter = normalizeStatus(selectedAfter && selectedAfter.status);
      const now = Date.now();
      if (statusAfter !== statusBefore || now - lastSessionListRenderAt >= 1200) {
        renderSessionList();
      }

      syncCurrentSessionMeta();
    } catch {
      // silent to keep interaction smooth while polling
    } finally {
      polling = false;
    }
  }

  async function refreshSessions() {
    setBusy("refreshSessionsButton", true, t("action.refreshSessions"), t("action.refreshing"));

    try {
      const result = await api.listProcessSessions({
        token: getToken(),
        include_exited: !!(refs.manageIncludeExitedInput && refs.manageIncludeExitedInput.checked)
      });

      sessions = sortSessions(result && result.items);
      const nextSelection = pickNextSelection();
      const changed = nextSelection !== selectedSessionId;
      selectedSessionId = nextSelection;

      renderSessionList();
      syncCurrentSessionMeta();

      if (changed) {
        await renderTerminalSnapshot();
      }

      if (selectedSessionId) {
        void pollSelectedSession();
      }
    } catch (error) {
      sessions = [];
      selectedSessionId = "";
      renderSessionList();
      syncCurrentSessionMeta();
      await renderTerminalSnapshot();
      setNotice("error", t("message.sessionsRefreshFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("refreshSessionsButton", false, t("action.refreshSessions"), t("action.refreshing"));
    }
  }

  async function createSession() {
    setBusy("createSessionButton", true, t("action.createSession"), t("action.creating"));

    try {
      const shell = refs.manageStartShellSelect ? refs.manageStartShellSelect.value : "powershell";
      const command = refs.manageStartCommandInput ? refs.manageStartCommandInput.value : "";

      const result = await api.startProcessSession({
        token: getToken(),
        shell,
        command: command || undefined
      });

      const createdSessionId = toText(result && result.sessionId, "");
      if (createdSessionId) {
        offsetsBySessionId.set(createdSessionId, { stdoutOffset: 0, stderrOffset: 0 });
        outputBySessionId.set(createdSessionId, "");
      }

      await refreshSessions();
      if (createdSessionId) {
        await selectSession(createdSessionId);
      }

      if (refs.manageStartCommandInput) {
        refs.manageStartCommandInput.value = "";
      }

      setNotice("ok", t("message.sessionCreated", { sessionId: createdSessionId || "-" }));
    } catch (error) {
      setNotice("error", t("message.sessionCreateFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("createSessionButton", false, t("action.createSession"), t("action.creating"));
    }
  }

  async function sendCtrlC() {
    if (!selectedSessionId) {
      setNotice("warn", t("message.selectSessionFirst"));
      return;
    }

    const selected = findSessionById(selectedSessionId);
    if (!selected || normalizeStatus(selected.status) !== "running") {
      setNotice("warn", t("message.terminalReadonlyExited"));
      return;
    }

    setBusy("sendCtrlCButton", true, t("action.sendCtrlC"), t("action.sending"));

    try {
      await api.writeProcessSession({
        token: getToken(),
        session_id: selectedSessionId,
        input: "\u0003"
      });
      setNotice("ok", t("message.ctrlCSent"));
      void pollSelectedSession();
    } catch (error) {
      setNotice("error", t("message.inputSendFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("sendCtrlCButton", false, t("action.sendCtrlC"), t("action.sending"));
    }
  }

  async function closeSelectedSession() {
    if (!selectedSessionId) {
      setNotice("warn", t("message.selectSessionFirst"));
      return;
    }

    setBusy("closeSessionButton", true, t("action.closeSession"), t("action.closing"));

    try {
      const closingId = selectedSessionId;
      await api.terminateProcessSession({
        token: getToken(),
        session_id: closingId,
        remove: true
      });

      inputQueueBySessionId.delete(closingId);
      offsetsBySessionId.delete(closingId);
      outputBySessionId.delete(closingId);
      truncationNotedSessionIds.delete(closingId);

      setNotice("ok", t("message.sessionClosed", { sessionId: closingId }));
      await refreshSessions();
    } catch (error) {
      setNotice("error", t("message.sessionCloseFailed", { error: asErrorMessage(error) }));
    } finally {
      setBusy("closeSessionButton", false, t("action.closeSession"), t("action.closing"));
    }
  }

  async function selectSession(sessionId) {
    const safeId = String(sessionId || "").trim();
    selectedSessionId = safeId;

    if (safeId) {
      ensureOffset(safeId);
    }

    renderSessionList();
    syncCurrentSessionMeta();
    await renderTerminalSnapshot();

    if (safeId) {
      void pollSelectedSession();
    }
  }

  function prefillToken(token) {
    if (!refs.manageTokenInput) {
      return;
    }

    if (refs.manageTokenInput.value.trim()) {
      return;
    }

    refs.manageTokenInput.value = toText(token, "");
  }

  function startPolling() {
    if (pollTimer) {
      return;
    }

    pollTimer = setInterval(() => {
      void pollSelectedSession();
    }, POLL_INTERVAL_MS);
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }

    for (const timer of inputFlushTimerBySessionId.values()) {
      clearTimeout(timer);
    }
    inputFlushTimerBySessionId.clear();

    if (resizeDebounceTimer) {
      clearTimeout(resizeDebounceTimer);
      resizeDebounceTimer = null;
    }
    pendingResize = null;
    lastAppliedResize = { sessionId: "", cols: 0, rows: 0 };

    resetTerminalWriteQueue();

    if (resizeObserver) {
      resizeObserver.disconnect();
      resizeObserver = null;
    } else {
      window.removeEventListener("resize", fitTerminal);
    }

    if (refs.terminalHost && terminalWheelStopHandler) {
      refs.terminalHost.removeEventListener("wheel", terminalWheelStopHandler);
    }
    terminalWheelStopHandler = null;
  }

  async function renderEmptyState() {
    sessions = [];
    selectedSessionId = "";
    renderSessionList();
    syncCurrentSessionMeta();
    await renderTerminalSnapshot();
    startPolling();
  }

  window.addEventListener("beforeunload", stopPolling);

  return {
    prefillToken,
    renderEmptyState,
    refreshSessions,
    createSession,
    sendCtrlC,
    closeSelectedSession,
    selectSession
  };
}
