@echo off
echo 启动答题提交服务...

:: 检查Java环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境，请先安装Java 11+
    pause
    exit /b 1
)

:: 检查SBT环境
sbt --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到SBT环境，请先安装SBT
    pause
    exit /b 1
)

:: 创建日志目录
if not exist "logs" mkdir logs

:: 检查配置文件
if not exist "server_config.json" (
    echo 错误: 找不到配置文件 server_config.json
    pause
    exit /b 1
)

echo 正在启动答题提交服务...
echo 服务将在端口 3004 上运行
echo 按 Ctrl+C 停止服务

sbt "run"

pause
