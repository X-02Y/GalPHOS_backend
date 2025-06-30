# FileStorageService

GalPHOS微服务架构下的文件存储服务，负责文件上传、下载、存储和管理。

## 服务概述

- **端口**: 3008
- **职责**: 文件上传存储和访问管理服务
- **API数量**: 7个核心端点

## 功能特性

### 核心功能
- ✅ 文件上传和存储
- ✅ 文件下载和访问控制
- ✅ 图片代理服务
- ✅ 成绩导出（Excel/PDF）
- ✅ 仪表盘统计数据
- ✅ 文件去重（基于SHA-256哈希）
- ✅ 文件访问日志记录

### 技术特性
- 🔐 用户身份验证和权限控制
- 📊 文件统计和仪表盘数据
- 🗃️ 独立数据库存储元数据
- 🔍 文件类型和大小验证
- 🧹 自动清理临时文件
- 📝 完整的访问日志

## API端点

根据 MICROSERVICE_ROUTING.md 文档，本服务提供以下API：

### 学生文件管理
```
GET /api/student/files/download/{fileId}     # 学生文件下载
```

### 阅卷员图片管理
```
GET /api/grader/images?url={imageUrl}        # 阅卷图片代理
```

### 教练文件管理
```
GET  /api/coach/exams/{examId}/ranking                # 考试排名导出
POST /api/coach/exams/{examId}/scores/export          # 成绩导出
GET  /api/coach/exams/{examId}/scores/statistics      # 成绩统计
GET  /api/coach/dashboard/stats                       # 教练仪表盘统计
```

### 管理员统计
```
GET /api/admin/dashboard/stats                        # 管理员仪表盘统计
```

### 通用API
```
POST   /api/files/upload                              # 文件上传
DELETE /api/files/{fileId}                            # 文件删除
GET    /api/health                                    # 健康检查
```

## 数据库设计

### 主要表结构

#### file_info - 文件信息表
- `file_id` - 文件唯一标识
- `original_name` - 原始文件名
- `stored_name` - 存储文件名
- `file_path` - 文件存储路径
- `file_size` - 文件大小
- `file_type` - 文件类型
- `upload_user_id` - 上传用户ID
- `upload_user_type` - 用户类型
- `file_hash` - 文件哈希值（用于去重）
- `related_exam_id` - 关联考试ID
- `related_submission_id` - 关联提交ID

#### file_access_log - 文件访问日志表
- `log_id` - 日志ID
- `file_id` - 文件ID
- `access_user_id` - 访问用户ID
- `access_type` - 访问类型（download/view/upload）
- `access_time` - 访问时间
- `client_ip` - 客户端IP

#### file_statistics - 文件统计表
- `stat_date` - 统计日期
- `total_files` - 总文件数
- `total_size` - 总文件大小
- `daily_uploads` - 当日上传数
- `daily_downloads` - 当日下载数

## 快速开始

### 1. 环境要求
- Java 11+
- Scala 3.4.2
- SBT 1.9.0+
- PostgreSQL 13+

### 2. 数据库初始化
```bash
# 创建数据库
createdb file_storage

# 初始化表结构
psql -U db -d file_storage -f init_database.sql
```

### 3. 配置文件
编辑 `server_config.json`:
```json
{
  "serverIP": "127.0.0.1",
  "serverPort": 3008,
  "jdbcUrl": "jdbc:postgresql://localhost:5432/file_storage?currentSchema=filestorage",
  "username": "db",
  "password": "root",
  "fileStoragePath": "./storage/files",
  "maxFileSize": 104857600,
  "allowedFileTypes": ["pdf", "doc", "docx", "xls", "xlsx", "jpg", "png"]
}
```

### 4. 启动服务

#### Windows
```bash
start.bat
```

#### Linux/Mac
```bash
chmod +x start.sh
./start.sh
```

#### 手动启动
```bash
sbt run
```

### 5. 验证服务
```bash
curl http://localhost:3008/api/health
```

## 文件存储结构

```
storage/files/
├── uploads/     # 用户上传文件
├── temp/       # 临时文件
├── images/     # 图片文件
├── exports/    # 导出文件
└── archives/   # 归档文件
```

## 配置说明

### 文件类型限制
支持的文件类型可以在配置文件中修改：
- 文档类: pdf, doc, docx, xls, xlsx, ppt, pptx
- 图片类: jpg, jpeg, png, gif, bmp
- 文本类: txt, csv
- 压缩类: zip, rar

### 文件大小限制
默认最大文件大小: 100MB (104857600 bytes)

### 存储路径
默认存储路径: `./storage/files`
可通过配置文件修改

## 监控和维护

### 健康检查
```bash
GET /api/health
```

### 统计数据查看
```bash
GET /api/admin/dashboard/stats
```

### 日志位置
- 应用日志: 标准输出
- 访问日志: 存储在数据库 `file_access_log` 表

### 清理任务
- 临时文件: 每24小时自动清理
- 统计数据: 每小时更新

## 开发和调试

### 开发模式启动
```bash
sbt ~reStart
```

### 构建JAR包
```bash
sbt assembly
```

### 运行测试
```bash
sbt test
```

## 微服务集成

### 服务发现
本服务运行在端口 3008，在微服务架构中通过以下方式访问：
- 开发环境: `http://localhost:3008`
- 生产环境: 通过服务发现机制

### 依赖关系
- **依赖**: 无直接依赖其他微服务
- **被依赖**: 被其他服务调用进行文件上传下载

### 认证集成
通过 JWT Token 进行用户身份验证，Token 由 UserAuthService (端口3001) 颁发。

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查PostgreSQL是否启动
   - 验证连接配置
   - 确认数据库权限

2. **文件上传失败**
   - 检查存储目录权限
   - 验证文件大小和类型限制
   - 查看磁盘空间

3. **内存不足**
   - 调整JVM参数 `-Xmx`
   - 检查文件缓存设置

### 日志分析
```bash
# 查看应用启动日志
tail -f logs/application.log

# 查看文件访问日志
psql -U db -d file_storage -c "SELECT * FROM filestorage.file_access_log ORDER BY access_time DESC LIMIT 10;"
```

## 版本信息

- **版本**: 1.0.0
- **Scala版本**: 3.4.2
- **兼容性**: GalPHOS v1.2.0
- **更新日期**: 2025年6月29日
