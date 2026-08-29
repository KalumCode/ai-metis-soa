# Metis 桌面端构建脚本（需 PowerShell 7 或 Windows PowerShell 5+）
# 用法: powershell -ExecutionPolicy Bypass -File desktop/windows/build.ps1
# 产物: desktop/windows/.build/app/Metis/Metis.exe

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path "$PSScriptRoot\..\.."
$desktopDir = Join-Path $repoRoot "desktop"
$shellDir = Join-Path $PSScriptRoot "Metis.Desktop"
$webDistSource = Join-Path $desktopDir "dist"
$webDistTarget = Join-Path $shellDir "Resources\webdist"
$outDir = Join-Path $PSScriptRoot ".build\app\Metis"

Write-Host "== 1/3 构建前端产物 =="
Push-Location $desktopDir
pnpm build
if ($LASTEXITCODE -ne 0) { Pop-Location; throw "前端构建失败" }
Pop-Location

Write-Host "== 2/3 同步 webdist 到壳资源 =="
if (Test-Path $webDistTarget) { Remove-Item -Recurse -Force $webDistTarget }
New-Item -ItemType Directory -Force -Path $webDistTarget | Out-Null
Copy-Item -Recurse -Force -Path (Join-Path $webDistSource "*") -Destination $webDistTarget
Write-Host "webdist -> $webDistTarget"

Write-Host "== 3/3 dotnet publish =="
dotnet publish $shellDir -c Release -r win-x64 --self-contained false -o $outDir
if ($LASTEXITCODE -ne 0) { throw "dotnet publish 失败" }

Write-Host ""
Write-Host "构建完成: $outDir\Metis.exe"
Write-Host "后端地址默认 http://127.0.0.1:8080，可在 %APPDATA%\Metis\config.json 修改"
