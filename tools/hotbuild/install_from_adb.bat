@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "HOTBUILD_DIR=%~dp0"
if "%HOTBUILD_DIR:~-1%"=="\" set "HOTBUILD_DIR=%HOTBUILD_DIR:~0,-1%"

set "APK=%HOTBUILD_DIR%\from.apk"
if not exist "%APK%" (
  echo from.apk not found: "%APK%"
  exit /b 2
)

where adb >nul 2>nul
if not "%errorlevel%"=="0" (
  echo adb not found in PATH
  echo Install Android platform-tools and ensure adb is available.
  exit /b 2
)

echo adb devices:
adb devices

echo Installing from.apk (ignore version conflict):
echo adb install -r -d "%APK%"
adb install -r -d "%APK%"
set "RC=%errorlevel%"
if not "%RC%"=="0" (
  echo adb install failed: %RC%
  exit /b %RC%
)

echo done
exit /b 0
