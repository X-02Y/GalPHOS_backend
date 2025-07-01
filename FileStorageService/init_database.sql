-- GalPHOS 文件存储服务数据库初始化脚本
-- 统一数据库初始化脚本
-- 数据库: file_storage
-- Schema: filestorage

-- 创建 schema
CREATE SCHEMA IF NOT EXISTS filestorage;

-- 设置搜索路径
SET search_path TO filestorage;

-- 创建文件信息表
CREATE TABLE IF NOT EXISTS files (
    file_id VARCHAR(36) PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(50) NOT NULL,
    mime_type VARCHAR(100),
    upload_user_id VARCHAR(36),
    upload_user_type VARCHAR(20) CHECK (upload_user_type IN ('student', 'coach', 'grader', 'admin')),
    upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    file_status VARCHAR(20) DEFAULT 'active' CHECK (file_status IN ('active', 'deleted', 'archived')),
    access_count INTEGER DEFAULT 0,
    download_count INTEGER DEFAULT 0,
    last_access_time TIMESTAMP,
    description TEXT,
    file_hash VARCHAR(64),
    related_exam_id VARCHAR(36),
    related_submission_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建文件访问日志表
CREATE TABLE IF NOT EXISTS file_accesses (
    log_id SERIAL PRIMARY KEY,
    file_id VARCHAR(36) NOT NULL REFERENCES files(file_id),
    access_user_id VARCHAR(36),
    access_user_type VARCHAR(20) CHECK (access_user_type IN ('student', 'coach', 'grader', 'admin')),
    access_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    access_type VARCHAR(20) CHECK (access_type IN ('download', 'view', 'upload')),
    client_ip VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN DEFAULT true,
    error_message TEXT
);

-- 创建文件统计表
CREATE TABLE IF NOT EXISTS file_statistics (
    stat_id SERIAL PRIMARY KEY,
    stat_date DATE DEFAULT CURRENT_DATE,
    total_files INTEGER DEFAULT 0,
    total_size BIGINT DEFAULT 0,
    active_files INTEGER DEFAULT 0,
    deleted_files INTEGER DEFAULT 0,
    daily_uploads INTEGER DEFAULT 0,
    daily_downloads INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_files_upload_user ON files(upload_user_id);
CREATE INDEX IF NOT EXISTS idx_files_upload_time ON files(upload_time);
CREATE INDEX IF NOT EXISTS idx_files_file_type ON files(file_type);
CREATE INDEX IF NOT EXISTS idx_files_status ON files(file_status);
CREATE INDEX IF NOT EXISTS idx_files_exam_id ON files(related_exam_id);
CREATE INDEX IF NOT EXISTS idx_file_access_log_file_id ON file_accesses(file_id);
CREATE INDEX IF NOT EXISTS idx_file_access_log_user ON file_accesses(access_user_id);
CREATE INDEX IF NOT EXISTS idx_file_access_log_time ON file_accesses(access_time);
CREATE INDEX IF NOT EXISTS idx_file_statistics_date ON file_statistics(stat_date);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_files_updated_at 
    BEFORE UPDATE ON files 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_file_statistics_updated_at 
    BEFORE UPDATE ON file_statistics 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 插入初始统计数据
INSERT INTO file_statistics (stat_date) VALUES (CURRENT_DATE) 
ON CONFLICT DO NOTHING;

-- 创建视图
CREATE OR REPLACE VIEW v_file_dashboard AS
SELECT 
    COUNT(*) as total_files,
    SUM(file_size) as total_size,
    COUNT(CASE WHEN file_status = 'active' THEN 1 END) as active_files,
    COUNT(CASE WHEN file_status = 'deleted' THEN 1 END) as deleted_files,
    COUNT(CASE WHEN upload_time >= CURRENT_DATE THEN 1 END) as today_uploads,
    COUNT(CASE WHEN upload_time >= CURRENT_DATE - INTERVAL '7 days' THEN 1 END) as week_uploads,
    COUNT(CASE WHEN upload_time >= CURRENT_DATE - INTERVAL '30 days' THEN 1 END) as month_uploads
FROM files;

-- 授权
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA filestorage TO db;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA filestorage TO db;
GRANT USAGE ON SCHEMA filestorage TO db;
