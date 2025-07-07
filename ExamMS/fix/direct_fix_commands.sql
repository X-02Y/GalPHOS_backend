-- Simple commands to fix the exam_files table
-- Copy and paste these commands directly into psql

-- Connect to your database first:
-- psql -h localhost -p 5432 -U db -d exam_service

-- Set the correct schema
SET search_path TO examservice;

-- Check current table structure
\d exam_files

-- Add the missing file_id column
ALTER TABLE exam_files ADD COLUMN IF NOT EXISTS file_id VARCHAR(255);

-- Verify the column was added
\d exam_files

-- If there are existing rows, update them with placeholder values
UPDATE exam_files SET file_id = 'PLACEHOLDER_' || id::text WHERE file_id IS NULL OR file_id = '';

-- Make the column NOT NULL (after updating existing rows)
ALTER TABLE exam_files ALTER COLUMN file_id SET NOT NULL;
