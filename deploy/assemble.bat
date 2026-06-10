@echo off
REM Reassemble mission-control.jar from split parts
cd /d "%~dp0"

if exist mission-control.jar (
    echo [OK] mission-control.jar already exists
    goto :eof
)

echo Assembling mission-control.jar from parts...
copy /b mission-control.jar.part.aa+mission-control.jar.part.ab+mission-control.jar.part.ac mission-control.jar >nul

if exist mission-control.jar (
    echo [OK] mission-control.jar assembled successfully
) else (
    echo [ERROR] Assembly failed
    exit /b 1
)
