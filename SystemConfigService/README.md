# 系统配置服务 (System Configuration Service)

系统配置服务负责管理系统的全局配置和管理员账户。

## 更新日志

### 2025-07-03 修复编译错误

- 修正 `DatabaseConfig.scala` 中 HikariTransactor 参数顺序，移除命名参数：`driver` → `driverClassName`, `blocker` → `transactEC`
- 在 `DoobieMeta.scala` 中使用 `TimestampMeta` 为 `ZonedDateTime` 和 `Option[ZonedDateTime]` 提供正确的 Meta 实例
- 修复 SQL 片段插值中 ZonedDateTime 类型，将 `${now}` 修改为 `$now` 以符合 doobie SQL 插值器要求
- 修复 `AdminRoutes` 和 `SettingsRoutes` 中的 AuthMiddleware 匿名函数，使用正确的括号包装参数
- 更新 `DatabaseSupport.scala`，提供更好的片段支持
- 重写 `TestZonedDateTime.scala`，解决 `ConnectionIO` 与 `IO` 类型转换问题
- 更新 `run.bat` 以支持 ZonedDateTime 测试：`run.bat test-datetime`

### 2025-07-03 修复编译问题

- 修复 `DatabaseSupport.scala` 中的重复对象定义
- 增强 `DoobieMeta.scala`，提供对 `ZonedDateTime` 和 `Option[ZonedDateTime]` 的支持
- 修正 `DatabaseConfig.scala` 中 HikariTransactor 的参数命名和顺序
- 修复 SQL 片段中的时间戳插值处理，`$now` 改为 `${now}`
- 改进 `AdminRoutes` 和 `SettingsRoutes` 中的 AuthMiddleware 实现
- 添加更多对时间类型的 Meta 实例，解决 Read 推断问题

## 功能概述

- **超级管理员专用功能**：管理其他管理员账号，包括创建、编辑、删除及密码重置
- **系统基础配置**：系统名称、默认设置、全局参数等基础信息管理
- **系统信息展示**：系统版本号、构建信息、技术栈和依赖组件信息
- **配置权限分离**：管理员专用设置与公共设置严格分离
- **版本控制**：系统版本信息维护和更新历史追踪
- **配置实时更新**：关键配置变更后实时生效无需重启服务

## 技术栈

- **Scala 3.4.2**
- **Http4s** - 函数式HTTP服务器和客户端
- **Cats Effect** - 函数式副作用管理
- **Circe** - JSON序列化/反序列化
- **Doobie** - 函数式JDBC层
- **PostgreSQL** - 数据库
- **Log4cats** - 日志管理

## API 端点

### 系统管理员管理（超级管理员专用）

- `/api/admin/system/admins` - 管理员列表和创建 (GET, POST)
- `/api/admin/system/admins/{adminId}` - 单个管理员操作（编辑/删除）(GET, PUT, DELETE)
- `/api/admin/system/admins/{adminId}/password` - 管理员密码重置 (POST)

### 系统基础配置

- `/api/admin/system/settings` - 管理员视图系统设置 (GET, PUT, POST)
- `/api/system/settings` - 公共系统设置 (GET)
- `/api/system/version` - 系统版本信息 (GET)

## 数据库表

- `system_config` - 系统配置表
- `system_admins` - 系统管理员表
- `config_history` - 配置变更历史表
- `system_version` - 系统版本表

## 构建与运行

### 前置要求

- JDK 17+
- SBT 1.6+
- PostgreSQL 14+

### 数据库初始化

```bash
# Windows
setup_database.bat

# Linux/macOS
./setup_database.sh
```

### 编译

```bash
sbt compile
```

### 运行测试

```bash
sbt test
```

### 创建可执行JAR包

```bash
sbt assembly
```

## 启动服务

### Windows

```
start.bat
```

### Linux/Mac

```
./start.sh
```

## 初始化数据库

初始化脚本在 `init_database.sql` 文件中。

```bash
# Windows
setup_database.bat

# Linux/Mac
./setup_database.sh
```

## Docker 支持

可以使用 Docker 运行服务：

```
docker-compose up -d
```

## 镜像源配置

本项目配置了多个镜像源以加速依赖下载：
- Aliyun Central
- Huawei Mirror
- Tsinghua Mirror
- Maven Central

## 依赖库版本

- http4s: 0.23.30
- circe: 0.14.10
- cats-effect: 3.x
- doobie: 1.0.0-RC2
- fs2: 3.11.0
- postgresql: 42.7.2
