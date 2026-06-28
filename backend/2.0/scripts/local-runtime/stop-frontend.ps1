$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
$Paths = Get-LocalRuntimePath -Name "frontend"
$Stopped = @()
$Stopped += Stop-PidFileProcess -Name "frontend" -PidFile $Paths.PidFile
$Stopped += Stop-PortProcess -Ports @(5173)
if ($Stopped.Count -eq 0) { Write-Host "Frontend was not running." } else { $Stopped | ForEach-Object { Write-Host "Stopped $_" } }
