$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
$Paths = Get-LocalRuntimePath -Name "backend"
$Stopped = @()
$Stopped += Stop-PidFileProcess -Name "backend" -PidFile $Paths.PidFile
$Stopped += Stop-PortProcess -Ports @(8080, 8081)
if ($Stopped.Count -eq 0) { Write-Host "Backend was not running." } else { $Stopped | ForEach-Object { Write-Host "Stopped $_" } }
