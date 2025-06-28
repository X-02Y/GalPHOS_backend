@echo off
echo Starting Region Management Service...

REM Set Java options
set JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC

REM Check if sbt is available
where sbt >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo SBT not found. Please install SBT first.
    pause
    exit /b 1
)

REM Run the service
echo Starting Region Management Service on port 3007...
sbt "run server_config.json"

pause
