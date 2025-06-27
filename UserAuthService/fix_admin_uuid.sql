-- 清理和更新管理员数据脚本

-- 删除旧的无效UUID记录
DELETE FROM authservice.admin_table WHERE admin_id = 'admin-001';

-- 插入正确的管理员记录
INSERT INTO authservice.admin_table (admin_id, username, password_hash, salt) VALUES 
    ('550e8400-e29b-41d4-a716-446655440000', 'admin', '0bb6a396f8c6c7f133f426e8ad6931b91f1d208a265acb66900ece3ca082aa66', 'GalPHOS_2025_SALT')
ON CONFLICT (username) DO UPDATE SET 
    admin_id = EXCLUDED.admin_id,
    password_hash = EXCLUDED.password_hash,
    salt = EXCLUDED.salt;

-- 验证结果
SELECT admin_id, username, LEFT(password_hash, 10) || '...' as password_preview FROM authservice.admin_table;
