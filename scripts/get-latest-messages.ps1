param(
    [int]$Limit = 150,
    [string]$DbHost = "localhost",
    [int]$DbPort = 5433,
    [string]$DbName = "neuroinfogrinder",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "postgres",
    [long]$GroupId,
    [long]$TopicId
)

[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$psqlCandidates = @(
    "psql",
    "C:\Program Files\PostgreSQL\14\bin\psql.exe",
    "C:\Program Files\PostgreSQL\15\bin\psql.exe"
)

$psqlPath = $psqlCandidates | Where-Object {
    if ($_ -eq "psql") {
        $null -ne (Get-Command psql -ErrorAction SilentlyContinue)
    } else {
        Test-Path $_
    }
} | Select-Object -First 1

if (-not $psqlPath) {
    throw "psql not found. Install PostgreSQL client or add psql to PATH."
}

$filters = @()

if ($PSBoundParameters.ContainsKey("GroupId")) {
    $filters += "m.group_id = $GroupId"
}

if ($PSBoundParameters.ContainsKey("TopicId")) {
    $filters += "m.topic_id = $TopicId"
}

$whereClause = if ($filters.Count -gt 0) {
    "where " + ($filters -join " and ")
} else {
    ""
}

$sql = @"
select
  m.id,
  m.group_id,
  g.title as group_title,
  m.telegram_message_id,
  m.message_date,
  m.created_at,
  m.sender_name,
  m.sender_telegram_user_id,
  m.is_bot,
  m.topic_id,
  m.topic_name,
  m.processing_status,
  left(coalesce(m.text, ''), 300) as text_preview
from messages m
left join groups g on g.id = m.group_id
$whereClause
order by m.message_date desc, m.id desc
limit $Limit;
"@

$tempSqlFile = Join-Path $PSScriptRoot "get-latest-messages.tmp.sql"
[System.IO.File]::WriteAllText(
    $tempSqlFile,
    $sql,
    [System.Text.UTF8Encoding]::new($false)
)

try {
    $env:PGPASSWORD = $DbPassword
    $env:PGCLIENTENCODING = "UTF8"
    & $psqlPath `
        --set ON_ERROR_STOP=1 `
        -h $DbHost `
        -p $DbPort `
        -U $DbUser `
        -d $DbName `
        -f $tempSqlFile
} finally {
    Remove-Item $tempSqlFile -ErrorAction SilentlyContinue
}
