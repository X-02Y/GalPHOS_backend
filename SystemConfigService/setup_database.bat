@echo off
echo "初始化 SystemConfigService 数据库..."
cd "%~dp0"

set PGPASSWORD=root
set PGUSER=db
set PGHOST=localhost
set PGPORT=5432
set PGDATABASE=galphos_systemconfig

echo "创建数据库..."
createdb -U %PGUSER% -h %PGHOST% -p %PGPORT% %PGDATABASE%

echo "执行初始化脚本..."
psql -U %PGUSER% -h %PGHOST% -p %PGPORT% -d %PGDATABASE% -f init_database.sql

echo "数据库初始化完成!"
pause
