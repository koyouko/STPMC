@echo off
setlocal

cd /d "%~dp0backend"

if exist mvnw.cmd (
  echo Starting backend with Maven wrapper...
  call mvnw.cmd spring-boot:run
  goto :eof
)

where mvn >nul 2>nul
if %errorlevel%==0 (
  echo Starting backend with installed Maven...
  call mvn spring-boot:run
  goto :eof
)

echo.
echo Could not find mvnw.cmd or mvn on PATH.
echo Install Maven 3.9.12 or restore backend\mvnw.cmd.
echo.
pause
