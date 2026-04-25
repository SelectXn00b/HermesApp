@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

where py >nul 2>&1
if %errorlevel% equ 0 (
    goto :use_py
)

where python >nul 2>&1
if %errorlevel% equ 0 (
    goto :use_python
)

echo Error: Python not found. Install Python or add it to PATH.
exit /b 1

:use_py
py -3 "%SCRIPT_DIR%debug_toolpkg.py" %*
exit /b %errorlevel%

:use_python
python "%SCRIPT_DIR%debug_toolpkg.py" %*
exit /b %errorlevel%
