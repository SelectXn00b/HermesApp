const packageJson = require("../../package.json");

const AGENT_VERSION = String(packageJson.version || "0.0.0").trim() || "0.0.0";

module.exports = {
  AGENT_VERSION
};
