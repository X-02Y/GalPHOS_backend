-- Migration script to fix exam_files table
-- Database: user=db, database=exam_service, schema=examservice
-- Add missing file_id column

-- Set the schema
SET search_path TO examservice;

-- First, let's check the current structure of exam_files table
\echo 'Current exam_files table structure:'
\d exam_files

-- Check if file_id column exists, if not add it
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'examservice'
        AND table_name = 'exam_files' 
        AND column_name = 'file_id'
    ) THEN
        ALTER TABLE examservice.exam_files ADD COLUMN file_id VARCHAR(255);
        RAISE NOTICE 'Added file_id column to exam_files table';
        
        -- Update existing rows to have a placeholder value (empty string or generate UUIDs)
        UPDATE examservice.exam_files SET file_id = 'PLACEHOLDER_' || id::text WHERE file_id IS NULL;
        
        -- Now make it NOT NULL
        ALTER TABLE examservice.exam_files ALTER COLUMN file_id SET NOT NULL;
        
        RAISE NOTICE 'Set file_id column as NOT NULL and updated existing rows';
    ELSE
        RAISE NOTICE 'file_id column already exists in exam_files table';
    END IF;
END$$;

-- Display the updated table structure
\echo 'Updated exam_files table structure:'
\d examservice.exam_files
