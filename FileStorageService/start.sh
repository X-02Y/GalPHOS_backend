#!/bin/bash

# FileStorageService 启动脚本

echo "正在启动 FileStorageService..."

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请安装Java 11或更高版本"
    exit 1
fi

# 检查sbt环境
if ! command -v sbt &> /dev/null; then
    echo "错误: 未找到sbt，请安装sbt"
    exit 1
fi

# 检查数据库连接
echo "检查数据库连接..."
if ! psql -h localhost -p 5432 -U db -d file_storage -c '\q' 2>/dev/null; then
    echo "警告: 数据库连接失败，请确保PostgreSQL已启动并配置正确"
    echo "数据库配置: localhost:5432/file_storage"
    echo "用户名: db"
    echo "如需初始化数据库，请运行: psql -U db -d file_storage -f init_database.sql"
fi

# 创建存储目录
mkdir -p ./storage/files/uploads
mkdir -p ./storage/files/temp
mkdir -p ./storage/files/images
mkdir -p ./storage/files/exports
mkdir -p ./storage/files/archives

echo "存储目录已创建: ./storage/files"

# 设置JVM参数
export JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"

# 启动服务
echo "正在编译和启动服务..."
sbt "run"
