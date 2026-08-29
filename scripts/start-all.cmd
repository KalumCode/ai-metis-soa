@echo off
REM Start backend + desktop dev in separate windows
setlocal
set "SCRIPTS=%~dp0"

echo [Metis] Starting backend (new window)...
start "Metis Backend" cmd /k ""%SCRIPTS%start-backend.cmd""

timeout /t 3 /nobreak >nul

echo [Metis] Starting frontend dev server (new window)...
start "Metis Frontend" cmd /k ""%SCRIPTS%start-frontend.cmd""

echo [Metis] Started in separate windows. Close a window to stop that service.
pause
endlocal
