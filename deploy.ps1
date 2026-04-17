# Firebase Deployment Script for Doctor App
# Usage: .\deploy.ps1 <RAILWAY_BACKEND_URL>
# Example: .\deploy.ps1 https://shifa-backend.railway.app
# NOTE: Do NOT include /api - the code adds it automatically

param(
    [Parameter(Mandatory=$true)]
    [string]$RailwayBackendUrl
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Shifa Doctor App - Firebase Deploy" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Ensure we're in the right directory
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

# Validate Railway URL
if (-not $RailwayBackendUrl.StartsWith("http")) {
    Write-Host "ERROR: Railway URL must start with http:// or https://" -ForegroundColor Red
    exit 1
}

# Remove /api if present (code paths already include /api)
$apiUrl = $RailwayBackendUrl.TrimEnd('/')
$apiUrl = $apiUrl -replace '/api/?$', ''

Write-Host "Backend Base URL: $apiUrl" -ForegroundColor Green
Write-Host "(Code will add /api to paths automatically)" -ForegroundColor Gray
Write-Host ""

# Step 1: Build
Write-Host "Step 1: Building Flutter web app..." -ForegroundColor Yellow
Write-Host ""

& .\scripts\build_staging.bat $apiUrl

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✓ Build complete!" -ForegroundColor Green
Write-Host ""

# Step 2: Deploy
Write-Host "Step 2: Deploying to Firebase..." -ForegroundColor Yellow
Write-Host ""

firebase deploy --only hosting

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "ERROR: Deployment failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✓ Deployment Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Your app should be live at:" -ForegroundColor Cyan
Write-Host "https://shifa-doctor-staging.web.app" -ForegroundColor White
Write-Host ""
Write-Host "Or check Firebase Console for the exact URL." -ForegroundColor Gray
Write-Host ""
