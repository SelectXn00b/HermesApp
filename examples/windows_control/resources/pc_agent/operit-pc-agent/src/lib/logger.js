const fs = require("fs");
const { ensureDir, safeJson } = require("./fs-utils");

function createRuntimeLogger({ logsDir, runtimeLogPath }) {
  function writeRuntimeLog(level, message, meta) {
    const suffix = meta !== undefined ? " " + safeJson(meta) : "";
    const line = `${new Date().toISOString()} [${level}] ${message}${suffix}`;

    try {
      ensureDir(logsDir);
      fs.appendFileSync(runtimeLogPath, line + "\n", "utf8");
    } catch {
      // ignore logging file errors
    }

    if (level === "ERROR") {
      console.error(line);
    } else {
      console.log(line);
    }
  }

  return {
    info(message, meta) {
      writeRuntimeLog("INFO", message, meta);
    },
    warn(message, meta) {
      writeRuntimeLog("WARN", message, meta);
    },
    error(message, meta) {
      writeRuntimeLog("ERROR", message, meta);
    }
  };
}

module.exports = {
  createRuntimeLogger
};
