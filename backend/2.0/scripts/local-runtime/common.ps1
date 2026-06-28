$ErrorActionPreference = "Stop"

$Script:Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")).Path
$Script:Backend = Join-Path $Script:Root "backend\2.0"
$Script:Frontend = Join-Path $Script:Root "frontend"
$Script:Worker = Join-Path $Script:Backend "model-worker"
$Script:RunDir = Join-Path $Script:Backend ".local-run"
$Script:LogDir = Join-Path $Script:RunDir "logs"
$Script:PidDir = Join-Path $Script:RunDir "pids"

function Initialize-LocalRuntime {
    New-Item -ItemType Directory -Force -Path $Script:LogDir | Out-Null
    New-Item -ItemType Directory -Force -Path $Script:PidDir | Out-Null
}

function Get-LocalRuntimePath {
    param([Parameter(Mandatory = $true)][string]$Name)
    Initialize-LocalRuntime
    [pscustomobject]@{
        LogFile = Join-Path $Script:LogDir "$Name.log"
        PidFile = Join-Path $Script:PidDir "$Name.pid"
    }
}

function Get-PidFromFile {
    param([Parameter(Mandatory = $true)][string]$PidFile)
    if (-not (Test-Path -LiteralPath $PidFile)) { return $null }
    $RawPid = Get-Content -LiteralPath $PidFile -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $RawPid) { return $null }
    $PidValue = 0
    if ([int]::TryParse($RawPid, [ref]$PidValue)) { return $PidValue }
    return $null
}

function Test-ProcessId {
    param([int]$ProcessId)
    if ($ProcessId -le 0) { return $false }
    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Stop-PidFileProcess {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$PidFile
    )
    $Stopped = @()
    $ProcessId = Get-PidFromFile -PidFile $PidFile
    if ($ProcessId -and (Test-ProcessId -ProcessId $ProcessId)) {
        Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
        $Stopped += "$Name pid=$ProcessId"
    }
    Remove-Item -LiteralPath $PidFile -Force -ErrorAction SilentlyContinue
    return $Stopped
}

function Stop-PortProcess {
    param([Parameter(Mandatory = $true)][int[]]$Ports)
    $Stopped = @()
    foreach ($Port in $Ports) {
        $Connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        foreach ($Connection in $Connections) {
            $ProcessId = $Connection.OwningProcess
            if ($ProcessId -and $ProcessId -ne $PID) {
                Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue
                $Stopped += "port=$Port pid=$ProcessId"
            }
        }
    }
    return $Stopped
}

function Get-PortProcessInfo {
    param([Parameter(Mandatory = $true)][int]$Port)
    $Connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $Connection) { return $null }
    $Process = Get-Process -Id $Connection.OwningProcess -ErrorAction SilentlyContinue
    [pscustomobject]@{
        Port = $Port
        Pid = $Connection.OwningProcess
        ProcessName = $Process.ProcessName
    }
}

function Wait-HttpReady {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][string]$LogFile,
        [scriptblock]$IsReady = { param($Response) $null -ne $Response }
    )
    $Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $Deadline) {
        try {
            $Response = Invoke-RestMethod -Uri $Uri -TimeoutSec 3
            if (& $IsReady $Response) { return $true }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    Write-Host "$Name failed to become ready within $TimeoutSeconds seconds."
    Write-Host "Last $Name logs:"
    Get-Content -LiteralPath $LogFile -Tail 100 -ErrorAction SilentlyContinue
    return $false
}

function Get-HttpStatusText {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [scriptblock]$Formatter
    )
    try {
        $Response = Invoke-RestMethod -Uri $Uri -TimeoutSec 3
        if ($Formatter) { return (& $Formatter $Response) }
        if ($Response.status) { return $Response.status }
        return "OK"
    } catch {
        return "DOWN"
    }
}

function Test-TcpPort {
    param([Parameter(Mandatory = $true)][int]$Port)
    try {
        $Client = [System.Net.Sockets.TcpClient]::new()
        $Async = $Client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $Reachable = $Async.AsyncWaitHandle.WaitOne(2000, $false)
        if ($Reachable) { $Client.EndConnect($Async) }
        $Client.Close()
        return $Reachable
    } catch {
        return $false
    }
}

function Start-BackgroundPowerShell {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$LogFile,
        [Parameter(Mandatory = $true)][string]$PidFile
    )
    $Wrapper = @"
`$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath '$WorkingDirectory'
$Command *> '$LogFile'
"@
    $Process = Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $Wrapper -PassThru -WindowStyle Hidden
    $Process.Id | Set-Content -LiteralPath $PidFile -Encoding ASCII
    return $Process
}
