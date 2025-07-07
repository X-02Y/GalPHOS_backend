-- 创建成绩统计服务数据库schema
CREATE SCHEMA IF NOT EXISTS scorestatistics;

-- 设置搜索路径
SET search_path TO scorestatistics, public;

-- 考试成绩表
CREATE TABLE IF NOT EXISTS exam_scores (
    id SERIAL PRIMARY KEY,
    exam_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    total_score DECIMAL(5,2) DEFAULT 0.00,
    question_scores JSONB DEFAULT '{}',
    rank_position INTEGER DEFAULT 0,
    percentile DECIMAL(5,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(exam_id, student_id)
);

-- 考试统计表
CREATE TABLE IF NOT EXISTS exam_statistics (
    id SERIAL PRIMARY KEY,
    exam_id INTEGER NOT NULL UNIQUE,
    total_submissions INTEGER DEFAULT 0,
    average_score DECIMAL(5,2) DEFAULT 0.00,
    highest_score DECIMAL(5,2) DEFAULT 0.00,
    lowest_score DECIMAL(5,2) DEFAULT 0.00,
    median_score DECIMAL(5,2) DEFAULT 0.00,
    pass_rate DECIMAL(5,2) DEFAULT 0.00,
    difficulty_analysis JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 学生统计表
CREATE TABLE IF NOT EXISTS student_statistics (
    id SERIAL PRIMARY KEY,
    student_id INTEGER NOT NULL UNIQUE,
    total_exams INTEGER DEFAULT 0,
    average_score DECIMAL(5,2) DEFAULT 0.00,
    best_score DECIMAL(5,2) DEFAULT 0.00,
    worst_score DECIMAL(5,2) DEFAULT 0.00,
    improvement_trend DECIMAL(5,2) DEFAULT 0.00,
    strong_subjects JSONB DEFAULT '[]',
    weak_subjects JSONB DEFAULT '[]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 教练统计表
CREATE TABLE IF NOT EXISTS coach_statistics (
    id SERIAL PRIMARY KEY,
    coach_id INTEGER NOT NULL UNIQUE,
    total_students INTEGER DEFAULT 0,
    total_exams INTEGER DEFAULT 0,
    average_student_score DECIMAL(5,2) DEFAULT 0.00,
    best_student_score DECIMAL(5,2) DEFAULT 0.00,
    class_performance JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 阅卷员统计表
CREATE TABLE IF NOT EXISTS grader_statistics (
    id SERIAL PRIMARY KEY,
    grader_id INTEGER NOT NULL UNIQUE,
    total_graded INTEGER DEFAULT 0,
    grading_accuracy DECIMAL(5,2) DEFAULT 0.00,
    grading_speed DECIMAL(5,2) DEFAULT 0.00,
    grading_history JSONB DEFAULT '[]',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统统计表
CREATE TABLE IF NOT EXISTS system_statistics (
    id SERIAL PRIMARY KEY,
    stat_date DATE NOT NULL UNIQUE,
    total_users INTEGER DEFAULT 0,
    total_students INTEGER DEFAULT 0,
    total_coaches INTEGER DEFAULT 0,
    total_graders INTEGER DEFAULT 0,
    total_exams INTEGER DEFAULT 0,
    total_submissions INTEGER DEFAULT 0,
    system_metrics JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_exam_scores_exam_id ON exam_scores(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_scores_student_id ON exam_scores(student_id);
CREATE INDEX IF NOT EXISTS idx_exam_scores_rank ON exam_scores(rank_position);
CREATE INDEX IF NOT EXISTS idx_exam_statistics_exam_id ON exam_statistics(exam_id);
CREATE INDEX IF NOT EXISTS idx_student_statistics_student_id ON student_statistics(student_id);
CREATE INDEX IF NOT EXISTS idx_coach_statistics_coach_id ON coach_statistics(coach_id);
CREATE INDEX IF NOT EXISTS idx_grader_statistics_grader_id ON grader_statistics(grader_id);
CREATE INDEX IF NOT EXISTS idx_system_statistics_date ON system_statistics(stat_date);

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为相关表添加更新时间触发器
CREATE TRIGGER update_exam_scores_modtime 
    BEFORE UPDATE ON exam_scores 
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_exam_statistics_modtime 
    BEFORE UPDATE ON exam_statistics 
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_student_statistics_modtime 
    BEFORE UPDATE ON student_statistics 
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_coach_statistics_modtime 
    BEFORE UPDATE ON coach_statistics 
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_grader_statistics_modtime 
    BEFORE UPDATE ON grader_statistics 
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

-- 插入示例数据
INSERT INTO system_statistics (stat_date, total_users, total_students, total_coaches, total_graders, total_exams, total_submissions)
VALUES (CURRENT_DATE, 100, 80, 10, 5, 20, 150) ON CONFLICT (stat_date) DO NOTHING;
