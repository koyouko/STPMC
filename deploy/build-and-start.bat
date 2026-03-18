@echo off
title STP Kafka Mission Control - Build and Start
color 0A

echo.
echo  =============================================
echo   STP Kafka Mission Control - Build ^& Start
echo  =============================================
echo.

:: Check Java
where java >nul 2>nul
if not %errorlevel%==0 (
    echo  [ERROR] Java 17+ not found.
    echo  Install: winget install EclipseAdoptium.Temurin.17.JDK
    pause
    exit /b 1
)
echo  [OK] Java found

set "ROOT_DIR=%~dp0.."

:: Check if JAR already exists
if exist "%~dp0mission-control.jar" (
    echo  [OK] JAR already built. Starting...
    goto start_server
)

:: Build backend (includes frontend static files)
echo.
echo  Step 1/2: Building frontend...
cd /d "%ROOT_DIR%\frontend"
if not exist node_modules (
    echo  Installing frontend dependencies...
    call npm install
    if not %errorlevel%==0 (
        echo  [ERROR] npm install failed. See above for details.
        pause
        exit /b 1
    )
)
call npm run build
if not %errorlevel%==0 (
    echo  [ERROR] Frontend build failed.
    pause
    exit /b 1
)
echo  [OK] Frontend built

:: Copy frontend dist to Spring Boot static resources
echo.
echo  Step 2/2: Building backend JAR...
xcopy /E /Y /Q "%ROOT_DIR%\frontend\dist\*" "%ROOT_DIR%\backend\src\main\resources\static\" >nul
cd /d "%ROOT_DIR%\backend"
call mvnw.cmd package -DskipTests -q
if not %errorlevel%==0 (
    echo  [ERROR] Backend build failed.
    pause
    exit /b 1
)

:: Copy JAR to deploy folder
copy /Y "%ROOT_DIR%\backend\target\mission-control-0.0.1-SNAPSHOT.jar" "%~dp0mission-control.jar" >nul
echo  [OK] Backend JAR built

:start_server
echo.
echo  Starting STP Kafka Mission Control...
echo.

:: Start the single JAR
start "MC-Server" java -jar "%~dp0mission-control.jar"

:: Wait for startup
echo  Waiting for server to be ready...
:wait_loop
timeout /t 2 /nobreak >nul
curl -s http://localhost:8080/actuator/health >nul 2>nul
if not %errorlevel%==0 goto wait_loop
echo  [OK] Server is ready

:: Open browser
start http://localhost:8080

echo.
echo  =============================================
echo   STP Kafka Mission Control is running!
echo  =============================================
echo.
echo   URL:       http://localhost:8080
echo   Database:  H2 (in-memory)
echo.
echo   Press any key to stop the server.
echo  =============================================
echo.
pause

taskkill /fi "windowtitle eq MC-Server*" /f >nul 2>nul
echo  Server stopped.
pause
