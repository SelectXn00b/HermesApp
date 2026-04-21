# Operit PC Agent (Windows)

Windows side helper project for Operit.

- Local config UI: `http://127.0.0.1:58321`
- Single-entry launcher: `operit_pc_agent.bat`
- HTTP relay for mobile side `windows_control`
- Preset and raw command execution endpoints

## Quick Start

1. Install Node.js 18+ on Windows.
2. Double click `operit_pc_agent.bat`.
3. It will auto-clean previous process, start service, and open browser.
4. In UI, complete relay verification and copy config text for mobile side paste.

## Entry and Scripts

- `operit_pc_agent.bat`: only user-facing launcher bat
- `scripts/launch_agent.ps1`: internal launcher logic (cleanup + start + open UI)
- (No OpenSSH dependency in current HTTP relay mode)

## Logs

- `logs/launcher.log`: launcher flow
- `logs/agent.runtime.log`: runtime server logs (HTTP/API/process)
- `logs/agent.out.log`: Node stdout stream
- `logs/agent.err.log`: Node stderr stream

## Web UI Structure

- `public/index.html`: page entry
- `public/styles/tokens.css`: design tokens
- `public/styles/base.css`: layout and responsive rules
- `public/styles/components.css`: reusable UI component styles
- `public/scripts/main.js`: declarative layout tree and event wiring
- `public/scripts/ui/runtime.js`: lightweight declarative renderer
- `public/scripts/ui/widgets.js`: reusable widgets (layout + controls)
- `public/scripts/i18n/strings.js`: centralized i18n resources (zh/en)
- `public/scripts/services/api.js`: API request layer

## Backend Structure

- `src/server.js`: composition root (wiring + lifecycle)
- `src/config/paths.js`: project path constants
- `src/config/constants.js`: default config, presets, static MIME map
- `src/lib/logger.js`: runtime logger (`logs/agent.runtime.log`)
- `src/lib/http-utils.js`: JSON body parse, response helpers, static file serving
- `src/stores/config-store.js`: config load/save and preset normalization
- `src/stores/runtime-store.js`: runtime.json write/remove
- `src/services/process-service.js`: process execution, network/user snapshot
- `src/handlers/api-handler.js`: all `/api/*` route handling

## API (local)

Public read endpoints:
- `GET /api/health`
- `GET /api/config`
- `GET /api/presets`
- `GET /api/startup/state`

Config / command endpoint:
- `POST /api/config`
- `POST /api/command/execute` (requires `token`)
- `POST /api/startup/apply_recommended_bind`

File endpoints (all require `token` in JSON body):
- `POST /api/file/list`
  - body: `{ "token": "...", "path": "D:/..." | "relative/path", "depth": 1 }`
- `POST /api/file/read`
  - body: `{ "token": "...", "path": "...", "encoding": "utf8" }`
- `POST /api/file/read_segment`
  - body: `{ "token": "...", "path": "...", "offset": 0, "length": 65536, "encoding": "utf8" }`
- `POST /api/file/write`
  - body: `{ "token": "...", "path": "...", "content": "...", "encoding": "utf8" }`
- `POST /api/file/edit`
  - body: `{ "token": "...", "path": "...", "old_text": "...", "new_text": "...", "expected_replacements": 1, "encoding": "utf8" }`
- `POST /api/file/read_base64`
  - body: `{ "token": "...", "path": "...", "offset": 0, "length": 1024 }`
- `POST /api/file/write_base64`
  - body: `{ "token": "...", "path": "...", "base64": "..." }`

## Security Notes

- Default bind address is `127.0.0.1`.
- `apiToken` is always enabled. If missing, agent auto-generates one.
- In wizard one-click fill, token is generated only when missing (existing token is reused).

## Troubleshooting

- If browser does not open:
  1. Confirm Node.js is installed (`node -v`).
  2. Check `logs/launcher.log`.
  3. Check `logs/agent.err.log` and `logs/agent.runtime.log`.
  4. Double click `operit_pc_agent.bat` again (it auto-cleans old process).
- If configured `bindAddress` is no longer available (for example LAN IP changed):
  1. Launcher will start temporary local mode on `127.0.0.1`.
  2. Opened web console shows startup recovery panel.
  3. Click the web button to apply recommended IPv4 and auto-restart.
