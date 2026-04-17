@echo off
REM Ensure Firebase is configured (web only). Called by build_staging.bat.
setlocal
cd /d "%~dp0\.."

echo Checking Firebase config...
findstr /C:"UnsupportedError" lib\firebase_options.dart 1>nul 2>nul
if errorlevel 1 goto firebase_skip

echo Configuring Firebase for web only (Phone OTP)...
del lib\firebase_options.dart.bak 2>nul
ren lib\firebase_options.dart firebase_options.dart.bak
dart pub global run flutterfire_cli:flutterfire configure --project=shifa-doctor-staging --platforms=web -y
if errorlevel 1 goto firebase_failed
del lib\firebase_options.dart.bak 2>nul
echo Firebase configured successfully.
goto firebase_done

:firebase_failed
ren lib\firebase_options.dart.bak firebase_options.dart 2>nul
echo WARNING: flutterfire configure failed. Run manually then re-run this script.
goto firebase_done

:firebase_skip
echo Firebase options already configured.

:firebase_done
echo(
endlocal
