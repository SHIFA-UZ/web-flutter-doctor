@echo off
REM Clean, build web, deploy to Firebase. Called by build_staging.bat.
setlocal
cd /d "%~dp0\.."
set API_BASE_URL=%~1
set GOOGLE_MAPS_API_KEY=%~2

echo Cleaning previous build...
call flutter clean

echo Getting dependencies...
call flutter pub get

echo Building web release...
if "%GOOGLE_MAPS_API_KEY%"=="" (
    call flutter build web --release --pwa-strategy=none --dart-define=API_BASE_URL=%API_BASE_URL% --dart-define=ENVIRONMENT=staging --base-href=/
) else (
    call flutter build web --release --pwa-strategy=none --dart-define=API_BASE_URL=%API_BASE_URL% --dart-define=ENVIRONMENT=staging --dart-define=GOOGLE_MAPS_API_KEY=%GOOGLE_MAPS_API_KEY% --base-href=/
)
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo.
echo Build complete. Deploying to Firebase (staging - doctor app)...
call firebase deploy --only hosting:doctor --project staging
if errorlevel 1 (
    echo Firebase deploy failed for doctor app.
    exit /b 1
)

echo.
echo Deploying to Firebase (staging - admin panel)...
call firebase deploy --only hosting:admin --project staging
if errorlevel 1 (
    echo Firebase deploy failed for admin panel.
    exit /b 1
)

echo.
echo Done. Doctor app and admin panel are live.
endlocal
