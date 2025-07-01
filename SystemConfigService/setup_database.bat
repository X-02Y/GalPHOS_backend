@echo off
echo ========================================
echo   SystemConfigService Database Setup
echo ========================================

REM 检查PostgreSQL命令行工具
psql --version >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: PostgreSQL command line tools not found
    echo Please install PostgreSQL or add psql to your PATH
    pause
    exit /b 1
)

REM 设置数据库连接参数
set PGHOST=localhost
set PGPORT=5432
set PGUSER=postgres
set PGPASSWORD=postgres

echo.
echo Connecting to PostgreSQL server...
echo Host: %PGHOST%
echo Port: %PGPORT%
echo User: %PGUSER%

REM 检查初始化脚本文件
if not exist "init_database.sql" (
    echo Error: init_database.sql not found
    echo Please ensure the database initialization script exists
    pause
    exit /b 1
)

echo.
echo Creating database 'systemconfig_db'...

REM 创建数据库
psql -h %PGHOST% -p %PGPORT% -U %PGUSER% -c "CREATE DATABASE systemconfig_db;" postgres
if %errorlevel% neq 0 (
    echo Note: Database may already exist, continuing with initialization...
)

echo.
echo Initializing database schema and data...

REM 执行初始化脚本
psql -h %PGHOST% -p %PGPORT% -U %PGUSER% -d systemconfig_db -f init_database.sql
if %errorlevel% neq 0 (
    echo Error: Database initialization failed
    pause
    exit /b 1
)

echo.
echo ========================================
echo   Database setup completed successfully!
echo ========================================
echo.
echo Database: systemconfig_db
echo Host: %PGHOST%:%PGPORT%
echo.
echo You can now start the SystemConfigService
echo.
pause
