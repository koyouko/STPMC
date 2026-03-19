@echo off
title STP Kafka Mission Control
color 0A

echo.
echo  =============================================
echo   STP Kafka Mission Control - Demo Launcher
echo  =============================================
echo.

:: Auto-assemble JAR from split parts if needed
if not exist "%~dp0mission-control.jar" (
    call "%~dp0assemble.bat"
)

:: Check Java
where java >nul 2>nul
if not %errorlevel%==0 (
    echo  [ERROR] Java 17+ not found.
    echo  Install: winget install EclipseAdoptium.Temurin.17.JDK
    echo.
    pause
    exit /b 1
)
echo  [OK] Java found

:: Get script directory
set "SCRIPT_DIR=%~dp0"

:: Check JAR exists
if not exist "%SCRIPT_DIR%mission-control.jar" (
    echo  [ERROR] mission-control.jar not found in %SCRIPT_DIR%
    pause
    exit /b 1
)
echo  [OK] Backend JAR found (frontend bundled inside)
echo.
echo  Starting STP Kafka Mission Control...
echo.

:: Start the single JAR — serves both API + frontend
start "MC-Server" java -jar "%SCRIPT_DIR%mission-control.jar"

:: Wait for startup
echo  Waiting for server to be ready...
:wait_loop
timeout /t 2 /nobreak >nul
curl -s http://localhost:8080/actuator/health >nul 2>nul
if not %errorlevel%==0 goto wait_loop
echo  [OK] Server is ready
echo.

:: Open browser
start http://localhost:8080

echo  =============================================
echo   STP Kafka Mission Control is running!
echo  =============================================
echo.
echo   URL:       http://localhost:8080
echo   Database:  H2 (in-memory)
echo.
echo   Prerequisites: Java 17+ only
echo   No Node, npm, Maven, or Docker needed.
echo.
echo   To connect to your Kafka clusters, click
echo   "Onboard cluster" in the UI and enter your
echo   bootstrap server address.
echo.
echo   Press any key to stop the server.
echo  =============================================
echo.
pause

:: Kill server
taskkill /fi "windowtitle eq MC-Server*" /f >nul 2>nul
echo  Server stopped.
pause
