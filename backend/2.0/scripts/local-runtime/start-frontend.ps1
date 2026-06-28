$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
Initialize-LocalRuntime

$Port = 5173
$Paths = Get-LocalRuntimePath -Name "frontend"
Stop-PidFileProcess -Name "frontend" -PidFile $Paths.PidFile | ForEach-Object { Write-Host "Stopped $_" }
Stop-PortProcess -Ports @($Port) | ForEach-Object { Write-Host "Stopped $_" }

$Command = "npm run dev -- --host 127.0.0.1"
$Process = Start-BackgroundPowerShell -Name "frontend" -WorkingDirectory $Script:Frontend -Command $Command -LogFile $Paths.LogFile -PidFile $Paths.PidFile
$Ready = Wait-HttpReady -Name "Frontend" -Uri "http://127.0.0.1:$Port/" -TimeoutSeconds 60 -LogFile $Paths.LogFile
if (-not $Ready) {
    Stop-PidFileProcess -Name "frontend" -PidFile $Paths.PidFile | Out-Null
    Stop-PortProcess -Ports @($Port) | Out-Null
    exit 1
}
Write-Host "Frontend ready. PID=$($Process.Id), port=$Port, log=$($Paths.LogFile)"
