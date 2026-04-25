@echo off
setlocal ENABLEDELAYEDEXPANSION

rem === 0. 检查 adb 是否可用 ===
adb version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] adb not found in PATH.
    echo 请先安装 Android Platform Tools 并把 adb 加入 PATH 环境变量。
    exit /b 1
)

rem === 1. 在设备上杀掉 Shower server 进程 ===
echo [INFO] Killing Shower server process on device...
adb shell "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"
if errorlevel 1 (
    echo [ERROR] 无法发送 kill 命令，请确认设备已连接并授权调试。
    exit /b 1
)

echo [OK] 已发送 kill 命令，Shower server 进程应已被终止（如果存在）。

echo.
endlocal
