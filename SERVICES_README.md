# GalPHOS 微服务管理脚本

本目录包含了用于管理 GalPHOS 后端微服务集群的批处理脚本。

## 🚀 快速开始

### 1. 启动所有服务
```cmd
manage_services.bat
```
或者直接运行：
```cmd
start_all_services.bat
```

### 2. 检查服务状态
```cmd
check_services.bat
```

### 3. 停止所有服务
```cmd
stop_all_services.bat
```

## 📋 脚本说明

### `manage_services.bat` - 主控制台
- 提供友好的菜单界面
- 集成所有管理功能
- 包含数据库管理功能
- **推荐使用此脚本**

### `start_all_services.bat` - 启动脚本
- 按正确顺序启动所有微服务
- 自动检查服务目录和配置
- 显示启动进度和状态
- 包含启动前预检查

### `stop_all_services.bat` - 停止脚本
- 停止所有微服务进程
- 释放所有服务端口
- 关闭相关控制台窗口
- 执行清理操作

### `check_services.bat` - 状态检查
- 检查所有服务运行状态
- 验证端口占用情况
- 尝试健康检查接口
- 显示详细的服务信息

## 🏗️ 服务架构

### 端口映射
| 服务名称 | 端口 | 描述 |
|---------|------|------|
| UserAuthService | 3001 | 用户认证服务 |
| UserManagementService | 3002 | 用户管理服务 |
| ExamMS | 3003 | 考试管理服务 |
| SubmissionService | 3004 | 提交服务 |
| GradingService | 3005 | 评分服务 |
| ScoreStatisticsService | 3006 | 分数统计服务 |
| RegionMS | 3007 | 地区管理服务 |
| FileStorageService | 3008 | 文件存储服务 |
| SystemConfigService | 3009 | 系统配置服务 |

### 启动顺序
1. **第一阶段**: 核心基础服务
   - UserAuthService (认证服务)
   - FileStorageService (文件存储服务)

2. **第二阶段**: 业务服务
   - UserManagementService (用户管理)
   - RegionMS (地区管理)
   - ExamMS (考试管理)

3. **第三阶段**: 依赖服务
   - SubmissionService (提交服务)
   - GradingService (评分服务)
   - ScoreStatisticsService (统计服务)

4. **第四阶段**: 配置服务
   - SystemConfigService (系统配置)

## ⚙️ 环境要求

### 必要条件
- **Java 11+** - 运行微服务
- **PostgreSQL** - 数据库服务
- **Windows 10/11** - 操作系统
- **PowerShell/CMD** - 命令行环境

### 网络要求
- 端口 3001-3009 未被占用
- PostgreSQL 端口 5432 可访问
- localhost 网络连接正常

### 数据库配置
- 主机: `localhost:5432`
- 用户: `db`
- 密码: `root`
- 数据库: 各服务独立数据库

## 🔧 配置说明

### 服务配置文件
每个微服务目录下都有 `server_config.json` 配置文件：
```json
{
  "serverIP": "localhost",
  "serverPort": 3001,
  "jdbcUrl": "jdbc:postgresql://localhost:5432/database_name",
  "username": "db",
  "password": "root"
}
```

### 数据库初始化
每个服务目录下的 `init_database.sql` 文件用于初始化数据库表结构。

## 🚨 故障排除

### 常见问题

#### 1. 端口被占用
```cmd
# 查看端口占用
netstat -ano | findstr :3001

# 停止占用进程
taskkill /PID 进程ID /F
```

#### 2. 数据库连接失败
- 检查 PostgreSQL 服务是否启动
- 验证数据库用户名密码 (db/root)
- 确认端口 5432 可访问
- 检查防火墙设置

#### 3. 服务启动失败
- 查看对应的控制台窗口错误信息
- 检查 `server_config.json` 配置
- 确认数据库是否正确初始化
- 验证 Java 环境是否正确

#### 4. 内存不足
- 检查系统可用内存
- 调整 `start.bat` 中的 Java 内存参数
- 关闭不必要的应用程序

### 服务健康检查
访问以下 URL 检查服务状态：
- http://localhost:3001/health (认证服务)
- http://localhost:3002/health (用户管理)
- http://localhost:3003/health (考试管理)
- 其他服务类似...

## 📝 日志管理

### 日志位置
部分服务在其目录下的 `logs` 文件夹中保存日志：
- `ExamMS/logs/`
- `RegionMS/logs/`
- `SubmissionService/logs/`
- 等...

### 查看日志
使用管理控制台的"查看服务日志"功能，或直接访问对应目录。

## 🔄 维护操作

### 定期维护
1. **清理日志文件** - 定期删除旧的日志文件
2. **数据库备份** - 定期备份重要数据
3. **服务重启** - 定期重启服务释放内存
4. **系统监控** - 监控服务性能和资源使用

### 升级部署
1. 停止所有服务
2. 备份数据库
3. 更新服务代码
4. 运行数据库迁移脚本（如果需要）
5. 重新启动服务

## 📞 技术支持

如果遇到问题，请检查：
1. 所有前置条件是否满足
2. 配置文件是否正确
3. 网络和端口是否可用
4. 系统资源是否充足

详细错误信息请查看对应服务的控制台输出或日志文件。
