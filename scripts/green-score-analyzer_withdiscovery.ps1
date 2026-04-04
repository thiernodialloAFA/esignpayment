###############################################################################
#  Green Score Analyzer WITH DISCOVERY (Fully Dynamic) - PowerShell
#  ==================================================================
#  All endpoint measurements are discovered from the OpenAPI/Swagger spec.
#  No hardcoded endpoints — works with any API.
#
#  This script is a thin wrapper around green-api-auto-discover.py which
#  handles all discovery, measurement, scoring, and reporting.
#
#  Usage:
#    .\green-score-analyzer_withdiscovery.ps1
#    .\green-score-analyzer_withdiscovery.ps1 -Port 8080
#    .\green-score-analyzer_withdiscovery.ps1 -SwaggerUrl http://localhost:8080/api/v3/api-docs
#    .\green-score-analyzer_withdiscovery.ps1 -Debug
###############################################################################
param(
    [int]$Port = 8080,
    [string]$TargetUrl = "",
    [string]$SwaggerUrl = "",
    [string]$BearerToken = "",
    [int]$Repeat = 3,
    [switch]$SkipSpectral = $true,
    [switch]$Debug
)

$ErrorActionPreference = "Continue"

if (-not $TargetUrl) { $TargetUrl = "http://localhost:$Port" }

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$OutputDir = Join-Path $RootDir "reports"
$AutoDiscoverPy = Join-Path $ScriptDir "green-api-auto-discover.py"

Write-Host ""
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "  🌿 Green API Score Analyzer — Fully Dynamic Discovery" -ForegroundColor Cyan
Write-Host "  Devoxx France 2026 — Green Architecture" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host ""

###############################################################################
# Pre-flight checks
###############################################################################

# Verify Python 3
$python = Get-Command python3 -ErrorAction SilentlyContinue
if (-not $python) { $python = Get-Command python -ErrorAction SilentlyContinue }
if (-not $python) {
    Write-Host "❌ python3 is required but not found" -ForegroundColor Red
    exit 1
}
$PY = $python.Source

# Verify auto-discover script
if (-not (Test-Path $AutoDiscoverPy)) {
    Write-Host "❌ green-api-auto-discover.py not found at: $AutoDiscoverPy" -ForegroundColor Red
    exit 1
}

# Wait for API
Write-Host "--- ⏳ Waiting for API at $TargetUrl ---" -ForegroundColor Yellow
$maxWait = 90
for ($i = 1; $i -le $maxWait; $i++) {
    try {
        $null = Invoke-WebRequest -Uri "$TargetUrl/actuator/health" -UseBasicParsing -TimeoutSec 3 -ErrorAction Stop
        Write-Host "  ✓ API is up" -ForegroundColor Green
        break
    } catch {
        if ($i -eq $maxWait) {
            Write-Host "  ✗ API not reachable after ${maxWait}s" -ForegroundColor Red
            exit 1
        }
        Start-Sleep -Seconds 1
    }
}
Write-Host ""

###############################################################################
# Run the fully dynamic Python analyzer
###############################################################################

Write-Host "--- 🔍 Running Auto-Discover Analyzer ---" -ForegroundColor Yellow
Write-Host "  Target:   $TargetUrl" -ForegroundColor Cyan
Write-Host "  Repeat:   $Repeat" -ForegroundColor Cyan
if ($SwaggerUrl) { Write-Host "  Swagger:  $SwaggerUrl" -ForegroundColor Cyan }
if ($BearerToken) { Write-Host "  Auth:     Bearer ****" -ForegroundColor Cyan }
Write-Host ""

# Build command args
$cmdArgs = @($AutoDiscoverPy, "--target", $TargetUrl, "--repeat", $Repeat)
if ($SwaggerUrl) { $cmdArgs += @("--swagger", $SwaggerUrl) }
if ($BearerToken) { $cmdArgs += @("--bearer", $BearerToken) }
if ($SkipSpectral) { $cmdArgs += "--skip-spectral" }

# Execute
& $PY @cmdArgs
$exitCode = $LASTEXITCODE

if ($exitCode -ne 0) {
    Write-Host "❌ Analyzer exited with code $exitCode" -ForegroundColor Red
    exit $exitCode
}

Write-Host ""

###############################################################################
# Post-processing: Badge + Dashboard + Summary
###############################################################################

$LatestReport = Join-Path $OutputDir "latest-report.json"

if (-not (Test-Path $LatestReport)) {
    Write-Host "❌ Report not generated: $LatestReport" -ForegroundColor Red
    exit 1
}

# Generate badge
$badgeScript = Join-Path $RootDir "scripts\generate-badge.ps1"
if (Test-Path $badgeScript) {
    Write-Host "--- 🏷️  Generating Badge ---" -ForegroundColor Yellow
    try { & $badgeScript $LatestReport (Join-Path $RootDir "badges\green-score.svg") } catch {}
}

# Generate dashboard
$dashboardScript = Join-Path $RootDir "scripts\generate-dashboard.ps1"
$dashboardTemplate = Join-Path $RootDir "dashboard\index.save.html"
$dashboardOutput = Join-Path $RootDir "dashboard\index.html"
if ((Test-Path $dashboardScript) -and (Test-Path $dashboardTemplate)) {
    Write-Host "--- 📊 Generating Dashboard ---" -ForegroundColor Yellow
    try { & $dashboardScript $LatestReport $dashboardTemplate $dashboardOutput } catch {}
}

# Display summary
Write-Host ""
try {
    $report = Get-Content $LatestReport -Raw | ConvertFrom-Json
    $gs = $report.green_score
    $disc = $report.auto_discovery

    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host "  📄 Report: $LatestReport" -ForegroundColor Green
    Write-Host "================================================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  🌿 GREEN SCORE: $($gs.total)/$($gs.max)   Grade: $($gs.grade)" -ForegroundColor Green
    Write-Host "  🔍 Endpoints discovered: $($disc.endpoints_discovered)  measured: $($disc.endpoints_measured)" -ForegroundColor Green
    Write-Host ""

    if ($Debug) {
        Write-Host "--- 🐛 DEBUG: Score breakdown ---" -ForegroundColor Yellow
        foreach ($rule in $gs.breakdown.PSObject.Properties) {
            $score = $rule.Value
            $detail = ""
            if ($gs.details.PSObject.Properties[$rule.Name]) {
                $detail = $gs.details.$($rule.Name).note
            }
            $icon = if ($score -gt 0) { "✅" } else { "❌" }
            Write-Host "  $icon $($rule.Name.PadRight(25)) $("$score".PadLeft(5))  $detail"
        }
        Write-Host ""
        foreach ($ep in ($disc.discovered_endpoints | Select-Object -First 20)) {
            Write-Host ("    {0,-6} {1,-50} {2,3}  {3,8} B  {4:N3}s" -f $ep.method, $ep.path, $ep.http_code, $ep.size_download, $ep.time_total) -ForegroundColor Gray
        }
    }
} catch {
    Write-Host "  Could not parse report: $_" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Open the dashboard: dashboard\index.html" -ForegroundColor Yellow
