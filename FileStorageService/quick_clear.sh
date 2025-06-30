#!/bin/bash
# FileStorageService 快速清理脚本

echo "=== FileStorageService 存储清理 ==="
echo ""

# 进入FileStorageService目录
cd "$(dirname "$0")"

# 清理数据库
echo "正在清理数据库..."
export PGPASSWORD="password"

# 直接执行SQL命令清理
psql -h localhost -p 5432 -U db -d file_storage -c "
SELECT 'Files before cleanup' as status, COUNT(*) as total_files FROM files;
DELETE FROM files;
SELECT 'Files after cleanup' as status, COUNT(*) as remaining_files FROM files;
" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "✓ 数据库清理完成"
else
    echo "✗ 数据库清理失败，请检查数据库连接"
fi

# 清理存储文件
echo "正在清理存储文件..."
STORAGE_DIR="./storage/files"

if [ -d "$STORAGE_DIR" ]; then
    # 统计清理前的文件
    BEFORE_COUNT=$(find "$STORAGE_DIR" -type f 2>/dev/null | wc -l)
    
    # 删除所有文件但保留目录结构
    find "$STORAGE_DIR" -type f -delete 2>/dev/null
    
    # 统计清理后的文件
    AFTER_COUNT=$(find "$STORAGE_DIR" -type f 2>/dev/null | wc -l)
    
    DELETED_COUNT=$((BEFORE_COUNT - AFTER_COUNT))
    
    echo "✓ 文件清理完成，已删除 $DELETED_COUNT 个文件"
    
    if [ $AFTER_COUNT -gt 0 ]; then
        echo "⚠ 警告: 还有 $AFTER_COUNT 个文件未能删除"
    fi
else
    echo "⚠ 存储目录不存在: $STORAGE_DIR"
fi

echo ""
echo "=== 清理完成 ==="
echo "建议重启 FileStorageService 以确保服务正常运行"
