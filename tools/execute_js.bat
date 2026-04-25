@echo off
setlocal EnableDelayedExpansion

REM Check parameters
if "%~1"=="" (
    echo Usage: %0 ^<JS_file_path^> [function_name] [parameters_JSON^|@params_file] [env_file_path]
    echo Example: %0 example.js
    echo Example: %0 example.js main
    echo Example: %0 example.js main "{\"name\":\"John\"}"
    echo Example: %0 example.js main @params.json
    echo Example: %0 example.js main "{}" .env.local
    echo Note: set OPERIT_RESULT_WAIT_SECONDS to customize result wait, default is 15 seconds
    exit /b 1
)

REM Parameters
set "FILE_PATH=%~1"
if "%~2"=="" (
    set "FUNCTION_NAME=main"
    set "PARAMS_ARG={}"
    set "ENV_FILE_PATH="
) else (
    set "FUNCTION_NAME=%~2"
    if "%~3"=="" (
        set "PARAMS_ARG={}"
        set "ENV_FILE_PATH=%~4"
    ) else (
        set "PARAMS_ARG=%~3"
        set "ENV_FILE_PATH=%~4"
    )
)

if "%OPERIT_RESULT_WAIT_SECONDS%"=="" (
    set "RESULT_WAIT_SECONDS=15"
) else (
    set "RESULT_WAIT_SECONDS=%OPERIT_RESULT_WAIT_SECONDS%"
)

REM Check if file exists
if not exist "%FILE_PATH%" (
    echo Error: File does not exist - %FILE_PATH%
    exit /b 1
)

REM Check ADB availability
adb version >nul 2>&1 || (
    echo Error: ADB command not found.
    echo Make sure Android SDK is installed and adb is in PATH
    exit /b 1
)

REM Device detection
set "DEVICE_SERIAL="
set "DEVICE_COUNT=0"
echo Checking connected devices...

REM Get valid devices list
for /f "skip=1 tokens=1,2" %%a in ('adb devices') do (
    if "%%b" == "device" (
        set /a DEVICE_COUNT+=1
        set "DEVICE_!DEVICE_COUNT!=%%a"
        echo [!DEVICE_COUNT!] %%a
    )
)

if %DEVICE_COUNT% equ 0 (
    echo Error: No authorized devices found
    exit /b 1
)

REM Device selection
if %DEVICE_COUNT% equ 1 (
    set "DEVICE_SERIAL=!DEVICE_1!"
    echo Using the only connected device: !DEVICE_SERIAL!
) else (
    :device_menu
    echo Multiple devices detected:
    for /l %%i in (1,1,%DEVICE_COUNT%) do echo   %%i. !DEVICE_%%i!
    set /p "CHOICE=Select device (1-%DEVICE_COUNT%): "

    REM Validate input
    echo !CHOICE!|findstr /r "^[1-9][0-9]*$" >nul || (
        echo Invalid input. Numbers only.
        goto :device_menu
    )
    set /a CHOICE=!CHOICE! >nul
    if !CHOICE! lss 1 (
        echo Number too small
        goto :device_menu
    )
    if !CHOICE! gtr %DEVICE_COUNT% (
        echo Number too large
        goto :device_menu
    )

    for %%i in (!CHOICE!) do set "DEVICE_SERIAL=!DEVICE_%%i!"
    echo Selected device: !DEVICE_SERIAL!
)

endlocal & set "DEVICE_SERIAL=%DEVICE_SERIAL%"
setlocal DisableDelayedExpansion

set "FILE_PATH=%~1"
if "%~2"=="" (
    set "FUNCTION_NAME=main"
    set "PARAMS_ARG={}"
    set "ENV_FILE_PATH="
) else (
    set "FUNCTION_NAME=%~2"
    if "%~3"=="" (
        set "PARAMS_ARG={}"
        set "ENV_FILE_PATH=%~4"
    ) else (
        set "PARAMS_ARG=%~3"
        set "ENV_FILE_PATH=%~4"
    )
)

if "%OPERIT_RESULT_WAIT_SECONDS%"=="" (
    set "RESULT_WAIT_SECONDS=15"
) else (
    set "RESULT_WAIT_SECONDS=%OPERIT_RESULT_WAIT_SECONDS%"
)

set "PARAMS_NEEDS_LEGACY_NORMALIZATION=false"
if not "%~3"=="" (
    if not "%~5"=="" (
        set "PARAMS_ARG=%*"
        call set "PARAMS_ARG=%%PARAMS_ARG:*%~1 %~2 =%%"
        set "ENV_FILE_PATH="
        set "PARAMS_NEEDS_LEGACY_NORMALIZATION=true"
    ) else if not "%~4"=="" (
        if not exist "%~4" (
            if not "%PARAMS_ARG:~0,1%"=="@" (
                set "PARAMS_ARG=%*"
                call set "PARAMS_ARG=%%PARAMS_ARG:*%~1 %~2 =%%"
                set "ENV_FILE_PATH="
                set "PARAMS_NEEDS_LEGACY_NORMALIZATION=true"
            )
        )
    )
)

REM File operations
echo Creating directory structure...
set "TARGET_DIR=/sdcard/Android/data/com.ai.assistance.operit/js_temp"
adb -s "%DEVICE_SERIAL%" shell mkdir -p "%TARGET_DIR%"

for %%F in ("%FILE_PATH%") do set "TARGET_FILE=%TARGET_DIR%/%%~nxF"
for %%F in ("%FILE_PATH%") do set "TARGET_RESULT_FILE=%TARGET_DIR%/%%~nF_%FUNCTION_NAME%_%RANDOM%.json"

echo Pushing [%FILE_PATH%] to device...
adb -s "%DEVICE_SERIAL%" push "%FILE_PATH%" "%TARGET_FILE%"
if errorlevel 1 (
    echo Error: Failed to push file
    exit /b 1
)

REM Resolve env file path
if "%ENV_FILE_PATH%"=="" (
    for %%F in ("%FILE_PATH%") do set "SCRIPT_DIR=%%~dpF"
    if exist "%SCRIPT_DIR%.env.local" (
        set "ENV_FILE_PATH=%SCRIPT_DIR%.env.local"
    )
)

set "TARGET_ENV_FILE="
set "HAS_ENV_FILE=false"
if not "%ENV_FILE_PATH%"=="" (
    if exist "%ENV_FILE_PATH%" (
        for %%E in ("%ENV_FILE_PATH%") do set "TARGET_ENV_FILE=%TARGET_DIR%/%%~nxE"
        echo Pushing env file [%ENV_FILE_PATH%] to device...
        adb -s "%DEVICE_SERIAL%" push "%ENV_FILE_PATH%" "%TARGET_ENV_FILE%"
        if errorlevel 1 (
            echo Error: Failed to push env file
            exit /b 1
        )
        set "HAS_ENV_FILE=true"
    )
)

call :prepare_params_file
if errorlevel 1 exit /b 1

for %%P in ("%LOCAL_PARAMS_FILE%") do set "TARGET_PARAMS_FILE=%TARGET_DIR%/%%~nP_%RANDOM%.json"

echo Pushing params [%PARAMS_PREVIEW%] to device...
adb -s "%DEVICE_SERIAL%" push "%LOCAL_PARAMS_FILE%" "%TARGET_PARAMS_FILE%"
if errorlevel 1 (
    call :cleanup_local_params
    echo Error: Failed to push params file
    exit /b 1
)

adb -s "%DEVICE_SERIAL%" shell rm -f "%TARGET_RESULT_FILE%"

REM Execute JS function
echo Executing [%FUNCTION_NAME%] with params source: %TARGET_PARAMS_FILE%
if "%HAS_ENV_FILE%"=="true" (
    adb -s "%DEVICE_SERIAL%" shell "am broadcast -a com.ai.assistance.operit.EXECUTE_JS -n com.ai.assistance.operit/.core.tools.javascript.ScriptExecutionReceiver --include-stopped-packages --es file_path '%TARGET_FILE%' --es function_name '%FUNCTION_NAME%' --es params_file_path '%TARGET_PARAMS_FILE%' --es env_file_path '%TARGET_ENV_FILE%' --es result_file_path '%TARGET_RESULT_FILE%' --ez temp_file true --ez temp_params_file true --ez temp_env_file true"
) else (
    adb -s "%DEVICE_SERIAL%" shell "am broadcast -a com.ai.assistance.operit.EXECUTE_JS -n com.ai.assistance.operit/.core.tools.javascript.ScriptExecutionReceiver --include-stopped-packages --es file_path '%TARGET_FILE%' --es function_name '%FUNCTION_NAME%' --es params_file_path '%TARGET_PARAMS_FILE%' --es result_file_path '%TARGET_RESULT_FILE%' --ez temp_file true --ez temp_params_file true"
)
if errorlevel 1 (
    call :cleanup_local_params
    echo Error: Failed to send execution broadcast
    exit /b 1
)

call :cleanup_local_params

echo Waiting up to %RESULT_WAIT_SECONDS%s for structured result...
set "RESULT_READY="
for /l %%i in (1,1,%RESULT_WAIT_SECONDS%) do (
    adb -s "%DEVICE_SERIAL%" shell "if [ -f '%TARGET_RESULT_FILE%' ]; then echo __READY__; fi" | findstr "__READY__" >nul
    if not errorlevel 1 (
        set "RESULT_READY=1"
        goto :result_ready
    )
    timeout /t 1 /nobreak >nul
)

if not defined RESULT_READY (
    echo Error: Timed out waiting for result file: %TARGET_RESULT_FILE%
    exit /b 1
)

:result_ready
echo Structured execution result:
adb -s "%DEVICE_SERIAL%" shell "cat '%TARGET_RESULT_FILE%'"
adb -s "%DEVICE_SERIAL%" shell rm -f "%TARGET_RESULT_FILE%"
exit /b 0

:prepare_params_file
set "LOCAL_PARAMS_FILE="
set "PARAMS_PREVIEW=%PARAMS_ARG%"
set "PARAMS_LOCAL_TEMP=false"

if not defined PARAMS_ARG set "PARAMS_ARG={}"

if "%PARAMS_ARG:~0,1%"=="@" (
    call set "LOCAL_PARAMS_FILE=%%PARAMS_ARG:~1%%"
)

if defined LOCAL_PARAMS_FILE (
    if not exist "%LOCAL_PARAMS_FILE%" (
        echo Error: Params file does not exist - %LOCAL_PARAMS_FILE%
        exit /b 1
    )
    exit /b 0
)

set "PARAMS_LOCAL_TEMP=true"
set "LOCAL_PARAMS_FILE=%TEMP%\operit_js_params_%RANDOM%_%RANDOM%.json"
set "OPERIT_JSON_FILE=%LOCAL_PARAMS_FILE%"
set "OPERIT_JSON_CONTENT=%PARAMS_ARG%"
set "OPERIT_JSON_LEGACY_MODE=%PARAMS_NEEDS_LEGACY_NORMALIZATION%"
powershell -NoProfile -Command "$raw=$env:OPERIT_JSON_CONTENT; if ($env:OPERIT_JSON_LEGACY_MODE -eq 'true') { $normalized=$raw -replace '^\{\\\s+','{\"' -replace '\\,\\','\",\"' -replace ',\\',',\"' -replace '\\:','\":' -replace ':\\',':\"' -replace '\\}','\"}' -replace '\\/','/' } else { $normalized=$raw }; [System.IO.File]::WriteAllText($env:OPERIT_JSON_FILE, $normalized, [System.Text.UTF8Encoding]::new($false))"
set "OPERIT_JSON_FILE="
set "OPERIT_JSON_CONTENT="
set "OPERIT_JSON_LEGACY_MODE="
if errorlevel 1 (
    echo Error: Failed to create local params file
    exit /b 1
)
exit /b 0

:cleanup_local_params
if "%PARAMS_LOCAL_TEMP%"=="true" (
    if exist "%LOCAL_PARAMS_FILE%" del /q "%LOCAL_PARAMS_FILE%" >nul 2>&1
)
exit /b 0
