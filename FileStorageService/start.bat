@echo off
REM FileStorageService 启动脚本 (Windows)

echo 正在启动 FileStorageService...

REM 检查Java环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境，请安装Java 11或更高版本
    pause
    exit /b 1
)

REM 检查sbt环境
sbt about >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到sbt，请安装sbt
    pause
    exit /b 1
)

REM 创建存储目录
if not exist "storage\files\uploads" mkdir storage\files\uploads
if not exist "storage\files\temp" mkdir storage\files\temp
if not exist "storage\files\images" mkdir storage\files\images
if not exist "storage\files\exports" mkdir storage\files\exports
if not exist "storage\files\archives" mkdir storage\files\archives

echo 存储目录已创建: .\storage\files

REM 设置JVM参数
set JAVA_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC

REM 启动服务
echo 正在编译和启动服务...
sbt "run"

pause
