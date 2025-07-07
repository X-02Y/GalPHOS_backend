# 成绩统计服务 (Score Statistics Service)

## 概述

成绩统计服务是GalPHOS系统的核心组件之一，负责处理学生成绩计算、统计分析、排名生成等功能。该服务运行在端口3006，为前端提供完整的成绩管理API。

## 功能特性

### 核心功能
- 📊 **成绩计算与统计分析**：自动计算考试成绩、生成统计报告
- 🏆 **排名系统**：实时生成学生排名和百分比位置
- 📈 **多维度数据分析**：支持按考试、学生、教练、阅卷员等维度统计
- 🎯 **仪表板数据聚合**：为各角色提供统一的仪表板统计数据

### 支持的用户角色
- **学生**：查看个人成绩、排名、历史记录
- **教练**：管理学生成绩、查看班级统计
- **阅卷员**：查看阅卷统计、工作记录
- **管理员**：系统全局统计、数据分析

## API 端点

### 学生成绩相关 API
```
GET /api/student/exams/{examId}/score      # 获取学生考试成绩
GET /api/student/exams/{examId}/ranking    # 获取学生考试排名
GET /api/student/scores                    # 获取学生历史成绩
GET /api/student/dashboard/stats           # 获取学生仪表板统计
```

### 教练成绩管理 API
```
GET /api/coach/grades/overview                               # 教练成绩概览
GET /api/coach/grades/details                                # 教练成绩详情
GET /api/coach/students/scores                               # 教练学生成绩
GET /api/coach/students/{studentId}/exams/{examId}/score     # 教练查看学生成绩
GET /api/coach/dashboard/stats                               # 教练仪表板统计
```

### 阅卷员统计 API
```
GET /api/grader/statistics                 # 阅卷员统计数据（简化版）
GET /api/grader/dashboard/stats            # 阅卷员仪表板统计
GET /api/grader/history                    # 阅卷员历史记录
```

### 管理员统计 API
```
GET /api/admin/dashboard/stats             # 管理员仪表板统计
```

### 系统健康检查
```
GET /health                                # 服务健康状态
```

## 数据库结构

### 核心表结构
- `exam_scores` - 考试成绩表
- `exam_statistics` - 考试统计表
- `student_statistics` - 学生统计表
- `coach_statistics` - 教练统计表
- `grader_statistics` - 阅卷员统计表
- `system_statistics` - 系统统计表

## 部署说明

### 环境要求
- Java 11+
- SBT 1.9.7
- PostgreSQL 15+
- 内存：最低512MB，推荐1GB+

### 配置文件
服务配置文件：`server_config.json`
```json
{
  "serverIP": "127.0.0.1",
  "serverPort": 3006,
  "jdbcUrl": "jdbc:postgresql://localhost:5432/galphos?currentSchema=scorestatistics",
  "username": "db",
  "password": "root",
  "userManagementServiceUrl": "http://localhost:3002",
  "examServiceUrl": "http://localhost:3003",
  "submissionServiceUrl": "http://localhost:3004",
  "gradingServiceUrl": "http://localhost:3005"
}
```

### 快速启动

#### 使用SBT启动
```bash
# Windows
start.bat

# Linux/Mac
./start.sh
```

#### 使用Docker启动
```bash
# 构建和启动
docker-compose up -d

# 查看日志
docker-compose logs -f score-statistics-service

# 停止服务
docker-compose down
```

### 数据库初始化
服务启动时会自动执行数据库初始化，包括：
- 创建schema：`scorestatistics`
- 创建所有必要的表
- 插入初始示例数据
- 设置索引和触发器

## 微服务架构

### 服务依赖
- **用户管理服务** (端口3002)：获取用户信息
- **考试管理服务** (端口3003)：获取考试信息
- **答题提交服务** (端口3004)：获取提交数据
- **阅卷管理服务** (端口3005)：获取阅卷数据
- **区域管理服务** (端口3007)：获取区域信息

### 通信协议
- HTTP/REST API
- JSON数据格式
- 支持CORS跨域请求

## 开发指南

### 项目结构
```
src/main/scala/
├── Config/          # 配置类
├── Controllers/     # HTTP控制器
├── Database/        # 数据库访问层
├── Models/          # 数据模型
├── Process/         # 主程序和初始化
└── Services/        # 业务逻辑层
```

### 构建和测试
```bash
# 编译项目
sbt compile

# 运行测试
sbt test

# 构建可执行JAR
sbt assembly

# 运行应用
sbt run
```

### 日志配置
日志配置文件：`src/main/resources/logback.xml`
- 控制台输出：INFO级别
- 文件输出：`logs/score-statistics-service.log`
- 数据库层：DEBUG级别

## 监控和运维

### 健康检查
```bash
curl http://localhost:3006/health
```

### 性能监控
- 数据库连接池监控
- API响应时间统计
- 错误率监控

### 故障排除
1. 检查数据库连接状态
2. 查看服务日志文件
3. 验证依赖服务状态
4. 检查配置文件格式

## 版本信息
- **服务版本**: 0.1.0-SNAPSHOT
- **Scala版本**: 3.4.2
- **SBT版本**: 1.9.7
- **端口**: 3006

## 联系方式
如有问题请联系开发团队或查看项目文档。
