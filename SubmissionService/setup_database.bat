@echo off
echo Setting up SubmissionService Database...
echo.

echo This script will create the database and schema for SubmissionService
echo Make sure PostgreSQL is running before proceeding.
echo.

set /p PROCEED="Do you want to continue? (y/n): "
if /i "%PROCEED%" neq "y" (
    echo Cancelled.
    pause
    exit /b 0
)

echo.
echo Connecting to PostgreSQL...

rem Create database and user
psql -U postgres -c "CREATE DATABASE submission_service;" 2>nul
if %ERRORLEVEL% neq 0 (
    echo Database may already exist, continuing...
)

psql -U postgres -c "CREATE USER db WITH PASSWORD 'root';" 2>nul
if %ERRORLEVEL% neq 0 (
    echo User may already exist, continuing...
)

psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE submission_service TO db;" 2>nul

rem Initialize schema
echo.
echo Initializing database schema...
psql -U db -d submission_service -f init_database.sql

if %ERRORLEVEL% equ 0 (
    echo.
    echo Database setup completed successfully!
    echo.
    echo Database: submission_service
    echo Username: db
    echo Password: root
    echo Schema: submissionservice
) else (
    echo.
    echo Error: Database setup failed
    pause
    exit /b 1
)

echo.
pause
