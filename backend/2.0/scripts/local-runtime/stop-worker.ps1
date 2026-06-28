$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
$Paths = Get-LocalRuntimePath -Name "worker"
$Stopped = @()
$Stopped += Stop-PidFileProcess -Name "worker" -PidFile $Paths.PidFile
$Stopped += Stop-PortProcess -Ports @(8095)
if ($Stopped.Count -eq 0) { Write-Host "Worker was not running." } else { $Stopped | ForEach-Object { Write-Host "Stopped $_" } }
