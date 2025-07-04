-- 检查admin_table表是否存在的SQL脚本

-- 1. 检查authservice schema是否存在
SELECT EXISTS (
    SELECT 1 
    FROM information_schema.schemata 
    WHERE schema_name = 'authservice'
) AS authservice_schema_exists;

-- 2. 检查admin_table表是否存在
SELECT EXISTS (
    SELECT 1 
    FROM information_schema.tables 
    WHERE table_schema = 'authservice' 
    AND table_name = 'admin_table'
) AS admin_table_exists;

-- 3. 如果表存在，显示表结构
SELECT 
    column_name, 
    data_type, 
    is_nullable, 
    column_default
FROM information_schema.columns 
WHERE table_schema = 'authservice' 
AND table_name = 'admin_table'
ORDER BY ordinal_position;

-- 4. 如果表存在，显示表中的数据统计
SELECT 
    COUNT(*) as total_admins,
    COUNT(CASE WHEN role = 'admin' THEN 1 END) as admin_count,
    COUNT(CASE WHEN role = 'super_admin' THEN 1 END) as super_admin_count,
    COUNT(CASE WHEN status = 'active' THEN 1 END) as active_count
FROM authservice.admin_table;

-- 5. 显示所有管理员用户名（不显示密码）
SELECT 
    admin_id,
    username,
    role,
    status,
    name,
    created_at
FROM authservice.admin_table
ORDER BY created_at DESC;
