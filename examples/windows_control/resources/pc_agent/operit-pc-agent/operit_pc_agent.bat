@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "ELEVATE_FLAG=--elevated"
if /i "%~1"=="%ELEVATE_FLAG%" shift

powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent()); if($p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){exit 0}else{exit 1}" >nul 2>&1
if errorlevel 1 (
  echo [INFO] Requesting administrator privileges...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -WorkingDirectory '%~dp0' -ArgumentList @('%ELEVATE_FLAG%') -Verb RunAs"
  set "ELEVATE_EXIT=!ERRORLEVEL!"

  if not "!ELEVATE_EXIT!"=="0" (
    echo.
    echo [ERROR] Failed to request administrator privileges.
    pause
    exit /b !ELEVATE_EXIT!
  )

  exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\launch_agent.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo [ERROR] Operit PC Agent launcher failed with code %EXIT_CODE%.
  pause
)

exit /b %EXIT_CODE%
