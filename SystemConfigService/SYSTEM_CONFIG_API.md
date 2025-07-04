# 系统配置服务 API 文档 (System Configuration Service API Reference)

本文档详细介绍了系统配置服务提供的API端点。

## 1. 系统管理员管理 API

### 1.1 获取所有管理员

**请求**:
```
GET /api/admin/system/admins
```

**权限**:
- 需要超级管理员权限

**响应**:
```json
[
  {
    "adminId": 1,
    "username": "superadmin",
    "fullName": "超级管理员",
    "email": "admin@galphos.com",
    "isSuperAdmin": true,
    "createdAt": "2025-07-01T12:00:00Z",
    "updatedAt": "2025-07-01T12:00:00Z",
    "lastLogin": "2025-07-03T09:15:22Z"
  },
  {
    "adminId": 2,
    "username": "systemadmin",
    "fullName": "系统管理员",
    "email": "system@galphos.com",
    "isSuperAdmin": false,
    "createdAt": "2025-07-02T10:30:00Z",
    "updatedAt": "2025-07-02T10:30:00Z",
    "lastLogin": null
  }
]
```

### 1.2 创建管理员

**请求**:
```
POST /api/admin/system/admins
```

**权限**:
- 需要超级管理员权限

**请求体**:
```json
{
  "username": "newadmin",
  "password": "securepassword",
  "fullName": "新管理员",
  "email": "new@galphos.com",
  "isSuperAdmin": false
}
```

**响应**:
```json
{
  "adminId": 3,
  "username": "newadmin",
  "fullName": "新管理员",
  "email": "new@galphos.com",
  "isSuperAdmin": false,
  "createdAt": "2025-07-03T14:22:30Z",
  "updatedAt": "2025-07-03T14:22:30Z",
  "lastLogin": null
}
```

### 1.3 获取单个管理员

**请求**:
```
GET /api/admin/system/admins/{adminId}
```

**权限**:
- 需要超级管理员权限

**响应**:
```json
{
  "adminId": 1,
  "username": "superadmin",
  "fullName": "超级管理员",
  "email": "admin@galphos.com",
  "isSuperAdmin": true,
  "createdAt": "2025-07-01T12:00:00Z",
  "updatedAt": "2025-07-01T12:00:00Z",
  "lastLogin": "2025-07-03T09:15:22Z"
}
```

### 1.4 更新管理员

**请求**:
```
PUT /api/admin/system/admins/{adminId}
```

**权限**:
- 需要超级管理员权限

**请求体**:
```json
{
  "fullName": "已更新的管理员",
  "email": "updated@galphos.com",
  "isSuperAdmin": false
}
```

**响应**:
```json
{
  "adminId": 2,
  "username": "systemadmin",
  "fullName": "已更新的管理员",
  "email": "updated@galphos.com",
  "isSuperAdmin": false,
  "createdAt": "2025-07-02T10:30:00Z",
  "updatedAt": "2025-07-03T15:45:12Z",
  "lastLogin": null
}
```

### 1.5 删除管理员

**请求**:
```
DELETE /api/admin/system/admins/{adminId}
```

**权限**:
- 需要超级管理员权限

**响应**:
```
204 No Content
```

### 1.6 重置管理员密码

**请求**:
```
POST /api/admin/system/admins/{adminId}/password
```

**权限**:
- 需要超级管理员权限

**请求体**:
```json
{
  "password": "newSecurePassword"
}
```

**响应**:
```json
{
  "message": "密码已重置"
}
```

## 2. 系统基础配置 API

### 2.1 获取管理员视图系统设置

**请求**:
```
GET /api/admin/system/settings
```

**权限**:
- 需要管理员权限

**响应**:
```json
[
  {
    "id": 1,
    "configKey": "system.name",
    "configValue": "GalPHOS 统一管理系统",
    "description": "系统名称",
    "isPublic": true,
    "createdAt": "2025-07-01T12:00:00Z",
    "updatedAt": "2025-07-01T12:00:00Z"
  },
  {
    "id": 2,
    "configKey": "system.maintenance",
    "configValue": "false",
    "description": "是否处于维护模式",
    "isPublic": true,
    "createdAt": "2025-07-01T12:00:00Z",
    "updatedAt": "2025-07-01T12:00:00Z"
  },
  {
    "id": 3,
    "configKey": "admin.session.timeout",
    "configValue": "3600",
    "description": "管理员会话超时时间（秒）",
    "isPublic": false,
    "createdAt": "2025-07-01T12:00:00Z",
    "updatedAt": "2025-07-01T12:00:00Z"
  }
]
```

### 2.2 更新系统设置

**请求**:
```
PUT /api/admin/system/settings
```

**权限**:
- 需要管理员权限

**请求体**:
```json
{
  "configKey": "system.name",
  "configValue": "GalPHOS 统一考试管理系统 v1.3",
  "isPublic": true
}
```

**响应**:
```json
{
  "id": 1,
  "configKey": "system.name",
  "configValue": "GalPHOS 统一考试管理系统 v1.3",
  "description": "系统名称",
  "isPublic": true,
  "createdAt": "2025-07-01T12:00:00Z",
  "updatedAt": "2025-07-03T16:24:45Z"
}
```

### 2.3 创建系统设置

**请求**:
```
POST /api/admin/system/settings
```

**权限**:
- 需要管理员权限

**请求体**:
```json
{
  "configKey": "system.theme",
  "configValue": "light",
  "description": "系统默认主题",
  "isPublic": true
}
```

**响应**:
```json
{
  "id": 7,
  "configKey": "system.theme",
  "configValue": "light",
  "description": "系统默认主题",
  "isPublic": true,
  "createdAt": "2025-07-03T16:30:22Z",
  "updatedAt": "2025-07-03T16:30:22Z"
}
```

### 2.4 删除系统设置

**请求**:
```
DELETE /api/admin/system/settings/{configKey}
```

**权限**:
- 需要管理员权限

**路径参数**:
- `configKey`: 要删除的配置项键名

**响应**:
```
204 No Content
```

### 2.4 获取公共系统设置

**请求**:
```
GET /api/system/settings
```

**权限**:
- 无需权限，公开访问

**响应**:
```json
[
  {
    "id": 1,
    "configKey": "system.name",
    "configValue": "GalPHOS 统一考试管理系统 v1.3",
    "description": "系统名称",
    "isPublic": true,
    "createdAt": "2025-07-01T12:00:00Z",
    "updatedAt": "2025-07-03T16:24:45Z"
  },
  {
    "id": 2,
    "configKey": "system.maintenance",
    "configValue": "false",
    "description": "是否处于维护模式",
    "isPublic": true,
    "createdAt": "2025-07-01T12:00:00Z",
    "updatedAt": "2025-07-01T12:00:00Z"
  },
  {
    "id": 7,
    "configKey": "system.theme",
    "configValue": "light",
    "description": "系统默认主题",
    "isPublic": true,
    "createdAt": "2025-07-03T16:30:22Z",
    "updatedAt": "2025-07-03T16:30:22Z"
  }
]
```

### 2.5 获取系统版本信息

**请求**:
```
GET /api/system/version
```

**权限**:
- 无需权限，公开访问

**响应**:
```json
{
  "id": 1,
  "version": "1.3.0",
  "buildNumber": "20250701",
  "releaseDate": "2025-07-01T00:00:00Z",
  "releaseNotes": "新版本发布，优化系统配置功能",
  "isCurrent": true
}
```

## 3. 健康检查 API

### 3.1 服务健康检查

**请求**:
```
GET /health
```

**响应**:
```json
{
  "status": "UP",
  "service": "SystemConfigService",
  "time": 1719132758000
}
```
