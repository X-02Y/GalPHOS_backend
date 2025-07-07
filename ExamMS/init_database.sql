-- 考试管理服务数据库初始化脚本
-- 创建考试管理相关的表结构

-- 创建schema
CREATE SCHEMA IF NOT EXISTS examservice;
SET search_path TO examservice;

-- 创建考试表
CREATE TABLE IF NOT EXISTS exams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'ongoing', 'grading', 'completed')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,
    duration INTEGER, -- 考试时长（分钟）
    total_questions INTEGER,
    total_score DECIMAL(10,2),
    max_score DECIMAL(10,2),
    subject VARCHAR(100),
    instructions TEXT,
    question_file_id UUID,
    answer_file_id UUID,
    answer_sheet_file_id UUID
);

-- 创建考试文件表
CREATE TABLE IF NOT EXISTS exam_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    file_id VARCHAR(255) NOT NULL, -- 文件存储服务返回的文件ID
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    file_url VARCHAR(500),
    file_size BIGINT,
    file_type VARCHAR(20) CHECK (file_type IN ('question', 'answer', 'answerSheet')),
    mime_type VARCHAR(100),
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    uploaded_by VARCHAR(100) NOT NULL
);

-- 创建考试问题表
CREATE TABLE IF NOT EXISTS exam_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    question_number INTEGER NOT NULL,
    score DECIMAL(10,2) NOT NULL DEFAULT 0,
    max_score DECIMAL(10,2),
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(exam_id, question_number)
);

-- 创建考试提交表
CREATE TABLE IF NOT EXISTS exam_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_username VARCHAR(100) NOT NULL,
    submitted_by VARCHAR(100), -- 提交者（可能是教练代提交）
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'submitted' CHECK (status IN ('submitted', 'graded')),
    total_score DECIMAL(10,2),
    rank_position INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(exam_id, student_username)
);

-- 创建考试答案表
CREATE TABLE IF NOT EXISTS exam_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES exam_submissions(id) ON DELETE CASCADE,
    question_number INTEGER NOT NULL,
    answer_image_url VARCHAR(500),
    answer_file_id VARCHAR(255), -- 文件存储服务返回的文件ID
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    score DECIMAL(10,2), -- 该题得分
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(submission_id, question_number)
);

-- 创建考试参与者表
CREATE TABLE IF NOT EXISTS exam_participants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_username VARCHAR(100) NOT NULL,
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) DEFAULT 'registered' CHECK (status IN ('registered', 'submitted', 'graded')),
    UNIQUE(exam_id, student_username)
);

-- 创建考试统计表
CREATE TABLE IF NOT EXISTS exam_statistics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    total_participants INTEGER DEFAULT 0,
    total_submissions INTEGER DEFAULT 0,
    total_graded INTEGER DEFAULT 0,
    average_score DECIMAL(10,2),
    highest_score DECIMAL(10,2),
    lowest_score DECIMAL(10,2),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建预申请考试ID表
CREATE TABLE IF NOT EXISTS reserved_exam_ids (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID UNIQUE NOT NULL,
    reserved_by VARCHAR(100) NOT NULL,
    reserved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP + INTERVAL '24 hours',
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_exams_status ON exams(status);
CREATE INDEX IF NOT EXISTS idx_exams_created_by ON exams(created_by);
CREATE INDEX IF NOT EXISTS idx_exams_start_time ON exams(start_time);
CREATE INDEX IF NOT EXISTS idx_exams_end_time ON exams(end_time);

CREATE INDEX IF NOT EXISTS idx_exam_files_exam_id ON exam_files(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_files_type ON exam_files(file_type);

CREATE INDEX IF NOT EXISTS idx_exam_questions_exam_id ON exam_questions(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_questions_number ON exam_questions(question_number);

CREATE INDEX IF NOT EXISTS idx_exam_submissions_exam_id ON exam_submissions(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_submissions_student ON exam_submissions(student_username);
CREATE INDEX IF NOT EXISTS idx_exam_submissions_status ON exam_submissions(status);

CREATE INDEX IF NOT EXISTS idx_exam_answers_submission_id ON exam_answers(submission_id);
CREATE INDEX IF NOT EXISTS idx_exam_answers_question_number ON exam_answers(question_number);

CREATE INDEX IF NOT EXISTS idx_exam_participants_exam_id ON exam_participants(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_participants_student ON exam_participants(student_username);

CREATE INDEX IF NOT EXISTS idx_reserved_exam_ids_exam_id ON reserved_exam_ids(exam_id);
CREATE INDEX IF NOT EXISTS idx_reserved_exam_ids_reserved_by ON reserved_exam_ids(reserved_by);
CREATE INDEX IF NOT EXISTS idx_reserved_exam_ids_expires_at ON reserved_exam_ids(expires_at);

-- 创建更新时间的触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_exams_updated_at BEFORE UPDATE ON exams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_exam_questions_updated_at BEFORE UPDATE ON exam_questions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_exam_submissions_updated_at BEFORE UPDATE ON exam_submissions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_exam_answers_updated_at BEFORE UPDATE ON exam_answers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 插入默认数据
INSERT INTO exams (title, description, start_time, end_time, status, created_by, duration, total_questions, total_score, max_score, subject)
VALUES 
    ('示例物理考试', '这是一个示例物理考试', 
     CURRENT_TIMESTAMP + INTERVAL '1 day', 
     CURRENT_TIMESTAMP + INTERVAL '1 day' + INTERVAL '3 hours', 
     'draft', 'admin', 180, 20, 100.0, 100.0, '物理'),
    ('示例数学考试', '这是一个示例数学考试', 
     CURRENT_TIMESTAMP + INTERVAL '2 days', 
     CURRENT_TIMESTAMP + INTERVAL '2 days' + INTERVAL '2 hours', 
     'draft', 'admin', 120, 15, 75.0, 75.0, '数学')
ON CONFLICT DO NOTHING;

COMMIT;
