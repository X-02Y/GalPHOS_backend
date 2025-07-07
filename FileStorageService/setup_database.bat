@echo off
echo Setting up File Storage Service Database...

echo Creating database (if not exists)...
psql -U db -h localhost -c "CREATE DATABASE file_storage;" 2>nul

echo Initializing tables and schema...
psql -U db -h localhost -d file_storage -f init_database.sql

echo Database setup completed!
pause
