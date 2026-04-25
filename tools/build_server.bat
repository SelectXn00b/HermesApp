@echo off
setlocal ENABLEDELAYEDEXPANSION

rem === 1. 切换到 tools\shower 并编译 Shower (release) ===
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%shower"

echo [1/3] Building Shower (assembleRelease)...
call gradlew.bat assembleRelease
if errorlevel 1 (
    echo [ERROR] Gradle build failed.
    exit /b 1
)

rem === 2. 查找生成的 release APK ===
set APK_DIR=app\build\outputs\apk\release
set APK_PATH=
for /f "delims=" %%F in ('dir /b "%APK_DIR%\*.apk" 2^>nul') do (
    set APK_PATH=%APK_DIR%\%%F
    goto :apk_found
)

echo [ERROR] 未在 %APK_DIR% 下找到 APK 文件，请检查构建输出。
exit /b 1

:apk_found
echo [INFO] 使用 APK: %APK_PATH%

rem === 3. 复制到 showerclient/src/main/assets 目录作为 shower-server.jar ===
set TARGET_ASSETS_DIR=%SCRIPT_DIR%..\showerclient\src\main\assets
if not exist "%TARGET_ASSETS_DIR%" (
    mkdir "%TARGET_ASSETS_DIR%"
)
set TARGET_JAR=%TARGET_ASSETS_DIR%\shower-server.jar
echo [2/3] Copying APK to %TARGET_JAR% ...
copy /Y "%APK_PATH%" "%TARGET_JAR%" >nul
if errorlevel 1 (
    echo [ERROR] 无法复制 APK 到 %TARGET_JAR%。
    exit /b 1
)

echo [3/3] Done. Generated %TARGET_JAR%

endlocal
