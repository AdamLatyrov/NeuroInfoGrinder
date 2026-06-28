$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
Initialize-LocalRuntime

$Port = 8095
$Paths = Get-LocalRuntimePath -Name "worker"
Stop-PidFileProcess -Name "worker" -PidFile $Paths.PidFile | ForEach-Object { Write-Host "Stopped $_" }
Stop-PortProcess -Ports @($Port) | ForEach-Object { Write-Host "Stopped $_" }

$Python = if ($env:PYTHON) { $env:PYTHON } else { "python" }
$Command = "& '$Python' -m uvicorn app:app --host 127.0.0.1 --port $Port"
$Process = Start-BackgroundPowerShell -Name "worker" -WorkingDirectory $Script:Worker -Command $Command -LogFile $Paths.LogFile -PidFile $Paths.PidFile
$Ready = Wait-HttpReady -Name "Worker" -Uri "http://127.0.0.1:$Port/health" -TimeoutSeconds 180 -LogFile $Paths.LogFile -IsReady { param($Response) $Response.status -eq "OK" -or $Response.status -eq "UP" -or $Response.status -eq "DEGRADED" -or $Response.worker -or $Response }
if (-not $Ready) {
    Stop-PidFileProcess -Name "worker" -PidFile $Paths.PidFile | Out-Null
    Stop-PortProcess -Ports @($Port) | Out-Null
    exit 1
}
$Health = Get-HttpStatusText -Uri "http://127.0.0.1:$Port/health"
Write-Host "Worker ready. PID=$($Process.Id), port=$Port, health=$Health, log=$($Paths.LogFile)"
