@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0install-debug.ps1"
endlocal
