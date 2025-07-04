# SystemConfigService 启动顺序说明

## 问题描述
SystemConfigService 在启动时会立即验证管理员Token，如果UserAuthService尚未启动，会导致认证失败，从而强制管理员登出。

## 解决方案

### 1. 正确的服务启动顺序
1. **首先启动 UserAuthService** (端口 3001)
2. **等待 UserAuthService 完全启动** (约10-15秒)
3. **然后启动 SystemConfigService** (端口 3009)

### 2. 启动脚本
```bash
# 步骤1：启动认证服务
cd UserAuthService
start.bat

# 步骤2：等待15秒
timeout /t 15

# 步骤3：启动系统配置服务
cd ..\SystemConfigService
start.bat
```

### 3. 代码优化
- 添加了认证服务健康检查
- 添加了启动延迟 (5秒)
- 添加了重试机制
- 添加了更详细的日志记录
- 在认证服务不可用时提供优雅降级

### 4. 验证步骤
1. 检查UserAuthService健康状态: `GET http://localhost:3001/health`
2. 检查SystemConfigService健康状态: `GET http://localhost:3009/health`
3. 测试管理员登录和访问SystemConfigService接口

## 注意事项
- 确保两个服务都连接到同一个数据库
- 检查网络连接和防火墙设置
- 查看日志文件以诊断具体问题
