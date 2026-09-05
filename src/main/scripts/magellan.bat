@echo off
REM Starts Magellan from the Maven distribution zip.
cd /d "%~dp0"
for %%f in (magellan2-*.jar) do set JARFILE=%%f
start "Magellan" javaw -Xmx1200m -jar "%JARFILE%" %*
