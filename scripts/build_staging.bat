@echo off
REM Build Flutter web app for staging and deploy to Firebase (Windows). Web only, no Android/iOS.
REM Usage: scripts\build_staging.bat API_BASE_URL [GOOGLE_MAPS_API_KEY]
REM Example: scripts\build_staging.bat https://shifa-doc-backend-mvp-production.up.railway.app
REM NOTE: API_BASE_URL should NOT include trailing /api - the app adds it automatically

setlocal
cd /d "%~dp0\.."

if "%1"=="" (
    echo Usage: %~nx0 API_BASE_URL [GOOGLE_MAPS_API_KEY]
    echo Example: %~nx0 https://shifa-doc-backend-mvp-production.up.railway.app
    exit /b 1
)

set API_BASE_URL=%1
REM Remove trailing /api if present
for /f "delims=" %%i in ('powershell -command "$url='%API_BASE_URL%'; $url -replace '/api/?$', ''"') do set API_BASE_URL=%%i

if "%2"=="" (
    set GOOGLE_MAPS_API_KEY=
    echo NOTE: Google Maps API key not provided. Will be fetched from backend at runtime.
) else (
    set GOOGLE_MAPS_API_KEY=%2
)

echo Building Flutter web app for STAGING...
echo API Base URL: %API_BASE_URL%
if "%GOOGLE_MAPS_API_KEY%"=="" goto no_map_key
echo Google Maps API Key: build-time
goto after_map_key
:no_map_key
echo Google Maps API Key: from backend at runtime
:after_map_key
echo(

call "%~dp0build_staging_firebase.bat"

echo Starting clean, build and deploy...
call "%~dp0build_staging_build.bat" "%API_BASE_URL%" "%GOOGLE_MAPS_API_KEY%"
exit /b %errorlevel%
endlocal
