-- GalPHOS 用户认证服务数据库初始化脚本（修正版）

-- 确保 schema 存在
CREATE SCHEMA IF NOT EXISTS authservice;

-- 设置搜索路径
SET search_path TO authservice, public;

-- 创建省份表
CREATE TABLE IF NOT EXISTS province_table (
    province_id VARCHAR NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建学校表
CREATE TABLE IF NOT EXISTS school_table (
    school_id VARCHAR NOT NULL PRIMARY KEY,
    province_id VARCHAR NOT NULL REFERENCES province_table(province_id),
    name TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建用户表
CREATE TABLE IF NOT EXISTS user_table (
    user_id VARCHAR NOT NULL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    province_id VARCHAR REFERENCES province_table(province_id),
    school_id VARCHAR REFERENCES school_table(school_id),
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP DEFAULT NULL
);

-- 创建管理员表
CREATE TABLE IF NOT EXISTS admin_table (
    admin_id VARCHAR NOT NULL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建Token黑名单表
CREATE TABLE IF NOT EXISTS token_blacklist_table (
    token_id VARCHAR NOT NULL PRIMARY KEY,
    token_hash TEXT NOT NULL UNIQUE,
    expired_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建登录日志表
CREATE TABLE IF NOT EXISTS login_log_table (
    log_id VARCHAR NOT NULL PRIMARY KEY,
    user_id VARCHAR,
    admin_id VARCHAR,
    login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT,
    login_result TEXT NOT NULL -- 'SUCCESS', 'FAILED'
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_table_username ON user_table(username);
CREATE INDEX IF NOT EXISTS idx_user_table_role ON user_table(role);
CREATE INDEX IF NOT EXISTS idx_user_table_status ON user_table(status);
CREATE INDEX IF NOT EXISTS idx_user_table_approved_at ON user_table(approved_at);
CREATE INDEX IF NOT EXISTS idx_admin_table_username ON admin_table(username);
CREATE INDEX IF NOT EXISTS idx_token_blacklist_expired_at ON token_blacklist_table(expired_at);
CREATE INDEX IF NOT EXISTS idx_login_log_user_id ON login_log_table(user_id);
CREATE INDEX IF NOT EXISTS idx_login_log_admin_id ON login_log_table(admin_id);
CREATE INDEX IF NOT EXISTS idx_login_log_time ON login_log_table(login_time);

-- 插入测试管理员账户
-- 密码: admin123，前端已进行SHA-256+盐值哈希
-- 哈希值: 0bb6a396f8c6c7f133f426e8ad6931b91f1d208a265acb66900ece3ca082aa66
-- 使用真实的UUID作为admin_id
INSERT INTO admin_table (admin_id, username, password_hash, salt) VALUES 
    ('550e8400-e29b-41d4-a716-446655440000', 'admin', '0bb6a396f8c6c7f133f426e8ad6931b91f1d208a265acb66900ece3ca082aa66', 'GalPHOS_2025_SALT')
ON CONFLICT (username) DO NOTHING;

-- 移除测试用户账户初始化，用户通过注册流程创建
-- 移除模拟省份和学校数据，实际数据通过管理接口添加

-- 显示初始化结果
SELECT 'Admin count:' as info, COUNT(*) as count FROM admin_table
UNION ALL
SELECT 'User count:' as info, COUNT(*) as count FROM user_table
UNION ALL
SELECT 'Province count:' as info, COUNT(*) as count FROM province_table
UNION ALL
SELECT 'School count:' as info, COUNT(*) as count FROM school_table;

COMMIT;
