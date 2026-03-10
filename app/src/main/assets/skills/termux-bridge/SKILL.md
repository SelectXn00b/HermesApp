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

**User must have installed**:
1. Termux (from F-Droid or GitHub)
2. Termux:API plugin
3. PhoneForClaw Bridge Server running in Termux

**Setup**: See [Installation Guide](#installation-guide)

## Available Tool

### termux_exec

Execute code in Termux environment or manage Termux settings.

**Parameters**:
- `action` (optional): Action type - "exec" (default) or "setup_storage"
- `runtime` (required for exec): "python" | "nodejs" | "shell"
- `code` (required for exec): Code string to execute
- `cwd` (optional): Working directory (default: Termux home)
- `timeout` (optional): Timeout in seconds (default: 60)

**Returns**:
- `stdout`: Standard output
- `stderr`: Standard error (if any)
- `returncode`: Exit code (0 = success)

## Usage Examples

### Setup Storage Access (First Time)

Before accessing `/sdcard/` files, setup storage permissions:

```javascript
termux_exec({
    action: "setup_storage"
})
```

This will:
1. Trigger Termux permission dialog
2. User grants storage access
3. Termux can now access:
   - `/sdcard/` - Shared storage
   - `~/storage/shared/` - Link to /sdcard/
   - `~/storage/downloads/` - Downloads folder
   - `~/storage/dcim/` - Camera photos

**After setup, you can access files**:

```javascript
termux_exec({
    runtime: "python",
    code: `
import os
# Access shared storage
files = os.listdir('/sdcard/Download')
print(f"Found {len(files)} files in Downloads")

# Or use symbolic link
files = os.listdir('~/storage/downloads')
    `
})
```

### Python

```javascript
termux_exec({
    runtime: "python",
    code: `
import sys
print(f"Python {sys.version}")
print("Hello from Termux!")
    `
})
```

**Web scraping**:
```javascript
termux_exec({
    runtime: "python",
    code: `
import requests
from bs4 import BeautifulSoup

resp = requests.get('https://example.com')
soup = BeautifulSoup(resp.text, 'html.parser')
print(soup.title.string)
    `
})
```

**Data processing**:
```javascript
termux_exec({
    runtime: "python",
    code: `
import pandas as pd
import json

data = [
    {"name": "Alice", "age": 30},
    {"name": "Bob", "age": 25}
]

df = pd.DataFrame(data)
print(df.to_string())
    `
})
```

### Node.js

```javascript
termux_exec({
    runtime: "nodejs",
    code: `
const os = require('os');
console.log('Platform:', os.platform());
console.log('Node:', process.version);
    `
})
```

**File operations**:
```javascript
termux_exec({
    runtime: "nodejs",
    code: `
const fs = require('fs');
const path = require('path');

const files = fs.readdirSync('/sdcard/Download');
console.log('Downloaded files:', files.length);
files.slice(0, 5).forEach(f => console.log('-', f));
    `
})
```

### Shell

```javascript
termux_exec({
    runtime: "shell",
    code: "ls -lh /sdcard/Download | head -10"
})
```

**System info**:
```javascript
termux_exec({
    runtime: "shell",
    code: `
echo "=== System Info ==="
uname -a
echo ""
echo "=== Memory ==="
free -h
echo ""
echo "=== Storage ==="
df -h /sdcard
    `
})
```

## Common Patterns

### 1. Setup storage on first use

```javascript
// First time setup - only need to do once
termux_exec({
    action: "setup_storage"
})

// Wait for user to grant permission (check in UI)
// Then you can access files
```

### 2. Access Android files from Python

```javascript
// Read a file from Downloads
termux_exec({
    runtime: "python",
    code: `
# Method 1: Direct path
with open('/sdcard/Download/data.csv') as f:
    data = f.read()

# Method 2: Using storage link (after setup_storage)
with open('~/storage/downloads/data.csv') as f:
    data = f.read()

print(f"Read {len(data)} bytes")
    `
})
```

### 3. Process images from Camera

```javascript
termux_exec({
    runtime: "python",
    code: `
from PIL import Image
import os

# Access DCIM folder
dcim = '/sdcard/DCIM/Camera'
photos = [f for f in os.listdir(dcim) if f.endswith('.jpg')]

print(f"Found {len(photos)} photos")

# Process first photo
if photos:
    img = Image.open(os.path.join(dcim, photos[0]))
    print(f"Image size: {img.size}")
    `
})
```

### 4. Check if Termux is ready

Before using termux_exec, check if the environment is ready:

```javascript
// Use termux_exec with a simple ping
const result = termux_exec({
    runtime: "python",
    code: "print('ready')"
})

// If successful, environment is ready
// If error, guide user through setup
```

### 5. Install dependencies

```javascript
// Install Python package
termux_exec({
    runtime: "shell",
    code: "pip3 install requests beautifulsoup4"
})

// Install Node.js package
termux_exec({
    runtime: "shell",
    code: "npm install -g axios cheerio"
})
```

### 6. Multi-step tasks

```javascript
// Step 1: Download data
termux_exec({
    runtime: "python",
    code: `
import requests
data = requests.get('https://api.example.com/data.json').json()
with open('/sdcard/data.json', 'w') as f:
    f.write(str(data))
    `
})

// Step 2: Process data
termux_exec({
    runtime: "python",
    code: `
import json
with open('/sdcard/data.json') as f:
    data = json.load(f)
# Process data...
print(f"Processed {len(data)} items")
    `
})
```

### 7. Error handling

```javascript
const result = termux_exec({
    runtime: "python",
    code: "import nonexistent_module"
})

// Check result
if (result.includes("Error:") || result.includes("ModuleNotFoundError")) {
    // Handle error - maybe install missing package
    termux_exec({
        runtime: "shell",
        code: "pip3 install <missing-package>"
    })
}
```

## Important Notes

### Limitations

1. **No real-time I/O**: Cannot handle interactive input (stdin)
2. **File-based communication**: Uses shared files for IPC
3. **Polling delay**: ~0.5-1s latency for request/response
4. **Timeout**: Default 60s, configurable per request

### Security

1. **Sandboxed**: Termux runs in its own sandbox
2. **Storage access**: Can access `/sdcard/` and Termux home
3. **No root**: Cannot execute privileged operations
4. **User control**: User manages Termux environment

### Performance

- **Cold start**: ~1-2s for Python/Node.js import
- **Warm execution**: ~100-500ms for simple scripts
- **Heavy tasks**: Use appropriate timeout (e.g., 300s for large file processing)

### Best Practices

1. **Check environment first**: Test with simple "ping" before complex operations
2. **Handle missing packages**: Catch import errors and guide user to install
3. **Use timeouts**: Set realistic timeout for long-running tasks
4. **Clean up files**: Delete temporary files after use
5. **Stream large data**: For large results, write to file instead of stdout

## Troubleshooting

### "Termux not installed"

User needs to install Termux:
- F-Droid: https://f-droid.org/packages/com.termux/
- GitHub: https://github.com/termux/termux-app/releases

### "Termux:API not installed"

User needs to install Termux:API plugin:
- F-Droid: https://f-droid.org/packages/com.termux.api/
- GitHub: https://github.com/termux/termux-api/releases

Then in Termux:
```bash
pkg install termux-api
```

### "Server not running"

User needs to start the bridge server:
```bash
bash ~/.termux/start_bridge.sh
```

### "Module not found"

Install missing Python/Node packages:
```bash
# Python
pip3 install <package>

# Node.js
npm install -g <package>
```

### Timeout

Increase timeout for long-running tasks:
```javascript
termux_exec({
    runtime: "python",
    code: "...",
    timeout: 300  // 5 minutes
})
```

## Installation Guide

### Step 1: Install Termux

Download and install:
- **F-Droid** (recommended): https://f-droid.org/packages/com.termux/
- **GitHub**: https://github.com/termux/termux-app/releases

**⚠️ Do NOT use Google Play version** (outdated)

### Step 2: Install Termux:API

Download and install:
- **F-Droid**: https://f-droid.org/packages/com.termux.api/
- **GitHub**: https://github.com/termux/termux-api/releases

Then in Termux:
```bash
pkg install termux-api
```

### Step 3: Install Base Dependencies

In Termux:
```bash
# Update package lists
pkg update

# Install Python
pkg install python

# Install Node.js (optional)
pkg install nodejs

# Install common Python packages
pip3 install requests beautifulsoup4 pandas
```

### Step 4: Install Bridge Server

Download server script:
```bash
# Create directory
mkdir -p ~/.termux

# Download server script
cd ~/.termux
curl -O https://raw.githubusercontent.com/xiaomochn/AndroidForClaw/main/docs/termux-integration/phoneforclaw_server.py

# Download start script
curl -O https://raw.githubusercontent.com/xiaomochn/AndroidForClaw/main/docs/termux-integration/start_bridge.sh

# Make executable
chmod +x start_bridge.sh
```

### Step 5: Start Server

```bash
bash ~/.termux/start_bridge.sh
```

**Run in background**:
```bash
nohup python3 ~/.termux/phoneforclaw_server.py > ~/.termux/server.log 2>&1 &
```

### Step 6: Test

In AndroidForClaw, test with:
```javascript
termux_exec({
    runtime: "python",
    code: "print('Hello from Termux!')"
})
```

Should output: `Hello from Termux!`

## Comparison with Other Tools

| Feature | termux_exec | javascript_exec | exec |
|---------|-------------|-----------------|------|
| Python | ✅ Full Python 3 | ❌ | ❌ |
| Node.js | ✅ Full Node.js | ❌ | ❌ |
| npm/pip | ✅ | ❌ | ❌ |
| Network | ✅ | ✅ (limited) | ❌ |
| File I/O | ✅ Full | ✅ Limited | ✅ Limited |
| Performance | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| Setup | Requires Termux | Built-in | Built-in |

**Use termux_exec when**:
- Need Python/Node.js ecosystem
- Complex data processing
- Web scraping
- External libraries

**Use javascript_exec when**:
- Simple JS operations
- No external dependencies
- Fastest execution

**Use exec when**:
- Android shell commands
- File operations
- System info

## References

- [Termux Wiki](https://wiki.termux.com/)
- [Termux:API](https://wiki.termux.com/wiki/Termux:API)
- [Python in Termux](https://wiki.termux.com/wiki/Python)
- [Node.js in Termux](https://wiki.termux.com/wiki/Node.js)
