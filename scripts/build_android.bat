@echo off
REM Build Flutter Android APK or AAB with API base URL (for testing and Play Store)
REM Usage: scripts\build_android.bat API_BASE_URL [apk|aab] [GOOGLE_MAPS_API_KEY]
REM Example: scripts\build_android.bat https://your-app.up.railway.app apk
REM Example: scripts\build_android.bat https://your-app.up.railway.app aab
REM NOTE: API_BASE_URL should NOT include trailing /api - the app adds it automatically

setlocal

if "%1"=="" (
    echo Usage: %~nx0 API_BASE_URL [apk^|aab] [GOOGLE_MAPS_API_KEY]
    echo.
    echo Step 1 - Test with APK first (install on device and verify login):
    echo   scripts\build_android.bat https://your-app.up.railway.app apk
    echo.
    echo Step 2 - When APK works, build AAB for Google Play:
    echo   scripts\build_android.bat https://your-app.up.railway.app aab
    echo.
    echo Optional: pass Google Maps API key as 3rd argument (or 2nd if building aab).
    exit /b 1
)

set "API_BASE_URL=%~1"
REM Do not include /api in the URL - the app adds it automatically

set BUILD_TARGET=aab
set GOOGLE_MAPS_API_KEY=%3
if /i "%2"=="apk" set BUILD_TARGET=apk
if /i "%2"=="aab" set BUILD_TARGET=aab
if not "%2"=="" if not "%2"=="apk" if not "%2"=="aab" set GOOGLE_MAPS_API_KEY=%2

echo.
echo ========================================
echo Android build: %BUILD_TARGET%
echo API Base URL: %API_BASE_URL%
echo ========================================
echo.

echo Cleaning...
call flutter clean
call flutter pub get

set DART_DEFINES=--dart-define=API_BASE_URL=%API_BASE_URL% --dart-define=ENVIRONMENT=production
if not "%GOOGLE_MAPS_API_KEY%"=="" (
    set DART_DEFINES=%DART_DEFINES% --dart-define=GOOGLE_MAPS_API_KEY=%GOOGLE_MAPS_API_KEY%
)

if "%BUILD_TARGET%"=="apk" (
    echo Building release APK (test on device before uploading AAB to Play)...
    call flutter build apk --release %DART_DEFINES%
    if errorlevel 1 (
        echo APK build failed.
        exit /b 1
    )
    echo.
    echo APK output: build\app\outputs\flutter-apk\app-release.apk
    echo Install on device and test login. Then run this script with "aab" for Play Store.
) else (
    echo Building release AAB (for Google Play upload)...
    call flutter build appbundle --release %DART_DEFINES%
    if errorlevel 1 (
        echo AAB build failed.
        exit /b 1
    )
    echo.
    echo AAB output: build\app\outputs\bundle\release\app-release.aab
    echo Upload this file to Google Play Console.
)

echo.
echo Done.
endlocal
