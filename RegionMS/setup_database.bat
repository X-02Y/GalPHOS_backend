@echo off
echo Setting up Region Management Database...

REM Set PostgreSQL path
set PGPATH="C:\Program Files\PostgreSQL\16\bin"
set PGUSER=postgres
set PGPASSWORD=your_postgres_password

echo.
echo Please make sure PostgreSQL is running and you have the correct password.
echo.

REM Create database user if not exists
echo Creating database user 'db'...
%PGPATH%\psql -U %PGUSER% -c "CREATE USER db WITH PASSWORD 'root';" 2>nul

REM Grant privileges
echo Granting privileges to user 'db'...
%PGPATH%\psql -U %PGUSER% -c "ALTER USER db CREATEDB;"

REM Create database
echo Creating region_management database...
%PGPATH%\psql -U %PGUSER% -c "CREATE DATABASE region_management OWNER db;"

REM Initialize database schema
echo Initializing database schema...
%PGPATH%\psql -U db -d region_management -f init_database.sql

echo.
echo Database setup complete!
echo Database: region_management
echo User: db
echo Password: root
echo.

pause
