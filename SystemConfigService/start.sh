#!/bin/bash

echo "========================================"
echo "    Starting SystemConfigService"
echo "========================================"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    echo "Please install Java 11 or higher"
    exit 1
fi

echo "Java version:"
java -version

# 检查SBT环境
if ! command -v sbt &> /dev/null; then
    echo "Error: SBT is not installed or not in PATH"
    echo "Please install SBT 1.9.6 or higher"
    exit 1
fi

echo "SBT version:"
sbt --version

# 检查配置文件
if [ ! -f "server_config.json" ]; then
    echo "Error: server_config.json not found"
    echo "Please ensure the configuration file exists"
    exit 1
fi

echo "Configuration file found: server_config.json"

# 设置环境变量
export SBT_OPTS="-Xmx2G -XX:+UseG1GC"

echo ""
echo "Starting compilation..."
if ! sbt compile; then
    echo ""
    echo "Error: Compilation failed"
    exit 1
fi

echo ""
echo "Compilation successful!"
echo ""
echo "Starting SystemConfigService..."
echo "Service will be available at: http://localhost:8085"
echo ""
echo "Press Ctrl+C to stop the service"
echo "========================================"

# 启动服务
sbt run

echo ""
echo "Service stopped."
