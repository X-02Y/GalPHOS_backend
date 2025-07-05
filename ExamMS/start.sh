#!/bin/bash

echo "Starting Exam Management Service..."
echo

# 设置环境变量
export JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC -XX:G1HeapRegionSize=16m"

# 检查配置文件
if [ ! -f "server_config.json" ]; then
    echo "Error: server_config.json not found!"
    echo "Please copy server_config.json.example to server_config.json and configure it."
    exit 1
fi

# 检查数据库初始化脚本
if [ ! -f "init_database.sql" ]; then
    echo "Warning: init_database.sql not found!"
    echo "Database initialization will be skipped."
    echo
fi

# 启动服务
echo "Starting service on port 3003..."
sbt run

if [ $? -ne 0 ]; then
    echo
    echo "Service startup failed!"
    echo "Check the logs above for error details."
    exit 1
fi

echo
echo "Service started successfully!"
