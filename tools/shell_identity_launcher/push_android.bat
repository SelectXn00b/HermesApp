@echo off
setlocal enabledelayedexpansion

rem Directory where this script lives (normalize without trailing backslash)
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

rem === Build and push Shower server (参考 run_shower_server.bat) ===
set "SHOWER_DIR=%SCRIPT_DIR%\..\shower"
set "SHOWER_LOG=/data/local/tmp/shower.log"
if not exist "%SHOWER_DIR%\gradlew.bat" (
    echo [ERROR] Could not find Shower project at %SHOWER_DIR%.
    goto :error
)

echo [1/4] Building Shower (assembleDebug)...
pushd "%SHOWER_DIR%"
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo [ERROR] Gradle build failed.
    popd
    goto :error
)

set "APK_DIR=app\build\outputs\apk\debug"
set "APK_PATH="
for /f "delims=" %%F in ('dir /b "%APK_DIR%\*.apk" 2^>nul') do (
    set "APK_PATH=%APK_DIR%\%%F"
    goto :apk_found
)

echo [ERROR] No APK found in %APK_DIR%.
popd
goto :error

:apk_found
echo [INFO] Using APK: %APK_PATH%
set "REMOTE_DIR=/data/local/tmp"
set "REMOTE_JAR=shower-server.jar"
set "REMOTE_CLASSPATH=%REMOTE_DIR%/%REMOTE_JAR%"

echo [2/4] Pushing APK as %REMOTE_JAR% to %REMOTE_DIR% ...
adb push "%APK_PATH%" "%REMOTE_DIR%/%REMOTE_JAR%"
if errorlevel 1 (
    echo [ERROR] Failed to push Shower server jar.
    popd
    goto :error
)

popd

rem === Build the native launcher and copy it into app assets ===
call "%SCRIPT_DIR%\build_android.bat"
if errorlevel 1 goto :error

set "BINARY=%SCRIPT_DIR%\build\operit_shell_exec"
set "DEVICE_PATH=/data/local/tmp/operit_shell_exec"

if not exist "%BINARY%" (
    echo [ERROR] Binary not found: %BINARY%
    echo Please run build_android.bat first.
    goto :error
)

echo [3/4] Pushing %BINARY% to %DEVICE_PATH% ...
adb push "%BINARY%" "%DEVICE_PATH%"
if errorlevel 1 goto :error

echo Setting executable permission on device ...
adb shell chmod 755 "%DEVICE_PATH%"
if errorlevel 1 goto :error

echo.
echo [OK] Deployed operit_shell_exec to %DEVICE_PATH%

echo Running self-test via operit_shell_exec ...
adb shell su -c "%DEVICE_PATH% id"
if errorlevel 1 (
    echo.
    echo [WARN] Self-test command failed. Please check su / Magisk configuration.
    goto :end
)

echo.
echo [INFO] Removing previous Shower log ...
adb shell su -c "rm -f %SHOWER_LOG%"

echo.
echo [INFO] Killing existing Shower server (if any) ...
adb shell su -c "pkill -f com.ai.assistance.shower.Main >/dev/null 2>&1 || true"

echo [INFO] Starting Shower server via operit_shell_exec (native launcher) ...
adb shell su -c "%DEVICE_PATH% CLASSPATH=%REMOTE_CLASSPATH% app_process / com.ai.assistance.shower.Main"
if errorlevel 1 (
    echo [WARN] Failed to start Shower server via operit_shell_exec; please check manually.
) else (
    echo [INFO] Shower server start command sent.
)

echo.
echo [INFO] Self-test finished. Now tailing Shower log (Ctrl+C to stop) ...
echo.
adb shell "tail -n 100 -f %SHOWER_LOG%"

:end
exit /b 0

:error
echo.
echo [ERROR] Push or chmod failed.
exit /b 1
