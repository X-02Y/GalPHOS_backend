-- 创建答题提交服务数据库表

-- 考试提交记录表
CREATE TABLE IF NOT EXISTS exam_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL,
    student_id VARCHAR(255) NOT NULL,
    student_username VARCHAR(255) NOT NULL,
    coach_id VARCHAR(255),  -- 教练代理提交时的教练ID
    is_proxy_submission BOOLEAN DEFAULT FALSE,  -- 是否为代理提交
    submission_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) DEFAULT 'submitted',  -- submitted, graded, cancelled
    total_score DECIMAL(5,2),
    max_score DECIMAL(5,2),
    feedback TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 提交答案详情表
CREATE TABLE IF NOT EXISTS submission_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL REFERENCES exam_submissions(id) ON DELETE CASCADE,
    question_number INTEGER NOT NULL,
    question_id VARCHAR(255),
    answer_text TEXT,  -- 文本答案（如果有）
    answer_image_url TEXT,  -- 答案图片URL
    upload_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    score DECIMAL(5,2),  -- 该题得分
    max_score DECIMAL(5,2),  -- 该题满分
    grader_feedback TEXT,  -- 阅卷员反馈
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 文件上传记录表
CREATE TABLE IF NOT EXISTS submission_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID REFERENCES exam_submissions(id) ON DELETE CASCADE,
    file_storage_id VARCHAR(255) NOT NULL,  -- 文件存储服务中的文件ID
    original_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    upload_user_id VARCHAR(255) NOT NULL,
    upload_user_type VARCHAR(50) NOT NULL,  -- student, coach
    upload_time TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    file_url TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_exam_submissions_exam_id ON exam_submissions(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_submissions_student_id ON exam_submissions(student_id);
CREATE INDEX IF NOT EXISTS idx_exam_submissions_coach_id ON exam_submissions(coach_id);
CREATE INDEX IF NOT EXISTS idx_submission_answers_submission_id ON submission_answers(submission_id);
CREATE INDEX IF NOT EXISTS idx_submission_answers_question_number ON submission_answers(question_number);
CREATE INDEX IF NOT EXISTS idx_submission_files_submission_id ON submission_files(submission_id);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_exam_submissions_updated_at BEFORE UPDATE ON exam_submissions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_submission_answers_updated_at BEFORE UPDATE ON submission_answers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
