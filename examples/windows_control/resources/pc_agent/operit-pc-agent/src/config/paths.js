const path = require("path");

const PROJECT_ROOT = path.resolve(__dirname, "..", "..");
const PUBLIC_DIR = path.join(PROJECT_ROOT, "public");
const DATA_DIR = path.join(PROJECT_ROOT, "data");
const SCRIPTS_DIR = path.join(PROJECT_ROOT, "scripts");
const LOGS_DIR = path.join(PROJECT_ROOT, "logs");

const CONFIG_PATH = path.join(DATA_DIR, "config.json");
const RUNTIME_PATH = path.join(DATA_DIR, "runtime.json");
const STARTUP_STATE_PATH = path.join(DATA_DIR, "startup_state.json");
const RUNTIME_LOG_PATH = path.join(LOGS_DIR, "agent.runtime.log");

module.exports = {
  PROJECT_ROOT,
  PUBLIC_DIR,
  DATA_DIR,
  SCRIPTS_DIR,
  LOGS_DIR,
  CONFIG_PATH,
  RUNTIME_PATH,
  STARTUP_STATE_PATH,
  RUNTIME_LOG_PATH
};
