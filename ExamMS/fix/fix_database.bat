@echo off
echo Fixing exam_files table schema...

REM Read database configuration
for /f "tokens=1,2 delims==" %%a in ('findstr /r "database\." server_config.json') do (
    if "%%a"==""database.host"" set DB_HOST=%%b
    if "%%a"==""database.port"" set DB_PORT=%%b
    if "%%a"==""database.name"" set DB_NAME=%%b
    if "%%a"==""database.user"" set DB_USER=%%b
    if "%%a"==""database.password"" set DB_PASSWORD=%%b
)

REM Clean the values (remove quotes and commas)
set DB_HOST=%DB_HOST:"=%
set DB_HOST=%DB_HOST:,=%
set DB_PORT=%DB_PORT:"=%
set DB_PORT=%DB_PORT:,=%
set DB_NAME=%DB_NAME:"=%
set DB_NAME=%DB_NAME:,=%
set DB_USER=%DB_USER:"=%
set DB_USER=%DB_USER:,=%
set DB_PASSWORD=%DB_PASSWORD:"=%
set DB_PASSWORD=%DB_PASSWORD:,=%

echo Connecting to database: %DB_HOST%:%DB_PORT%/%DB_NAME%
echo User: %DB_USER%

REM Set PGPASSWORD environment variable
set PGPASSWORD=%DB_PASSWORD%

REM Execute the migration script
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -f fix_exam_files_table.sql

if %errorlevel% equ 0 (
    echo Migration completed successfully!
) else (
    echo Migration failed! Error code: %errorlevel%
)

pause
