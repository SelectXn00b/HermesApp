const fs = require("fs");
const { ensureDir } = require("../lib/fs-utils");

function createStartupStateStore({ dataDir, startupStatePath }) {
  function loadState() {
    ensureDir(dataDir);
    if (!fs.existsSync(startupStatePath)) {
      return null;
    }

    try {
      const raw = fs.readFileSync(startupStatePath, "utf8");
      if (!raw.trim()) {
        return null;
      }
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  function saveState(state) {
    ensureDir(dataDir);
    fs.writeFileSync(startupStatePath, JSON.stringify(state, null, 2), "utf8");
  }

  function clearState() {
    try {
      fs.unlinkSync(startupStatePath);
    } catch {
      // ignore
    }
  }

  return {
    loadState,
    saveState,
    clearState
  };
}

module.exports = {
  createStartupStateStore
};
