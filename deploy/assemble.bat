@echo off
REM Reassemble mission-control.jar from split parts
cd /d "%~dp0"
setlocal EnableExtensions EnableDelayedExpansion

if exist mission-control.jar (
    if exist mission-control.jar.sha256 (
        for /f "tokens=1" %%H in (mission-control.jar.sha256) do set "EXPECTED=%%H"
        set "ACTUAL="
        for /f "skip=1 tokens=1" %%H in ('certutil -hashfile mission-control.jar SHA256') do (
            if not defined ACTUAL set "ACTUAL=%%H"
        )
        if /I "!ACTUAL!"=="!EXPECTED!" (
            echo [OK] mission-control.jar already assembled
            exit /b 0
        )
        echo [WARN] Existing mission-control.jar checksum does not match split parts; rebuilding it
        del /f /q mission-control.jar >nul 2>nul
    ) else (
        echo [OK] mission-control.jar already exists
        exit /b 0
    )
)

echo Assembling mission-control.jar from parts...
copy /b mission-control.jar.part.* mission-control.jar >nul

if exist mission-control.jar (
    echo [OK] mission-control.jar assembled successfully
) else (
    echo [ERROR] Assembly failed
    exit /b 1
)
