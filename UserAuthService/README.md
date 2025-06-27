# GalPHOS 用户认证服务

## 概述

GalPHOS 用户认证服务是一个基于 Scala 3 和 Http4s 框架构建的微服务，专门负责处理用户认证、授权、会话管理等功能。本服务采用现代化的函数式编程范式，提供高性能和高可靠性的认证解决方案。

## 功能特性

### 核心功能
- **多角色登录**: 支持管理员、教练、学生、阅卷员四种角色
- **用户注册**: 提供完整的用户注册流程，支持审核机制
- **JWT Token管理**: 基于JWT的无状态认证方案
- **Token黑名单**: 支持Token撤销和黑名单管理
- **密码安全**: 采用SHA-256+盐值的密码哈希策略
- **会话管理**: 完整的用户会话生命周期管理
- **权限验证**: 细粒度的权限控制和验证

### 技术特性
- **函数式编程**: 基于 Cats Effect 的纯函数式设计
- **类型安全**: 利用 Scala 3 的强类型系统保证代码安全
- **异步处理**: 完全异步的 IO 操作，提供高并发性能
- **JSON序列化**: 基于 Circe 的高性能 JSON 处理
- **数据库连接**: HikariCP 连接池管理，支持 PostgreSQL
- **CORS支持**: 完整的跨域资源共享支持
- **错误处理**: 统一的错误处理和响应格式

## 技术栈

### 核心依赖
- **Scala 3.4.2**: 现代化的 Scala 语言特性
- **Http4s 1.0.0-M44**: 纯函数式 HTTP 库
- **Cats Effect**: 函数式编程效果系统
- **Circe 0.14.10**: JSON 序列化/反序列化
- **JWT Scala 10.0.1**: JWT Token 处理
- **HikariCP 5.1.0**: 数据库连接池
- **PostgreSQL 42.7.2**: 数据库驱动

### 构建工具
- **SBT 1.10.11**: Scala 构建工具
- **sbt-assembly**: 打包插件
- **sbt-native-packager**: 部署包生成

## 项目结构

```
UserAuthService/
├── src/main/scala/
│   ├── Config/
│   │   └── ServerConfig.scala          # 服务器配置模型
│   ├── Models/
│   │   └── Models.scala                # 数据模型定义
│   ├── Database/
│   │   └── DatabaseManager.scala      # 数据库管理器
│   ├── Services/
│   │   ├── UserService.scala          # 用户服务
│   │   ├── AdminService.scala         # 管理员服务
│   │   ├── TokenService.scala         # Token服务
│   │   ├── RegionService.scala        # 地区服务
│   │   └── AuthService.scala          # 认证服务
│   ├── Controllers/
│   │   └── AuthController.scala       # 认证控制器
│   └── Process/
│       ├── Server.scala               # 服务器主程序
│       ├── Init.scala                 # 初始化程序
│       └── ProcessUtils.scala         # 工具函数
├── init_database.sql                  # 数据库初始化脚本
├── update_passwords.sql               # 密码更新脚本
├── server_config.json                 # 服务器配置文件
├── build.sbt                          # 构建配置
└── README.md                          # 项目文档
```

## API 接口

### 认证相关

#### 1. 用户登录
- **接口**: `POST /api/auth/login`
- **描述**: 教练、学生、阅卷员登录
- **请求体**:
```json
{
  "role": "coach|student|grader",
  "username": "user001",
  "password": "hashed_password_string"
}
```
- **响应**:
```json
{
  "success": true,
  "data": {
    "username": "user001",
    "role": "coach",
    "province": "北京市",
    "school": "清华大学"
  },
  "message": "登录成功",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### 2. 管理员登录
- **接口**: `POST /api/auth/admin-login`
- **请求体**:
```json
{
  "username": "admin",
  "password": "hashed_password_string"
}
```

#### 3. 用户注册
- **接口**: `POST /api/auth/register`
- **请求体**:
```json
{
  "role": "student",
  "username": "newuser",
  "phone": "13800138000",
  "password": "hashed_password_string",
  "confirmPassword": "hashed_password_string",
  "province": "1",
  "school": "1-1"
}
```

#### 4. Token验证
- **接口**: `GET /api/auth/validate`
- **请求头**: `Authorization: Bearer <token>`

#### 5. 用户登出
- **接口**: `POST /api/auth/logout`
- **请求头**: `Authorization: Bearer <token>`

### 地区相关

#### 6. 获取省份学校数据
- **接口**: `GET /api/regions/provinces-schools`
- **响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": "1",
      "name": "北京市",
      "schools": [
        {"id": "1-1", "name": "清华大学"},
        {"id": "1-2", "name": "北京大学"}
      ]
    }
  ]
}
```

## 数据库设计

### 用户表 (user_table)
```sql
CREATE TABLE user_table (
    user_id VARCHAR PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    phone VARCHAR(20),
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    role TEXT NOT NULL,
    status TEXT DEFAULT 'PENDING',
    province_id VARCHAR,
    school_id VARCHAR,
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 管理员表 (admin_table)
```sql
CREATE TABLE admin_table (
    admin_id VARCHAR PRIMARY KEY,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    salt TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Token黑名单表 (token_blacklist_table)
```sql
CREATE TABLE token_blacklist_table (
    token_id VARCHAR PRIMARY KEY,
    token_hash TEXT UNIQUE NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## 快速开始

### 1. 环境准备
- **Java 11+**: 确保安装了 Java 11 或更高版本
- **SBT**: 安装 SBT 构建工具
- **PostgreSQL**: 安装并配置 PostgreSQL 数据库

### 2. 数据库设置
```bash
# 创建数据库
createdb galphos_db

# 执行初始化脚本
psql -d galphos_db -f init_database.sql

# 更新测试密码（可选）
psql -d galphos_db -f update_passwords.sql
```

### 3. 配置文件
编辑 `server_config.json` 文件，配置数据库连接和服务器参数：
```json
{
  "serverIP": "0.0.0.0",
  "serverPort": 3001,
  "maximumServerConnection": 1000,
  "databaseConfig": {
    "jdbcUrl": "jdbc:postgresql://localhost:5432/galphos_db",
    "username": "postgres",
    "password": "your_password",
    "prepStmtCacheSize": 250,
    "prepStmtCacheSqlLimit": 2048,
    "maximumPoolSize": 20
  },
  "jwtSecret": "GalPHOS_2025_JWT_SECRET_KEY_CHANGE_IN_PRODUCTION",
  "jwtExpirationHours": 24,
  "saltValue": "GalPHOS_2025_SALT"
}
```

### 4. 编译和运行
```bash
# 编译项目
sbt compile

# 运行服务
sbt run

# 或者打包运行
sbt assembly
java -jar target/scala-3.4.2/UserAuthService-assembly-0.1.0-SNAPSHOT.jar
```

### 5. 测试API
```bash
# 健康检查
curl http://localhost:3001/health

# 测试登录
curl -X POST http://localhost:3001/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "role": "student",
    "username": "student001",
    "password": "hashed_password_here"
  }'
```

## 安全考虑

### 密码安全
- 使用 SHA-256 + 固定盐值进行密码哈希
- 前端负责密码哈希，后端直接比较哈希值
- 盐值配置在服务器配置中，可根据环境调整

### Token安全
- 使用 HS256 算法签名JWT Token
- Token包含用户ID、角色信息和过期时间
- 支持Token黑名单机制，实现安全登出
- Token默认24小时过期，可配置

### 数据库安全
- 使用连接池管理数据库连接
- 预处理语句防止SQL注入
- 敏感信息加密存储

## 部署指南

### Docker部署
```dockerfile
FROM openjdk:11-jre-slim
COPY target/scala-3.4.2/UserAuthService-assembly-0.1.0-SNAPSHOT.jar app.jar
COPY server_config.json server_config.json
EXPOSE 3001
CMD ["java", "-jar", "app.jar"]
```

### 生产环境配置
1. 修改JWT密钥为强随机字符串
2. 配置生产数据库连接
3. 启用HTTPS
4. 配置日志级别和输出
5. 设置JVM内存参数

## 监控和维护

### 日志
- 使用 Logback 进行日志管理
- 支持不同级别的日志输出
- 关键操作包含详细日志记录

### 性能监控
- 数据库连接池监控
- HTTP请求响应时间统计
- 内存使用情况监控

### 维护任务
- 定期清理过期Token黑名单
- 定期备份用户数据
- 监控系统资源使用情况

## 开发指南

### 代码风格
- 使用函数式编程范式
- 利用 Scala 3 的新特性（enum、given/using等）
- 保持代码简洁和类型安全

### 测试
- 单元测试覆盖核心业务逻辑
- 集成测试验证API接口
- 数据库操作测试

### 扩展功能
- 多因素认证
- OAuth2集成
- 用户行为审计
- 分布式会话管理

## 故障排除

### 常见问题
1. **数据库连接失败**: 检查数据库配置和网络连通性
2. **Token验证失败**: 检查JWT密钥配置和Token格式
3. **端口冲突**: 修改服务器端口配置
4. **内存不足**: 调整JVM堆内存设置

### 调试技巧
- 启用详细日志输出
- 使用数据库查询工具验证数据
- 利用HTTP客户端测试API接口

## 许可证

本项目采用 MIT 许可证，详情请参见 LICENSE 文件。
