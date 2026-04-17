@echo off
REM Build Flutter web app for QA and deploy to Firebase (Windows)
REM Usage: scripts\build_qa.bat [API_BASE_URL] [GOOGLE_MAPS_API_KEY]
REM Example: scripts\build_qa.bat
REM Example: scripts\build_qa.bat https://shifa-doc-backend-mvp-qa.up.railway.app
REM NOTE: API_BASE_URL should NOT include trailing /api - the app adds it automatically

setlocal

if "%1"=="" (
    set API_BASE_URL=https://shifa-doc-backend-mvp-qa.up.railway.app
    echo Using default QA backend: %API_BASE_URL%
) else (
    set API_BASE_URL=%1
    REM Remove trailing /api if present
    for /f "delims=" %%i in ('powershell -command "$url='%API_BASE_URL%'; $url -replace '/api/?$', ''"') do set API_BASE_URL=%%i
)

if "%2"=="" (
    set GOOGLE_MAPS_API_KEY=
    echo NOTE: Google Maps API key not provided. Will be fetched from backend at runtime.
) else (
    set GOOGLE_MAPS_API_KEY=%2
)

echo Building Flutter web app for QA...
echo API Base URL: %API_BASE_URL%
if not "%GOOGLE_MAPS_API_KEY%"=="" (
    echo Google Maps API Key: (build-time)
) else (
    echo Google Maps API Key: from backend at runtime
)
echo.

echo Cleaning previous build...
call flutter clean

echo Getting dependencies...
call flutter pub get

echo Building web release...
if "%GOOGLE_MAPS_API_KEY%"=="" (
    call flutter build web --release --dart-define=API_BASE_URL=%API_BASE_URL% --dart-define=ENVIRONMENT=qa --base-href=/
) else (
    call flutter build web --release --dart-define=API_BASE_URL=%API_BASE_URL% --dart-define=ENVIRONMENT=qa --dart-define=GOOGLE_MAPS_API_KEY=%GOOGLE_MAPS_API_KEY% --base-href=/
)

if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo.
echo Build complete. Deploying to Firebase (shifa-doctor-qa)...
call firebase deploy --only hosting --project shifa-doctor-qa

if errorlevel 1 (
    echo Firebase deploy failed.
    exit /b 1
)

echo.
echo Done. QA app is live.
endlocal
