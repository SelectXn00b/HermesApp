@echo off
setlocal ENABLEDELAYEDEXPANSION

rem === 0. 检查 adb 是否可用 ===
adb version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] adb not found in PATH.
    echo 请先安装 Android Platform Tools 并把 adb 加入 PATH 环境变量。
    exit /b 1
)

rem === 1. 切换到 tools\shower 并编译 Shower ===
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%shower"

echo [1/4] Building Shower (assembleDebug)...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo [ERROR] Gradle build failed.
    exit /b 1
)

rem === 2. 查找生成的 APK ===
set APK_DIR=app\build\outputs\apk\debug
set APK_PATH=
for /f "delims=" %%F in ('dir /b "%APK_DIR%\*.apk" 2^>nul') do (
    set APK_PATH=%APK_DIR%\%%F
    goto :apk_found
)

echo [ERROR] 未在 %APK_DIR% 下找到 APK 文件，请检查构建输出。
exit /b 1

:apk_found
echo [INFO] 使用 APK: %APK_PATH%

rem === 3. 推送到设备 /data/local/tmp 并重命名为 .jar ===
set REMOTE_DIR=/data/local/tmp
set REMOTE_JAR=shower-server.jar

echo [2/4] Pushing APK as %REMOTE_JAR% to %REMOTE_DIR% ...
adb push "%APK_PATH%" "%REMOTE_DIR%/%REMOTE_JAR%"
if errorlevel 1 (
    echo [ERROR] adb push 失败，请确认设备已连接并授权。
    exit /b 1
)

rem === 4. 使用 app_process 在 adb 环境中启动 Shower server ===
set REMOTE_CLASSPATH=%REMOTE_DIR%/%REMOTE_JAR%

echo [3/4] Killing existing Shower server (if any) ...
adb shell "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"

echo [4/4] Starting Shower server via app_process ...
rem 与 scrcpy 类似：CLASSPATH=/data/local/tmp/shower-server.jar app_process / com.ai.assistance.shower.Main
echo adb shell "CLASSPATH=%REMOTE_CLASSPATH% app_process / com.ai.assistance.shower.Main &"
adb shell "CLASSPATH=%REMOTE_CLASSPATH% app_process / com.ai.assistance.shower.Main &"
if errorlevel 1 (
    echo [ERROR] 无法通过 app_process 启动 Shower server。
    exit /b 1
)

echo [4/4] Shower server 已在设备后台尝试启动（若无错误信息）。
echo 现在可以从上位机连接 ws://<device>:8986 ，发送例如:

echo   CREATE_DISPLAY 1080 1920 320

echo 或之后发送:

echo   STOP

echo 来测试虚拟屏幕创建和销毁逻辑。

echo.
echo 若需要停止该进程，可在設備上通過 WebSocket 發送 STOP 或使用:
echo   adb shell pkill -f com.ai.assistance.shower.Main

echo.
endlocal
