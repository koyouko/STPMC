@echo off
title STP Kafka Mission Control
color 0A

echo.
echo  ============================================
echo   STP Kafka Mission Control - Quick Start
echo  ============================================
echo.

:: Check Java
where java >nul 2>nul
if not %errorlevel%==0 (
  echo  [ERROR] Java not found. Install: winget install EclipseAdoptium.Temurin.17.JDK
  pause
  exit /b 1
)

:: Check Node
where node >nul 2>nul
if not %errorlevel%==0 (
  echo  [ERROR] Node.js not found. Install: winget install OpenJS.NodeJS.LTS
  pause
  exit /b 1
)

echo  [OK] Java found
echo  [OK] Node.js found
echo.

:: Install frontend deps if needed
if not exist "%~dp0frontend\node_modules" (
  echo  Installing frontend dependencies...
  cd /d "%~dp0frontend"
  call npm install
  cd /d "%~dp0"
  echo.
)

echo  Starting backend on http://localhost:8080 ...
start "MC-Backend" cmd /k "cd /d "%~dp0backend" && mvnw.cmd spring-boot:run"

:: Wait for backend to start
echo  Waiting for backend to be ready...
:wait_backend
timeout /t 2 /nobreak >nul
curl -s http://localhost:8080/actuator/health >nul 2>nul
if not %errorlevel%==0 goto wait_backend
echo  [OK] Backend is ready
echo.

echo  Starting frontend on http://localhost:5173 ...
start "MC-Frontend" cmd /k "cd /d "%~dp0frontend" && npm run dev"

timeout /t 3 /nobreak >nul

echo.
echo  ============================================
echo   STP Kafka Mission Control is running!
echo  ============================================
echo.
echo   Frontend:  http://localhost:5173
echo   Backend:   http://localhost:8080
echo   Database:  H2 (in-memory)
echo.
echo   To onboard a cluster, click "Onboard cluster"
echo   and enter your Kafka bootstrap server address.
echo.
echo   Close this window to keep services running,
echo   or press any key to stop everything.
echo  ============================================
echo.
pause

:: Kill both
taskkill /fi "windowtitle eq MC-Backend*" /f >nul 2>nul
taskkill /fi "windowtitle eq MC-Frontend*" /f >nul 2>nul
echo  Services stopped.
pause
