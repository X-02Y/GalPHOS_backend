REM Direct command to connect to PostgreSQL and add the missing column
REM Run this in Command Prompt after setting your password

REM Set the database password (replace 'your_password' with actual password)
set PGPASSWORD=your_password

REM Connect and execute the fix
psql -h localhost -p 5432 -U db -d exam_service -c "SET search_path TO examservice; ALTER TABLE exam_files ADD COLUMN IF NOT EXISTS file_id VARCHAR(255);"

REM Check the result
psql -h localhost -p 5432 -U db -d exam_service -c "SET search_path TO examservice; \d exam_files"

pause
