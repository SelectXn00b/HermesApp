@echo off
setlocal enabledelayedexpansion

rem Directory where this script lives (normalize without trailing backslash)
set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

rem Try to locate project root (two levels up: assistance/)
pushd "%SCRIPT_DIR%..\.." >nul 2>nul
set "PROJECT_ROOT=%CD%"
popd >nul 2>nul

rem If ANDROID_NDK_HOME is not set, try to infer it from local.properties (sdk.dir)
if not defined ANDROID_NDK_HOME (
    set "LOCAL_PROPERTIES=%PROJECT_ROOT%\local.properties"
    if exist "%LOCAL_PROPERTIES%" (
        for /f "usebackq tokens=1,* delims==" %%A in ("%LOCAL_PROPERTIES%") do (
            if "%%A"=="sdk.dir" set "SDK_DIR=%%B"
        )
    )
    if defined SDK_DIR (
        set "NDK_BASE=%SDK_DIR%\ndk"
        for /d %%D in ("%NDK_BASE%\*") do set "ANDROID_NDK_HOME=%%D"
    )
)

if not defined ANDROID_NDK_HOME (
    echo [ERROR] ANDROID_NDK_HOME is not set and could not be inferred from local.properties.
    echo Please set ANDROID_NDK_HOME to your Android NDK path, e.g.
    echo   set ANDROID_NDK_HOME=D:\ProgramData\AndroidSDK\ndk\26.1.10909125
    exit /b 1
)

echo Using ANDROID_NDK_HOME=%ANDROID_NDK_HOME%

rem Try to locate Ninja without modifying global PATH, based on ANDROID_NDK_HOME
set "NINJA_EXE="
set "CMAKE_BASE=%ANDROID_NDK_HOME%\..\..\cmake"
for /d %%C in ("%CMAKE_BASE%\*") do (
    if exist "%%C\bin\ninja.exe" (
        set "NINJA_EXE=%%C\bin\ninja.exe"
        goto :have_ninja
    )
)

:have_ninja
if not defined NINJA_EXE (
    echo [ERROR] Could not find ninja.exe under %CMAKE_BASE%\*\bin
    echo Please install CMake with Ninja or adjust this script to point to your ninja.exe.
    goto :error
)

rem Configure CMake build directory under this folder (clean old cache to avoid generator mismatch)
if exist "%SCRIPT_DIR%\build" rmdir /S /Q "%SCRIPT_DIR%\build"
mkdir "%SCRIPT_DIR%\build"

cmake -G Ninja -S "%SCRIPT_DIR%" -B "%SCRIPT_DIR%\build" ^
  -DCMAKE_TOOLCHAIN_FILE="%ANDROID_NDK_HOME%\build\cmake\android.toolchain.cmake" ^
  -DCMAKE_MAKE_PROGRAM="%NINJA_EXE%" ^
  -DANDROID_ABI=arm64-v8a ^
  -DANDROID_PLATFORM=android-21
if errorlevel 1 goto :error

cmake --build "%SCRIPT_DIR%\build" --config Release
if errorlevel 1 goto :error

echo.
echo [1/2] Build finished. The operit_shell_exec binary should be under the build directory.

rem Copy binary into main app assets so it can be packaged with the APK
set "BINARY=%SCRIPT_DIR%\build\operit_shell_exec"
if not exist "%BINARY%" (
    echo [ERROR] Binary not found after build: %BINARY%
    goto :error
)

set "TARGET_ASSETS_DIR=%SCRIPT_DIR%\..\..\app\src\main\assets"
if not exist "%TARGET_ASSETS_DIR%" (
    mkdir "%TARGET_ASSETS_DIR%"
)
set "TARGET_BIN=%TARGET_ASSETS_DIR%\operit_shell_exec"
echo [2/2] Copying %BINARY% to %TARGET_BIN% ...
copy /Y "%BINARY%" "%TARGET_BIN%" >nul
if errorlevel 1 goto :error

echo [OK] Build and asset copy finished.
exit /b 0

:error
echo.
echo [ERROR] Build failed.
exit /b 1
