# SystemConfigService 系统配置服务

## 服务概述

SystemConfigService是GalPHOS系统的系统配置管理微服务，负责系统管理员管理和全局系统配置管理。该服务提供RESTful API接口，支持系统管理员的增删改查、系统设置的查询和更新、以及服务健康检查等功能。

## 功能特性

### 系统管理员管理
- 系统管理员创建、查询、更新、删除
- 管理员密码修改
- 用户名唯一性验证
- 管理员状态管理（启用/禁用）
- 角色权限管理

### 系统设置管理
- 系统全局配置查询
- 批量系统设置更新
- 配置项类型转换和验证

### 服务监控
- 健康检查API
- 数据库连接状态监控
- 服务状态报告

## 技术栈

- **语言**: Scala 3.4.2
- **框架**: Akka HTTP
- **数据库**: PostgreSQL
- **构建工具**: SBT
- **JSON处理**: Spray JSON

## API接口

### 系统管理员管理
```
GET    /api/system/admins           - 获取所有系统管理员
POST   /api/system/admins           - 创建新的系统管理员
GET    /api/system/admins/{id}      - 根据ID获取管理员信息
PUT    /api/system/admins/{id}      - 更新管理员信息
DELETE /api/system/admins/{id}      - 删除管理员
PUT    /api/system/admins/{id}/password - 修改管理员密码
GET    /api/system/admins/check-username/{username} - 检查用户名是否可用
```

### 系统设置管理
```
GET    /api/system/settings         - 获取所有系统设置
PUT    /api/system/settings         - 批量更新系统设置
```

### 服务监控
```
GET    /api/system/health           - 服务健康检查
```

## 数据库表结构

### system_admins (系统管理员表)
- id: UUID (主键)
- username: VARCHAR(100) (唯一)
- password_hash: VARCHAR(255)
- email: VARCHAR(255)
- full_name: VARCHAR(255)
- role: VARCHAR(50)
- status: VARCHAR(20)
- created_at: TIMESTAMP
- updated_at: TIMESTAMP

### system_settings (系统设置表)
- id: UUID (主键)
- setting_key: VARCHAR(100) (唯一)
- setting_value: TEXT
- value_type: VARCHAR(20)
- description: TEXT
- is_public: BOOLEAN
- updated_at: TIMESTAMP
- updated_by: UUID



## 配置文件

### server_config.json
```json
{
  "server": {
    "host": "localhost",
    "port": 8085
  },
  "database": {
    "host": "localhost",
    "port": 5432,
    "name": "systemconfig_db",
    "username": "postgres",
    "password": "postgres",
    "maxConnections": 20
  },
  "security": {
    "jwtSecret": "your-secret-key",
    "jwtExpiryHours": 24
  }
}
```

## 部署说明

### 1. 环境要求
- Java 11+
- SBT 1.9.6+
- PostgreSQL 12+

### 2. 数据库初始化
```bash
# 连接到PostgreSQL
psql -U postgres -h localhost

# 创建数据库
CREATE DATABASE systemconfig_db;

# 执行初始化脚本
\c systemconfig_db
\i init_database.sql
```

### 3. 配置文件设置
编辑 `server_config.json` 文件，确保数据库连接信息正确。

### 4. 编译和运行
```bash
# 编译项目
sbt compile

# 运行服务
sbt run
```

### 5. 使用启动脚本
```bash
# Windows
start.bat

# Linux/macOS
./start.sh
```

## Docker部署

### 1. 构建镜像
```bash
docker build -t systemconfig-service .
```

### 2. 运行容器
```bash
docker-compose up -d
```

## 开发说明

### 项目结构
```
SystemConfigService/
├── build.sbt                          # SBT构建配置
├── server_config.json                 # 服务配置文件
├── init_database.sql                  # 数据库初始化脚本
├── project/
│   ├── build.properties              # SBT版本配置
│   └── plugins.sbt                   # SBT插件配置
└── src/main/scala/
    ├── Init.scala                     # 服务启动入口
    ├── Config/
    │   ├── ServerConfig.scala         # 服务器配置
    │   └── Constants.scala            # 常量定义
    ├── Models/
    │   └── Models.scala               # 数据模型定义
    ├── Database/
    │   └── DatabaseManager.scala     # 数据库操作管理
    ├── Services/
    │   ├── SystemAdminService.scala   # 系统管理员服务
    │   └── SystemSettingsService.scala # 系统设置服务
    ├── Controllers/
    │   └── SystemConfigController.scala # HTTP控制器
    └── Process/
        ├── Server.scala               # HTTP服务器
        └── ProcessUtils.scala         # 工具函数
```

### 添加新功能
1. 在 `Models.scala` 中定义新的数据模型
2. 在相应的Service中实现业务逻辑
3. 在Controller中添加路由和处理逻辑
4. 更新数据库脚本（如需要）

## 测试

### API测试
可以使用Postman、curl或其他HTTP客户端测试API接口。

示例：
```bash
# 健康检查
curl -X GET http://localhost:8085/api/system/health

# 获取系统设置
curl -X GET http://localhost:8085/api/system/settings

# 获取系统管理员列表
curl -X GET http://localhost:8085/api/system/admins
```

## 日志

服务运行日志将输出到控制台，包括：
- 服务启动信息
- 数据库连接状态
- API请求和响应
- 错误信息

## 故障排除

### 常见问题

1. **数据库连接失败**
   - 检查PostgreSQL服务是否运行
   - 验证数据库连接信息
   - 确认数据库用户权限

2. **端口冲突**
   - 修改 `server_config.json` 中的端口号
   - 检查是否有其他服务占用端口

3. **编译错误**
   - 确保Java和SBT版本正确
   - 清理并重新编译：`sbt clean compile`

## 版本历史

- v1.0.0 - 初始版本，实现基本的系统管理员和系统设置管理功能

## 许可证

本项目遵循相应的开源许可证。

## 联系方式

如有问题或建议，请联系开发团队。
