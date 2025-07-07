@echo off
echo Starting SubmissionService...
echo.

echo Checking if PostgreSQL is running...
tasklist /FI "IMAGENAME eq postgres.exe" 2>NUL | find /I /N "postgres.exe">NUL
if "%ERRORLEVEL%"=="0" (
    echo PostgreSQL is running.
) else (
    echo Warning: PostgreSQL is not running. Please start PostgreSQL first.
    echo You can start it with: net start postgresql-x64-14
    pause
    exit /b 1
)

echo.
echo Compiling and starting the service...
sbt "runMain Main.SubmissionServiceApp"

if %ERRORLEVEL% neq 0 (
    echo.
    echo Error: Failed to start SubmissionService
    pause
    exit /b 1
)

echo.
echo SubmissionService stopped
pause
