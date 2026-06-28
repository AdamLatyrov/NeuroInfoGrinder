$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
Initialize-LocalRuntime

function Show-ServiceStatus {
    param([string]$Name, [int]$Port, [string]$HealthUri, [scriptblock]$Formatter)
    $Paths = Get-LocalRuntimePath -Name $Name.ToLowerInvariant()
    $FilePid = Get-PidFromFile -PidFile $Paths.PidFile
    $PortInfo = Get-PortProcessInfo -Port $Port
    $Health = if ($HealthUri) { Get-HttpStatusText -Uri $HealthUri -Formatter $Formatter } else { "n/a" }
    Write-Host "${Name}:"
    Write-Host "  pid: $FilePid"
    Write-Host "  port: $Port"
    Write-Host "  portProcess: $(if ($PortInfo) { "$($PortInfo.ProcessName) pid=$($PortInfo.Pid)" } else { "none" })"
    Write-Host "  health: $Health"
    Write-Host "  log: $($Paths.LogFile)"
}

Show-ServiceStatus -Name "Backend" -Port 8080 -HealthUri "http://127.0.0.1:8080/actuator/health" -Formatter { param($Response) $Response.status }
if (-not (Get-PortProcessInfo -Port 8080)) {
    Show-ServiceStatus -Name "Backend" -Port 8081 -HealthUri "http://127.0.0.1:8081/actuator/health" -Formatter { param($Response) $Response.status }
}

$WorkerPaths = Get-LocalRuntimePath -Name "worker"
$WorkerPid = Get-PidFromFile -PidFile $WorkerPaths.PidFile
$WorkerPort = Get-PortProcessInfo -Port 8095
$WorkerHealth = try { Invoke-RestMethod -Uri "http://127.0.0.1:8095/health" -TimeoutSec 3 } catch { $null }
Write-Host "Worker:"
Write-Host "  pid: $WorkerPid"
Write-Host "  port: 8095"
Write-Host "  portProcess: $(if ($WorkerPort) { "$($WorkerPort.ProcessName) pid=$($WorkerPort.Pid)" } else { "none" })"
Write-Host "  health: $(if ($WorkerHealth) { if ($WorkerHealth.status) { $WorkerHealth.status } else { "OK" } } else { "DOWN" })"
Write-Host "  bge: $(if ($WorkerHealth -and $WorkerHealth.bge) { $WorkerHealth.bge } elseif ($WorkerHealth -and $WorkerHealth.bgeConfigured -ne $null) { $WorkerHealth.bgeConfigured } else { "unknown" })"
Write-Host "  classifier: $(if ($WorkerHealth -and $WorkerHealth.classifier) { $WorkerHealth.classifier } elseif ($WorkerHealth -and $WorkerHealth.classifierConfigured -ne $null) { $WorkerHealth.classifierConfigured } else { "unknown" })"
Write-Host "  log: $($WorkerPaths.LogFile)"

Show-ServiceStatus -Name "Frontend" -Port 5173 -HealthUri "http://127.0.0.1:5173/"

Write-Host "Database:"
Write-Host "  host: 127.0.0.1"
Write-Host "  port: 5433"
Write-Host "  reachable: $(Test-TcpPort -Port 5433)"
