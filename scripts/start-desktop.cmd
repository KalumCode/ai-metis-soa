@echo off
REM Build and launch Windows desktop shell (requires .NET 8 SDK + WebView2 Runtime)
setlocal
set "ROOT=%~dp0.."

echo [Metis] Building renderer dist...
cd /d "%ROOT%\desktop"
if errorlevel 1 goto :fail
call pnpm install
if errorlevel 1 goto :fail
call pnpm build
if errorlevel 1 goto :fail

echo [Metis] Building desktop shell...
powershell -ExecutionPolicy Bypass -File "%ROOT%\desktop\windows\build.ps1"
if errorlevel 1 goto :fail

echo [Metis] Launching Metis.exe...
start "" "%ROOT%\desktop\windows\.build\app\Metis\Metis.exe"
exit /b 0

:fail
echo [ERROR] Build failed, see output above.
pause
exit /b 1
