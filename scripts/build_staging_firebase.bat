@echo off
REM Ensure Firebase is configured (web only). Called by build_staging.bat.
setlocal
cd /d "%~dp0\.."

echo Checking Firebase config...
if not exist lib\firebase_options.dart goto firebase_configure

REM Web-only configs still contain UnsupportedError for Android/iOS — check for a real web block instead.
findstr /C:"static const FirebaseOptions web = FirebaseOptions(" lib\firebase_options.dart 1>nul 2>nul
if not errorlevel 1 goto firebase_skip
findstr /C:"apiKey:" lib\firebase_options.dart 1>nul 2>nul
if not errorlevel 1 goto firebase_skip

:firebase_configure
echo Configuring Firebase for web only (Phone OTP)...
del lib\firebase_options.dart.bak 2>nul
if exist lib\firebase_options.dart ren lib\firebase_options.dart firebase_options.dart.bak
dart pub global run flutterfire_cli:flutterfire configure --project=shifa-doctor-staging --platforms=web -y
if errorlevel 1 goto firebase_failed
del lib\firebase_options.dart.bak 2>nul
echo Firebase configured successfully.
goto firebase_done

:firebase_failed
if exist lib\firebase_options.dart del lib\firebase_options.dart
if exist lib\firebase_options.dart.bak ren lib\firebase_options.dart.bak firebase_options.dart
if not exist lib\firebase_options.dart (
    echo ERROR: lib\firebase_options.dart is missing and could not be restored.
    echo Run: firebase login
    echo Then: dart pub global run flutterfire_cli:flutterfire configure --project=shifa-doctor-staging --platforms=web -y
    endlocal
    exit /b 1
)
echo WARNING: flutterfire configure failed. Using existing firebase_options.dart backup.

:firebase_skip
echo Firebase options already configured.

:firebase_done
if not exist lib\firebase_options.dart (
    echo ERROR: lib\firebase_options.dart is required for web builds.
    endlocal
    exit /b 1
)
echo(
endlocal
