@echo off
REM Start desktop renderer dev mode (Vite dev server, proxies to backend 8080)
setlocal
cd /d "%~dp0..\desktop"
if errorlevel 1 (
    echo [ERROR] Cannot enter desktop directory
    pause
    exit /b 1
)

if not exist node_modules (
    echo [Metis] First run, installing dependencies...
    call pnpm install
    if errorlevel 1 (
        echo [ERROR] pnpm install failed
        pause
        exit /b 1
    )
)

echo [Metis] Starting Vite dev server...
call pnpm dev
endlocal
