-- Exam Management Service Database Schema
-- Database: exam_service

-- Create exam_service database
CREATE DATABASE exam_service
    WITH 
    OWNER = db
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

\c exam_service;

-- Create ENUM types
CREATE TYPE exam_status AS ENUM ('draft', 'published', 'active', 'completed', 'cancelled');
CREATE TYPE question_type AS ENUM ('multiple_choice', 'short_answer', 'essay', 'calculation');
CREATE TYPE file_type AS ENUM ('question_paper', 'answer_key', 'resource', 'attachment');

-- Exams table
CREATE TABLE exams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    subject VARCHAR(100) NOT NULL,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    duration INTEGER NOT NULL, -- in minutes
    status exam_status NOT NULL DEFAULT 'draft',
    total_score DECIMAL(10,2) NOT NULL DEFAULT 0,
    question_count INTEGER NOT NULL DEFAULT 0,
    instructions TEXT,
    settings JSONB,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Questions table
CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    question_number INTEGER NOT NULL,
    content TEXT NOT NULL,
    question_type question_type NOT NULL,
    score DECIMAL(10,2) NOT NULL DEFAULT 0,
    options JSONB, -- for multiple choice questions
    correct_answer TEXT,
    scoring_criteria JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(exam_id, question_number)
);

-- Exam files table
CREATE TABLE exam_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_type file_type NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Question scores configuration table
CREATE TABLE question_scores (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    question_number INTEGER NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    partial_scoring BOOLEAN DEFAULT false,
    scoring_criteria JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(exam_id, question_number)
);

-- Exam permissions table (for role-based access)
CREATE TABLE exam_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL, -- 'student', 'coach', 'grader', 'admin'
    permissions JSONB, -- specific permissions for this user on this exam
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_exams_status ON exams(status);
CREATE INDEX idx_exams_subject ON exams(subject);
CREATE INDEX idx_exams_start_time ON exams(start_time);
CREATE INDEX idx_exams_end_time ON exams(end_time);
CREATE INDEX idx_exams_created_by ON exams(created_by);
CREATE INDEX idx_questions_exam_id ON questions(exam_id);
CREATE INDEX idx_questions_number ON questions(exam_id, question_number);
CREATE INDEX idx_exam_files_exam_id ON exam_files(exam_id);
CREATE INDEX idx_exam_files_type ON exam_files(file_type);
CREATE INDEX idx_question_scores_exam_id ON question_scores(exam_id);
CREATE INDEX idx_exam_permissions_exam_id ON exam_permissions(exam_id);
CREATE INDEX idx_exam_permissions_user_id ON exam_permissions(user_id);
CREATE INDEX idx_exam_permissions_role ON exam_permissions(role);

-- Create trigger function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at columns
CREATE TRIGGER update_exams_updated_at BEFORE UPDATE ON exams
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_questions_updated_at BEFORE UPDATE ON questions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_question_scores_updated_at BEFORE UPDATE ON question_scores
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data for testing
INSERT INTO exams (id, title, description, subject, start_time, end_time, duration, status, total_score, question_count, instructions, created_by) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 'Mathematics Mid-term Exam', 'Mid-term examination for Mathematics course', 'Mathematics', '2025-07-01 09:00:00+00', '2025-07-01 11:00:00+00', 120, 'published', 100.00, 10, 'Please read all questions carefully and show your work.', '550e8400-e29b-41d4-a716-446655440000'),
    ('550e8400-e29b-41d4-a716-446655440002', 'Physics Final Exam', 'Final examination for Physics course', 'Physics', '2025-07-15 14:00:00+00', '2025-07-15 17:00:00+00', 180, 'draft', 150.00, 15, 'This is a comprehensive final exam covering all course material.', '550e8400-e29b-41d4-a716-446655440000');

-- Insert sample questions
INSERT INTO questions (exam_id, question_number, content, question_type, score, options, correct_answer) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 1, 'What is 2 + 2?', 'multiple_choice', 10.00, '["2", "3", "4", "5"]', '4'),
    ('550e8400-e29b-41d4-a716-446655440001', 2, 'Solve for x: 2x + 5 = 15', 'short_answer', 15.00, null, 'x = 5');

-- Insert sample question scores
INSERT INTO question_scores (exam_id, question_number, max_score, partial_scoring) VALUES
    ('550e8400-e29b-41d4-a716-446655440001', 1, 10.00, false),
    ('550e8400-e29b-41d4-a716-446655440001', 2, 15.00, true);
