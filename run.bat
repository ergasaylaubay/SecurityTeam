@echo off
setlocal
cd /d "%~dp0"
title SecurityTeam Secure Messenger

where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java 17 or newer is required.
  pause
  exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo ERROR: Maven is not on PATH. Install Maven and try again.
  pause
  exit /b 1
)

if not defined MESSENGER_KEYSTORE_PASSWORD (
  echo Enter a local keystore password (at least 12 characters).
  set /p "MESSENGER_KEYSTORE_PASSWORD=Password: "
)
if not defined MESSENGER_KEYSTORE_PASSWORD (
  echo ERROR: Keystore password is required.
  pause
  exit /b 1
)

if not exist "secrets\server.p12" (
  echo Setting up local certificates and keys...
  powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\setup-local.ps1"
  if errorlevel 1 goto failed
)

echo Building Maven modules...
call mvn clean package
if errorlevel 1 goto failed

echo Starting server in demo mode...
start "SecurityTeam Server" cmd /k "cd /d ""%~dp0"" && powershell -NoProfile -ExecutionPolicy Bypass -File scripts\start-server.ps1 -Demo"

echo Waiting for server startup...
timeout /t 8 /nobreak >nul

echo Starting JavaFX client...
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\start-client.ps1"
if errorlevel 1 goto failed
exit /b 0

:failed
echo.
echo Startup failed. Check the error above.
pause
exit /b 1
