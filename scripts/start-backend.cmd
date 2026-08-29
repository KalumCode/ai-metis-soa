@echo off
REM Start backend (Spring Boot). API key priority: arg > ARK_API_KEY env > scripts/ark-api-key.txt
setlocal
cd /d "%~dp0..\server"

if "%~1"=="" if "%ARK_API_KEY%"=="" (
    if exist "%~dp0ark-api-key.txt" (
        set /p ARK_API_KEY=<"%~dp0ark-api-key.txt"
        echo [Metis] API key loaded from scripts\ark-api-key.txt
    )
)
if "%ARK_API_KEY%"=="" (
    echo [ERROR] ARK_API_KEY not set: pass as arg, env var, or scripts\ark-api-key.txt
    pause
    exit /b 1
)

echo [Metis] Starting backend: http://localhost:8080
call mvn spring-boot:run
endlocal
