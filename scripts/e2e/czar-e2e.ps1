<#
.SYNOPSIS
    Czar Backend â€” End-to-End Test Runner

.DESCRIPTION
    Starts Docker services, runs all API test suites, reports results.
    Uses test-config.yml (or a config file you specify) to control
    which services to start and which test suites to run.

.PARAMETER ConfigFile
    Path to the YAML config file. Default: .\test-config.yml

.PARAMETER Services
    Comma-separated list of services to start, overriding config.
    E.g.: "czar-gateway,czar-auth,czar-user"

.PARAMETER Tests
    Comma-separated list of test suites to run, overriding config.
    E.g.: "health,auth,user"

.PARAMETER Rebuild
    Rebuild Docker images before starting.

.PARAMETER StopAfter
    Stop Docker services after tests complete.

.PARAMETER SkipDockerManagement
    Do not start/stop Docker. Assume services are already running.

.PARAMETER BaseUrl
    Override the gateway base URL. Default: http://localhost:8080

.EXAMPLE
    # Full run with defaults
    .\czar-e2e.ps1

    # Only auth and user tests, services already running
    .\czar-e2e.ps1 -SkipDockerManagement -Tests auth,user

    # Rebuild and run specific services only
    .\czar-e2e.ps1 -Rebuild -Services "czar-gateway,czar-auth,czar-user"

    # Full run and stop everything after
    .\czar-e2e.ps1 -StopAfter
#>
[CmdletBinding()]
param(
    [string]  $ConfigFile           = "",  # resolved below after PSScriptRoot is available
    [string[]]$Services             = @(),
    [string[]]$Tests                = @(),
    [switch]  $Rebuild,
    [switch]  $StopAfter,
    [switch]  $SkipDockerManagement,
    [string]  $BaseUrl              = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Continue"  # Don't kill the session on errors; handled per-test
# Resolve script directory robustly (works both interactively and in subprocess)
$ScriptDir = if ($PSScriptRoot -and $PSScriptRoot -ne '') { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
if (-not $ScriptDir) { $ScriptDir = $PWD.Path }
if ($ConfigFile -eq '') { $ConfigFile = Join-Path $ScriptDir 'test-config.yml' }

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# Colour helpers
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Write-Pass   { param([string]$msg) Write-Host "  [PASS] $msg" -ForegroundColor Green  }
function Write-Fail   { param([string]$msg) Write-Host "  [FAIL] $msg" -ForegroundColor Red    }
function Write-Skip   { param([string]$msg) Write-Host "  [SKIP] $msg" -ForegroundColor DarkGray }
function Write-Info   { param([string]$msg) Write-Host "  [INFO] $msg" -ForegroundColor Cyan    }
function Write-Warn   { param([string]$msg) Write-Host "  [WARN] $msg" -ForegroundColor Yellow  }
function Write-Header { param([string]$msg) Write-Host "`n====== $msg ======" -ForegroundColor Magenta }
function Write-Sub    { param([string]$msg) Write-Host "  â”€â”€ $msg" -ForegroundColor White }

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# Minimal YAML parser â€” handles the specific structure of test-config.yml
# Returns nested hashtable.
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Read-SimpleYaml {
    param([string]$Path)
    $result  = @{}
    $section = $null
    foreach ($raw in (Get-Content $Path)) {
        $line = $raw -replace '#.*$', ''   # strip comments
        if ($line.Trim() -eq '') { continue }

        # Top-level key with no value â†’ start a section
        if ($line -match '^([a-zA-Z_][a-zA-Z0-9_-]*):\s*$') {
            $section = $Matches[1]
            $result[$section] = @{}
            continue
        }
        # Indented key: value
        if ($line -match '^\s{2,}([a-zA-Z_][a-zA-Z0-9_./-]*):\s*(.+)$') {
            $k  = $Matches[1].Trim()
            $v  = $Matches[2].Trim().Trim('"').Trim("'")
            $bv = if ($v -eq 'true') { $true } elseif ($v -eq 'false') { $false } else { $v }
            if ($null -ne $section) { $result[$section][$k] = $bv } else { $result[$k] = $bv }
            continue
        }
        # Top-level key: value (no section)
        if ($line -match '^([a-zA-Z_][a-zA-Z0-9_./-]*):\s*(.+)$') {
            $k  = $Matches[1].Trim()
            $v  = $Matches[2].Trim().Trim('"').Trim("'")
            $bv = if ($v -eq 'true') { $true } elseif ($v -eq 'false') { $false } else { $v }
            $result[$k] = $bv
            $section = $null
        }
    }
    return $result
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# HTTP helper â€” wraps Invoke-RestMethod with consistent error handling.
# Returns @{ ok=$true/false; status=int; body=<object>; error=<string> }
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Invoke-Api {
    param(
        [string]  $Method,
        [string]  $Url,
        [object]  $Body       = $null,
        [string]  $Token      = "",
        [string]  $ServiceToken = "",
        [int]     $TimeoutSec = 30,
        [int[]]   $ExpectedStatus = @(200, 201, 204)
    )
    $headers = @{ "Content-Type" = "application/json" }
    if ($Token)        { $headers["Authorization"]  = "Bearer $Token" }
    if ($ServiceToken) { $headers["X-Service-Token"] = $ServiceToken  }

    $bodyJson = if ($null -ne $Body) { $Body | ConvertTo-Json -Depth 10 } else { $null }
    try {
        $reqParams = @{
            Method          = $Method
            Uri             = $Url
            Headers         = $headers
            TimeoutSec      = $TimeoutSec
            UseBasicParsing = $true
            ErrorAction     = "Stop"
        }
        if ($null -ne $bodyJson) { $reqParams["Body"] = $bodyJson }

        $resp       = Invoke-WebRequest @reqParams
        $statusCode = [int]$resp.StatusCode
        $bodyObj    = $null
        if ($null -ne $resp.Content) {
            try {
                $contentStr = if ($resp.Content -is [byte[]]) {
                    [System.Text.Encoding]::UTF8.GetString($resp.Content)
                } else {
                    [string]$resp.Content
                }
                if ($contentStr.Trim() -ne '') {
                    $bodyObj = $contentStr | ConvertFrom-Json
                }
            } catch {}
        }
        return @{ ok = $true; status = $statusCode; body = $bodyObj; error = "" }
    }
    catch [System.Net.WebException] {
        $statusCode = 0
        $errorBody  = $_.Exception.Message
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            try {
                $stream    = $_.Exception.Response.GetResponseStream()
                $reader    = New-Object System.IO.StreamReader($stream)
                $errorBody = $reader.ReadToEnd()
                $reader.Close()
            } catch {}
        }
        $ok = $statusCode -in $ExpectedStatus
        return @{ ok = $ok; status = $statusCode; body = $null; error = $errorBody }
    }
    catch {
        return @{ ok = $false; status = 0; body = $null; error = $_.Exception.Message }
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# Test recording
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
$script:Results = [System.Collections.Generic.List[hashtable]]::new()
$script:CurrentSuite = ""

function Add-Result {
    param([string]$Name, [bool]$Passed, [string]$Detail = "")
    $r = @{ Suite = $script:CurrentSuite; Name = $Name; Passed = $Passed; Detail = $Detail }
    $script:Results.Add($r)
    if ($Passed) { Write-Pass $Name } else { Write-Fail "$Name - $Detail" }
}

function Assert-Status {
    param([string]$Name, [hashtable]$Resp, [int[]]$Expected = @(200))
    $ok = $Resp.status -in $Expected
    $detail = if ($ok) { "HTTP $($Resp.status)" } else { "Expected $Expected, got $($Resp.status). $($Resp.error)" }
    Add-Result $Name $ok $detail
    return $ok
}

function Assert-Field {
    param([string]$Name, [object]$Obj, [string]$Field)
    $ok = ($null -ne $Obj) -and ($null -ne $Obj.$Field) -and ($Obj.$Field -ne "")
    $detail = if ($ok) { "" } else { "Field '$Field' missing or null" }
    Add-Result $Name $ok $detail
    return $ok
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# Docker helpers
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Start-Services {
    param([hashtable]$Cfg, [hashtable]$SvcEnabled)
    $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    Push-Location $repoRoot
    try {
        $e2eOverlay = Join-Path $repoRoot 'docker-compose.e2e.yml'
        if (Test-Path $e2eOverlay) {
            $composeArgs = "-f docker-compose.yml -f docker-compose.e2e.yml"
        } else {
            $composeArgs = "-f docker-compose.yml"
        }
        if ($SvcEnabled['rebuild']) {
            Write-Info "Building Docker images..."
            Invoke-Expression "docker compose $composeArgs build" 2>&1 | ForEach-Object { Write-Host "    $_" }
        }
        Write-Info "Starting infrastructure (postgres + pubsub-emulator)..."
        Invoke-Expression "docker compose $composeArgs up -d postgres pubsub-emulator" 2>&1 | Out-Null

        # Determine which app services to start
        $appServices = @()
        foreach ($svc in @('czar-gateway','czar-auth','czar-user','czar-planner','czar-notes','czar-voice-ai','czar-conflict','czar-notification')) {
            if ($SvcEnabled[$svc]) { $appServices += $svc }
        }
        if ($appServices.Count -gt 0) {
            Write-Info "Starting app services: $($appServices -join ', ')..."
            Invoke-Expression "docker compose $composeArgs up -d $($appServices -join ' ')" 2>&1 | Out-Null
        }
    }
    finally { Pop-Location }
}

function Stop-Services {
    $repoRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
    Write-Info "Stopping all services..."
    Push-Location $repoRoot
    try {
        $e2eOverlay = Join-Path $repoRoot 'docker-compose.e2e.yml'
        if (Test-Path $e2eOverlay) {
            Invoke-Expression "docker compose -f docker-compose.yml -f docker-compose.e2e.yml down" 2>&1 | Out-Null
        } else {
            Invoke-Expression "docker compose -f docker-compose.yml down" 2>&1 | Out-Null
        }
    }
    finally { Pop-Location }
}

function Wait-ForHealth {
    param([hashtable]$DirectUrls, [hashtable]$SvcEnabled, [int]$TimeoutSec)
    $checks = @{}
    foreach ($svc in $DirectUrls.Keys) {
        $key = $svc -replace '^czar-', ''   # e.g. "auth"
        if ($SvcEnabled[$svc]) {
            $checks[$svc] = "$($DirectUrls[$svc])/actuator/health"
        }
    }
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    Write-Info "Waiting up to ${TimeoutSec}s for services to become healthyâ€¦"
    $pending = [System.Collections.Generic.HashSet[string]]($checks.Keys)
    while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
        $done = @()
        foreach ($svc in @($pending)) {
            try {
                $r = Invoke-RestMethod -Uri $checks[$svc] -TimeoutSec 3 -ErrorAction Stop
                if ($r.status -eq 'UP') {
                    Write-Pass "$svc is UP"
                    $done += $svc
                }
            } catch {}
        }
        foreach ($d in $done) { [void]$pending.Remove($d) }
        if ($pending.Count -gt 0) { Start-Sleep -Seconds 3 }
    }
    if ($pending.Count -gt 0) {
        Write-Warn "These services did not become healthy in time: $($pending -join ', ')"
        Write-Warn "Tests may fail. Continuing anywayâ€¦"
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# OTP helper â€” reads latest OTP from local postgres via docker exec
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Get-LatestOtp {
    param([string]$Email)
    try {
        # czar-auth logs OTP to console when SENDGRID_API_KEY is blank (dev / E2E mode):
        # "=== [DEV MODE] OTP for <email> : <code> ==="
        $logs  = & docker logs czar-auth 2>&1
        $match = $logs | Select-String "DEV MODE.*$([regex]::Escape($Email)).*:\s*(\d{6})" | Select-Object -Last 1
        if ($match) {
            $m = [regex]::Match($match.Line, ':\s*(\d{6})\s*===')
            if ($m.Success) { return $m.Groups[1].Value }
        }
        return $null
    } catch {
        return $null
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• TEST SUITE: HEALTH â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Test-HealthSuite {
    param([hashtable]$Ctx)
    $script:CurrentSuite = "Health"
    Write-Header "Health Checks"

    $endpoints = @{
        "Gateway"      = "$($Ctx.BaseUrl)/actuator/health"
        "Auth"         = "$($Ctx.AuthUrl)/actuator/health"
        "User"         = "$($Ctx.UserUrl)/actuator/health"
        "Planner"      = "$($Ctx.PlannerUrl)/actuator/health"
        "Notes"        = "$($Ctx.NotesUrl)/actuator/health"
        "VoiceAI"      = "$($Ctx.VoiceAiUrl)/actuator/health"
    }

    foreach ($name in $endpoints.Keys) {
        $url = $endpoints[$name]
        try {
            $r = Invoke-RestMethod -Uri $url -TimeoutSec $Ctx.Timeout -ErrorAction Stop
            $ok = $r.status -eq 'UP'
            $detail = if (-not $ok) { "Status: $($r.status)" } else { "" }
            Add-Result "$name /actuator/health -> UP" $ok $detail
        } catch {
            Add-Result "$name /actuator/health â†’ UP" $false "Error: $_"
        }
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• TEST SUITE: AUTH â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Test-AuthSuite {
    param([hashtable]$Ctx)
    $script:CurrentSuite = "Auth"
    Write-Header "Auth - OTP Flow, JWT, Refresh, JWKS"

    $base   = $Ctx.BaseUrl
    $email  = $Ctx.TestEmail
    $to     = $Ctx.Timeout

    # 1. JWKS endpoint
    Write-Sub "GET /auth/.well-known/jwks.json"
    $r = Invoke-Api "GET" "$base/auth/.well-known/jwks.json" -TimeoutSec $to
    if (Assert-Status "JWKS endpoint returns 200" $r @(200)) {
        Add-Result "JWKS response has 'keys' array" ($null -ne $r.body.keys) ""
    }

    # 2. Request OTP
    Write-Sub "POST /auth/email/request-otp"
    $r = Invoke-Api "POST" "$base/auth/email/request-otp" @{ email = $email } -TimeoutSec $to
    Assert-Status "Request OTP returns 200" $r @(200) | Out-Null

    # 3. Fetch OTP from DB
    Write-Sub "Reading OTP from databaseâ€¦"
    Start-Sleep -Seconds 1
    $otp = Get-LatestOtp $email
    if ($null -eq $otp -or $otp -eq "") {
        Add-Result "OTP found in database" $false "Could not read OTP - check postgres is running"
        Write-Warn "Auth suite cannot continue without OTP. Skipping remaining auth tests."
        return
    }
    Add-Result "OTP found in database" $true "OTP: $otp"

    # 4. Verify OTP
    Write-Sub "POST /auth/email/verify-otp"
    $r = Invoke-Api "POST" "$base/auth/email/verify-otp" @{ email = $email; otp = $otp } -TimeoutSec $to
    if (-not (Assert-Status "Verify OTP returns 200" $r @(200))) {
        Write-Warn "Cannot proceed without a valid access token."
        return
    }
    Assert-Field "Response has accessToken"  $r.body "accessToken"  | Out-Null
    Assert-Field "Response has refreshToken" $r.body "refreshToken" | Out-Null
    Assert-Field "Response has tokenType"    $r.body "tokenType"    | Out-Null

    $Ctx.AccessToken  = $r.body.accessToken
    $Ctx.RefreshToken = $r.body.refreshToken
    Write-Info "Access token obtained."

    # 5. Verify OTP again (replay) â†’ should fail
    Write-Sub "POST /auth/email/verify-otp (replay attack)"
    $r2 = Invoke-Api "POST" "$base/auth/email/verify-otp" @{ email = $email; otp = $otp } -TimeoutSec $to -ExpectedStatus @(400, 401, 404)
    Add-Result "Replay of used OTP is rejected (4xx)" ($r2.status -in @(400, 401, 404)) "Got $($r2.status)"

    # 6. Verify OTP â€” wrong code
    Write-Sub "POST /auth/email/verify-otp (wrong OTP)"
    $r3 = Invoke-Api "POST" "$base/auth/email/verify-otp" @{ email = $email; otp = "000000" } -TimeoutSec $to -ExpectedStatus @(400, 401, 404)
    Add-Result "Wrong OTP is rejected (4xx)" ($r3.status -in @(400, 401, 404)) "Got $($r3.status)"

    # 7. Refresh token
    Write-Sub "POST /auth/token/refresh"
    $r = Invoke-Api "POST" "$base/auth/token/refresh" @{ refreshToken = $Ctx.RefreshToken } -TimeoutSec $to
    if (Assert-Status "Token refresh returns 200" $r @(200)) {
        Assert-Field "Refreshed accessToken present"  $r.body "accessToken"  | Out-Null
        Assert-Field "Refreshed refreshToken present" $r.body "refreshToken" | Out-Null
        $Ctx.AccessToken  = $r.body.accessToken
        $Ctx.RefreshToken = $r.body.refreshToken
        Write-Info "Token refreshed."
    }

    # 8. Protected route without token â†’ 401
    Write-Sub "GET /api/v1/users/me (no token)"
    $r = Invoke-Api "GET" "$base/api/v1/users/me" -TimeoutSec $to -ExpectedStatus @(401, 403)
    Add-Result "Unauthenticated request rejected (401/403)" ($r.status -in @(401, 403)) "Got $($r.status)"

    # 9. Logout
    Write-Sub "POST /auth/token/logout"
    $refreshBeforeLogout = $Ctx.RefreshToken
    $r = Invoke-Api "POST" "$base/auth/token/logout" @{ refreshToken = $Ctx.RefreshToken } -TimeoutSec $to -ExpectedStatus @(204, 200)
    Assert-Status "Logout returns 204/200" $r @(204, 200) | Out-Null

    # 10. Refresh after logout â†’ should fail
    Write-Sub "POST /auth/token/refresh (after logout)"
    $r2 = Invoke-Api "POST" "$base/auth/token/refresh" @{ refreshToken = $refreshBeforeLogout } -TimeoutSec $to -ExpectedStatus @(400, 401, 404)
    Add-Result "Refresh after logout is rejected (4xx)" ($r2.status -in @(400, 401, 404)) "Got $($r2.status)"

    # Re-login to get fresh tokens for remaining suites
    Write-Sub "Re-login to get fresh token for remaining suitesâ€¦"
    $r = Invoke-Api "POST" "$base/auth/email/request-otp" @{ email = $email } -TimeoutSec $to
    Start-Sleep -Seconds 1
    $otp2 = Get-LatestOtp $email
    if ($null -ne $otp2 -and $otp2 -ne "") {
        $r = Invoke-Api "POST" "$base/auth/email/verify-otp" @{ email = $email; otp = $otp2 } -TimeoutSec $to
        if ($r.ok -and $null -ne $r.body.accessToken) {
            $Ctx.AccessToken  = $r.body.accessToken
            $Ctx.RefreshToken = $r.body.refreshToken
            Write-Info "Re-login successful. Token ready for downstream tests."
        }
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• TEST SUITE: USER â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Test-UserSuite {
    param([hashtable]$Ctx)
    $script:CurrentSuite = "User"
    Write-Header "User - Profile, Tags, Preferences, Device Token"

    $base  = $Ctx.BaseUrl
    $token = $Ctx.AccessToken
    $to    = $Ctx.Timeout

    if (-not $token) { Write-Skip "No access token - skipping User suite" ; return }

    # 1. GET profile
    Write-Sub "GET /api/v1/users/me"
    $r = Invoke-Api "GET" "$base/api/v1/users/me" -Token $token -TimeoutSec $to
    if (Assert-Status "GET profile returns 200" $r @(200)) {
        Assert-Field "Profile has id"          $r.body "id"          | Out-Null
        Assert-Field "Profile has createdAt"   $r.body "createdAt"   | Out-Null
    }

    # 2. PATCH profile
    Write-Sub "PATCH /api/v1/users/me"
    $r = Invoke-Api "PATCH" "$base/api/v1/users/me" @{ displayName = "E2E Test User" } -Token $token -TimeoutSec $to
    if (Assert-Status "PATCH profile returns 200" $r @(200)) {
        Add-Result "Display name updated correctly" ($r.body.displayName -eq "E2E Test User") "Got: $($r.body.displayName)"
    }

    # 3. GET profile again â€” verify update persisted
    Write-Sub "GET /api/v1/users/me (verify update)"
    $r = Invoke-Api "GET" "$base/api/v1/users/me" -Token $token -TimeoutSec $to
    Add-Result "Display name persisted across requests" ($r.body.displayName -eq "E2E Test User") "Got: $($r.body.displayName)"

    # 4. GET preferences
    Write-Sub "GET /api/v1/users/me/preferences"
    $r = Invoke-Api "GET" "$base/api/v1/users/me/preferences" -Token $token -TimeoutSec $to
    if (Assert-Status "GET preferences returns 200" $r @(200)) {
        Assert-Field "Preferences has theme" $r.body "theme" | Out-Null
    }

    # 5. PATCH preferences
    Write-Sub "PATCH /api/v1/users/me/preferences"
    $r = Invoke-Api "PATCH" "$base/api/v1/users/me/preferences" @{ theme = "dark"; dashboardCollapsed = $true } -Token $token -TimeoutSec $to
    if (Assert-Status "PATCH preferences returns 200" $r @(200)) {
        Add-Result "Theme set to dark" ($r.body.theme -eq "dark") "Got: $($r.body.theme)"
    }

    # 6. GET tags (should have defaults)
    Write-Sub "GET /api/v1/users/me/tags"
    $r = Invoke-Api "GET" "$base/api/v1/users/me/tags" -Token $token -TimeoutSec $to
    if (Assert-Status "GET tags returns 200" $r @(200)) {
        $count = if ($r.body -is [array]) { $r.body.Count } else { 0 }
        Add-Result "Default tags seeded (expect >= 5)" ($count -ge 5) "Got $count tags"
        $Ctx.DefaultTagId = if ($r.body -is [array] -and $r.body.Count -gt 0) { $r.body[0].id } else { $null }
    }

    # 7. POST new tag
    Write-Sub "POST /api/v1/users/me/tags"
    $r = Invoke-Api "POST" "$base/api/v1/users/me/tags" @{ name = "E2ETag"; colorHex = "#FF5733" } -Token $token -TimeoutSec $to
    if (Assert-Status "POST tag returns 201" $r @(201)) {
        Assert-Field "Created tag has id"   $r.body "id"   | Out-Null
        Assert-Field "Created tag has name" $r.body "name" | Out-Null
        $Ctx.TestTagId = $r.body.id
    }

    # 8. PATCH tag
    if ($Ctx.TestTagId) {
        Write-Sub "PATCH /api/v1/users/me/tags/{id}"
        $r = Invoke-Api "PATCH" "$base/api/v1/users/me/tags/$($Ctx.TestTagId)" @{ name = "E2ETagUpdated"; colorHex = "#00FF88" } -Token $token -TimeoutSec $to
        if (Assert-Status "PATCH tag returns 200" $r @(200)) {
            Add-Result "Tag name updated" ($r.body.name -eq "E2ETagUpdated") "Got: $($r.body.name)"
        }
    }

    # 9. POST duplicate tag â†’ 409
    Write-Sub "POST /api/v1/users/me/tags (duplicate)"
    $r = Invoke-Api "POST" "$base/api/v1/users/me/tags" @{ name = "E2ETagUpdated"; colorHex = "#FF5733" } -Token $token -TimeoutSec $to -ExpectedStatus @(409)
    Add-Result "Duplicate tag name rejected (409)" ($r.status -eq 409) "Got $($r.status)"

    # 10. DELETE tag
    if ($Ctx.TestTagId) {
        Write-Sub "DELETE /api/v1/users/me/tags/{id}"
        $r = Invoke-Api "DELETE" "$base/api/v1/users/me/tags/$($Ctx.TestTagId)" -Token $token -TimeoutSec $to -ExpectedStatus @(204)
        Assert-Status "DELETE tag returns 204" $r @(204) | Out-Null
    }

    # 11. POST device token
    Write-Sub "POST /api/v1/users/me/device-token"
    $r = Invoke-Api "POST" "$base/api/v1/users/me/device-token" @{ fcmToken = "e2e-fcm-test-token-12345"; platform = "android" } -Token $token -TimeoutSec $to
    Assert-Status "POST device token returns 200" $r @(200) | Out-Null

    # 12. DELETE device token
    Write-Sub "DELETE /api/v1/users/me/device-token"
    $r = Invoke-Api "DELETE" "$base/api/v1/users/me/device-token" -Token $token -TimeoutSec $to -ExpectedStatus @(204, 200)
    Assert-Status "DELETE device token returns 200/204" $r @(200, 204) | Out-Null
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• TEST SUITE: PLANNER â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Test-PlannerSuite {
    param([hashtable]$Ctx)
    $script:CurrentSuite = "Planner"
    Write-Header "Planner - Plan CRUD, Status, Conflicts, Stats"

    $base  = $Ctx.BaseUrl
    $token = $Ctx.AccessToken
    $to    = $Ctx.Timeout
    $today = (Get-Date).ToString("yyyy-MM-dd")
    $tomorrow = (Get-Date).AddDays(1).ToString("yyyy-MM-dd")

    if (-not $token) { Write-Skip "No access token - skipping Planner suite" ; return }

    # 1. POST plan
    Write-Sub "POST /api/v1/plans"
    $planBody = @{
        title           = "E2E Team Meeting"
        planType        = "event"
        scheduledDate   = $tomorrow
        hour            = 14
        minute          = 0
        durationMinutes = 60
    }
    $r = Invoke-Api "POST" "$base/api/v1/plans" $planBody -Token $token -TimeoutSec $to
    if (Assert-Status "POST plan returns 201" $r @(201)) {
        Assert-Field "Created plan has id"     $r.body "id"     | Out-Null
        Assert-Field "Created plan has title"  $r.body "title"  | Out-Null
        Assert-Field "Created plan has status" $r.body "status" | Out-Null
        $Ctx.TestPlanId = $r.body.id
        Add-Result "Plan title is correct" ($r.body.title -eq "E2E Team Meeting") "Got: $($r.body.title)"
        Add-Result "Plan status is pending" ($r.body.status -eq "pending") "Got: $($r.body.status)"
    }

    # 2. POST second plan (same slot â†’ triggers conflict detection)
    Write-Sub "POST /api/v1/plans (overlapping - for conflict)"
    $plan2Body = @{
        title           = "E2E Conflicting Meeting"
        planType        = "event"
        scheduledDate   = $tomorrow
        hour            = 14
        minute          = 30
        durationMinutes = 60
    }
    $r2 = Invoke-Api "POST" "$base/api/v1/plans" $plan2Body -Token $token -TimeoutSec $to
    if (Assert-Status "POST overlapping plan returns 201" $r2 @(201)) {
        $Ctx.TestPlanId2 = $r2.body.id
    }

    # 3. GET plans list
    Write-Sub "GET /api/v1/plans?date=$tomorrow"
    $r = Invoke-Api "GET" "$base/api/v1/plans?date=$tomorrow" -Token $token -TimeoutSec $to
    if (Assert-Status "GET plans returns 200" $r @(200)) {
        $count = if ($r.body -is [array]) { $r.body.Count } else { 0 }
        Add-Result "Plans list contains our created plan" ($count -ge 1) "Got $count plans"
    }

    # 4. GET plan by ID
    if ($Ctx.TestPlanId) {
        Write-Sub "GET /api/v1/plans/$($Ctx.TestPlanId)"
        $r = Invoke-Api "GET" "$base/api/v1/plans/$($Ctx.TestPlanId)" -Token $token -TimeoutSec $to
        if (Assert-Status "GET plan by ID returns 200" $r @(200)) {
            Add-Result "Plan ID matches" ($r.body.id -eq $Ctx.TestPlanId) "Got: $($r.body.id)"
        }

        # 5. PUT plan (update title)
        Write-Sub "PUT /api/v1/plans/$($Ctx.TestPlanId)"
        $r = Invoke-Api "PUT" "$base/api/v1/plans/$($Ctx.TestPlanId)" @{
            title = "E2E Updated Meeting"; planType = "event"
            scheduledDate = $tomorrow; hour = 14; minute = 0; durationMinutes = 60
        } -Token $token -TimeoutSec $to
        if (Assert-Status "PUT plan returns 200" $r @(200)) {
            Add-Result "Plan title updated" ($r.body.title -eq "E2E Updated Meeting") "Got: $($r.body.title)"
        }

        # 6. PATCH status
        Write-Sub "PATCH /api/v1/plans/$($Ctx.TestPlanId)/status"
        $r = Invoke-Api "PATCH" "$base/api/v1/plans/$($Ctx.TestPlanId)/status" @{ status = "done" } -Token $token -TimeoutSec $to
        if (Assert-Status "PATCH status returns 200" $r @(200)) {
            Add-Result "Status set to done" ($r.body.status -eq "done") "Got: $($r.body.status)"
        }

        # 7. PATCH confirm
        Write-Sub "PATCH /api/v1/plans/$($Ctx.TestPlanId)/confirm"
        $r = Invoke-Api "PATCH" "$base/api/v1/plans/$($Ctx.TestPlanId)/confirm" -Token $token -TimeoutSec $to
        Assert-Status "PATCH confirm returns 200" $r @(200) | Out-Null

        # 8. PUT tags (assign default tag if we have one)
        if ($Ctx.DefaultTagId) {
            Write-Sub "PUT /api/v1/plans/$($Ctx.TestPlanId)/tags"
            $r = Invoke-Api "PUT" "$base/api/v1/plans/$($Ctx.TestPlanId)/tags" @{ tagIds = @($Ctx.DefaultTagId) } -Token $token -TimeoutSec $to
            Assert-Status "PUT plan tags returns 200" $r @(200) | Out-Null
        }
    }

    # 9. GET conflicts
    Write-Sub "GET /api/v1/plans/conflicts"
    $r = Invoke-Api "GET" "$base/api/v1/plans/conflicts" -Token $token -TimeoutSec $to
    Assert-Status "GET conflicts returns 200" $r @(200) | Out-Null

    # 10. GET stats
    Write-Sub "GET /api/v1/plans/stats?date=$tomorrow"
    $r = Invoke-Api "GET" "$base/api/v1/plans/stats?date=$tomorrow" -Token $token -TimeoutSec $to
    if (Assert-Status "GET stats returns 200" $r @(200)) {
        Assert-Field "Stats has total"    $r.body "total"    | Out-Null
        Assert-Field "Stats has pending"  $r.body "pending"  | Out-Null
    }

    # 11. GET non-existent plan â†’ 404
    Write-Sub "GET /api/v1/plans (non-existent)"
    $r = Invoke-Api "GET" "$base/api/v1/plans/00000000-0000-0000-0000-999999999999" -Token $token -TimeoutSec $to -ExpectedStatus @(404)
    Add-Result "Non-existent plan returns 404" ($r.status -eq 404) "Got $($r.status)"

    # 12. DELETE plans
    foreach ($planId in @($Ctx.TestPlanId, $Ctx.TestPlanId2)) {
        if ($planId) {
            Write-Sub "DELETE /api/v1/plans/$planId"
            $r = Invoke-Api "DELETE" "$base/api/v1/plans/$planId" -Token $token -TimeoutSec $to -ExpectedStatus @(204)
            Assert-Status "DELETE plan returns 204" $r @(204) | Out-Null
        }
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• TEST SUITE: NOTES â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Test-NotesSuite {
    param([hashtable]$Ctx)
    $script:CurrentSuite = "Notes"
    Write-Header "Notes - CRUD, Search, Pin, Tags"

    $base  = $Ctx.BaseUrl
    $token = $Ctx.AccessToken
    $to    = $Ctx.Timeout

    if (-not $token) { Write-Skip "No access token - skipping Notes suite" ; return }

    # 1. POST note
    Write-Sub "POST /api/v1/notes"
    $r = Invoke-Api "POST" "$base/api/v1/notes" @{ title = "E2E Test Note"; body = '{"text":"Buy milk and eggs. Pick up kids at 3pm."}'; pinned = $false } -Token $token -TimeoutSec $to
    if (Assert-Status "POST note returns 201" $r @(201)) {
        Assert-Field "Created note has id"    $r.body "id"    | Out-Null
        Assert-Field "Created note has title" $r.body "title" | Out-Null
        $Ctx.TestNoteId = $r.body.id
    }

    # 2. POST second note for search
    Write-Sub "POST /api/v1/notes (second)"
    $r = Invoke-Api "POST" "$base/api/v1/notes" @{ title = "E2E Pinned Note"; body = '{"text":"This note will be pinned."}'; pinned = $true } -Token $token -TimeoutSec $to
    if (Assert-Status "POST second note returns 201" $r @(201)) {
        $Ctx.TestNoteId2 = $r.body.id
    }

    # 3. GET all notes
    Write-Sub "GET /api/v1/notes"
    $r = Invoke-Api "GET" "$base/api/v1/notes" -Token $token -TimeoutSec $to
    if (Assert-Status "GET notes returns 200" $r @(200)) {
        $count = if ($r.body -is [array]) { $r.body.Count } else { 0 }
        Add-Result "Notes list is not empty" ($count -ge 1) "Got $count notes"
    }

    # 4. GET note by ID
    if ($Ctx.TestNoteId) {
        Write-Sub "GET /api/v1/notes/$($Ctx.TestNoteId)"
        $r = Invoke-Api "GET" "$base/api/v1/notes/$($Ctx.TestNoteId)" -Token $token -TimeoutSec $to
        if (Assert-Status "GET note by ID returns 200" $r @(200)) {
            Add-Result "Note ID matches" ($r.body.id -eq $Ctx.TestNoteId) ""
            Add-Result "Note title matches" ($r.body.title -eq "E2E Test Note") "Got: $($r.body.title)"
        }

        # 5. PUT note (update)
        Write-Sub "PUT /api/v1/notes/$($Ctx.TestNoteId)"
        $r = Invoke-Api "PUT" "$base/api/v1/notes/$($Ctx.TestNoteId)" @{ title = "E2E Updated Note"; body = '{"text":"Updated body text."}'; pinned = $true } -Token $token -TimeoutSec $to
        if (Assert-Status "PUT note returns 200" $r @(200)) {
            Add-Result "Note title updated" ($r.body.title -eq "E2E Updated Note") "Got: $($r.body.title)"
            Add-Result "Note pinned is true" ($r.body.pinned -eq $true) "Got: $($r.body.pinned)"
        }

        # 6. PUT note tags
        if ($Ctx.DefaultTagId) {
            Write-Sub "PUT /api/v1/notes/$($Ctx.TestNoteId)/tags"
            $r = Invoke-Api "PUT" "$base/api/v1/notes/$($Ctx.TestNoteId)/tags" @{ tagIds = @($Ctx.DefaultTagId) } -Token $token -TimeoutSec $to
            Assert-Status "PUT note tags returns 200" $r @(200) | Out-Null
        }
    }

    # 7. GET pinned notes
    Write-Sub "GET /api/v1/notes/pinned"
    $r = Invoke-Api "GET" "$base/api/v1/notes/pinned" -Token $token -TimeoutSec $to
    if (Assert-Status "GET pinned notes returns 200" $r @(200)) {
        $count = if ($r.body -is [array]) { $r.body.Count } else { 0 }
        Add-Result "Pinned notes list has at least 1" ($count -ge 1) "Got $count pinned"
    }

    # 8. GET notes with search
    Write-Sub "GET /api/v1/notes?search=Updated"
    $r = Invoke-Api "GET" "$base/api/v1/notes?search=Updated" -Token $token -TimeoutSec $to
    if (Assert-Status "GET notes search returns 200" $r @(200)) {
        $count = if ($r.body -is [array]) { $r.body.Count } else { 0 }
        Add-Result "Search returns matching notes" ($count -ge 1) "Got $count results for 'Updated'"
    }

    # 9. GET non-existent note â†’ 404
    Write-Sub "GET /api/v1/notes (non-existent)"
    $r = Invoke-Api "GET" "$base/api/v1/notes/00000000-0000-0000-0000-999999999999" -Token $token -TimeoutSec $to -ExpectedStatus @(404)
    Add-Result "Non-existent note returns 404" ($r.status -eq 404) "Got $($r.status)"

    # 10. DELETE notes
    foreach ($nid in @($Ctx.TestNoteId, $Ctx.TestNoteId2)) {
        if ($nid) {
            Write-Sub "DELETE /api/v1/notes/$nid"
            $r = Invoke-Api "DELETE" "$base/api/v1/notes/$nid" -Token $token -TimeoutSec $to -ExpectedStatus @(204)
            Assert-Status "DELETE note returns 204" $r @(204) | Out-Null
        }
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• TEST SUITE: VOICE AI â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Test-VoiceAiSuite {
    param([hashtable]$Ctx)
    $script:CurrentSuite = "VoiceAI"
    Write-Header "Voice AI - Parse, Commit"

    $base  = $Ctx.BaseUrl
    $token = $Ctx.AccessToken
    $to    = $Ctx.Timeout

    if (-not $token) { Write-Skip "No access token - skipping VoiceAI suite" ; return }

    # 1. Parse transcript
    Write-Sub "POST /api/v1/voice/parse"
    $parseBody = @{
        transcript = $Ctx.VoiceTranscript
        context    = @{
            date     = (Get-Date).ToString("yyyy-MM-dd")
            timezone = "UTC"
        }
    }
    $r = Invoke-Api "POST" "$base/api/v1/voice/parse" $parseBody -Token $token -TimeoutSec 60
    if (Assert-Status "POST voice/parse returns 200" $r @(200)) {
        Assert-Field "Parse response has items"       $r.body "items"       | Out-Null
        Assert-Field "Parse response has parsedCount" $r.body "parsedCount" | Out-Null
        $count = if ($null -ne $r.body.parsedCount) { $r.body.parsedCount } else { 0 }
        Add-Result "Parsed at least 1 item from transcript" ($count -ge 1) "parsedCount=$count"

        if ($null -ne $r.body.items -and $r.body.items.Count -gt 0) {
            $Ctx.ParsedItems = @($r.body.items)  # Force array even for single item
            Add-Result "First parsed item has type" ($null -ne $r.body.items[0].type) ""
            Add-Result "First parsed item has title" ($null -ne $r.body.items[0].title) ""
        }
    }

    # 2. Commit items (if we parsed successfully)
    if ($Ctx.ParsedItems -and $Ctx.ParsedItems.Count -gt 0) {
        Write-Sub "POST /api/v1/voice/commit"
        # Fill in any missing required fields so commit validation passes
        $readyItems = @($Ctx.ParsedItems | ForEach-Object {
            $item = $_
            if ($item.type -eq "plan") {
                if ($null -eq $item.durationMinutes) { Add-Member -InputObject $item -MemberType NoteProperty -Name durationMinutes -Value 60 -Force }
                if ($null -eq $item.hour)            { Add-Member -InputObject $item -MemberType NoteProperty -Name hour -Value 9 -Force }
                if ($null -eq $item.scheduledDate)   { Add-Member -InputObject $item -MemberType NoteProperty -Name scheduledDate -Value (Get-Date).AddDays(1).ToString("yyyy-MM-dd") -Force }
            }
            $item
        })
        # PS5.1 ConvertTo-Json unwraps single-element arrays in hashtables.
        # Build the JSON directly to ensure the array bracket is preserved.
        $itemsJsonArr = $readyItems | ConvertTo-Json -Depth 10
        if ($itemsJsonArr.TrimStart()[0] -ne '[') { $itemsJsonArr = "[$itemsJsonArr]" }
        $commitJson = "{`"items`": $itemsJsonArr}"
        $headers2 = @{ "Content-Type" = "application/json"; "Authorization" = "Bearer $token" }
        try {
            $resp2 = Invoke-WebRequest -Uri "$base/api/v1/voice/commit" -Method POST -Body $commitJson -Headers $headers2 -UseBasicParsing -TimeoutSec 30
            $bodyObj2 = $resp2.Content | ConvertFrom-Json
            $r = @{ ok = $true; status = [int]$resp2.StatusCode; body = $bodyObj2; error = "" }
        } catch [System.Net.WebException] {
            $sc2 = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
            $eb2 = ""
            if ($_.Exception.Response) { try { $s=$_.Exception.Response.GetResponseStream(); $rdr=New-Object System.IO.StreamReader($s); $eb2=$rdr.ReadToEnd() } catch {} }
            $r = @{ ok = $false; status = $sc2; body = $null; error = $eb2 }
        } catch { $r = @{ ok = $false; status = 0; body = $null; error = $_.Exception.Message } }
        if (Assert-Status "POST voice/commit returns 200" $r @(200)) {
            Assert-Field "Commit response has jobId"    $r.body "jobId"    | Out-Null
            Assert-Field "Commit response has published" $r.body "published" | Out-Null
            Add-Result "Commit published >= 1 item" ($r.body.published -ge 1) "published=$($r.body.published)"
        }
    } else {
        Write-Skip "Skipping commit - no parsed items available"
    }

    # 3. Parse with empty transcript â†’ 400
    Write-Sub "POST /api/v1/voice/parse (empty transcript)"
    $r = Invoke-Api "POST" "$base/api/v1/voice/parse" @{ transcript = "" } -Token $token -TimeoutSec $to -ExpectedStatus @(400)
    Add-Result "Empty transcript rejected (400)" ($r.status -eq 400) "Got $($r.status)"
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• TEST SUITE: GATEWAY â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Test-GatewaySuite {
    param([hashtable]$Ctx)
    $script:CurrentSuite = "Gateway"
    Write-Header "Gateway - Routing, Auth, CORS"

    $base  = $Ctx.BaseUrl
    $token = $Ctx.AccessToken
    $to    = $Ctx.Timeout

    # 1. Gateway health
    Write-Sub "GET /actuator/health (gateway)"
    $r = Invoke-Api "GET" "$base/actuator/health" -TimeoutSec $to
    Assert-Status "Gateway health returns 200" $r @(200) | Out-Null

    # 2. Auth route through gateway
    Write-Sub "GET /auth/.well-known/jwks.json (via gateway)"
    # Retry up to 3 times with 1s backoff (may be rate-limited if called many times in quick succession)
    $r = $null
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        $r = Invoke-Api "GET" "$base/auth/.well-known/jwks.json" -TimeoutSec $to
        if ($r.status -eq 200) { break }
        if ($attempt -lt 3) { Start-Sleep -Seconds 2 }
    }
    Assert-Status "JWKS via gateway returns 200" $r @(200) | Out-Null

    # 3. Protected route without JWT â†’ 401
    Write-Sub "GET /api/v1/users/me (no token - via gateway)"
    $r = Invoke-Api "GET" "$base/api/v1/users/me" -TimeoutSec $to -ExpectedStatus @(401, 403)
    Add-Result "Gateway blocks unauthenticated request (401/403)" ($r.status -in @(401, 403)) "Got $($r.status)"

    # 4. Protected route WITH JWT passes through
    if ($token) {
        Write-Sub "GET /api/v1/users/me (with token - via gateway)"
        $r = Invoke-Api "GET" "$base/api/v1/users/me" -Token $token -TimeoutSec $to
        Assert-Status "Gateway forwards authenticated request (200)" $r @(200) | Out-Null
    }

    # 5. Non-existent route â†’ 404
    Write-Sub "GET /api/v1/does-not-exist"
    $r = Invoke-Api "GET" "$base/api/v1/does-not-exist" -TimeoutSec $to -ExpectedStatus @(404, 503)
    Add-Result "Unknown route returns 404/503" ($r.status -in @(404, 503)) "Got $($r.status)"

    # 6. WebSocket endpoint is reachable (HTTP upgrade â€” just check no 404)
    Write-Sub "WS /ws endpoint exists"
    try {
        $wsResp = Invoke-WebRequest -Uri "$base/ws?token=test" -Method GET -TimeoutSec 5 -ErrorAction SilentlyContinue
        # WebSocket upgrade will return 400 Bad Request for non-WS clients - that is expected
        Add-Result "WebSocket /ws endpoint is reachable" ($wsResp.StatusCode -in @(400, 101)) "Got $($wsResp.StatusCode)"
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        # 400 = server understood WS but we didn't send proper upgrade headers â€” that's fine
        Add-Result "WebSocket /ws endpoint is reachable" ($code -in @(400, 401)) "Got $code"
    }
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• SUMMARY â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
function Write-Summary {
    Write-Host ""
    Write-Host "===============================================================" -ForegroundColor White
    Write-Host "  CZAR BACKEND - E2E TEST RESULTS" -ForegroundColor White
    Write-Host "===============================================================" -ForegroundColor White

    $suites = $script:Results | ForEach-Object { $_.Suite } | Select-Object -Unique
    $totalPass = 0; $totalFail = 0

    foreach ($suite in $suites) {
        $suiteResults = @($script:Results | Where-Object { $_.Suite -eq $suite })
        $pass = @($suiteResults | Where-Object { $_.Passed }).Count
        $fail = @($suiteResults | Where-Object { -not $_.Passed }).Count
        $totalPass += $pass; $totalFail += $fail
        $colour = if ($fail -eq 0) { "Green" } else { "Red" }
        Write-Host ("  {0,-18}  PASS: {1,3}  FAIL: {2,3}" -f $suite, $pass, $fail) -ForegroundColor $colour
    }

    Write-Host "---------------------------------------------------------------" -ForegroundColor White
    $finalColour = if ($totalFail -eq 0) { "Green" } else { "Red" }
    Write-Host ("  TOTAL              PASS: {0,3}  FAIL: {1,3}" -f $totalPass, $totalFail) -ForegroundColor $finalColour

    if ($totalFail -gt 0) {
        Write-Host "`n  Failed tests:" -ForegroundColor Red
        @($script:Results | Where-Object { -not $_.Passed }) | ForEach-Object {
            Write-Host "    [$($_.Suite)] $($_.Name): $($_.Detail)" -ForegroundColor Red
        }
    }
    Write-Host "===============================================================" -ForegroundColor White
    return $totalFail -eq 0
}

# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
# â•â• MAIN ENTRY POINT â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
# â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

Write-Host ""
Write-Host "  +----------------------------------------------+" -ForegroundColor Cyan
Write-Host "  |   CZAR BACKEND - E2E TEST RUNNER             |" -ForegroundColor Cyan
Write-Host "  |   $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')                   |" -ForegroundColor Cyan
Write-Host "  +----------------------------------------------+" -ForegroundColor Cyan
Write-Host ""

# â”€â”€ Load config â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
if (-not (Test-Path $ConfigFile)) {
    Write-Error "Config file not found: $ConfigFile"
    exit 1
}
Write-Info "Loading config from: $ConfigFile"
$cfg = Read-SimpleYaml $ConfigFile

# PS5-compatible helper: return $h[$k] if key exists, else $default
function Get-CfgVal { param($h,$k,$d) if ($h -and $h.ContainsKey($k)) { $h[$k] } else { $d } }

# â”€â”€ Build enabled services map â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
$svcEnabled = @{
    'postgres'          = $true
    'pubsub-emulator'   = $true
    'czar-gateway'      = [bool](Get-CfgVal $cfg.services 'czar-gateway'      $true)
    'czar-auth'         = [bool](Get-CfgVal $cfg.services 'czar-auth'         $true)
    'czar-user'         = [bool](Get-CfgVal $cfg.services 'czar-user'         $true)
    'czar-planner'      = [bool](Get-CfgVal $cfg.services 'czar-planner'      $true)
    'czar-notes'        = [bool](Get-CfgVal $cfg.services 'czar-notes'        $true)
    'czar-voice-ai'     = [bool](Get-CfgVal $cfg.services 'czar-voice-ai'     $true)
    'czar-conflict'     = [bool](Get-CfgVal $cfg.services 'czar-conflict'     $true)
    'czar-notification' = [bool](Get-CfgVal $cfg.services 'czar-notification' $true)
    'rebuild'           = $Rebuild.IsPresent -or [bool](Get-CfgVal $cfg.options 'rebuild' $false)
}
# Command-line -Services override
if ($Services.Count -gt 0) {
    foreach ($k in @('czar-gateway','czar-auth','czar-user','czar-planner','czar-notes','czar-voice-ai','czar-conflict','czar-notification')) {
        $svcEnabled[$k] = $Services -contains ($k -replace '^czar-', '')  -or $Services -contains $k
    }
}

# â”€â”€ Build enabled tests map â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
$testEnabled = @{
    'health'    = [bool](Get-CfgVal $cfg.tests 'health'    $true)
    'auth'      = [bool](Get-CfgVal $cfg.tests 'auth'      $true)
    'user'      = [bool](Get-CfgVal $cfg.tests 'user'      $true)
    'planner'   = [bool](Get-CfgVal $cfg.tests 'planner'   $true)
    'notes'     = [bool](Get-CfgVal $cfg.tests 'notes'     $true)
    'voice_ai'  = [bool](Get-CfgVal $cfg.tests 'voice_ai'  $true)
    'gateway'   = [bool](Get-CfgVal $cfg.tests 'gateway'   $true)
}
if ($Tests.Count -gt 0) {
    foreach ($k in $testEnabled.Keys) { $testEnabled[$k] = $Tests -contains $k }
}

# â”€â”€ Print plan â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
Write-Info "Services: $(($svcEnabled.GetEnumerator() | Where-Object { $_.Value -eq $true -and $_.Key -ne 'rebuild' } | ForEach-Object { $_.Key }) -join ', ')"
Write-Info "Tests:    $(($testEnabled.GetEnumerator() | Where-Object { $_.Value } | ForEach-Object { $_.Key }) -join ', ')"

# â”€â”€ Options â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
$opt        = $cfg.options
$baseUrl    = if ($BaseUrl) { $BaseUrl } else { Get-CfgVal $opt 'base_url'   "http://localhost:8080" }
$timeout    = [int](Get-CfgVal $opt 'request_timeout'  30)
$startupTmo = [int](Get-CfgVal $opt 'startup_timeout'  90)
$testEmailBase = Get-CfgVal $opt 'test_email' "e2e-test@czar.local"
# Use a unique email per run to avoid OTP rate limit (5/hour per address)
$runId     = (Get-Date).ToString("MMddHHmm")
$testEmail = $testEmailBase -replace "@", "-$runId@"
$intToken   = Get-CfgVal $opt 'internal_service_token' "czar-internal-dev-token"
$transcript = Get-CfgVal $opt 'voice_transcript'       "Schedule a meeting tomorrow at 2pm"

# â”€â”€ Docker management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
if (-not $SkipDockerManagement) {
    $repoRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
    Push-Location $repoRoot
    try {
        Start-Services $cfg $svcEnabled
        $directUrls = @{
            'czar-auth'     = Get-CfgVal $opt 'auth_url'     "http://localhost:8081"
            'czar-user'     = Get-CfgVal $opt 'user_url'     "http://localhost:8082"
            'czar-planner'  = Get-CfgVal $opt 'planner_url'  "http://localhost:8083"
            'czar-notes'    = Get-CfgVal $opt 'notes_url'    "http://localhost:8084"
            'czar-voice-ai' = Get-CfgVal $opt 'voice_ai_url' "http://localhost:8085"
        }
        $activeDirect = @{}
        foreach ($k in $directUrls.Keys) { if ($svcEnabled[$k]) { $activeDirect[$k] = $directUrls[$k] } }
        Wait-ForHealth $activeDirect $svcEnabled $startupTmo
    }
    finally { Pop-Location }
}

# â”€â”€ Shared test context â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
$ctx = @{
    BaseUrl         = $baseUrl
    AuthUrl         = Get-CfgVal $opt 'auth_url'     "http://localhost:8081"
    UserUrl         = Get-CfgVal $opt 'user_url'     "http://localhost:8082"
    PlannerUrl      = Get-CfgVal $opt 'planner_url'  "http://localhost:8083"
    NotesUrl        = Get-CfgVal $opt 'notes_url'    "http://localhost:8084"
    VoiceAiUrl      = Get-CfgVal $opt 'voice_ai_url' "http://localhost:8085"
    Timeout         = $timeout
    TestEmail       = $testEmail
    IntServiceToken = $intToken
    VoiceTranscript = $transcript
    AccessToken     = ""
    RefreshToken    = ""
    TestTagId       = $null
    DefaultTagId    = $null
    TestPlanId      = $null
    TestPlanId2     = $null
    TestNoteId      = $null
    TestNoteId2     = $null
    ParsedItems     = $null
}

# â”€â”€ Run suites â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
if ($testEnabled['health'])   { Test-HealthSuite  $ctx }
if ($testEnabled['auth']   -and $svcEnabled['czar-auth'])     { Test-AuthSuite    $ctx }
if ($testEnabled['user']   -and $svcEnabled['czar-user'])     { Test-UserSuite    $ctx }
if ($testEnabled['planner']-and $svcEnabled['czar-planner'])  { Test-PlannerSuite $ctx }
if ($testEnabled['notes']  -and $svcEnabled['czar-notes'])    { Test-NotesSuite   $ctx }
if ($testEnabled['voice_ai']-and $svcEnabled['czar-voice-ai']){ Test-VoiceAiSuite $ctx }
if ($testEnabled['gateway']-and $svcEnabled['czar-gateway'])  { Test-GatewaySuite $ctx }

# â”€â”€ Results â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
$allPassed = Write-Summary

# â”€â”€ Cleanup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
$shouldStop = $StopAfter.IsPresent -or [bool](Get-CfgVal $opt 'stop_after_test' $false)
if (-not $SkipDockerManagement -and $shouldStop) { Stop-Services }

$exitCode = if ($allPassed) { 0 } else { 1 }
exit $exitCode

