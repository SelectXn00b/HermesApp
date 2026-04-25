$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$dataDir = Join-Path $projectRoot "data"
$logsDir = Join-Path $projectRoot "logs"
$configPath = Join-Path $dataDir "config.json"
$runtimePath = Join-Path $dataDir "runtime.json"
$pidPath = Join-Path $dataDir "agent.pid"
$activeLaunchPath = Join-Path $dataDir "active_launch.id"
$launcherLog = Join-Path $logsDir "launcher.log"

if (-not (Test-Path $dataDir)) { New-Item -ItemType Directory -Path $dataDir -Force | Out-Null }
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "$ts [$Level] $Message"
    Write-Host $line
    Add-Content -Path $launcherLog -Value $line -Encoding UTF8
}

function Resolve-AgentPort {
    param([string]$ConfigFilePath)

    $port = 58321
    try {
        if (Test-Path $ConfigFilePath) {
            $config = Get-Content -Raw $ConfigFilePath | ConvertFrom-Json
            if ($config -and $config.port) {
                $parsedPort = [int]$config.port
                if ($parsedPort -ge 1 -and $parsedPort -le 65535) {
                    $port = $parsedPort
                }
            }
        }
    }
    catch {
        Write-Log "WARN" "Failed to read config.json, fallback port: $($_.Exception.Message)"
    }

    return $port
}

function Stop-AgentProcess {
    param([int]$Port)

    $stoppedAny = $false

    if (Test-Path $pidPath) {
        try {
            $pidText = (Get-Content -Raw $pidPath).Trim()
            if ($pidText -match '^\d+$') {
                $pidValue = [int]$pidText
                Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
                Write-Log "INFO" "Stopped PID from pid file: $pidValue"
                $stoppedAny = $true
            }
        }
        catch {
            Write-Log "WARN" "Failed to stop pid-file process: $($_.Exception.Message)"
        }
    }

    try {
        $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($conns) {
            $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
            foreach ($p in $pids) {
                if ($p -gt 0) {
                    Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
                    Write-Log "INFO" "Stopped process listening on port ${Port}: PID $p"
                    $stoppedAny = $true
                }
            }
        }
    }
    catch {
        Write-Log "WARN" "Failed to inspect listening port ${Port}: $($_.Exception.Message)"
    }

    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $runtimePath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $activeLaunchPath -Force -ErrorAction SilentlyContinue

    return $stoppedAny
}

$mutexName = "Local\OperitPcAgentLauncher"
$mutex = New-Object System.Threading.Mutex($false, $mutexName)
$hasLock = $false

try {
    $hasLock = $mutex.WaitOne(0)
    if (-not $hasLock) {
        Write-Log "WARN" "Launcher is running. Close launching flow and retry stop."
        Write-Host "[WARN] Operit PC Agent is busy (launcher running)."
        exit 1
    }

    $port = Resolve-AgentPort -ConfigFilePath $configPath
    Write-Log "INFO" "===== operit_pc_agent_stop.bat ====="
    Write-Log "INFO" "Working directory: $projectRoot"
    Write-Log "INFO" "Target port: $port"

    $stopped = Stop-AgentProcess -Port $port
    if ($stopped) {
        Write-Host "[OK] Operit PC Agent stopped (port $port)."
        Write-Log "OK" "Agent stopped on port $port"
    }
    else {
        Write-Host "[OK] No running Operit PC Agent process found."
        Write-Log "INFO" "No running agent process found on port $port"
    }

    exit 0
}
finally {
    if ($hasLock) {
        $mutex.ReleaseMutex() | Out-Null
    }
    $mutex.Dispose()
}
