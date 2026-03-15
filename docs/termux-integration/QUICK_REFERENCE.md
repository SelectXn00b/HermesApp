# Termux Integration - Quick Reference

## 🚀 Setup (One-time)

### 1. Install Termux
- F-Droid: https://f-droid.org/packages/com.termux/
- GitHub: https://github.com/termux/termux-app/releases

### 2. Install Termux:API
- F-Droid: https://f-droid.org/packages/com.termux.api/
- GitHub: https://github.com/termux/termux-api/releases

### 3. Setup Termux
```bash
pkg update
pkg install python nodejs termux-api
pip3 install requests beautifulsoup4 pandas
```

### 4. Install Bridge Server
```bash
cd ~/.termux
curl -O https://raw.githubusercontent.com/SelectXn00b/AndroidForClaw/main/docs/termux-integration/phoneforclaw_server.py
curl -O https://raw.githubusercontent.com/SelectXn00b/AndroidForClaw/main/docs/termux-integration/start_bridge.sh
chmod +x start_bridge.sh
```

### 5. Start Server
```bash
bash ~/.termux/start_bridge.sh
```

### 6. Setup Storage Access
**In AndroidForClaw, tell Agent**:
```
请设置 Termux 存储权限
```

Or manually in Termux:
```bash
termux-setup-storage
```

---

## 📖 Usage

### Execute Python
```javascript
exec({
    runtime: "python",
    code: "print('Hello World')"
})
```

### Execute Node.js
```javascript
exec({
    runtime: "nodejs",
    code: "console.log('Hello World')"
})
```

### Execute Shell
```javascript
exec({
    runtime: "shell",
    code: "ls -lh /sdcard/Download"
})
```

### Access Android Files
```javascript
exec({
    runtime: "python",
    code: `
import os
files = os.listdir('/sdcard/Download')
print(f"Found {len(files)} files")
    `
})
```

### Install Package
```javascript
exec({
    runtime: "shell",
    code: "pip3 install numpy"
})
```

---

## 🗂️ File Access

After `termux-setup-storage`, Termux can access:

| Path | Description |
|------|-------------|
| `/sdcard/` | Shared storage |
| `/sdcard/Download/` | Downloads |
| `/sdcard/DCIM/Camera/` | Camera photos |
| `/sdcard/Pictures/` | Pictures |
| `/sdcard/Documents/` | Documents |
| `~/storage/shared/` | Link to /sdcard/ |
| `~/storage/downloads/` | Link to Downloads |
| `~/storage/dcim/` | Link to DCIM |

---

## 🔧 Common Tasks

### Web Scraping
```javascript
exec({
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

### Data Processing
```javascript
exec({
    runtime: "python",
    code: `
import pandas as pd

df = pd.read_csv('/sdcard/Download/data.csv')
print(df.describe())
    `
})
```

### Image Processing
```javascript
exec({
    runtime: "python",
    code: `
from PIL import Image

img = Image.open('/sdcard/DCIM/Camera/photo.jpg')
img.thumbnail((800, 800))
img.save('/sdcard/Pictures/resized.jpg')
print(f"Resized to {img.size}")
    `
})
```

### File Management
```javascript
exec({
    runtime: "shell",
    code: "find /sdcard/Download -name '*.pdf' -type f"
})
```

---

## 🐛 Troubleshooting

### Server not running
```bash
bash ~/.termux/start_bridge.sh
```

### Permission denied on /sdcard/
```bash
termux-setup-storage
# Grant permission in popup
```

### Module not found
```bash
pip3 install <package>
```

### Check server status
```bash
ps aux | grep phoneforclaw_server
tail -f /sdcard/.androidforclaw/.ipc/server.log
```

---

## 💡 Tips

1. **Long-running tasks**: Use `&` to run in background
   ```bash
   nohup python script.py > output.log 2>&1 &
   ```

2. **Environment variables**: Set in `~/.bashrc`
   ```bash
   echo 'export OPENAI_API_KEY="sk-xxx"' >> ~/.bashrc
   source ~/.bashrc
   ```

3. **Git operations**: Just use git directly
   ```bash
   git clone https://github.com/user/repo.git
   cd repo
   python main.py
   ```

4. **HTTP server**: For quick preview
   ```bash
   cd /sdcard/website
   python3 -m http.server 8080
   ```

---

## 📚 Resources

- [Full Documentation](README.md)
- [Skill Reference](../../app/src/main/assets/skills/termux-bridge/SKILL.md)
- [Termux Wiki](https://wiki.termux.com/)
- [Termux:API Docs](https://wiki.termux.com/wiki/Termux:API)
