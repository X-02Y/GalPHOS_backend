-- File Storage Service Database Schema
-- PostgreSQL Database Initialization Script

-- Create database (run this manually if needed)
-- CREATE DATABASE file_storage;

-- Create schema
CREATE SCHEMA IF NOT EXISTS filestorage;

-- Set search path to use the schema
SET search_path TO filestorage, public;

-- Drop existing table if it exists to ensure clean setup
DROP TABLE IF EXISTS filestorage.files CASCADE;

-- Create files table
CREATE TABLE filestorage.files (
  id VARCHAR(36) PRIMARY KEY,
  file_name VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  file_url VARCHAR(500) NOT NULL,
  file_size BIGINT NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  file_type VARCHAR(50),
  category VARCHAR(50),
  exam_id VARCHAR(36),
  question_number INTEGER,
  student_id VARCHAR(36),
  uploaded_by VARCHAR(100) NOT NULL,
  upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_files_exam_id ON files(exam_id);
CREATE INDEX idx_files_student_id ON files(student_id);
CREATE INDEX idx_files_uploaded_by ON files(uploaded_by);
CREATE INDEX idx_files_file_type ON files(file_type);
CREATE INDEX idx_files_category ON files(category);
CREATE INDEX idx_files_upload_time ON files(upload_time);
CREATE INDEX idx_files_exam_category ON files(exam_id, category);
CREATE INDEX idx_files_exam_student ON files(exam_id, student_id);

-- Update trigger for updated_at field
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_files_updated_at BEFORE UPDATE
    ON files FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert some sample data for testing (optional)
INSERT INTO files (
  id, file_name, original_name, file_url, file_size, mime_type, 
  file_type, category, uploaded_by
) VALUES 
(
  'sample-file-1', 'sample-1.pdf', 'Sample Document.pdf', 
  'http://localhost:3008/api/files/sample-file-1/download', 
  1024, 'application/pdf', 'document', 'document', 'system'
) ON CONFLICT (id) DO NOTHING;

-- Grant necessary permissions (adjust username as needed)
-- GRANT ALL PRIVILEGES ON DATABASE galphos_filestorage TO postgres;
-- GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
-- GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
