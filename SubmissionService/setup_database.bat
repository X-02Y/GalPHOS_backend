@echo off
echo 设置答题提交服务数据库...

:: 检查PostgreSQL环境
psql --version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到PostgreSQL客户端，请先安装PostgreSQL
    pause
    exit /b 1
)

:: 检查数据库初始化脚本
if not exist "init_database.sql" (
    echo 错误: 找不到数据库初始化脚本 init_database.sql
    pause
    exit /b 1
)

echo 正在初始化答题提交服务数据库表...
echo 连接数据库: galphos@localhost:5432

:: 执行数据库初始化脚本
psql -h localhost -U postgres -d galphos -f init_database.sql

if %errorlevel% equ 0 (
    echo 数据库初始化成功！
) else (
    echo 数据库初始化失败！请检查数据库连接和权限。
)

pause
