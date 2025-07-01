-- GalPHOS 用户管理服务数据库初始化脚本
-- 统一数据库初始化脚本
-- 数据库: galphos
-- Schema: authservice (共享UserAuthService的schema)

-- 设置搜索路径
SET search_path TO authservice, public;

-- 用户注册申请表（学生注册申请）
CREATE TABLE IF NOT EXISTS user_registration_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(255) NOT NULL,
    province VARCHAR(100) NOT NULL,
    school VARCHAR(100) NOT NULL,
    coach_username VARCHAR(100),
    reason TEXT,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMP DEFAULT NOW(),
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_note TEXT
);

-- 教练管理的学生表
CREATE TABLE IF NOT EXISTS coach_managed_students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id VARCHAR NOT NULL,
    student_id VARCHAR NOT NULL,
    student_username VARCHAR(100) NOT NULL,
    student_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(coach_id, student_username)
);

-- 用户状态变更日志表
CREATE TABLE IF NOT EXISTS user_status_change_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    reason TEXT,
    changed_at TIMESTAMP DEFAULT NOW()
);

-- 区域变更申请表（已在UserAuthService中定义，这里确保存在）
CREATE TABLE IF NOT EXISTS region_change_requests (
    id VARCHAR NOT NULL PRIMARY KEY,
    user_id VARCHAR NOT NULL,
    username TEXT NOT NULL,
    role TEXT NOT NULL,
    current_province TEXT,
    current_school TEXT,
    requested_province TEXT NOT NULL,
    requested_school TEXT NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    processed_by TEXT,
    admin_comment TEXT
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_status ON user_registration_requests(status);
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_coach ON user_registration_requests(coach_username);
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_created_at ON user_registration_requests(created_at);

CREATE INDEX IF NOT EXISTS idx_coach_managed_students_coach_id ON coach_managed_students(coach_id);
CREATE INDEX IF NOT EXISTS idx_coach_managed_students_student_username ON coach_managed_students(student_username);

CREATE INDEX IF NOT EXISTS idx_user_status_change_log_user_id ON user_status_change_log(user_id);
CREATE INDEX IF NOT EXISTS idx_user_status_change_log_changed_at ON user_status_change_log(changed_at);

CREATE INDEX IF NOT EXISTS idx_region_change_requests_user_id ON region_change_requests(user_id);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_username ON region_change_requests(username);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_status ON region_change_requests(status);
CREATE INDEX IF NOT EXISTS idx_region_change_requests_created_at ON region_change_requests(created_at);

-- 创建触发器函数：用户状态变更时自动记录日志
CREATE OR REPLACE FUNCTION log_user_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO user_status_change_log (user_id, old_status, new_status, changed_by, changed_at)
        VALUES (NEW.user_id, OLD.status, NEW.status, COALESCE(current_setting('app.current_user', true), 'system'), NOW());
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
DROP TRIGGER IF EXISTS trigger_user_status_change ON user_table;
CREATE TRIGGER trigger_user_status_change
    AFTER UPDATE ON user_table
    FOR EACH ROW
    EXECUTE FUNCTION log_user_status_change();

-- 表注释
COMMENT ON TABLE user_registration_requests IS '用户注册申请表，主要用于学生注册申请流程';
COMMENT ON TABLE coach_managed_students IS '教练管理的学生关系表';
COMMENT ON TABLE user_status_change_log IS '用户状态变更日志表，用于审计追踪';
COMMENT ON TABLE region_change_requests IS '区域变更申请表，用于用户申请更改省份和学校';

-- 显示初始化结果
SELECT 'UserManagementService tables created successfully' as status;

COMMIT;
