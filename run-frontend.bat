@echo off
setlocal

cd /d "%~dp0frontend"

where npm >nul 2>nul
if not %errorlevel%==0 (
  echo.
  echo npm was not found on PATH.
  echo Install Node.js 22 LTS and reopen this terminal.
  echo.
  pause
  exit /b 1
)

if not exist node_modules (
  echo Installing frontend dependencies...
  call npm install
  if errorlevel 1 (
    echo.
    echo npm install failed.
    pause
    exit /b 1
  )
)

echo Starting frontend dev server...
call npm run dev
