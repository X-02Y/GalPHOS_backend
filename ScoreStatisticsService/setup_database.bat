@echo off
echo Setting up Score Statistics Service Database...
echo.

set PGPASSWORD=root
set DB_NAME=galphos
set DB_USER=db
set DB_HOST=localhost
set DB_PORT=5432

echo Executing database initialization script...
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -f init_database.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Database setup completed successfully!
    echo.
    echo Schema 'scorestatistics' created with all tables.
    echo Initial data inserted.
    echo.
) else (
    echo.
    echo Database setup failed!
    echo Please check your PostgreSQL connection and credentials.
    echo.
)

pause
