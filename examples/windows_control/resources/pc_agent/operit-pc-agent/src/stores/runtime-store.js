const fs = require("fs");
const { ensureDir } = require("../lib/fs-utils");

function createRuntimeStore({ dataDir, runtimePath }) {
  function writeRuntimeFile({ port, pid, host }) {
    ensureDir(dataDir);
    const data = {
      pid,
      startedAt: new Date().toISOString(),
      port,
      host
    };
    fs.writeFileSync(runtimePath, JSON.stringify(data, null, 2), "utf8");
  }

  function removeRuntimeFile() {
    try {
      if (fs.existsSync(runtimePath)) {
        fs.unlinkSync(runtimePath);
      }
    } catch {
      // ignore cleanup errors
    }
  }

  return {
    writeRuntimeFile,
    removeRuntimeFile
  };
}

module.exports = {
  createRuntimeStore
};
