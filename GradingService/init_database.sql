-- 阅卷管理服务数据库初始化脚本
-- 创建阅卷相关的数据表

-- 1. 阅卷任务表
CREATE TABLE IF NOT EXISTS grading_tasks (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    grader_id BIGINT,
    question_number INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    max_score DECIMAL(10,2) NOT NULL,
    actual_score DECIMAL(10,2),
    feedback TEXT,
    assigned_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 题目分数配置表
CREATE TABLE IF NOT EXISTS question_scores (
    exam_id BIGINT NOT NULL,
    question_number INTEGER NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    question_type VARCHAR(50) NOT NULL DEFAULT 'SUBJECTIVE',
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exam_id, question_number)
);

-- 3. 教练学生表（非独立学生账号）
CREATE TABLE IF NOT EXISTS coach_students (
    id BIGSERIAL PRIMARY KEY,
    coach_id BIGINT NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    student_school VARCHAR(200) NOT NULL,
    student_province VARCHAR(50) NOT NULL,
    grade VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. 评分历史表
CREATE TABLE IF NOT EXISTS score_history (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    grader_id BIGINT NOT NULL,
    question_number INTEGER NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    feedback TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. 阅卷图片表
CREATE TABLE IF NOT EXISTS grading_images (
    id BIGSERIAL PRIMARY KEY,
    image_url VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    exam_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    question_number INTEGER NOT NULL,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    file_size BIGINT,
    mime_type VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_grading_tasks_exam_id ON grading_tasks(exam_id);
CREATE INDEX IF NOT EXISTS idx_grading_tasks_grader_id ON grading_tasks(grader_id);
CREATE INDEX IF NOT EXISTS idx_grading_tasks_status ON grading_tasks(status);
CREATE INDEX IF NOT EXISTS idx_grading_tasks_submission_id ON grading_tasks(submission_id);
CREATE INDEX IF NOT EXISTS idx_coach_students_coach_id ON coach_students(coach_id);
CREATE INDEX IF NOT EXISTS idx_coach_students_is_active ON coach_students(is_active);
CREATE INDEX IF NOT EXISTS idx_score_history_task_id ON score_history(task_id);
CREATE INDEX IF NOT EXISTS idx_score_history_grader_id ON score_history(grader_id);
CREATE INDEX IF NOT EXISTS idx_grading_images_exam_id ON grading_images(exam_id);
CREATE INDEX IF NOT EXISTS idx_grading_images_student_id ON grading_images(student_id);
CREATE INDEX IF NOT EXISTS idx_grading_images_question_number ON grading_images(question_number);
CREATE INDEX IF NOT EXISTS idx_grading_images_upload_time ON grading_images(upload_time);

-- 插入测试数据（可选）
-- 注意：这些数据仅用于开发测试，生产环境应删除

-- 题目分数配置示例
INSERT INTO question_scores (exam_id, question_number, max_score, question_type, description) VALUES
(1, 1, 10.00, 'MULTIPLE_CHOICE', '单选题'),
(1, 2, 10.00, 'MULTIPLE_CHOICE', '单选题'),
(1, 3, 15.00, 'SUBJECTIVE', '主观题'),
(1, 4, 15.00, 'SUBJECTIVE', '主观题'),
(1, 5, 25.00, 'SUBJECTIVE', '综合题'),
(1, 6, 25.00, 'SUBJECTIVE', '综合题')
ON CONFLICT (exam_id, question_number) DO NOTHING;

-- 阅卷任务示例
INSERT INTO grading_tasks (exam_id, submission_id, question_number, status, max_score) VALUES
(1, 1, 1, 'PENDING', 10.00),
(1, 1, 2, 'PENDING', 10.00),
(1, 1, 3, 'PENDING', 15.00),
(1, 1, 4, 'PENDING', 15.00),
(1, 1, 5, 'PENDING', 25.00),
(1, 1, 6, 'PENDING', 25.00),
(1, 2, 1, 'PENDING', 10.00),
(1, 2, 2, 'PENDING', 10.00),
(1, 2, 3, 'PENDING', 15.00),
(1, 2, 4, 'PENDING', 15.00),
(1, 2, 5, 'PENDING', 25.00),
(1, 2, 6, 'PENDING', 25.00)
ON CONFLICT DO NOTHING;

-- 教练学生示例（假设教练ID为2）
INSERT INTO coach_students (coach_id, student_name, student_school, student_province, grade) VALUES
(2, '张三', '北京市第一中学', '北京市', '高三'),
(2, '李四', '北京市第二中学', '北京市', '高三'),
(2, '王五', '上海市第一中学', '上海市', '高二')
ON CONFLICT DO NOTHING;

-- 阅卷图片示例
INSERT INTO grading_images (image_url, file_name, exam_id, student_id, question_number, file_size, mime_type) VALUES
('/uploads/exams/1/students/1/question_1_answer.jpg', 'question_1_answer.jpg', 1, 1, 1, 245760, 'image/jpeg'),
('/uploads/exams/1/students/1/question_2_answer.jpg', 'question_2_answer.jpg', 1, 1, 2, 189440, 'image/jpeg'),
('/uploads/exams/1/students/1/question_3_answer.jpg', 'question_3_answer.jpg', 1, 1, 3, 312580, 'image/jpeg'),
('/uploads/exams/1/students/2/question_1_answer.jpg', 'question_1_answer.jpg', 1, 2, 1, 298140, 'image/jpeg'),
('/uploads/exams/1/students/2/question_2_answer.jpg', 'question_2_answer.jpg', 1, 2, 2, 221090, 'image/jpeg'),
('/uploads/exams/1/students/2/question_3_answer.jpg', 'question_3_answer.jpg', 1, 2, 3, 276880, 'image/jpeg')
ON CONFLICT DO NOTHING;

-- 创建约束和触发器
-- 更新时间自动更新触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为相关表添加更新时间触发器
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_grading_tasks_updated_at') THEN
        CREATE TRIGGER update_grading_tasks_updated_at
            BEFORE UPDATE ON grading_tasks
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_question_scores_updated_at') THEN
        CREATE TRIGGER update_question_scores_updated_at
            BEFORE UPDATE ON question_scores
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_coach_students_updated_at') THEN
        CREATE TRIGGER update_coach_students_updated_at
            BEFORE UPDATE ON coach_students
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END$$;

-- 插入完成消息
DO $$
BEGIN
    RAISE NOTICE '阅卷管理服务数据库初始化完成';
    RAISE NOTICE '已创建表: grading_tasks, question_scores, coach_students, score_history, grading_images';
    RAISE NOTICE '已创建索引以提高查询性能';
    RAISE NOTICE '已插入测试数据（如需要）';
END$$;
