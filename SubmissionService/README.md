# 答题提交服务 (Submission Service)

## 概述

答题提交服务是GalPHOS教育平台的核心微服务之一，专门负责处理考试答题卡的提交和管理。该服务运行在端口3004上，提供完整的答题提交功能，包括学生自主提交、教练代理提交、提交记录管理等。

## 主要功能

### 🎯 核心功能
- **学生自主提交**：独立学生账号可以自主提交考试答案
- **教练代理提交**：教练可以代理非独立学生账号提交答案
- **文件上传管理**：支持答案图片和文档的上传处理
- **提交记录查询**：提供完整的提交历史和状态查询
- **阅卷进度跟踪**：为阅卷员提供提交记录和进度信息

### 🔐 权限控制
- **严格的角色验证**：区分独立学生、教练、阅卷员权限
- **代理权限管理**：教练只能管理自己添加的非独立学生
- **考试状态验证**：检查考试发布状态和时间限制
- **文件类型限制**：支持jpg、jpeg、png、pdf等格式

## 技术架构

### 技术栈
- **Scala 3.4.2** - 现代函数式编程语言
- **Http4s** - 纯函数式HTTP服务器和客户端
- **Cats Effect** - 异步和并发编程库
- **Circe** - JSON序列化/反序列化
- **PostgreSQL** - 关系型数据库
- **HikariCP** - 数据库连接池
- **JWT** - 身份认证和授权
- **STTP** - HTTP客户端（服务间通信）

### 服务架构
```
src/main/scala/
├── Config/           # 配置管理
├── Controllers/      # HTTP请求处理
├── Database/         # 数据库操作
├── Models/          # 数据模型定义
├── Process/         # 服务器启动和进程管理
└── Services/        # 业务逻辑层
```

## API接口

### 学生端接口
- `POST /api/student/exams/{examId}/submit` - 学生自主提交答案
- `GET /api/student/exams/{examId}/submission` - 获取学生提交记录

### 教练端接口
- `GET /api/coach/exams/{examId}/submissions` - 查看代管学生提交记录
- `POST /api/coach/exams/{examId}/upload-answer` - 代理非独立学生提交答卷

### 阅卷员接口
- `GET /api/grader/submissions/{submissionId}` - 查看具体提交详情
- `GET /api/grader/exams/{examId}/progress` - 查看阅卷进度

## 数据库设计

### 主要数据表
1. **exam_submissions** - 考试提交记录表
2. **submission_answers** - 提交答案详情表
3. **submission_files** - 文件上传记录表

### 关键字段
- `is_proxy_submission` - 标识是否为代理提交
- `coach_id` - 代理提交的教练ID
- `student_username` - 学生用户名
- `submission_time` - 提交时间
- `status` - 提交状态（submitted, graded, cancelled）

## 快速开始

### 1. 环境准备
```bash
# 确保已安装以下环境
- Java 11+
- SBT 1.9+
- PostgreSQL 12+
```

### 2. 数据库设置
```bash
# Windows
.\setup_database.bat

# Linux/macOS
chmod +x setup_database.sh
./setup_database.sh
```

### 3. 配置文件
编辑 `server_config.json` 配置数据库连接和服务依赖：
```json
{
  "port": 3004,
  "host": "localhost",
  "database": {
    "url": "jdbc:postgresql://localhost:5432/galphos",
    "username": "postgres",
    "password": "your_password"
  },
  "services": {
    "authService": "http://localhost:3001",
    "examService": "http://localhost:3003",
    "fileStorageService": "http://localhost:3008"
  }
}
```

### 4. 启动服务
```bash
# Windows
start.bat

# Linux/macOS
chmod +x start.sh
./start.sh
```

### 5. 验证服务
```bash
# 健康检查
curl http://localhost:3004/health
```

## 服务依赖关系

### 上游依赖
- **认证服务 (3001)** - Token验证和用户信息
- **考试管理服务 (3003)** - 考试信息和权限验证
- **文件存储服务 (3008)** - 文件上传和存储

### 数据库依赖
- **PostgreSQL** - 共享galphos数据库
- **连接池** - HikariCP管理数据库连接

## 业务逻辑

### 学生类型区分
系统中存在两种不同类型的学生账号：

#### 独立学生账号
- 拥有完整的登录凭据
- 可以独立登录系统
- 自主管理个人资料和考试参与
- 使用 `/api/student/*` 接口

#### 非独立学生账号
- 无登录能力
- 仅作为教练管理的团体成员存在
- 所有操作通过教练代理完成
- 教练使用 `/api/coach/*` 接口管理

### 提交流程
1. **权限验证** - 验证用户身份和考试权限
2. **考试状态检查** - 确认考试已发布且在有效时间内
3. **文件处理** - 上传和存储答案文件
4. **记录创建** - 创建或更新提交记录
5. **关联管理** - 维护答案与题目的关联关系

### 代理提交机制
教练代理提交的特殊处理：
- 验证教练与学生的关联关系
- 标记为代理提交（`is_proxy_submission = true`）
- 记录代理操作的教练信息
- 确保数据隔离和权限控制

## 错误处理

### 常见错误码
- `400` - 请求参数错误或业务逻辑错误
- `401` - 认证失败或Token无效
- `403` - 权限不足
- `404` - 资源不存在
- `500` - 服务器内部错误

### 错误响应格式
```json
{
  "success": false,
  "message": "错误描述信息"
}
```

## 监控和日志

### 日志级别
- **INFO** - 正常业务操作
- **WARN** - 警告信息
- **ERROR** - 错误信息

### 日志文件
- 日志路径：`logs/submission-service.log`
- 滚动策略：按天滚动，最大10MB
- 保留策略：保留30天，总大小300MB

### 监控指标
- 提交成功率
- 响应时间
- 数据库连接状态
- 服务依赖健康状况

## 开发指南

### 添加新接口
1. 在 `Models.scala` 中定义请求/响应模型
2. 在 `SubmissionService.scala` 中实现业务逻辑
3. 在 `SubmissionController.scala` 中添加路由处理
4. 更新数据库Schema（如需要）
5. 添加测试用例

### 数据库操作
使用 `DatabaseManager` 进行数据库操作：
```scala
// 查询示例
DatabaseManager.executeQuery(sql, params)

// 更新示例
DatabaseManager.executeUpdate(sql, params)
```

### 服务间通信
使用 STTP 客户端调用其他微服务：
```scala
val request = basicRequest
  .get(uri"$serviceUrl/api/endpoint")
  .header("Authorization", s"Bearer $token")
```

## 部署指南

### 生产环境部署
1. **编译打包**
```bash
sbt assembly
```

2. **运行JAR**
```bash
java -jar target/scala-3.4.2/SubmissionService-assembly-0.1.0-SNAPSHOT.jar
```

3. **环境配置**
- 确保数据库连接正常
- 配置正确的服务依赖地址
- 设置合适的JVM参数

### Docker部署
```dockerfile
FROM openjdk:11-jre-slim
COPY target/scala-3.4.2/SubmissionService-assembly-*.jar app.jar
COPY server_config.json .
EXPOSE 3004
CMD ["java", "-jar", "app.jar"]
```

## 故障排除

### 常见问题

1. **服务启动失败**
   - 检查Java版本（需要11+）
   - 检查端口3004是否被占用
   - 验证数据库连接配置

2. **数据库连接失败**
   - 确认PostgreSQL服务运行状态
   - 检查数据库连接参数
   - 验证用户权限

3. **服务依赖失败**
   - 确认认证服务（3001）正常运行
   - 确认考试管理服务（3003）正常运行
   - 确认文件存储服务（3008）正常运行

4. **文件上传失败**
   - 检查文件大小限制
   - 验证文件类型支持
   - 确认文件存储服务可用

### 日志分析
查看服务日志以诊断问题：
```bash
tail -f logs/submission-service.log
```

## 版本信息
- **当前版本**: 1.0.0
- **Scala版本**: 3.4.2
- **Http4s版本**: 1.0.0-M44
- **数据库**: PostgreSQL 12+

## 贡献指南
1. Fork项目
2. 创建功能分支
3. 提交代码更改
4. 创建Pull Request

## 许可证
本项目是GalPHOS教育平台的一部分，遵循项目整体许可协议。
