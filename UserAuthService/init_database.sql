-- GalPHOS 用户认证服务数据库初始化脚本
-- 统一数据库初始化脚本，地区数据完全由RegionMS提供
-- 数据库: galphos
-- Schema: authservice

-- 确保 schema 存在
CREATE SCHEMA IF NOT EXISTS authservice;

-- 设置搜索路径
SET search_path TO authservice, public;

-- 删除旧的地区相关表（如果存在）
DROP TABLE IF EXISTS user_table CASCADE;
DROP TABLE IF EXISTS school_table CASCADE;
DROP TABLE IF EXISTS province_table CASCADE;

-- 创建用户表（移除地区表外键约束，改为存储地区ID字符串）
CREATE TABLE IF NOT EXISTS user_table (
    user_id VARCHAR NOT NULL PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    phone VARCHAR(20),
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    province_id VARCHAR,  -- 存储RegionMS的province UUID
    school_id VARCHAR,    -- 存储RegionMS的school UUID
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

-- 创建用户注册申请表
CREATE TABLE IF NOT EXISTS user_registration_requests (
    id VARCHAR NOT NULL PRIMARY KEY,
    username TEXT NOT NULL,
    province TEXT NOT NULL,        -- 存储省份名称
    school TEXT NOT NULL,          -- 存储学校名称
    coach_username TEXT,           -- 关联的教练用户名
    reason TEXT,                   -- 申请理由
    status TEXT NOT NULL DEFAULT 'pending',  -- pending, approved, rejected
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_by TEXT,              -- 审核人
    reviewed_at TIMESTAMP,         -- 审核时间
    review_note TEXT               -- 审核备注
);

-- 创建区域变更申请表
CREATE TABLE IF NOT EXISTS region_change_requests (
    id VARCHAR NOT NULL PRIMARY KEY,
    user_id VARCHAR NOT NULL,
    username TEXT NOT NULL,
    role TEXT NOT NULL,
    current_province TEXT,         -- 当前省份名称
    current_school TEXT,           -- 当前学校名称
    requested_province TEXT NOT NULL,  -- 申请变更的省份名称
    requested_school TEXT NOT NULL,    -- 申请变更的学校名称
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',  -- pending, approved, rejected
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    processed_by TEXT,             -- 处理人
    admin_comment TEXT             -- 管理员备注
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_user_table_username ON user_table(username);
CREATE INDEX IF NOT EXISTS idx_user_table_role ON user_table(role);
CREATE INDEX IF NOT EXISTS idx_user_table_status ON user_table(status);
CREATE INDEX IF NOT EXISTS idx_user_table_province_id ON user_table(province_id);
CREATE INDEX IF NOT EXISTS idx_user_table_school_id ON user_table(school_id);
CREATE INDEX IF NOT EXISTS idx_user_table_approved_at ON user_table(approved_at);
CREATE INDEX IF NOT EXISTS idx_admin_table_username ON admin_table(username);
CREATE INDEX IF NOT EXISTS idx_token_blacklist_expired_at ON token_blacklist_table(expired_at);
CREATE INDEX IF NOT EXISTS idx_login_log_user_id ON login_log_table(user_id);
CREATE INDEX IF NOT EXISTS idx_login_log_admin_id ON login_log_table(admin_id);
CREATE INDEX IF NOT EXISTS idx_login_log_time ON login_log_table(login_time);
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_status ON user_registration_requests(status);
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_created_at ON user_registration_requests(created_at);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_user_id ON region_change_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_status ON region_change_requests(status);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_created_at ON region_change_requests(created_at);

-- 插入测试管理员账户
-- 密码: admin123，前端已进行SHA-256+盐值哈希
-- 哈希值: 0bb6a396f8c6c7f133f426e8ad6931b91f1d208a265acb66900ece3ca082aa66
-- 使用真实的UUID作为admin_id
INSERT INTO admin_table (admin_id, username, password_hash, salt) VALUES 
    ('550e8400-e29b-41d4-a716-446655440000', 'admin', '0bb6a396f8c6c7f133f426e8ad6931b91f1d208a265acb66900ece3ca082aa66', 'GalPHOS_2025_SALT')
ON CONFLICT (username) DO NOTHING;

-- 显示初始化结果
SELECT 'Admin count:' as info, COUNT(*) as count FROM admin_table
UNION ALL
SELECT 'User count:' as info, COUNT(*) as count FROM user_table
UNION ALL
SELECT 'User registration requests:' as info, COUNT(*) as count FROM user_registration_requests
UNION ALL
SELECT 'Region change requests:' as info, COUNT(*) as count FROM region_change_requests;

-- 显示表结构信息
SELECT 'Tables created successfully' as status;

COMMIT;
