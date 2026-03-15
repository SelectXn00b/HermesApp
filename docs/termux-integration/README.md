# Termux Integration Guide

本指南介绍如何为 AndroidForClaw 配置 Termux 本地代码执行能力。

## 📌 概述

通过 Termux 集成，AndroidForClaw 可以在本地执行 Python、Node.js 和 Shell 代码，无需远程服务器。

**架构**:
```
AndroidForClaw (Agent)
    ↓ 文件 IPC
Termux (RPC Server)
    ↓ 执行
Python/Node.js/Shell
    ↓ 返回
结果
```

**优势**:
- ✅ 完整 Python/Node.js 生态
- ✅ 本地执行，无需网络
- ✅ APK 体积不增加
- ✅ 用户完全控制环境

## 🚀 快速开始

### 前置要求

- Android 7.0+ (API 24+)
- 存储权限
- ~500MB 可用空间

### 安装步骤

#### 1. 安装 Termux

**⚠️ 重要**: 只能从 F-Droid 或 GitHub 安装，**不要使用 Google Play 版本**（已过时）

**方式 A: F-Droid (推荐)**
1. 安装 F-Droid: https://f-droid.org/
2. 在 F-Droid 中搜索 "Termux"
3. 安装 Termux

**方式 B: GitHub**
1. 访问: https://github.com/termux/termux-app/releases
2. 下载最新 APK (例如: `termux-app_v0.118.0+github-debug_arm64-v8a.apk`)
3. 安装 APK

#### 2. 安装 Termux:API

**⚠️ 必须安装两个应用**: Termux 主应用 + Termux:API 插件

**方式 A: F-Droid**
1. 在 F-Droid 中搜索 "Termux:API"
2. 安装 Termux:API

**方式 B: GitHub**
1. 访问: https://github.com/termux/termux-api/releases
2. 下载最新 APK
3. 安装 APK

#### 3. 配置 Termux

打开 Termux，运行以下命令:

```bash
# 更新包列表
pkg update

# 安装 Python
pkg install python

# 安装 Node.js (可选)
pkg install nodejs

# 安装 Termux:API 命令行工具
pkg install termux-api

# 安装常用 Python 库
pip3 install requests beautifulsoup4 pandas
```

#### 4. 安装 Bridge Server

**方式 A: 自动安装（推荐）**

在 Termux 中运行:
```bash
curl -fsSL https://raw.githubusercontent.com/SelectXn00b/AndroidForClaw/main/docs/termux-integration/install.sh | bash
```

**方式 B: 手动安装**

```bash
# 创建目录
mkdir -p ~/.termux

# 下载服务器脚本
cd ~/.termux
curl -O https://raw.githubusercontent.com/SelectXn00b/AndroidForClaw/main/docs/termux-integration/phoneforclaw_server.py

# 下载启动脚本
curl -O https://raw.githubusercontent.com/SelectXn00b/AndroidForClaw/main/docs/termux-integration/start_bridge.sh

# 设置执行权限
chmod +x phoneforclaw_server.py start_bridge.sh
```

#### 5. 启动 Server

**前台运行**:
```bash
bash ~/.termux/start_bridge.sh
```

**后台运行**:
```bash
nohup python3 ~/.termux/phoneforclaw_server.py > /sdcard/.androidforclaw/.ipc/server.log 2>&1 &
```

**开机自启动** (可选):
```bash
# 创建自启动脚本
cat > ~/.termux/boot/start-bridge.sh <<'EOF'
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
nohup python3 ~/.termux/phoneforclaw_server.py > /sdcard/.androidforclaw/.ipc/server.log 2>&1 &
EOF

chmod +x ~/.termux/boot/start-bridge.sh
```

#### 6. 设置存储权限（重要！）

为了让 Termux 访问 `/sdcard/` 文件，需要设置存储权限：

**方式 A: 通过 Agent**
```
请设置 Termux 存储权限
```

Agent 会调用:
```javascript
exec({
    action: "setup_storage"
})
```

**方式 B: 手动在 Termux 中**
```bash
termux-setup-storage
```

授权后，Termux 可以访问：
- `/sdcard/` - 共享存储
- `~/storage/shared/` - 指向 /sdcard/ 的链接
- `~/storage/downloads/` - 下载文件夹
- `~/storage/dcim/` - 相机照片

#### 7. 测试

**基础测试**:
```
帮我用 Python 打印 "Hello from Termux!"
```

Agent 会调用:
```javascript
exec({
    runtime: "python",
    code: "print('Hello from Termux!')"
})
```

如果输出 `Hello from Termux!`，说明配置成功！

**文件访问测试**:
```
列出我的下载文件夹有哪些文件
```

Agent 会调用:
```javascript
exec({
    runtime: "python",
    code: `
import os
files = os.listdir('/sdcard/Download')
for f in files[:10]:
    print(f)
    `
})
```

## 📖 使用示例

### 存储访问示例

**访问相机照片**:
```python
exec({
    runtime: "python",
    code: `
import os
from PIL import Image

# 访问 DCIM 文件夹
dcim = '/sdcard/DCIM/Camera'
photos = [f for f in os.listdir(dcim) if f.endswith('.jpg')]

print(f"找到 {len(photos)} 张照片")

# 显示最新照片信息
if photos:
    latest = sorted(photos)[-1]
    img = Image.open(os.path.join(dcim, latest))
    print(f"最新照片: {latest}")
    print(f"尺寸: {img.size}")
    print(f"格式: {img.format}")
    `
})
```

**处理下载文件**:
```python
exec({
    runtime: "python",
    code: `
import os

downloads = '/sdcard/Download'
files = os.listdir(downloads)

# 统计文件类型
extensions = {}
for f in files:
    ext = f.split('.')[-1].lower()
    extensions[ext] = extensions.get(ext, 0) + 1

print("文件类型统计:")
for ext, count in sorted(extensions.items()):
    print(f"  {ext}: {count} 个")
    `
})
```

### Python 示例

**数据处理**:
```python
exec({
    runtime: "python",
    code: `
import pandas as pd

data = [
    {"name": "Alice", "score": 95},
    {"name": "Bob", "score": 87},
    {"name": "Charlie", "score": 92}
]

df = pd.DataFrame(data)
print(df.to_string())
print(f"\nAverage: {df['score'].mean():.1f}")
    `
})
```

**网络请求**:
```python
exec({
    runtime: "python",
    code: `
import requests

resp = requests.get('https://api.github.com/repos/SelectXn00b/AndroidForClaw')
data = resp.json()
print(f"⭐ Stars: {data['stargazers_count']}")
print(f"🍴 Forks: {data['forks_count']}")
    `
})
```

### Node.js 示例

**文件处理**:
```javascript
exec({
    runtime: "nodejs",
    code: `
const fs = require('fs');
const path = require('path');

const downloadDir = '/sdcard/Download';
const files = fs.readdirSync(downloadDir);

console.log(\`Found \${files.length} files:\`);
files.slice(0, 10).forEach(f => {
    const stats = fs.statSync(path.join(downloadDir, f));
    const sizeMB = (stats.size / 1024 / 1024).toFixed(2);
    console.log(\`- \${f} (\${sizeMB}MB)\`);
});
    `
})
```

### Shell 示例

**系统信息**:
```bash
exec({
    runtime: "shell",
    code: `
echo "=== CPU Info ==="
cat /proc/cpuinfo | grep "model name" | head -1

echo ""
echo "=== Memory ==="
free -h

echo ""
echo "=== Storage ==="
df -h /sdcard | tail -1
    `
})
```

## 🔧 故障排查

### 问题 1: "Termux not installed"

**解决**:
- 安装 Termux (从 F-Droid 或 GitHub)
- 不要使用 Google Play 版本

### 问题 2: "Termux:API not installed"

**解决**:
1. 安装 Termux:API 应用 (APK)
2. 在 Termux 中运行: `pkg install termux-api`

### 问题 3: "Server not running"

**解决**:
```bash
# 检查服务器进程
ps aux | grep phoneforclaw_server

# 如果没有运行，启动服务器
bash ~/.termux/start_bridge.sh

# 或后台运行
nohup python3 ~/.termux/phoneforclaw_server.py > /sdcard/.androidforclaw/.ipc/server.log 2>&1 &
```

### 问题 4: "ModuleNotFoundError"

**解决**:
```bash
# 安装缺失的 Python 包
pip3 install <package-name>

# 例如
pip3 install requests beautifulsoup4 pandas numpy
```

### 问题 5: 超时

**解决**:
- 增加 timeout 参数:
  ```javascript
  exec({
      runtime: "python",
      code: "...",
      timeout: 300  // 5 分钟
  })
  ```

### 问题 6: 服务器日志

查看日志:
```bash
# 查看实时日志
tail -f /sdcard/.androidforclaw/.ipc/server.log

# 查看最近 50 行
tail -50 /sdcard/.androidforclaw/.ipc/server.log
```

### 问题 7: 权限问题

如果无法访问 `/sdcard/`:

**方式 A: 通过 Agent**
```
请设置 Termux 存储权限
```

**方式 B: 手动设置**
```bash
# 在 Termux 中请求存储权限
termux-setup-storage

# 在弹出的对话框中允许权限
```

**验证权限**:
```bash
# 检查是否可以访问
ls /sdcard/Download
ls ~/storage/
```

## 🔒 安全说明

### 隔离机制

- Termux 运行在独立沙箱中
- 只能访问 Termux home 和 `/sdcard/`
- 无法访问其他应用数据
- 无 root 权限

### 文件访问

Bridge Server 可以访问:
- ✅ `/sdcard/` (共享存储)
- ✅ `/data/data/com.termux/` (Termux home)
- ❌ `/data/data/com.xiaomo.androidforclaw/` (AndroidForClaw 私有数据)

### 代码执行

- 所有代码在 Termux 沙箱中执行
- 用户可以查看和修改服务器脚本
- 支持自定义沙箱策略

## 📊 性能

| 操作 | 延迟 |
|------|------|
| 简单 Python 脚本 | ~0.5-1s |
| Python import (首次) | ~1-2s |
| Node.js 执行 | ~0.3-0.5s |
| Shell 命令 | ~0.1-0.3s |
| 文件 IPC 轮询 | ~0.5s |

## 🔄 更新

**更新 Bridge Server**:
```bash
cd ~/.termux
curl -O https://raw.githubusercontent.com/SelectXn00b/AndroidForClaw/main/docs/termux-integration/phoneforclaw_server.py
```

**更新 Termux**:
```bash
pkg update
pkg upgrade
```

## 💡 高级用法

### 自定义工作目录

```javascript
exec({
    runtime: "python",
    code: "print(__file__)",
    cwd: "/sdcard/MyProject"
})
```

### 环境变量

在 Termux 中设置:
```bash
echo 'export MY_API_KEY="xxx"' >> ~/.bashrc
source ~/.bashrc
```

在代码中使用:
```python
import os
api_key = os.environ.get('MY_API_KEY')
```

### 长时间运行任务

使用后台任务:
```javascript
// 1. 启动后台任务
exec({
    runtime: "python",
    code: `
import time
with open('/sdcard/task.log', 'w') as f:
    for i in range(100):
        f.write(f"Progress: {i}%\n")
        f.flush()
        time.sleep(1)
    f.write("Done!\n")
    `,
    timeout: 300
})

// 2. 轮询检查进度
exec({
    runtime: "shell",
    code: "tail -1 /sdcard/task.log"
})
```

## 📚 参考资料

- [Termux Wiki](https://wiki.termux.com/)
- [Termux:API Documentation](https://wiki.termux.com/wiki/Termux:API)
- [Python in Termux](https://wiki.termux.com/wiki/Python)
- [Node.js in Termux](https://wiki.termux.com/wiki/Node.js)
- [AndroidForClaw Docs](https://github.com/SelectXn00b/AndroidForClaw)

## 🤝 贡献

欢迎提交 Issue 和 PR！

- GitHub: https://github.com/SelectXn00b/AndroidForClaw
- Issues: https://github.com/SelectXn00b/AndroidForClaw/issues

## 📄 许可

MIT License
