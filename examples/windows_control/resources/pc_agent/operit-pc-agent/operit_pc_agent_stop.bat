@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\stop_agent.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo [ERROR] Operit PC Agent stop failed with code %EXIT_CODE%.
  pause
)

exit /b %EXIT_CODE%
