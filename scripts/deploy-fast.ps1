[CmdletBinding()]
param(
    [ValidateSet(
        "status",
        "tdlib",
        "frontend",
        "backend",
        "model-worker",
        "all",
        "restart-backend",
        "restart-frontend",
        "rollback-backend",
        "rollback-frontend"
    )]
    [string]$Target = "status",

    [ValidateSet("scp", "git", "none")]
    [string]$SyncMode = "scp",

    [string]$HostName = "185.130.212.188",
    [string]$User = "root",
    [string]$KeyPath = "$env:USERPROFILE\.ssh\neuroinfogrinder_ru_vps",
    [string]$RemoteDir = "/srv/neuroinfogrinder/app",
    [string]$TdlibCommit = "e0943d068ce90b5010f1aea946e6901e25b43bf6",

    [switch]$NoUpload,
    [switch]$NoPull,
    [switch]$KeepArchive
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$tdlibImage = "neuroinfogrinder-tdlib:$TdlibCommit"
$remote = "$User@$HostName"

if (-not (Test-Path -LiteralPath $KeyPath)) {
    throw "SSH key not found: $KeyPath"
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath failed with exit code $LASTEXITCODE"
    }
}

function Invoke-RemoteScript {
    param([Parameter(Mandatory = $true)][string]$Script)

    $normalized = $Script -replace "`r`n", "`n"
    $normalized | ssh -i $KeyPath -o StrictHostKeyChecking=yes $remote bash -s
    if ($LASTEXITCODE -ne 0) {
        throw "Remote command failed with exit code $LASTEXITCODE"
    }
}

function Test-RemoteGitWorktree {
    $probe = "cd '$RemoteDir' && git rev-parse --is-inside-work-tree >/dev/null 2>&1"
    $probe | ssh -i $KeyPath -o StrictHostKeyChecking=yes $remote bash -s
    return $LASTEXITCODE -eq 0
}

function Add-ExistingPath {
    param(
        [Parameter()]
        [System.Collections.Generic.List[string]]$Paths,

        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $nativePath = Join-Path $repoRoot ($RelativePath -replace "/", [System.IO.Path]::DirectorySeparatorChar)
    if (Test-Path -LiteralPath $nativePath) {
        $Paths.Add($RelativePath) | Out-Null
    }
}

function Add-ExistingGlob {
    param(
        [Parameter()]
        [System.Collections.Generic.List[string]]$Paths,

        [Parameter(Mandatory = $true)]
        [string]$Directory,

        [Parameter(Mandatory = $true)]
        [string]$Filter
    )

    $nativeDir = Join-Path $repoRoot ($Directory -replace "/", [System.IO.Path]::DirectorySeparatorChar)
    if (-not (Test-Path -LiteralPath $nativeDir)) {
        return
    }

    Get-ChildItem -LiteralPath $nativeDir -Filter $Filter -File | ForEach-Object {
        $relative = $_.FullName.Substring($repoRoot.Length + 1)
        $Paths.Add(($relative -replace "\\", "/")) | Out-Null
    }
}

function Get-ArchivePaths {
    param([Parameter(Mandatory = $true)][string]$DeployTarget)

    $paths = [System.Collections.Generic.List[string]]::new()

    foreach ($path in @(
        "docker-compose.prod.yml",
        "docker-compose.fast.yml",
        "docker-compose.backend2-clean.yml",
        "docker-compose.heavy-worker.yml",
        "deploy/tdlib/Dockerfile",
        "scripts/deploy-fast.ps1",
        "docs/fast-deploy.md"
    )) {
        Add-ExistingPath -Paths $paths -RelativePath $path
    }

    if ($DeployTarget -in @("backend", "model-worker", "all")) {
        foreach ($path in @(
            "backend/2.0/.dockerignore",
            "backend/2.0/pom.xml",
            "backend/2.0/Dockerfile",
            "backend/2.0/Dockerfile.fast",
            "backend/2.0/model-worker",
            "backend/2.0/src"
        )) {
            Add-ExistingPath -Paths $paths -RelativePath $path
        }
    }

    if ($DeployTarget -in @("frontend", "all")) {
        foreach ($path in @(
            "frontend/.dockerignore",
            "frontend/Dockerfile",
            "frontend/nginx.conf",
            "frontend/package.json",
            "frontend/package-lock.json",
            "frontend/index.html",
            "frontend/vite.config.ts",
            "frontend/public",
            "frontend/src"
        )) {
            Add-ExistingPath -Paths $paths -RelativePath $path
        }
        Add-ExistingGlob -Paths $paths -Directory "frontend" -Filter "tsconfig*.json"
    }

    return $paths | Select-Object -Unique
}

function New-DeployArchive {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DeployTarget
    )

    $paths = @(Get-ArchivePaths -DeployTarget $DeployTarget)
    if ($paths.Count -eq 0) {
        throw "No files selected for upload."
    }

    $archiveName = "neuroinfogrinder-$DeployTarget-$(Get-Date -Format 'yyyyMMddHHmmss').tar.gz"
    $archivePath = Join-Path ([System.IO.Path]::GetTempPath()) $archiveName

    Push-Location $repoRoot
    try {
        if (Test-Path -LiteralPath $archivePath) {
            Remove-Item -LiteralPath $archivePath -Force
        }

        Invoke-Native tar -czf $archivePath @paths
        $entryCount = (& tar -tzf $archivePath | Measure-Object).Count
        Write-Host "Created deploy archive: $archivePath ($entryCount entries)"
    }
    finally {
        Pop-Location
    }

    return $archivePath
}

function Sync-WithScp {
    param([Parameter(Mandatory = $true)][string]$DeployTarget)

    $archivePath = New-DeployArchive -DeployTarget $DeployTarget
    $archiveName = Split-Path -Leaf $archivePath
    $remoteArchive = "/tmp/$archiveName"

    try {
        Invoke-Native -FilePath scp -Arguments @(
            "-i",
            $KeyPath,
            "-o",
            "StrictHostKeyChecking=yes",
            $archivePath,
            "${remote}:$remoteArchive"
        )
        Invoke-RemoteScript @"
set -euo pipefail
cd '$RemoteDir'
tar -xzf '$remoteArchive' -C '$RemoteDir'
rm -f '$remoteArchive'
echo 'Uploaded files extracted into $RemoteDir'
"@
    }
    finally {
        if (-not $KeepArchive -and (Test-Path -LiteralPath $archivePath)) {
            Remove-Item -LiteralPath $archivePath -Force
        }
    }
}

function Sync-WithGit {
    if (-not (Test-RemoteGitWorktree)) {
        throw "Remote directory is not a git worktree. Use -SyncMode scp or prepare git worktree manually."
    }

    if ($NoPull) {
        Write-Host "Skipping git pull because -NoPull was passed."
        return
    }

    Invoke-RemoteScript @"
set -euo pipefail
cd '$RemoteDir'
git pull --ff-only
"@
}

function Invoke-Sync {
    param([Parameter(Mandatory = $true)][string]$DeployTarget)

    if ($DeployTarget -in @("status", "restart-backend", "restart-frontend", "rollback-backend", "rollback-frontend")) {
        return
    }

    if ($NoUpload) {
        Write-Host "Skipping upload because -NoUpload was passed."
        return
    }

    switch ($SyncMode) {
        "scp" { Sync-WithScp -DeployTarget $DeployTarget }
        "git" { Sync-WithGit }
        "none" { Write-Host "Skipping upload because -SyncMode none was selected." }
    }
}

$common = @"
set -euo pipefail
cd '$RemoteDir'
export TDLIB_IMAGE='$tdlibImage'
if [ -f docker-compose.fast.yml ]; then
  if [ -f docker-compose.backend2-clean.yml ]; then
    COMPOSE='docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml -f docker-compose.backend2-clean.yml --env-file .env'
  else
    COMPOSE='docker compose -f docker-compose.prod.yml -f docker-compose.fast.yml --env-file .env'
  fi
else
  COMPOSE='docker compose -f docker-compose.prod.yml --env-file .env'
fi
"@

$ensureFastFiles = @'
require_fast_files() {
  test -f docker-compose.fast.yml || { echo 'Missing docker-compose.fast.yml. Run with -SyncMode scp first.' >&2; return 1; }
  test -f backend/2.0/Dockerfile.fast || { echo 'Missing backend/2.0/Dockerfile.fast. Run with -SyncMode scp first.' >&2; return 1; }
  test -f deploy/tdlib/Dockerfile || { echo 'Missing deploy/tdlib/Dockerfile. Run with -SyncMode scp first.' >&2; return 1; }
}
'@

$ensureTdlib = @"
if ! docker image inspect '$tdlibImage' >/dev/null 2>&1; then
  echo 'Building TDLib base image once: $tdlibImage'
  docker build \
    -f deploy/tdlib/Dockerfile \
    -t '$tdlibImage' \
    --build-arg TDLIB_GIT_COMMIT='$TdlibCommit' \
    deploy/tdlib
else
  echo 'TDLib base image already exists: $tdlibImage'
fi
"@

$remoteHelpers = @'
wait_health() {
  service="$1"
  attempts="${2:-60}"
  echo "Waiting for ${service} health..."

  cid=$($COMPOSE ps -q "$service")
  if [ -z "$cid" ]; then
    echo "No container found for ${service}" >&2
    return 1
  fi

  for i in $(seq 1 "$attempts"); do
    status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || true)
    if [ "$status" = "healthy" ]; then
      echo "${service} health OK"
      return 0
    fi
    if [ "$status" = "running" ]; then
      echo "${service} is running (no Docker health status)"
      return 0
    fi
    sleep 2
  done

  echo "${service} health check timed out; last status=${status:-unknown}" >&2
  docker logs --tail=120 "$cid" >&2 || true
  return 1
}

tag_rollback() {
  service="$1"
  image="neuroinfogrinder-${service}:latest"
  rollback="neuroinfogrinder-${service}:rollback"
  if docker image inspect "$image" >/dev/null 2>&1; then
    docker tag "$image" "$rollback"
    echo "Tagged rollback image: $rollback"
  else
    echo "No current image to tag for ${service}"
  fi
}

rollback_service() {
  service="$1"
  image="neuroinfogrinder-${service}:latest"
  rollback="neuroinfogrinder-${service}:rollback"

  docker image inspect "$rollback" >/dev/null
  docker tag "$rollback" "$image"
  $COMPOSE up -d --no-deps --force-recreate "$service"
  wait_health "$service" 60
  $COMPOSE ps "$service"
}
'@

Invoke-Sync -DeployTarget $Target

switch ($Target) {
    "status" {
        Invoke-RemoteScript @"
$common
`$COMPOSE ps
$remoteHelpers
wait_health backend 5 || true
wait_health frontend 5 || true
curl -fsS -o /dev/null -w 'Public URL: http=%{http_code} time_total=%{time_total}\n' https://neuroinfogrinder.latrdev.ru/ || true
"@
    }

    "tdlib" {
        Invoke-RemoteScript @"
$common
$ensureFastFiles
require_fast_files
$ensureTdlib
"@
    }

    "frontend" {
        Invoke-RemoteScript @"
$common
$remoteHelpers
tag_rollback frontend
`$COMPOSE build frontend
`$COMPOSE up -d --no-deps frontend
wait_health frontend 60
`$COMPOSE ps frontend
"@
    }

    "backend" {
        Invoke-RemoteScript @"
$common
$ensureFastFiles
require_fast_files
$ensureTdlib
$remoteHelpers
tag_rollback backend
`$COMPOSE build backend
`$COMPOSE up -d --no-deps backend
wait_health backend 90
`$COMPOSE ps backend
"@
    }

    "model-worker" {
        Invoke-RemoteScript @"
$common
$ensureFastFiles
require_fast_files
$remoteHelpers
HEAVY_COMPOSE="`$COMPOSE -f docker-compose.heavy-worker.yml"
`$HEAVY_COMPOSE build model-worker
`$HEAVY_COMPOSE up -d --no-deps model-worker
wait_health model-worker 90
`$HEAVY_COMPOSE ps model-worker
"@
    }

    "all" {
        Invoke-RemoteScript @"
$common
$ensureFastFiles
require_fast_files
$ensureTdlib
$remoteHelpers
tag_rollback backend
tag_rollback frontend
`$COMPOSE build backend frontend
`$COMPOSE up -d --no-deps backend frontend
wait_health backend 90
wait_health frontend 60
`$COMPOSE ps backend frontend
"@
    }

    "restart-backend" {
        Invoke-RemoteScript @"
$common
$remoteHelpers
`$COMPOSE restart backend
wait_health backend 90
`$COMPOSE ps backend
"@
    }

    "restart-frontend" {
        Invoke-RemoteScript @"
$common
$remoteHelpers
`$COMPOSE restart frontend
wait_health frontend 60
`$COMPOSE ps frontend
"@
    }

    "rollback-backend" {
        Invoke-RemoteScript @"
$common
$remoteHelpers
rollback_service backend
"@
    }

    "rollback-frontend" {
        Invoke-RemoteScript @"
$common
$remoteHelpers
rollback_service frontend
"@
    }
}
