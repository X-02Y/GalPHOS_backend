@echo off
echo ========================================
echo    Starting SystemConfigService
echo ========================================

REM 检查Java环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Java is not installed or not in PATH
    echo Please install Java 11 or higher
    pause
    exit /b 1
)

REM 检查SBT环境
sbt --version >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: SBT is not installed or not in PATH
    echo Please install SBT 1.9.6 or higher
    pause
    exit /b 1
)

REM 检查配置文件
if not exist "server_config.json" (
    echo Error: server_config.json not found
    echo Please ensure the configuration file exists
    pause
    exit /b 1
)

echo Checking configuration file...
echo Configuration file found: server_config.json

REM 设置环境变量
set SBT_OPTS=-Xmx2G -XX:+UseG1GC

echo.
echo Starting compilation...
sbt compile
if %errorlevel% neq 0 (
    echo.
    echo Error: Compilation failed
    pause
    exit /b 1
)

echo.
echo Compilation successful!
echo.
echo Starting SystemConfigService...
echo Service will be available at: http://localhost:8085
echo.
echo Press Ctrl+C to stop the service
echo ========================================

echo Starting the service...
sbt run
if %errorlevel% neq 0 (
    echo.
    echo Error: Service failed to start
    pause
    exit /b 1
)

echo.
echo Service started successfully on port 3009!
pause
