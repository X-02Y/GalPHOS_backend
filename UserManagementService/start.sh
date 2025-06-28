#!/bin/bash

# GalPHOS 用户认证服务启动脚本

# 设置环境变量
export JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC"

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装Java 11或更高版本"
    exit 1
fi

# 检查SBT环境
if ! command -v sbt &> /dev/null; then
    echo "错误: 未找到SBT，请先安装SBT构建工具"
    exit 1
fi

# 检查配置文件
if [ ! -f "server_config.json" ]; then
    echo "错误: 未找到配置文件 server_config.json"
    exit 1
fi

# 检查数据库连接
echo "检查数据库连接..."
# 这里可以添加数据库连接检查逻辑

echo "开始编译项目..."
sbt compile

if [ $? -ne 0 ]; then
    echo "错误: 项目编译失败"
    exit 1
fi

echo "启动用户管理服务..."
echo "服务将在 http://localhost:3002 启动"
echo "按 Ctrl+C 停止服务"

sbt run
