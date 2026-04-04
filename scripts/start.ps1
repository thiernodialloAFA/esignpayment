#!/usr/bin/env pwsh
<##
  Start baseline + optimized (local dev)
  Usage: .\scripts\start.ps1 [-Analyze] [-Debug] [-AppName <name>]
##>
param(
  [switch]$Analyze,
  [switch]$Debug,
  [string]$AppName = ""
)

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

# Default AppName = root folder basename
if (-not $AppName) {
  $AppName = (Get-Item $Root).Name
}
$env:APPNAME = $AppName

Write-Host "Starting baseline (8080)..."
Start-Process -FilePath "mvn" -ArgumentList "-q", "spring-boot:run" -WorkingDirectory (Join-Path $Root "green-api-baseline")

Write-Host "Starting optimized (8081)..."
Start-Process -FilePath "mvn" -ArgumentList "-q", "spring-boot:run" -WorkingDirectory (Join-Path $Root "green-api-optimized")

if ($Analyze) {
  Write-Host "Running Green Score analyzer..."
  $analyzerArgs = @{}
  if ($Debug) { $analyzerArgs["Debug"] = $true }
  $analyzerArgs["AppName"] = $AppName
  & (Join-Path $Root "scripts\green-score-analyzer.ps1") @analyzerArgs
}

Write-Host "📦 App:       $AppName"
Write-Host "Baseline:  http://localhost:8080"
Write-Host "Optimized: http://localhost:8081"
Write-Host "Dashboard: .\dashboard\index.html"

