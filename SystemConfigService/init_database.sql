-- SystemConfigService 初始化脚本

-- 创建系统配置表
CREATE TABLE IF NOT EXISTS system_config (
    id SERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT NOT NULL,
    description TEXT,
    is_public BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 不再创建独立的管理员表，所有管理员数据存储在 UserAuthService 的 admin_table 中
-- CREATE TABLE IF NOT EXISTS system_admins (
--     admin_id SERIAL PRIMARY KEY,
--     username VARCHAR(50) NOT NULL UNIQUE,
--     password_hash VARCHAR(255) NOT NULL,
--     role VARCHAR(20) NOT NULL DEFAULT 'admin',  -- 角色字段，匹配前端
--     is_super_admin BOOLEAN NOT NULL DEFAULT FALSE,
--     created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
--     updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
--     last_login TIMESTAMP WITH TIME ZONE
-- );

-- 创建配置变更历史表
CREATE TABLE IF NOT EXISTS config_history (
    id SERIAL PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL,
    old_value TEXT,
    new_value TEXT,
    changed_by INTEGER REFERENCES system_admins(admin_id),
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建版本信息表
CREATE TABLE IF NOT EXISTS system_version (
    id SERIAL PRIMARY KEY,
    version VARCHAR(20) NOT NULL,
    build_number VARCHAR(50) NOT NULL,
    release_date TIMESTAMP WITH TIME ZONE NOT NULL,
    release_notes TEXT,
    is_current BOOLEAN NOT NULL DEFAULT FALSE
);

-- 插入默认系统配置
INSERT INTO system_config (config_key, config_value, description, is_public) VALUES
('system.name', 'GalPHOS 统一管理系统', '系统名称', TRUE),
('system.maintenance', 'false', '是否处于维护模式', TRUE),
('system.announcement', '', '系统公告', TRUE),
('admin.session.timeout', '3600', '管理员会话超时时间（秒）', FALSE),
('admin.login.attempts', '5', '管理员登录尝试次数限制', FALSE),
('admin.password.minLength', '8', '管理员密码最小长度', FALSE)
ON CONFLICT (config_key) DO NOTHING;

-- 不再插入独立的超级管理员账号，管理员账号统一由 UserAuthService 管理
-- INSERT INTO system_admins (username, password_hash, role, is_super_admin) VALUES
-- ('superadmin', '$2a$10$xVn9UnZzMbKPG.47InwU3.8cM6FzZve7dOmC7qfxRkJJnXepLsiEa', 'super_admin', TRUE)
-- ON CONFLICT (username) DO NOTHING;

-- 插入当前版本信息
INSERT INTO system_version (version, build_number, release_date, release_notes, is_current) VALUES
('1.3.0', '20250701', CURRENT_TIMESTAMP, '新版本发布，优化系统配置功能', TRUE)
ON CONFLICT DO NOTHING;
