-- 创建系统配置数据库初始化脚本
-- 数据库名称: system_config
-- Schema: systemconfig
-- 端口: 3009

BEGIN;

-- 创建数据库（如果不存在）
-- CREATE DATABASE system_config OWNER db ENCODING 'UTF8' LC_COLLATE = 'en_US.utf8' LC_CTYPE = 'en_US.utf8';

-- 使用数据库
\c system_config;

-- 创建schema
CREATE SCHEMA IF NOT EXISTS systemconfig;
SET search_path = systemconfig;

-- 删除旧表（如果存在）
DROP TABLE IF EXISTS system_admins CASCADE;
DROP TABLE IF EXISTS system_settings CASCADE;

-- 创建系统管理员表
CREATE TABLE IF NOT EXISTS system_admins (
    admin_id VARCHAR NOT NULL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'admin' CHECK (role IN ('admin', 'super_admin')),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'disabled')),
    name TEXT,  -- 显示名称
    email TEXT,  -- 邮箱地址
    phone TEXT,  -- 联系电话
    avatar_url TEXT,  -- 头像链接
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP
);

-- 创建系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    setting_id VARCHAR NOT NULL PRIMARY KEY,
    setting_key TEXT NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    setting_type TEXT NOT NULL DEFAULT 'text' CHECK (setting_type IN ('text', 'boolean', 'number', 'json')),
    category TEXT NOT NULL DEFAULT 'general',
    description TEXT,
    is_public BOOLEAN DEFAULT false,  -- 是否为公开设置（前端可访问）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR REFERENCES system_admins(admin_id)
);



-- 创建索引
CREATE INDEX IF NOT EXISTS idx_system_admins_username ON system_admins(username);
CREATE INDEX IF NOT EXISTS idx_system_admins_status ON system_admins(status);
CREATE INDEX IF NOT EXISTS idx_system_settings_key ON system_settings(setting_key);
CREATE INDEX IF NOT EXISTS idx_system_settings_category ON system_settings(category);
CREATE INDEX IF NOT EXISTS idx_system_settings_public ON system_settings(is_public);

-- 插入默认系统管理员
-- 用户名: admin, 密码: admin123
-- 密码哈希值: SHA256("admin123" + "SYSTEM_CONFIG_SALT")
-- 使用真实的UUID作为admin_id，设置为超级管理员
INSERT INTO system_admins (admin_id, username, password_hash, salt, role, status, name) VALUES 
    ('660e8400-e29b-41d4-a716-446655440000', 'admin', '4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a', 'SYSTEM_CONFIG_SALT', 'super_admin', 'active', '系统配置管理员')
ON CONFLICT (username) DO UPDATE SET 
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    name = EXCLUDED.name;

-- 插入默认系统设置
INSERT INTO system_settings (setting_id, setting_key, setting_value, setting_type, category, description, is_public) VALUES 
    ('setting_001', 'maintenance_mode', 'false', 'boolean', 'system', '维护模式开关', true),
    ('setting_002', 'system_title', 'GalPHOS 考试管理系统', 'text', 'display', '系统标题', true),
    ('setting_003', 'system_version', '1.3.0', 'text', 'system', '系统版本', true),
    ('setting_004', 'max_upload_size', '10485760', 'number', 'file', '最大文件上传大小（字节）', false),
    ('setting_005', 'session_timeout', '3600', 'number', 'security', '会话超时时间（秒）', false),
    ('setting_006', 'enable_registration', 'true', 'boolean', 'user', '允许用户注册', false),
    ('setting_007', 'default_language', 'zh-CN', 'text', 'display', '默认语言', true),
    ('setting_008', 'system_logo_url', '/images/logo.png', 'text', 'display', '系统Logo地址', true)
ON CONFLICT (setting_key) DO UPDATE SET 
    setting_value = EXCLUDED.setting_value,
    updated_at = CURRENT_TIMESTAMP;



-- 显示初始化结果
SELECT 'System admin count:' as info, COUNT(*) as count FROM system_admins
UNION ALL
SELECT 'System settings count:' as info, COUNT(*) as count FROM system_settings;

-- 显示表结构信息
SELECT 'Tables created successfully' as status;

COMMIT;
