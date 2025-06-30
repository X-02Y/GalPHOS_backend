-- PostgreSQL 数据库创建脚本
-- 需要以 postgres 超级用户身份运行

-- 创建用户（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'db') THEN
        CREATE USER db WITH PASSWORD 'root';
    END IF;
END
$$;

-- 创建数据库（如果不存在）
SELECT 'CREATE DATABASE file_storage OWNER db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'file_storage')\gexec

-- 授予权限
GRANT ALL PRIVILEGES ON DATABASE file_storage TO db;
GRANT CREATE ON DATABASE file_storage TO db;

-- 提示信息
\echo '数据库 file_storage 已创建，用户 db 已创建并授权'
\echo '请现在连接到 file_storage 数据库执行 init_database.sql 来创建表结构'
