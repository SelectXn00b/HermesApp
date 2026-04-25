const DEFAULT_CONFIG = {
  bindAddress: "127.0.0.1",
  port: 58321,
  apiToken: "",
  maxCommandMs: 30000,
  allowedPresets: ["health_probe", "hostname", "whoami", "list_processes"]
};

const PRESET_COMMANDS = {
  health_probe: {
    shell: "powershell",
    command: "$PSVersionTable.PSVersion.ToString(); [Environment]::MachineName; whoami; (Get-Date).ToString('o')",
    description: "PowerShell version, machine name, user, current time"
  },
  hostname: {
    shell: "cmd",
    command: "hostname",
    description: "Show host name"
  },
  whoami: {
    shell: "cmd",
    command: "whoami",
    description: "Show current user"
  },
  list_processes: {
    shell: "powershell",
    command: "Get-Process | Select-Object -First 12 Name,Id,CPU,WS",
    description: "List top process info"
  }
};

const STATIC_CONTENT_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".gif": "image/gif",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".woff": "font/woff",
  ".woff2": "font/woff2",
  ".ttf": "font/ttf"
};

module.exports = {
  DEFAULT_CONFIG,
  PRESET_COMMANDS,
  STATIC_CONTENT_TYPES
};
