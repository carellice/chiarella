@echo off
rem Compila Chiarella, alza la versione e la pubblica nelle release di GitHub.
rem Uso: doppio clic, oppure  release.bat -Version 2.0 -Notes "Novita..."
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\release.ps1" %*
set ERR=%ERRORLEVEL%
echo.
pause
exit /b %ERR%
