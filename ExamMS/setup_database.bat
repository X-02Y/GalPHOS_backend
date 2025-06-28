@echo off
echo Setting up Exam Management Service database...

echo Creating database and tables...
psql -U db -h localhost -f init_database.sql

echo Database setup completed!
pause
