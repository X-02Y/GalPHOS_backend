@echo off
echo Starting Exam Management Service...
echo.

REM 设置环境变量
set JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC -XX:G1HeapRegionSize=16m

REM 检查配置文件
if not exist "server_config.json" (
    echo Error: server_config.json not found!
    echo Please copy server_config.json.example to server_config.json and configure it.
    pause
    exit /b 1
)

REM 检查数据库初始化脚本
if not exist "init_database.sql" (
    echo Warning: init_database.sql not found!
    echo Database initialization will be skipped.
    echo.
)

REM 启动服务
echo Starting service on port 3003...
sbt "run"

if %ERRORLEVEL% neq 0 (
    echo.
    echo Service startup failed!
    echo Check the logs above for error details.
    pause
    exit /b 1
)

echo.
echo Service started successfully!
echo Press any key to exit...
pause > nul
