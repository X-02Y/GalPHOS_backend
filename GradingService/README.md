# 阅卷管理服务 (GradingService)

阅卷管理服务是 GalPHOS 系统中负责阅卷任务分配和过程管理的微服务，运行在端口 3005。

## 功能概述

### 核心功能
- **阅卷任务自动分配**: 将考试题目分配给不同的阅卷员
- **阅卷进度实时监控**: 跟踪阅卷任务的完成情况
- **阅卷员工作流管理**: 管理阅卷员的任务状态和流程
- **题目评分标准管理**: 设置和管理各题目的分值配置
- **教练非独立学生管理**: 管理教练添加的非独立学生账号

### 服务端口
- **端口**: 3005
- **协议**: HTTP
- **数据格式**: JSON

## API 接口

### 管理员阅卷管理 (4个接口)
```
GET  /api/admin/graders                     # 阅卷员管理
POST /api/admin/grading/assign              # 阅卷任务分配
GET  /api/admin/grading/progress/{examId}   # 阅卷进度监控
GET  /api/admin/grading/tasks               # 阅卷任务管理
```

### 题目分数管理 (3个接口)
```
GET  /api/admin/exams/{examId}/question-scores                    # 题目分数设置和查看
POST /api/admin/exams/{examId}/question-scores                    # 创建题目分数配置
PUT  /api/admin/exams/{examId}/question-scores/{questionNumber}   # 单题分数更新
```

### 阅卷员任务管理 (6个接口)
```
GET  /api/grader/tasks                                # 阅卷任务列表
GET  /api/grader/tasks/{taskId}                       # 阅卷任务详情
POST /api/grader/tasks/{taskId}/start                 # 开始阅卷任务
POST /api/grader/tasks/{taskId}/submit                # 提交阅卷结果
POST /api/grader/tasks/{taskId}/abandon               # 放弃阅卷任务
POST /api/grader/tasks/{taskId}/save-progress         # 保存阅卷进度
```

### 阅卷过程管理 (3个接口)
```
POST /api/grader/tasks/{taskId}/questions/{questionNumber}/score   # 题目评分
GET  /api/grader/tasks/{taskId}/questions/{questionNumber}/history # 评分历史
GET  /api/grader/exams/{examId}/questions/scores                   # 考试题目分数查看
```

### 阅卷图片管理 (1个接口)
```
GET  /api/grader/images                                           # 获取阅卷图片
```

### 教练非独立学生管理 (4个接口)
```
GET    /api/coach/students                    # 教练代管的非独立学生列表
GET    /api/coach/students/{studentId}        # 教练代管的非独立学生详情
POST   /api/coach/students                    # 创建非独立学生
PUT    /api/coach/students/{studentId}        # 更新非独立学生信息
DELETE /api/coach/students/{studentId}        # 删除非独立学生
```

**总计**: 21个API接口

## 数据库结构

### 主要数据表

#### 1. grading_tasks (阅卷任务表)
```sql
CREATE TABLE grading_tasks (
    id BIGSERIAL PRIMARY KEY,
    exam_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    grader_id BIGINT,
    question_number INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    max_score DECIMAL(10,2) NOT NULL,
    actual_score DECIMAL(10,2),
    feedback TEXT,
    assigned_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 2. question_scores (题目分数配置表)
```sql
CREATE TABLE question_scores (
    exam_id BIGINT NOT NULL,
    question_number INTEGER NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    question_type VARCHAR(50) NOT NULL DEFAULT 'SUBJECTIVE',
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exam_id, question_number)
);
```

#### 3. coach_students (教练学生表)
```sql
CREATE TABLE coach_students (
    id BIGSERIAL PRIMARY KEY,
    coach_id BIGINT NOT NULL,
    student_name VARCHAR(100) NOT NULL,
    student_school VARCHAR(200) NOT NULL,
    student_province VARCHAR(50) NOT NULL,
    grade VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 4. score_history (评分历史表)
```sql
CREATE TABLE score_history (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    grader_id BIGINT NOT NULL,
    question_number INTEGER NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    feedback TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 环境要求

### 系统要求
- **Java**: JDK 11 或更高版本
- **Scala**: 3.4.2
- **SBT**: 1.9.7
- **PostgreSQL**: 12 或更高版本

### 依赖服务
- **数据库**: PostgreSQL (共享数据库实例)
- **用户管理服务**: localhost:3002
- **考试管理服务**: localhost:3003
- **答题提交服务**: localhost:3004

## 配置说明

### 服务配置 (server_config.json)
```json
{
  "serverIP": "127.0.0.1",
  "serverPort": 3005,
  "maximumServerConnection": 10000,
  "maximumClientConnection": 10000,
  "jdbcUrl": "jdbc:postgresql://localhost:5432/galphos?currentSchema=authservice",
  "username": "db",
  "password": "root",
  "maximumPoolSize": 3,
  "userManagementService": {
    "host": "127.0.0.1",
    "port": 3002,
    "internalApiKey": "internal-service-key-2024",
    "timeout": 30000
  },
  "examManagementService": {
    "host": "127.0.0.1",
    "port": 3003,
    "internalApiKey": "internal-service-key-2024",
    "timeout": 30000
  },
  "submissionService": {
    "host": "127.0.0.1",
    "port": 3004,
    "internalApiKey": "internal-service-key-2024",
    "timeout": 30000
  }
}
```

## 部署说明

### 1. 数据库初始化
```bash
# 执行数据库初始化脚本
psql -h localhost -p 5432 -U db -d galphos -f init_database.sql
```

### 2. 编译和运行
```bash
# Windows
start.bat

# Linux/macOS
chmod +x start.sh
./start.sh
```

### 3. 验证服务状态
```bash
# 检查服务是否正常启动
curl http://localhost:3005/api/admin/graders
```

## 开发说明

### 项目结构
```
GradingService/
├── build.sbt                    # SBT构建配置
├── server_config.json           # 服务配置文件
├── init_database.sql           # 数据库初始化脚本
├── start.bat                   # Windows启动脚本
├── start.sh                    # Linux/macOS启动脚本
├── project/
│   ├── build.properties        # SBT版本配置
│   └── plugins.sbt             # SBT插件配置
└── src/main/scala/
    ├── Config/                 # 配置管理
    │   └── ServiceConfig.scala
    ├── Controllers/            # HTTP控制器
    │   └── GradingController.scala
    ├── Database/               # 数据库访问
    │   └── DatabaseManager.scala
    ├── Main/                   # 应用程序入口
    │   └── GradingServiceApp.scala
    ├── Models/                 # 数据模型
    │   └── GradingModels.scala
    ├── Process/                # 初始化处理
    │   └── Init.scala
    ├── Services/               # 业务逻辑服务
    │   ├── GradingService.scala
    │   └── QuestionScoreService.scala
    └── Utils/                  # 工具类
        └── AuthUtils.scala
```

### 关键特性

#### 1. 阅卷任务状态管理
```scala
val TASK_STATUS_PENDING = "PENDING"         // 待分配
val TASK_STATUS_ASSIGNED = "ASSIGNED"       // 已分配
val TASK_STATUS_IN_PROGRESS = "IN_PROGRESS" // 阅卷中
val TASK_STATUS_COMPLETED = "COMPLETED"     // 已完成
val TASK_STATUS_ABANDONED = "ABANDONED"     // 已放弃
```

#### 2. 权限控制
- **管理员权限**: 阅卷员管理、任务分配、进度监控、题目分数设置
- **阅卷员权限**: 查看分配的任务、执行阅卷操作、提交评分结果
- **教练权限**: 管理非独立学生账号

#### 3. 非独立学生管理
- 教练可以添加、编辑、删除非独立学生
- 非独立学生不能直接登录系统
- 所有考试操作由教练代理执行

## 监控和日志

### 日志级别
- **INFO**: 服务启动、重要操作完成
- **DEBUG**: 数据库查询、详细操作日志  
- **WARN**: 警告信息、异常情况
- **ERROR**: 错误信息、异常堆栈

### 监控端点
```
GET /health          # 服务健康检查
GET /metrics         # 服务指标（如果启用）
```

## 故障排除

### 常见问题

1. **服务无法启动**
   - 检查端口3005是否被占用
   - 确认数据库连接配置正确
   - 查看日志文件中的错误信息

2. **数据库连接失败**
   - 检查PostgreSQL服务是否运行
   - 验证数据库连接参数
   - 确认数据库用户权限

3. **API调用失败**
   - 检查请求格式是否正确
   - 验证认证令牌
   - 确认用户权限

### 日志查看
```bash
# 查看实时日志
tail -f logs/grading-service.log

# 查看错误日志
grep ERROR logs/grading-service.log
```

## 版本信息

- **服务版本**: 1.0.0
- **Scala版本**: 3.4.2
- **SBT版本**: 1.9.7
- **最后更新**: 2025年7月7日

## 技术栈

- **Web框架**: HTTP4s
- **JSON处理**: Circe
- **数据库**: PostgreSQL + HikariCP
- **并发**: Cats Effect
- **认证**: JWT
- **构建工具**: SBT
- **日志**: Logback + SLF4J

## 联系信息

如有问题或建议，请联系开发团队。
