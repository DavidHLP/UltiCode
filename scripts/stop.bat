@echo off
REM Deprecated compatibility alias. DevStack is the only supported development launcher.
setlocal

set "SCRIPT_DIR=%~dp0"
bash "%SCRIPT_DIR%dev\stop.sh" %*
exit /b %ERRORLEVEL%
