#!/bin/bash

echo "启动答题提交服务..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装Java 11+"
    exit 1
fi

# 检查SBT环境
if ! command -v sbt &> /dev/null; then
    echo "错误: 未找到SBT环境，请先安装SBT"
    exit 1
fi

# 创建日志目录
mkdir -p logs

# 检查配置文件
if [ ! -f "server_config.json" ]; then
    echo "错误: 找不到配置文件 server_config.json"
    exit 1
fi

echo "正在启动答题提交服务..."
echo "服务将在端口 3004 上运行"
echo "按 Ctrl+C 停止服务"

sbt "run"
