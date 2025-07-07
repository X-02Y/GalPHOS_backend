-- SubmissionService Database Initialization Script

-- Create schema if not exists
CREATE SCHEMA IF NOT EXISTS submissionservice;

-- Set search path
SET search_path TO submissionservice;

-- Create submissions table
CREATE TABLE IF NOT EXISTS submissions (
    id VARCHAR(36) PRIMARY KEY,
    exam_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    student_username VARCHAR(100) NOT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'submitted',
    total_score DECIMAL(5,2),
    max_score DECIMAL(5,2),
    graded_at TIMESTAMP,
    graded_by VARCHAR(36),
    feedback TEXT,
    submitted_by VARCHAR(36), -- For coach proxy submissions
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create answers table
CREATE TABLE IF NOT EXISTS answers (
    id VARCHAR(36) PRIMARY KEY,
    submission_id VARCHAR(36) NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    question_id VARCHAR(36) NOT NULL,
    question_number INTEGER NOT NULL,
    answer_text TEXT,
    score DECIMAL(5,2),
    max_score DECIMAL(5,2) NOT NULL DEFAULT 0,
    comments TEXT,
    image_url VARCHAR(500),
    upload_time TIMESTAMP,
    grader_id VARCHAR(36),
    grader_name VARCHAR(100),
    graded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_submissions_exam_id ON submissions(exam_id);
CREATE INDEX IF NOT EXISTS idx_submissions_student_id ON submissions(student_id);
CREATE INDEX IF NOT EXISTS idx_submissions_student_username ON submissions(student_username);
CREATE INDEX IF NOT EXISTS idx_submissions_status ON submissions(status);
CREATE INDEX IF NOT EXISTS idx_submissions_submitted_at ON submissions(submitted_at);
CREATE INDEX IF NOT EXISTS idx_submissions_graded_by ON submissions(graded_by);

CREATE INDEX IF NOT EXISTS idx_answers_submission_id ON answers(submission_id);
CREATE INDEX IF NOT EXISTS idx_answers_question_id ON answers(question_id);
CREATE INDEX IF NOT EXISTS idx_answers_question_number ON answers(question_number);
CREATE INDEX IF NOT EXISTS idx_answers_grader_id ON answers(grader_id);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_submissions_updated_at 
    BEFORE UPDATE ON submissions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_answers_updated_at 
    BEFORE UPDATE ON answers 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert sample data for testing (optional)
-- This can be removed in production

-- Note: Make sure to create the database and user first:
-- CREATE DATABASE submission_service;
-- CREATE USER db WITH PASSWORD 'root';
-- GRANT ALL PRIVILEGES ON DATABASE submission_service TO db;
-- GRANT ALL PRIVILEGES ON SCHEMA submissionservice TO db;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA submissionservice TO db;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA submissionservice TO db;
