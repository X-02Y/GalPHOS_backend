@echo off
echo Starting GradingService...
echo.

echo Checking if SBT is available...
sbt -version
if %errorlevel% neq 0 (
    echo Error: SBT is not installed or not in PATH
    pause
    exit /b 1
)

echo.
echo Compiling and running GradingService...
sbt "run"

if %errorlevel% neq 0 (
    echo.
    echo Error: Failed to start GradingService
    pause
    exit /b 1
)

echo.
echo GradingService stopped.
pause
