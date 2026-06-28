param(
    [ValidateSet("backend", "worker", "frontend", "all")]
    [string]$Service = "all",
    [int]$Tail = 100
)
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\common.ps1"
Initialize-LocalRuntime
$Names = if ($Service -eq "all") { @("backend", "worker", "frontend") } else { @($Service) }
foreach ($Name in $Names) {
    $Paths = Get-LocalRuntimePath -Name $Name
    Write-Host "==== $Name ($($Paths.LogFile)) ===="
    Get-Content -LiteralPath $Paths.LogFile -Tail $Tail -ErrorAction SilentlyContinue
}
