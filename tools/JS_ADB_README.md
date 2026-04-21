# ADB JavaScript Executor

This tool allows you to push JavaScript files to Android devices via ADB and remotely execute specific functions.
For direct whole-script execution without specifying an exported function, use `run_sandbox_script.sh` / `run_sandbox_script.bat`.

## Features

- Push JavaScript files to Android devices using ADB
- Execute specified JavaScript functions
- Support for JSON parameter passing
- Temporary file option (auto-deletion after execution)
- Implementations in Shell script, Windows Batch file, and Python script
- **Device selection for multi-device setups**

## Prerequisites

- Android SDK (ADB)
- Android device with USB debugging enabled
- ADB debugging permission granted on the device
- Your application installed on the device

## Quick Start

### Using Shell Script (Linux/macOS)

1. Make the script executable:
```bash
chmod +x execute_js.sh
```

2. Execute the script:
```bash
./execute_js.sh path/to/your/script.js functionName '{"param1":"value1"}'
```

3. Execute a whole sandbox script directly:
```bash
./run_sandbox_script.sh path/to/your/script.js '{"param1":"value1"}'
```

### Using Batch Script (Windows)

1. Execute the batch file:
```cmd
execute_js.bat path\to\your\script.js functionName @params.json
```

2. Execute a whole sandbox script directly:
```cmd
run_sandbox_script.bat path\to\your\script.js @params.json
```

You can still pass inline JSON directly. The scripts now write that JSON into a temporary file and push it to the device, which avoids the old `adb shell am broadcast --es params ...` quoting breakage. On PowerShell, prefer single-quoted JSON like `'{"param1":"value1"}'` or use `@params.json`.

### Using Python Script (Cross-platform)

1. Ensure Python 3 is installed

2. Execute the script:
```bash
python execute_js.py path/to/your/script.js functionName --params '{"param1":"value1"}'
```

3. Additional options:
```bash
# Specify a device directly (skip device selection prompt)
python execute_js.py path/to/script.js functionName --device DEVICE_SERIAL

# Keep the file on the device after execution
python execute_js.py path/to/script.js functionName --keep
```

## Device Selection

When multiple devices are connected:

1. The script will display a list of available devices
2. Enter the number corresponding to your target device
3. All ADB operations will be performed on the selected device

Example:
```
Checking connected devices...
1: emulator-5554
2: 192.168.1.100:5555
Enter device number (1-2): 
```

## Examples

### 1. Execute the greeting function

```bash
# Linux/macOS
./execute_js.sh test_script.js sayHello '{"name":"John"}'

# Windows
execute_js.bat test_script.js sayHello @params.json

# Python (any platform)
python execute_js.py test_script.js sayHello --params '{"name":"John"}'
```

### 2. Execute the calculation function

```bash
# Linux/macOS
./execute_js.sh test_script.js calculate '{"num1":10,"num2":5,"operation":"multiply"}'

# Windows
execute_js.bat test_script.js calculate @params.json

# Python (any platform)
python execute_js.py test_script.js calculate --params '{"num1":10,"num2":5,"operation":"multiply"}'
```

## Viewing Execution Results

The executor now waits for a dedicated structured result file instead of scraping `adb logcat`.
After execution it prints a JSON payload containing:

- `success`
- `result`
- `error`
- `events` (including `console.log/info/warn/error` and intermediate updates)
- `durationMs`

You can adjust the wait timeout with:

```bash
OPERIT_RESULT_WAIT_SECONDS=30 ./execute_js.sh path/to/your/script.js functionName '{"param1":"value1"}'
```

## Sandbox Script Debug Notes

When you use `run_sandbox_script.sh` / `run_sandbox_script.bat`, or call package tools such as `operit_editor:debug_run_sandbox_script` with `source_code`, the code is executed as a **top-level script snippet**, not as `function(params) { ... }`.

That means:

- Use `console.log(...)`, `console.info(...)`, `console.warn(...)`, `console.error(...)` for logs
- Use `emit(...)` to send intermediate events
- Use `complete(...)` to finish with a structured result
- Do **not** assume `params` is directly available inside `source_code`
- Do **not** call `intermediate(...)`; the supported helper name is `emit(...)`

Recommended inline snippet:

```javascript
console.log("inline debug start");
emit({ stage: "inline", ok: true });
complete({
  success: true,
  message: "inline debug finished"
});
```

If you need parameter-driven logic, prefer one of these approaches:

- Use `execute_js.*` and call an exported function like `function myFunc(params) { ... }`
- Use `debug_run_sandbox_script` with `source_path` and put the logic in a real script file
- Keep `source_code` for quick top-level smoke tests, logging, and minimal one-off checks

## JavaScript File Requirements

- Files must be valid JavaScript (TypeScript not supported)
- Functions must be exported (using `exports.functionName = functionName`)
- Functions must accept a params parameter (containing the passed parameters)
- Functions should use the `complete(result)` function to return results

## Required Function Example

```javascript
function myFunction(params) {
    // Process parameters
    const name = params.name || "default";
    
    // Execute business logic
    const result = `Result: ${name}`;
    
    // Return result
    complete({
        success: true,
        result: result
    });
}

// Export function
exports.myFunction = myFunction;
```

## Technical Details

1. The Shell/Batch script pushes the JavaScript file to a temporary directory on the device
2. Inline JSON parameters are first written to a temp JSON file, then pushed to the device as `params_file_path`
3. An ADB broadcast is sent to the application with the execution request
4. The application's BroadcastReceiver receives the request and uses JsEngine to execute the function or whole script
5. Execution results are written to a dedicated JSON file on the device
6. The executor waits for that file, prints it, and then cleans it up

## Troubleshooting

### Common Issues

1. **ADB command not found**  
   Ensure the Android SDK's platform-tools directory is in your PATH environment variable.

2. **No devices detected**
   - Make sure the device is connected
   - Ensure USB debugging is enabled
   - Check that the computer is authorized for USB debugging

3. **Broadcast sent but function not executed**
   - Check the logs for error messages
   - Confirm the application is running
   - Verify the receiver is correctly registered

4. **JavaScript errors**
   - Check for TypeScript-specific syntax (like type annotations)
   - Ensure functions are properly exported
   - Verify parameter formats are correct
   
## Required Permissions

This feature requires the following permissions:

- Read and write external storage
- Permission to receive broadcasts

## Security Considerations

- This feature should only be used for development and debugging purposes
- In production environments, this feature should be disabled or restricted to prevent security risks 
