-- 用户管理服务数据库初始化脚本
-- 注意：此脚本主要用于创建用户管理服务专用的表结构
-- 用户基础数据仍然使用认证服务的数据库表

-- 创建用户管理服务专用模式（如果需要）
-- CREATE SCHEMA IF NOT EXISTS usermgmt;

-- 用户注册申请表（学生注册申请）
CREATE TABLE IF NOT EXISTS authservice.user_registration_requests (
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

-- 教练管理的学生表（如果在认证服务中没有创建）
CREATE TABLE IF NOT EXISTS authservice.coach_managed_students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL,
    student_id UUID NOT NULL,
    student_username VARCHAR(100) NOT NULL,
    student_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (coach_id) REFERENCES authservice.user_table(user_id) ON DELETE CASCADE,
    UNIQUE(coach_id, student_username)
);

-- 用户状态变更日志表
CREATE TABLE IF NOT EXISTS authservice.user_status_change_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    reason TEXT,
    changed_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES authservice.user_table(user_id) ON DELETE CASCADE
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_status ON authservice.user_registration_requests(status);
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_coach ON authservice.user_registration_requests(coach_username);
CREATE INDEX IF NOT EXISTS idx_user_registration_requests_created_at ON authservice.user_registration_requests(created_at);

CREATE INDEX IF NOT EXISTS idx_coach_managed_students_coach_id ON authservice.coach_managed_students(coach_id);
CREATE INDEX IF NOT EXISTS idx_coach_managed_students_student_username ON authservice.coach_managed_students(student_username);

CREATE INDEX IF NOT EXISTS idx_user_status_change_log_user_id ON authservice.user_status_change_log(user_id);
CREATE INDEX IF NOT EXISTS idx_user_status_change_log_changed_at ON authservice.user_status_change_log(changed_at);

-- 创建触发器函数：用户状态变更时自动记录日志
CREATE OR REPLACE FUNCTION log_user_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO authservice.user_status_change_log (user_id, old_status, new_status, changed_by, changed_at)
        VALUES (NEW.user_id, OLD.status, NEW.status, COALESCE(current_setting('app.current_user', true), 'system'), NOW());
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
DROP TRIGGER IF EXISTS trigger_user_status_change ON authservice.user_table;
CREATE TRIGGER trigger_user_status_change
    AFTER UPDATE ON authservice.user_table
    FOR EACH ROW
    EXECUTE FUNCTION log_user_status_change();

-- 插入一些测试数据（可选）
-- INSERT INTO authservice.user_registration_requests (username, password_hash, salt, province, school, coach_username, reason)
-- VALUES 
--     ('student_test1', 'hashed_password_1', 'salt_1', '北京市', '北京第一中学', 'coach001', '申请参加数学竞赛'),
--     ('student_test2', 'hashed_password_2', 'salt_2', '上海市', '上海中学', 'coach002', '希望参与物理竞赛训练');

-- 授权（如果需要特定用户权限）
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA authservice TO usermgmt_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA authservice TO usermgmt_user;

COMMENT ON TABLE authservice.user_registration_requests IS '用户注册申请表，主要用于学生注册申请流程';
COMMENT ON TABLE authservice.coach_managed_students IS '教练管理的学生关系表';
COMMENT ON TABLE authservice.user_status_change_log IS '用户状态变更日志表，用于审计追踪';

-- 完成提示
DO $$
BEGIN
    RAISE NOTICE '用户管理服务数据库初始化完成！';
    RAISE NOTICE '已创建以下表：';
    RAISE NOTICE '- user_registration_requests: 用户注册申请表';
    RAISE NOTICE '- coach_managed_students: 教练学生关系表';
    RAISE NOTICE '- user_status_change_log: 用户状态变更日志表';
    RAISE NOTICE '已创建相关索引和触发器以提高性能和数据一致性';
END $$;
