$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
Initialize-LocalRuntime

$Port = if ($env:BACKEND_PORT) { [int]$env:BACKEND_PORT } else { 8080 }
if ($Port -notin @(8080, 8081)) { throw "Backend port must be 8080 or 8081." }
$Paths = Get-LocalRuntimePath -Name "backend"

Stop-PidFileProcess -Name "backend" -PidFile $Paths.PidFile | ForEach-Object { Write-Host "Stopped $_" }
Stop-PortProcess -Ports @($Port) | ForEach-Object { Write-Host "Stopped $_" }

$EnvBlock = @"
`$env:SERVER_PORT = '$Port'
`$env:DB_URL = 'jdbc:postgresql://127.0.0.1:5433/neuroinfogrinder2_dev'
`$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:5433/neuroinfogrinder2_dev'
`$env:DB_USERNAME = 'postgres'
`$env:SPRING_DATASOURCE_USERNAME = 'postgres'
"@
$Command = "$EnvBlock`nmvn spring-boot:run"
$Process = Start-BackgroundPowerShell -Name "backend" -WorkingDirectory $Script:Backend -Command $Command -LogFile $Paths.LogFile -PidFile $Paths.PidFile

$Ready = Wait-HttpReady -Name "Backend" -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSeconds 90 -LogFile $Paths.LogFile -IsReady { param($Response) $Response.status -eq "UP" }
if (-not $Ready) {
    Stop-PidFileProcess -Name "backend" -PidFile $Paths.PidFile | Out-Null
    Stop-PortProcess -Ports @($Port) | Out-Null
    exit 1
}
Write-Host "Backend ready. PID=$($Process.Id), port=$Port, log=$($Paths.LogFile)"
