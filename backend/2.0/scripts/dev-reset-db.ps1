param(
    [string]$DbUrl = $env:DB_URL,
    [string]$DbUser = $env:DB_USERNAME,
    [string]$Profile = $env:SPRING_PROFILES_ACTIVE
)

$ErrorActionPreference = "Stop"
$confirmFlag = "--yes-i-know-this-is-local"

if (-not $DbUrl) {
    $DbUrl = "jdbc:postgresql://127.0.0.1:55432/neuroinfogrinder2_dev"
}
if (-not $DbUser) {
    $DbUser = "neuroinfogrinder2"
}
if (-not $Profile) {
    $Profile = "local"
}

if ($DbUrl -notmatch '^jdbc:postgresql://([^/:]+)(?::(\d+))?/([^?]+)') {
    throw "Only jdbc:postgresql URLs are supported by this reset script."
}

$dbHost = $Matches[1].ToLowerInvariant()
$dbPort = if ($Matches[2]) { $Matches[2] } else { "5432" }
$dbName = $Matches[3]
$profileLower = $Profile.ToLowerInvariant()

$allowedHosts = @("localhost", "127.0.0.1", "host.docker.internal", "postgres")
$allowedProfiles = @("local", "dev", "test")
$allowedDbMarkers = @("dev", "local", "test", "neuroinfogrinder2")

if ($allowedProfiles -notcontains $profileLower) {
    throw "Reset denied: active profile '$Profile' is not local/dev/test."
}
if ($allowedHosts -notcontains $dbHost) {
    throw "Reset denied: DB host '$dbHost' is not an explicitly local host."
}
if (-not ($allowedDbMarkers | Where-Object { $dbName.ToLowerInvariant().Contains($_) })) {
    throw "Reset denied: DB name '$dbName' does not contain a local/dev/test marker."
}

Write-Host "Target local DB:"
Write-Host "  host: $dbHost"
Write-Host "  port: $dbPort"
Write-Host "  database: $dbName"
Write-Host "  profile: $Profile"
Write-Host "  user: $DbUser"

if ($args -notcontains $confirmFlag) {
    Write-Host ""
    Write-Host "Dry run only. Re-run with $confirmFlag to truncate backend 2.0 app tables."
    exit 2
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    throw "psql was not found in PATH."
}

$truncateSql = @"
TRUNCATE TABLE
  message_links,
  raw_message_media,
  raw_messages,
  tdlib_update_inbox,
  telegram_chat_health,
  telegram_topics,
  telegram_chats,
  telegram_account_health,
  telegram_accounts,
  pipeline_events
RESTART IDENTITY CASCADE;
"@

$previousPgPassword = $env:PGPASSWORD
try {
    if ($env:DB_PASSWORD) {
        $env:PGPASSWORD = $env:DB_PASSWORD
    } elseif ($dbHost -eq "127.0.0.1" -and $dbPort -eq "55432" -and $dbName -eq "neuroinfogrinder2_dev") {
        $env:PGPASSWORD = "neuroinfogrinder2_local"
    }
    & psql -h $dbHost -p $dbPort -U $DbUser -d $dbName -v ON_ERROR_STOP=1 -c $truncateSql
} finally {
    $env:PGPASSWORD = $previousPgPassword
}

Write-Host "Local backend 2.0 app tables were reset."
