---
name: termux-bridge
description: Execute Python, Node.js, and Shell code locally via Termux
metadata:
  {
    "openclaw": {
      "always": false,
      "emoji": "🐧"
    }
  }
---

# Termux Bridge - Local Code Execution

Execute Python, Node.js, and Shell scripts locally on the Android device using Termux.

## Requirements

- Termux installed (F-Droid or GitHub)
- Connection is auto-configured on first use

## Tool: exec

Run commands in Termux. Auto-routes when Termux is available; falls back to internal Android shell otherwise.

**Parameters**:
- `command` (required): Shell command to execute
- `working_dir` (optional): Working directory
- `timeout` (optional): Timeout in seconds (default: 60)
- `backend` (optional): "auto" | "termux" | "internal"

**Returns**: stdout, stderr, exitCode

## Examples

```javascript
exec({ command: "python3 --version && node --version" })
exec({ command: "pip3 install requests && python3 -c 'import requests; print(requests.get(\"https://httpbin.org/ip\").text)'" })
exec({ command: "ls -la /sdcard/Download | head -10" })
exec({ command: "npm install -g axios", working_dir: "/data/data/com.termux/files/home" })
```

## Notes

- First execution may take a few seconds while auto-configuring
- Termux runs in its own sandbox, can access /sdcard/
- No root required
- If Termux is not ready, guide user to open Termux app once
