@echo off
echo 启动GalPHOS用户管理服务...

REM 设置环境变量
set JAVA_OPTS=-Xmx1g -Xms512m -XX:+UseG1GC -XX:+UseStringDeduplication

REM 检查配置文件
if not exist "server_config.json" (
    echo 错误: 找不到配置文件 server_config.json
    exit /b 1
)

REM 检查JAR文件
set JAR_FILE=target\scala-3.4.2\UserManagementService-assembly-0.1.0-SNAPSHOT.jar
if not exist "%JAR_FILE%" (
    echo JAR文件不存在，开始编译...
    sbt assembly
    if errorlevel 1 (
        echo 编译失败
        exit /b 1
    )
)

echo 启动服务器...
echo 配置文件: server_config.json
echo JVM参数: %JAVA_OPTS%

java %JAVA_OPTS% -jar "%JAR_FILE%"
