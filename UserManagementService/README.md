# GalPHOS 用户管理服务 (User Management Service)

## 概述

用户管理服务是GalPHOS微服务架构中的第二个核心服务，负责用户生命周期管理、用户信息维护、教练学生关系管理以及地区信息管理。

## 主要功能

### 1. 用户管理
- 获取待审核用户列表
- 审核用户申请（批准/拒绝）
- 获取已审核用户列表（支持分页和筛选）
- 启用/禁用用户账户
- 删除用户
- 用户状态变更日志

### 2. 教练学生关系管理
- 获取教练学生关系列表
- 获取教练学生统计信息
- 创建教练学生关系
- 删除教练学生关系
- 按教练查询管理的学生

### 3. 地区管理
- 获取省份列表
- 创建省份
- 获取指定省份的学校列表
- 创建学校
- 更新学校信息
- 删除学校/省份（带依赖检查）

## 技术架构

### 技术栈
- **语言**: Scala 3.4.2
- **Web框架**: Http4s
- **JSON处理**: Circe
- **数据库**: PostgreSQL
- **连接池**: HikariCP
- **并发处理**: Cats Effect
- **构建工具**: SBT
- **容器化**: Docker

### 项目结构
```
UserManagementService/
├── build.sbt                          # SBT构建文件
├── server_config.json                 # 服务配置文件
├── init_usermgmt_database.sql         # 数据库初始化脚本
├── src/main/scala/
│   ├── Config/
│   │   └── ServiceConfig.scala        # 配置管理
│   ├── Models/
│   │   └── Models.scala               # 数据模型定义
│   ├── Database/
│   │   └── DatabaseManager.scala     # 数据库操作
│   ├── Services/
│   │   ├── UserManagementService.scala    # 用户管理服务
│   │   ├── CoachStudentService.scala      # 教练学生关系服务
│   │   ├── RegionManagementService.scala  # 地区管理服务
│   │   └── AuthMiddlewareService.scala    # 认证中间件
│   ├── Controllers/
│   │   └── UserManagementController.scala # HTTP控制器
│   └── Main/
│       └── UserManagementServiceApp.scala # 主应用程序
├── Dockerfile                         # Docker镜像构建
├── docker-compose.yml                 # Docker Compose配置
└── start.sh / start.bat               # 启动脚本
```

## 数据库设计

### 主要数据表
1. **authservice.user_table** - 用户基础信息（来自认证服务）
2. **authservice.coach_managed_students** - 教练学生关系
3. **authservice.user_registration_requests** - 用户注册申请
4. **authservice.user_status_change_log** - 用户状态变更日志
5. **authservice.province_table** - 省份信息
6. **authservice.school_table** - 学校信息

### 关键特性
- 外键约束确保数据一致性
- 索引优化查询性能
- 触发器自动记录状态变更日志
- UUID主键保证唯一性

## API接口

### 基础信息
- **Base URL**: `http://localhost:3002/api/admin`
- **认证方式**: Bearer Token（需要管理员权限）
- **响应格式**: 统一的JSON格式

### 主要端点

#### 用户管理
```
GET    /users/pending              # 获取待审核用户
POST   /users/approve              # 审核用户申请
GET    /users/approved             # 获取已审核用户（支持分页）
PUT    /users/status               # 更新用户状态
DELETE /users/{userId}             # 删除用户
```

#### 教练学生管理
```
GET    /coach-students             # 获取教练学生关系
GET    /coach-students/stats       # 获取统计信息
POST   /coach-students             # 创建关系
DELETE /coach-students/{id}        # 删除关系
```

#### 地区管理
```
GET    /regions/provinces          # 获取省份列表
POST   /regions/provinces          # 创建省份
GET    /regions/schools            # 获取学校列表
POST   /regions/schools            # 创建学校
PUT    /regions/schools/{id}       # 更新学校
DELETE /regions/schools/{id}       # 删除学校
```

## 安装和运行

### 前置要求
- Java 11+
- SBT 1.9+
- PostgreSQL 15+
- (可选) Docker & Docker Compose

### 本地开发

1. **克隆代码**
   ```bash
   git clone <repository>
   cd UserManagementService
   ```

2. **配置数据库**
   ```bash
   # 创建数据库（如果还没有）
   createdb galphos_userauth
   
   # 运行初始化脚本
   psql -d galphos_userauth -f init_usermgmt_database.sql
   ```

3. **修改配置**
   编辑 `server_config.json` 文件，配置数据库连接信息。

4. **编译和运行**
   ```bash
   # 编译
   sbt compile
   
   # 打包
   sbt assembly
   
   # 运行
   ./start.sh  # Linux/macOS
   start.bat   # Windows
   ```

### Docker部署

1. **构建镜像**
   ```bash
   docker build -t galphos-usermanagement .
   ```

2. **运行服务**
   ```bash
   docker-compose up -d
   ```

## 服务间通信

### 依赖关系
- **认证服务** (端口3001): 用于Token验证
- **PostgreSQL数据库**: 共享数据存储

### 认证机制
服务通过HTTP调用认证服务的 `/api/auth/validate` 接口验证管理员Token，确保只有合法的管理员用户才能访问用户管理功能。

## 监控和日志

### 健康检查
- **端点**: `GET /health`
- **响应**: `OK` (HTTP 200)

### 日志级别
- **INFO**: 正常业务操作
- **WARN**: 权限验证失败等警告
- **ERROR**: 系统错误和异常

### 关键指标
- 用户审核操作数量
- 教练学生关系变更数量
- API响应时间
- 数据库连接池状态

## 错误处理

### 标准错误响应格式
```json
{
  "success": false,
  "message": "错误描述信息"
}
```

### 常见错误码
- **400**: 请求参数错误
- **401**: 身份验证失败
- **403**: 权限不足
- **404**: 资源不存在
- **500**: 内部服务器错误

## 开发指南

### 添加新功能
1. 在 `Models/` 中定义数据模型
2. 在 `Services/` 中实现业务逻辑
3. 在 `Controllers/` 中添加HTTP端点
4. 更新数据库脚本（如需要）
5. 添加相应的测试

### 代码规范
- 使用Scala 3语法特性
- 遵循函数式编程原则
- 使用Cats Effect处理副作用
- 统一的错误处理机制
- 完善的日志记录

## 部署注意事项

### 生产环境配置
- 修改数据库连接密码
- 调整JVM内存参数
- 配置适当的日志级别
- 设置健康检查和监控

### 扩展性考虑
- 支持水平扩展（无状态设计）
- 数据库连接池配置
- 缓存策略（可选）
- 负载均衡配置

## 故障排除

### 常见问题
1. **数据库连接失败**: 检查配置文件和网络连接
2. **认证服务调用失败**: 确认认证服务正在运行
3. **权限验证失败**: 检查Token是否有效且具有管理员权限
4. **端口冲突**: 确认端口3002未被占用

### 日志查看
```bash
# Docker环境
docker logs galphos-usermanagement

# 本地环境
tail -f logs/application.log
```

## 版本历史

- **v0.1.0**: 初始版本，实现基础用户管理功能
  - 用户审核功能
  - 教练学生关系管理
  - 地区信息管理
  - 统一的认证中间件

## 贡献指南

1. Fork 项目
2. 创建功能分支
3. 提交代码更改
4. 创建 Pull Request
5. 代码审查通过后合并

## 许可证

[请根据项目实际情况添加许可证信息]
