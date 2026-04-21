$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$dataDir = Join-Path $projectRoot "data"
$logsDir = Join-Path $projectRoot "logs"
$configPath = Join-Path $dataDir "config.json"
$runtimePath = Join-Path $dataDir "runtime.json"
$pidPath = Join-Path $dataDir "agent.pid"
$activeLaunchPath = Join-Path $dataDir "active_launch.id"
$startupStatePath = Join-Path $dataDir "startup_state.json"
$launcherLog = Join-Path $logsDir "launcher.log"
$outLog = Join-Path $logsDir "agent.out.log"
$errLog = Join-Path $logsDir "agent.err.log"

if (-not (Test-Path $dataDir)) { New-Item -ItemType Directory -Path $dataDir -Force | Out-Null }
if (-not (Test-Path $logsDir)) { New-Item -ItemType Directory -Path $logsDir -Force | Out-Null }

function Write-Log {
    param([string]$Level, [string]$Message)
    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
    $line = "$ts [$Level] $Message"
    Write-Host $line
    Add-Content -Path $launcherLog -Value $line -Encoding UTF8
}

function Format-HostForUrl {
    param([string]$InputHost)

    $value = [string]$InputHost
    if ([string]::IsNullOrWhiteSpace($value)) {
        return "127.0.0.1"
    }

    $trimmed = $value.Trim()
    if ($trimmed.Contains(":") -and -not ($trimmed.StartsWith("[") -and $trimmed.EndsWith("]"))) {
        return "[$trimmed]"
    }

    return $trimmed
}

function Try-OpenBrowser {
    param([string]$TargetUrl, [string]$Reason)

    try {
        Write-Log "INFO" "Opening browser ($Reason): $TargetUrl"
        Start-Process $TargetUrl
    }
    catch {
        Write-Log "WARN" "Failed to open browser ($Reason): $($_.Exception.Message)"
    }
}

function New-LaunchContext {
    param([string]$BindAddress, [int]$Port)

    $launchHost = $BindAddress
    if ($launchHost -eq "0.0.0.0" -or $launchHost -eq "::") {
        $launchHost = "127.0.0.1"
    }

    $readyHosts = [System.Collections.Generic.List[string]]::new()
    $readyHosts.Add($BindAddress)

    if (-not $readyHosts.Contains("127.0.0.1")) {
        $readyHosts.Add("127.0.0.1")
    }

    if (-not $readyHosts.Contains("localhost")) {
        $readyHosts.Add("localhost")
    }

    $readyUrls = @()
    foreach ($readyHost in $readyHosts) {
        $readyUrls += "http://$(Format-HostForUrl -InputHost $readyHost):$Port/api/config"
    }

    return [pscustomobject]@{
        LaunchHost = $launchHost
        Url = "http://$(Format-HostForUrl -InputHost $launchHost):$Port"
        ReadyUrls = $readyUrls
    }
}

function Start-AgentAndProbeReady {
    param(
        [string]$NodePath,
        [string]$RootPath,
        [string]$OutLogPath,
        [string]$ErrLogPath,
        [string[]]$ReadyUrls,
        [string]$BindAddressOverride,
        [int]$MaxWaitMs,
        [int]$StepMs
    )

    $previousBindOverride = $env:OPERIT_BIND_ADDRESS_OVERRIDE
    try {
        if ([string]::IsNullOrWhiteSpace($BindAddressOverride)) {
            Remove-Item Env:OPERIT_BIND_ADDRESS_OVERRIDE -ErrorAction SilentlyContinue
        }
        else {
            $env:OPERIT_BIND_ADDRESS_OVERRIDE = $BindAddressOverride
        }

        Start-Process -FilePath $NodePath -ArgumentList "src/server.js" -WorkingDirectory $RootPath -WindowStyle Hidden -RedirectStandardOutput $OutLogPath -RedirectStandardError $ErrLogPath
    }
    finally {
        if ($null -eq $previousBindOverride) {
            Remove-Item Env:OPERIT_BIND_ADDRESS_OVERRIDE -ErrorAction SilentlyContinue
        }
        else {
            $env:OPERIT_BIND_ADDRESS_OVERRIDE = $previousBindOverride
        }
    }

    $readyUrlHit = $null
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

    while ($stopwatch.ElapsedMilliseconds -lt $MaxWaitMs) {
        foreach ($candidateReadyUrl in $ReadyUrls) {
            if (Test-AgentReady -ReadyUrl $candidateReadyUrl) {
                $readyUrlHit = $candidateReadyUrl
                break
            }
        }

        if ($readyUrlHit) {
            break
        }

        Start-Sleep -Milliseconds $StepMs
    }

    $stopwatch.Stop()

    return [pscustomobject]@{
        ReadyUrlHit = $readyUrlHit
        ElapsedMs = [int]$stopwatch.ElapsedMilliseconds
    }
}

function Get-AgentErrTailText {
    param([int]$TailLines = 50)

    if (-not (Test-Path $errLog)) {
        return ""
    }

    $errTailLines = @(Get-Content -Path $errLog -Tail $TailLines)
    foreach ($line in $errTailLines) {
        Write-Log "ERROR" "agent.err.log: $line"
    }

    return ($errTailLines -join "`n")
}

function Test-IsPrivateLanIpv4 {
    param([string]$Ipv4)

    if ([string]::IsNullOrWhiteSpace($Ipv4)) {
        return $false
    }

    if ($Ipv4.StartsWith("10.")) {
        return $true
    }

    if ($Ipv4.StartsWith("192.168.")) {
        return $true
    }

    $match = [regex]::Match($Ipv4, "^172\.(\d+)\.")
    if (-not $match.Success) {
        return $false
    }

    $second = [int]$match.Groups[1].Value
    return ($second -ge 16 -and $second -le 31)
}

function Test-IsVirtualInterfaceName {
    param([string]$InterfaceName)

    if ([string]::IsNullOrWhiteSpace($InterfaceName)) {
        return $false
    }

    $tokens = @(
        "vEthernet",
        "wsl",
        "hyper-v",
        "vmware",
        "virtualbox",
        "docker",
        "container",
        "tailscale",
        "zerotier",
        "loopback",
        "npcap",
        "hamachi"
    )

    foreach ($token in $tokens) {
        if ($InterfaceName.IndexOf($token, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }

    return $false
}

function Test-IsPreferredPhysicalInterfaceName {
    param([string]$InterfaceName)

    if ([string]::IsNullOrWhiteSpace($InterfaceName)) {
        return $false
    }

    $tokens = @(
        "wifi",
        "wi-fi",
        "wlan",
        "wireless",
        "ethernet",
        "lan"
    )

    foreach ($token in $tokens) {
        if ($InterfaceName.IndexOf($token, [System.StringComparison]::OrdinalIgnoreCase) -ge 0) {
            return $true
        }
    }

    return $false
}

function Get-Ipv4CandidateScore {
    param(
        [string]$Ipv4,
        [string]$InterfaceName
    )

    $score = 0
    if (Test-IsPrivateLanIpv4 -Ipv4 $Ipv4) {
        $score += 100
    }

    if (Test-IsVirtualInterfaceName -InterfaceName $InterfaceName) {
        $score -= 200
    }
    else {
        $score += 70
    }

    if (Test-IsPreferredPhysicalInterfaceName -InterfaceName $InterfaceName) {
        $score += 25
    }

    return $score
}

function Get-RecommendedIpv4 {
    $candidateMap = @{}

    try {
        $netIps = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop
        foreach ($item in $netIps) {
            $ip = [string]$item.IPAddress
            if ([string]::IsNullOrWhiteSpace($ip)) {
                continue
            }
            if ($ip -eq "127.0.0.1" -or $ip.StartsWith("169.254.")) {
                continue
            }

            $interfaceAlias = [string]$item.InterfaceAlias
            $isPrivateLan = Test-IsPrivateLanIpv4 -Ipv4 $ip
            $isVirtual = Test-IsVirtualInterfaceName -InterfaceName $interfaceAlias
            $score = Get-Ipv4CandidateScore -Ipv4 $ip -InterfaceName $interfaceAlias

            $candidate = [pscustomobject]@{
                Ip = $ip
                InterfaceAlias = $interfaceAlias
                IsPrivateLan = $isPrivateLan
                IsVirtual = $isVirtual
                Score = $score
            }

            if (-not $candidateMap.ContainsKey($ip) -or [int]$candidateMap[$ip].Score -lt $score) {
                $candidateMap[$ip] = $candidate
            }
        }
    }
    catch {
        Write-Log "WARN" "Get-NetIPAddress failed while collecting IPv4 candidates: $($_.Exception.Message)"
    }

    if ($candidateMap.Count -eq 0) {
        try {
            $allNics = [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()
            foreach ($nic in $allNics) {
                if ($nic.OperationalStatus -ne [System.Net.NetworkInformation.OperationalStatus]::Up) {
                    continue
                }

                $ipProps = $nic.GetIPProperties()
                foreach ($addrInfo in $ipProps.UnicastAddresses) {
                    $addr = $addrInfo.Address
                    if (-not $addr) {
                        continue
                    }
                    if ($addr.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
                        continue
                    }

                    $ip = $addr.ToString()
                    if ($ip -eq "127.0.0.1" -or $ip.StartsWith("169.254.")) {
                        continue
                    }

                    $interfaceAlias = [string]$nic.Name
                    $isPrivateLan = Test-IsPrivateLanIpv4 -Ipv4 $ip
                    $isVirtual = Test-IsVirtualInterfaceName -InterfaceName $interfaceAlias
                    $score = Get-Ipv4CandidateScore -Ipv4 $ip -InterfaceName $interfaceAlias

                    $candidate = [pscustomobject]@{
                        Ip = $ip
                        InterfaceAlias = $interfaceAlias
                        IsPrivateLan = $isPrivateLan
                        IsVirtual = $isVirtual
                        Score = $score
                    }

                    if (-not $candidateMap.ContainsKey($ip) -or [int]$candidateMap[$ip].Score -lt $score) {
                        $candidateMap[$ip] = $candidate
                    }
                }
            }
        }
        catch {
            Write-Log "WARN" "NetworkInterface fallback failed while collecting IPv4 candidates: $($_.Exception.Message)"
        }
    }

    $rankedCandidates = @($candidateMap.Values | Sort-Object -Property @{Expression = { $_.Score }; Descending = $true }, @{Expression = { $_.Ip }; Descending = $false })
    $candidates = [System.Collections.Generic.List[string]]::new()
    foreach ($entry in $rankedCandidates) {
        if (-not $candidates.Contains($entry.Ip)) {
            $candidates.Add($entry.Ip)
        }
    }

    $preferredLan = $null
    foreach ($entry in $rankedCandidates) {
        if ($entry.IsPrivateLan -and -not $entry.IsVirtual) {
            $preferredLan = [string]$entry.Ip
            break
        }
    }
    if (-not $preferredLan) {
        foreach ($entry in $rankedCandidates) {
            if ($entry.IsPrivateLan) {
                $preferredLan = [string]$entry.Ip
                break
            }
        }
    }

    $recommendedHost = ""
    $recommendedAlias = ""
    if ($rankedCandidates.Count -gt 0) {
        $recommendedHost = [string]$rankedCandidates[0].Ip
        $recommendedAlias = [string]$rankedCandidates[0].InterfaceAlias
    }

    return [pscustomobject]@{
        Candidates = @($candidates)
        PreferredLan = $preferredLan
        RecommendedHost = $recommendedHost
        RecommendedInterfaceAlias = $recommendedAlias
        RankedCandidates = @($rankedCandidates)
    }
}

function Write-StartupState {
    param(
        [string]$IssueType,
        [string]$ConfiguredBindAddress,
        [string]$RecommendedBindAddress,
        [object[]]$Ipv4Candidates,
        [int]$Port,
        [string]$LaunchId
    )

    $payload = [ordered]@{
        issueType = $IssueType
        status = "waiting_user_action"
        configuredBindAddress = $ConfiguredBindAddress
        recommendedBindAddress = $RecommendedBindAddress
        ipv4Candidates = @($Ipv4Candidates)
        port = $Port
        launchId = $LaunchId
        detectedAt = (Get-Date).ToString("o")
    }

    $json = $payload | ConvertTo-Json -Depth 10
    Set-Content -Path $startupStatePath -Value $json -Encoding UTF8
}

function Remove-StartupState {
    Remove-Item -LiteralPath $startupStatePath -Force -ErrorAction SilentlyContinue
}

function Test-AgentReady {
    param([string]$ReadyUrl)

    try {
        $resp = Invoke-RestMethod -Uri $ReadyUrl -Method Get -TimeoutSec 2
        return ($resp -and $resp.port -and [int]$resp.port -ge 1)
    }
    catch {
        return $false
    }
}

function Get-ListeningPid {
    param([int]$Port)

    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($conn -and $conn.OwningProcess) {
            return [int]$conn.OwningProcess
        }
    }
    catch {
        # ignore
    }

    return $null
}

function Stop-ExistingAgent {
    param([int]$Port)

    if (Test-Path $pidPath) {
        try {
            $pidText = (Get-Content -Raw $pidPath).Trim()
            if ($pidText -match '^\d+$') {
                $pidValue = [int]$pidText
                Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
                Write-Log "INFO" "Stopped PID from pid file: $pidValue"
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
                }
            }
        }
    }
    catch {
        $rows = netstat -ano | Select-String ":$Port .*LISTENING"
        foreach ($row in $rows) {
            $parts = ($row.ToString() -split '\s+') | Where-Object { $_ -ne '' }
            if ($parts.Count -ge 5 -and $parts[4] -match '^\d+$') {
                $p = [int]$parts[4]
                Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
                Write-Log "INFO" "Stopped process from netstat on port ${Port}: PID $p"
            }
        }
    }

    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $runtimePath -Force -ErrorAction SilentlyContinue
}

function Ensure-Dependencies {
    param([string]$RootPath)

    $requiredPackages = @(
        @{ Name = "node-pty"; Path = (Join-Path $RootPath "node_modules\node-pty\package.json") },
        @{ Name = "@xterm/headless"; Path = (Join-Path $RootPath "node_modules\@xterm\headless\package.json") }
    )

    $missingBefore = @($requiredPackages | Where-Object { -not (Test-Path $_.Path) })
    if ($missingBefore.Count -eq 0) {
        Write-Log "INFO" "Dependency check passed: node-pty, @xterm/headless are present."
        return
    }

    Write-Log "INFO" "Missing dependencies: $((@($missingBefore | ForEach-Object { $_.Name }) -join ', '))"

    $npmCmd = Get-Command npm -ErrorAction SilentlyContinue
    $pnpmCmd = Get-Command pnpm -ErrorAction SilentlyContinue
    $npmLockFilePath = Join-Path $RootPath "package-lock.json"
    $pnpmLockFilePath = Join-Path $RootPath "pnpm-lock.yaml"

    $installerName = $null
    $primaryArgs = @()
    $fallbackArgs = $null

    if ((Test-Path $pnpmLockFilePath) -and $pnpmCmd) {
        $installerName = "pnpm"
        $primaryArgs = @("install", "--frozen-lockfile", "--prefer-offline")
        $fallbackArgs = @("install", "--prefer-offline")
    }
    elseif ((Test-Path $npmLockFilePath) -and $npmCmd) {
        $installerName = "npm"
        $primaryArgs = @("install", "--no-audit", "--no-fund")
        $fallbackArgs = $null
    }
    elseif ($pnpmCmd) {
        $installerName = "pnpm"
        $primaryArgs = @("install", "--prefer-offline")
    }
    elseif ($npmCmd) {
        $installerName = "npm"
        $primaryArgs = @("install", "--no-audit", "--no-fund")
    }
    else {
        throw "Neither npm nor pnpm was found. Please install Node.js with npm or pnpm."
    }

    $commandName = if ($installerName -eq "pnpm") { "pnpm" } else { "npm" }

    Write-Log "INFO" "Installing dependencies: $installerName $($primaryArgs -join ' ')"
    & $commandName @primaryArgs
    $installExitCode = $LASTEXITCODE

    if ($installExitCode -ne 0 -and $fallbackArgs) {
        Write-Log "WARN" "Primary dependency install failed (exit $installExitCode). Retrying: $installerName $($fallbackArgs -join ' ')"
        & $commandName @fallbackArgs
        $installExitCode = $LASTEXITCODE
    }

    if ($installExitCode -ne 0) {
        throw "Dependency installation failed with exit code $installExitCode"
    }

    $missingAfter = @($requiredPackages | Where-Object { -not (Test-Path $_.Path) })
    if ($missingAfter.Count -gt 0) {
        throw "Dependencies are still missing after installation: $((@($missingAfter | ForEach-Object { $_.Name }) -join ', '))"
    }

    Write-Log "INFO" "Dependencies installed successfully via $installerName."
}

function Resolve-NodeRuntime {
    param([string]$RootPath)

    $systemNodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if ($systemNodeCmd -and $systemNodeCmd.Source) {
        $systemNodePath = $systemNodeCmd.Source
        return [pscustomobject]@{
            NodePath = $systemNodePath
            NodeDir = Split-Path -Parent $systemNodePath
            Source = "system"
        }
    }

    $localCandidates = @(
        (Join-Path $RootPath "node\node.exe"),
        (Join-Path $RootPath "runtime\node\node.exe"),
        (Join-Path $RootPath "local\node\node.exe")
    )

    foreach ($candidate in $localCandidates) {
        if (Test-Path $candidate) {
            $resolved = (Resolve-Path $candidate).Path
            return [pscustomobject]@{
                NodePath = $resolved
                NodeDir = Split-Path -Parent $resolved
                Source = "local"
            }
        }
    }

    return $null
}

function Get-NodeArchToken {
    $rawArch = $env:PROCESSOR_ARCHITEW6432
    if ([string]::IsNullOrWhiteSpace($rawArch)) {
        $rawArch = $env:PROCESSOR_ARCHITECTURE
    }

    if ([string]::IsNullOrWhiteSpace($rawArch)) {
        return "x64"
    }

    switch ($rawArch.Trim().ToUpperInvariant()) {
        "ARM64" { return "arm64" }
        "X86" { return "x86" }
        "AMD64" { return "x64" }
        default { return "x64" }
    }
}

function Ensure-LocalNodeRuntime {
    param([string]$RootPath)

    $localNodeDir = Join-Path $RootPath "node"
    $localNodeExe = Join-Path $localNodeDir "node.exe"

    if (Test-Path $localNodeExe) {
        return $localNodeExe
    }

    $arch = Get-NodeArchToken
    $zipTag = "win-$arch-zip"
    $indexUrl = "https://nodejs.org/dist/index.json"

    Write-Log "INFO" "Node.js not found locally. Auto-downloading official runtime to .\node (arch=$arch)."

    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    }
    catch {
        # Keep going; modern PowerShell versions usually handle TLS defaults correctly.
    }

    $releases = Invoke-RestMethod -Uri $indexUrl -Method Get -TimeoutSec 30
    if (-not $releases) {
        throw "Failed to query Node.js release index: $indexUrl"
    }

    $targetRelease = $releases | Where-Object { $_.lts -and $_.files -contains $zipTag } | Select-Object -First 1
    if (-not $targetRelease) {
        throw "No LTS Node.js package found for $zipTag."
    }

    $version = [string]$targetRelease.version
    if ([string]::IsNullOrWhiteSpace($version)) {
        throw "Invalid Node.js version from release index."
    }

    $zipName = "node-$version-win-$arch.zip"
    $downloadUrl = "https://nodejs.org/dist/$version/$zipName"
    $tempRoot = Join-Path $RootPath "data\node_runtime_download"
    $zipPath = Join-Path $tempRoot $zipName
    $extractRoot = Join-Path $tempRoot "extract"

    try {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Path $extractRoot -Force | Out-Null

        Write-Log "INFO" "Downloading Node.js package: $downloadUrl"
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath -TimeoutSec 120

        Write-Log "INFO" "Extracting Node.js package..."
        Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force

        $extractedFolder = Get-ChildItem -Path $extractRoot -Directory | Select-Object -First 1
        if (-not $extractedFolder) {
            throw "Node.js package extraction failed: no extracted directory found."
        }

        if (-not (Test-Path $localNodeDir)) {
            New-Item -ItemType Directory -Path $localNodeDir -Force | Out-Null
        }
        else {
            Get-ChildItem -LiteralPath $localNodeDir -Force | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
        }

        Copy-Item -Path (Join-Path $extractedFolder.FullName "*") -Destination $localNodeDir -Recurse -Force

        if (-not (Test-Path $localNodeExe)) {
            throw "Node.js download completed but node.exe is missing in .\node."
        }

        Write-Log "INFO" "Local Node.js runtime ready: $localNodeExe ($version)"
        return $localNodeExe
    }
    finally {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$config = $null
$port = 58321
$bindAddress = "127.0.0.1"

try {
    if (Test-Path $configPath) {
        $config = Get-Content -Raw $configPath | ConvertFrom-Json

        if ($config -and $config.port) {
            $parsedPort = [int]$config.port
            if ($parsedPort -ge 1 -and $parsedPort -le 65535) {
                $port = $parsedPort
            }
        }

        if ($config -and $config.bindAddress) {
            $parsedBindAddress = [string]$config.bindAddress
            if (-not [string]::IsNullOrWhiteSpace($parsedBindAddress)) {
                $bindAddress = $parsedBindAddress.Trim()
            }
        }
    }
}
catch {
    Write-Log "WARN" "Failed to read config.json, fallback host/port: $($_.Exception.Message)"
}

if ([string]::IsNullOrWhiteSpace($bindAddress)) {
    $bindAddress = "127.0.0.1"
}

$launchContext = New-LaunchContext -BindAddress $bindAddress -Port $port
$url = $launchContext.Url
$readyUrls = $launchContext.ReadyUrls
$launchId = [guid]::NewGuid().ToString()
Set-Content -Path $activeLaunchPath -Value $launchId -Encoding ASCII

$mutexName = "Local\OperitPcAgentLauncher"
$mutex = New-Object System.Threading.Mutex($false, $mutexName)
$hasLock = $false
$originalPath = $env:Path

try {
    $hasLock = $mutex.WaitOne(0)
    if (-not $hasLock) {
        Write-Log "WARN" "Another launcher instance is running."
        exit 0
    }

    Write-Log "INFO" "===== operit_pc_agent.bat ====="
    Write-Log "INFO" "Launch ID: $launchId"
    Write-Log "INFO" "Working directory: $projectRoot"
    Write-Log "INFO" "Target bind: $bindAddress"
    Write-Log "INFO" "Target port: $port"
    Write-Log "INFO" "Ready probe URLs: $($readyUrls -join ', ')"

    $nodeRuntime = Resolve-NodeRuntime -RootPath $projectRoot
    if (-not $nodeRuntime) {
        try {
            Ensure-LocalNodeRuntime -RootPath $projectRoot | Out-Null
        }
        catch {
            Write-Log "ERROR" "Failed to auto-download local Node.js runtime: $($_.Exception.Message)"
            exit 1
        }

        $nodeRuntime = Resolve-NodeRuntime -RootPath $projectRoot
        if (-not $nodeRuntime) {
            Write-Log "ERROR" "Node.js runtime unavailable after auto-download. Please install Node.js 18+ manually."
            exit 1
        }
    }

    if ($nodeRuntime.Source -eq "local") {
        Write-Log "INFO" "System Node.js not found. Using local runtime: $($nodeRuntime.NodePath)"
    }

    if (-not [string]::IsNullOrWhiteSpace($nodeRuntime.NodeDir)) {
        $env:Path = "$($nodeRuntime.NodeDir);$env:Path"
    }

    $nodeVersion = (& $nodeRuntime.NodePath -v 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($nodeVersion)) {
        Write-Log "ERROR" "Failed to run Node runtime: $($nodeRuntime.NodePath)"
        exit 1
    }

    Write-Log "INFO" "Node version: $nodeVersion"

    try {
        Ensure-Dependencies -RootPath $projectRoot
    }
    catch {
        Write-Log "ERROR" "Dependency check/install failed: $($_.Exception.Message)"
        exit 1
    }

    $originalNodeOptions = $env:NODE_OPTIONS
    $env:NODE_OPTIONS = ""

    Write-Log "INFO" "Force restart: cleaning previous running process..."
    Stop-ExistingAgent -Port $port
    Remove-Item -LiteralPath $runtimePath -Force -ErrorAction SilentlyContinue
    Remove-StartupState

    Write-Log "INFO" "Launching node process (hidden, NODE_OPTIONS cleared)."
    $maxWaitMs = 15000
    $stepMs = 250
    $startAttempt = Start-AgentAndProbeReady -NodePath $nodeRuntime.NodePath -RootPath $projectRoot -OutLogPath $outLog -ErrLogPath $errLog -ReadyUrls $readyUrls -BindAddressOverride "" -MaxWaitMs $maxWaitMs -StepMs $stepMs
    $readyUrlHit = $startAttempt.ReadyUrlHit
    $isIpv4Bind = $false
    $isBindUnavailableError = $false

    if (-not $readyUrlHit) {
        Write-Log "ERROR" "agent readiness endpoint not ready after startup (${maxWaitMs}ms). candidates: $($readyUrls -join ', ')"
        $errTailText = Get-AgentErrTailText -TailLines 50

        $isIpv4Bind = $bindAddress -match '^\d{1,3}(\.\d{1,3}){3}$'
        $isBindUnavailableError = $errTailText -match 'EADDRNOTAVAIL'

        if ($isIpv4Bind -and $isBindUnavailableError) {
            $network = Get-RecommendedIpv4
            $recommendedHost = [string]$network.RecommendedHost
            $recommendedAlias = [string]$network.RecommendedInterfaceAlias
            $candidateListText = if ($network.Candidates -and $network.Candidates.Count -gt 0) { ($network.Candidates -join ", ") } else { "(none)" }

            Write-Log "INFO" "IPv4 change candidates: $candidateListText"
            Write-Log "WARN" "Configured bind unavailable: $bindAddress. Recommended IPv4: $recommendedHost (interface: $recommendedAlias)"

            try {
                Write-StartupState -IssueType "bindAddressUnavailable" -ConfiguredBindAddress $bindAddress -RecommendedBindAddress $recommendedHost -Ipv4Candidates $network.Candidates -Port $port -LaunchId $launchId
            }
            catch {
                Write-Log "WARN" "Failed to write startup_state.json: $($_.Exception.Message)"
            }

            $fallbackBindAddress = "127.0.0.1"
            $launchContext = New-LaunchContext -BindAddress $fallbackBindAddress -Port $port
            $url = $launchContext.Url
            $readyUrls = $launchContext.ReadyUrls

            Write-Log "INFO" "Retry probe URLs (fallback): $($readyUrls -join ', ')"
            Write-Log "WARN" "Retrying node process in temporary local mode for web-based recovery..."

            Stop-ExistingAgent -Port $port
            Remove-Item -LiteralPath $runtimePath -Force -ErrorAction SilentlyContinue

            $fallbackAttempt = Start-AgentAndProbeReady -NodePath $nodeRuntime.NodePath -RootPath $projectRoot -OutLogPath $outLog -ErrLogPath $errLog -ReadyUrls $readyUrls -BindAddressOverride $fallbackBindAddress -MaxWaitMs $maxWaitMs -StepMs $stepMs
            $readyUrlHit = $fallbackAttempt.ReadyUrlHit

            if (-not $readyUrlHit) {
                Write-Log "ERROR" "agent readiness endpoint still not ready after fallback retry (${maxWaitMs}ms). candidates: $($readyUrls -join ', ')"
                [void](Get-AgentErrTailText -TailLines 50)
                exit 1
            }

            Write-Log "INFO" "Ready endpoint confirmed in fallback mode: $readyUrlHit"
        }
        else {
            exit 1
        }
    }

    if ($readyUrlHit) {
        if ($isIpv4Bind -and $isBindUnavailableError) {
            Write-Log "WARN" "Agent is running in fallback local mode. Use web UI controls to apply recommended IPv4 and restart."
        }
        else {
            Remove-StartupState
            Write-Log "INFO" "Ready endpoint confirmed: $readyUrlHit"
        }
    }

    $resolvedPid = $null

    if (Test-Path $runtimePath) {
        try {
            $runtime = Get-Content -Raw $runtimePath | ConvertFrom-Json
            if ($runtime.pid) {
                $resolvedPid = [int]$runtime.pid
                Write-Log "INFO" "Runtime PID: $resolvedPid"
            }
        }
        catch {
            Write-Log "WARN" "Failed to parse runtime.json: $($_.Exception.Message)"
        }
    }

    if (-not $resolvedPid) {
        $listeningPid = Get-ListeningPid -Port $port
        if ($listeningPid) {
            $resolvedPid = [int]$listeningPid
            Write-Log "INFO" "Listening PID: $resolvedPid"
        }
    }

    if ($resolvedPid) {
        Set-Content -Path $pidPath -Value ([string]$resolvedPid) -Encoding ASCII
    }

    Write-Host "[OK] Operit PC Agent started on $url"
    Write-Log "OK" "Agent started: $url"

    $latestLaunchId = ""
    if (Test-Path $activeLaunchPath) {
        $latestLaunchId = (Get-Content -Raw $activeLaunchPath).Trim()
    }

    if ($latestLaunchId -eq $launchId) {
        Try-OpenBrowser -TargetUrl $url -Reason "startup"
    }
    else {
        Write-Log "INFO" "Browser open skipped - superseded by newer launch ID."
    }

    exit 0
}
finally {
    if ($null -eq $originalPath) {
        Remove-Item Env:Path -ErrorAction SilentlyContinue
    }
    else {
        $env:Path = $originalPath
    }

    if ($null -eq $originalNodeOptions) {
        Remove-Item Env:NODE_OPTIONS -ErrorAction SilentlyContinue
    }
    else {
        $env:NODE_OPTIONS = $originalNodeOptions
    }

    if ($hasLock) {
        $mutex.ReleaseMutex() | Out-Null
    }
    $mutex.Dispose()
}
