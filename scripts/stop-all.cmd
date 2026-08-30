@echo off
REM Stop all Metis services: desktop shell, Vite dev server (5173), backend (8080)
setlocal

echo [Metis] Stopping desktop shell (Metis.exe)...
taskkill /IM Metis.exe /F >nul 2>&1
if %errorlevel%==0 (
    echo [Metis] Metis.exe stopped.
) else (
    echo [Metis] Metis.exe not running.
)

call :stop_port 8080 "backend"
call :stop_port 5173 "frontend dev server"

echo [Metis] All services stopped.
pause
endlocal
exit /b 0

REM Kill every process LISTENING on the given port (IPv4 + IPv6 entries deduped by taskkill)
:stop_port
set "PORT=%~1"
set "LABEL=%~2"
set "FOUND="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr /c:":%PORT% " ^| findstr "LISTENING"') do (
    set "FOUND=1"
    taskkill /F /PID %%a >nul 2>&1
)
if defined FOUND (
    echo [Metis] %LABEL% ^(port %PORT%^) stopped.
) else (
    echo [Metis] %LABEL% ^(port %PORT%^) not running.
)
exit /b 0
