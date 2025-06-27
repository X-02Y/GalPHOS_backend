@echo off
REM GalPHOS 用户认证服务启动脚本 (Windows)

REM 设置环境变量
set JAVA_OPTS=-Xmx2g -Xms512m -XX:+UseG1GC

REM 检查Java环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境，请先安装Java 11或更高版本
    pause
    exit /b 1
)

REM 检查SBT环境
sbt --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到SBT，请先安装SBT构建工具
    pause
    exit /b 1
)

REM 检查配置文件
if not exist "server_config.json" (
    echo 错误: 未找到配置文件 server_config.json
    pause
    exit /b 1
)

echo 开始编译项目...
sbt compile

if %errorlevel% neq 0 (
    echo 错误: 项目编译失败
    pause
    exit /b 1
)

echo 启动用户认证服务...
echo 服务将在 http://localhost:3001 启动
echo 按 Ctrl+C 停止服务

sbt run
