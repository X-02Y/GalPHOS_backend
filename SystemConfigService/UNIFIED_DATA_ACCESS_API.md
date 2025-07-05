# SystemConfigService 统一数据访问 API 文档

## 概述

SystemConfigService 现在通过内部代理服务提供统一的用户和管理员数据访问接口。所有数据存储在同一个数据库中，通过相同的方式访问。

## 架构设计

```
SystemConfigService
├── AdminProxyService      # 管理员数据代理
├── UserProxyService       # 用户数据代理  
├── UnifiedDataProxyService # 统一数据访问代理
└── 统一路由处理器
    ↓ HTTP 请求
UserManagementService
    ↓ 直接数据库访问
Database (galphos)
├── authservice.admin_table  # 管理员数据
├── authservice.user_table   # 用户数据
└── 其他相关表...
```

## API 端点

### 基础信息
- **Base URL**: `http://localhost:3003/api`
- **认证方式**: Bearer Token（需要管理员权限）
- **响应格式**: 统一的JSON格式

### 1. 管理员管理 API (保持不变)

#### 1.1 获取所有管理员
```
GET /admin/system/admins
Authorization: Bearer {token}
```

#### 1.2 创建管理员
```
POST /admin/system/admins
Authorization: Bearer {token}
Content-Type: application/json

{
  "username": "newadmin",
  "password": "password123",
  "role": "admin"
}
```

### 2. 用户管理 API (新增)

#### 2.1 获取待审核用户
```
GET /admin/users/pending
Authorization: Bearer {token}
```

#### 2.2 获取已审核用户
```
GET /admin/users/approved?page=1&limit=20&role=student&status=active
Authorization: Bearer {token}
```

#### 2.3 审核用户申请
```
POST /admin/users/approve
Authorization: Bearer {token}
Content-Type: application/json

{
  "userId": "user123",
  "action": "approve",
  "reason": "符合条件"
}
```

#### 2.4 更新用户状态
```
PUT /admin/users/{userId}/status
Authorization: Bearer {token}
Content-Type: application/json

{
  "status": "active"
}
```

#### 2.5 删除用户
```
DELETE /admin/users/{userId}
Authorization: Bearer {token}
```

### 3. 统一数据访问 API (新增核心功能)

#### 3.1 获取所有用户（包括管理员）
```
GET /admin/data/users/all?includeAdmins=true
Authorization: Bearer {token}
```

**响应示例**:
```json
{
  "users": [
    {
      "userId": "user1",
      "username": "student1",
      "role": "student",
      "status": "active",
      "province": "北京市",
      "school": "清华大学"
    }
  ],
  "admins": [
    {
      "adminId": 1,
      "username": "admin1",
      "role": "super_admin",
      "isSuperAdmin": true
    }
  ],
  "totalUsers": 150,
  "totalAdmins": 5,
  "total": 155
}
```

#### 3.2 按角色获取用户
```
GET /admin/data/users/by-role/{role}
Authorization: Bearer {token}
```

支持的角色:
- `student` - 学生
- `coach` - 教练
- `grader` - 阅卷员
- `admin` - 管理员
- `super_admin` - 超级管理员

#### 3.3 按状态获取用户
```
GET /admin/data/users/by-status/{status}
Authorization: Bearer {token}
```

支持的状态:
- `pending` - 待审核
- `active` - 活跃
- `disabled` - 禁用

#### 3.4 搜索用户
```
GET /admin/data/users/search?q={query}&includeAdmins=true
Authorization: Bearer {token}
```

#### 3.5 数据统计
```
GET /admin/data/statistics
Authorization: Bearer {token}
```

**响应示例**:
```json
{
  "totalUsers": 150,
  "totalAdmins": 5,
  "totalAll": 155,
  "pendingUsers": 12,
  "usersByRole": {
    "student": 100,
    "coach": 30,
    "grader": 20,
    "admin": 4,
    "super_admin": 1
  },
  "lastUpdated": "2025-07-04T10:30:00Z"
}
```

### 4. 健康检查和信息接口

#### 4.1 服务健康检查
```
GET /data/health
```

#### 4.2 服务信息
```
GET /data/info
```

## 数据模型

### User 模型
```json
{
  "userId": "string",
  "username": "string",
  "phone": "string (optional)",
  "role": "student|coach|grader",
  "status": "pending|active|disabled",
  "province": "string (optional)",
  "school": "string (optional)",
  "avatarUrl": "string (optional)",
  "createdAt": "ISO datetime (optional)",
  "updatedAt": "ISO datetime (optional)",
  "lastLogin": "ISO datetime (optional)"
}
```

### Admin 模型
```json
{
  "adminId": "number",
  "username": "string",
  "role": "admin|super_admin",
  "isSuperAdmin": "boolean",
  "createdAt": "ISO datetime (optional)",
  "updatedAt": "ISO datetime (optional)",
  "lastLogin": "ISO datetime (optional)"
}
```

## 使用示例

### 获取系统中所有用户数据
```javascript
// 获取所有用户和管理员
const response = await fetch('/api/admin/data/users/all?includeAdmins=true', {
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  }
});

const data = await response.json();
console.log(`系统中共有 ${data.total} 个用户，其中 ${data.totalUsers} 个普通用户，${data.totalAdmins} 个管理员`);
```

### 按角色筛选用户
```javascript
// 获取所有学生
const students = await fetch('/api/admin/data/users/by-role/student', {
  headers: { 'Authorization': `Bearer ${token}` }
});

// 获取所有管理员
const admins = await fetch('/api/admin/data/users/by-role/admin', {
  headers: { 'Authorization': `Bearer ${token}` }
});
```

### 搜索用户
```javascript
// 搜索用户名包含"张"的所有用户（包括管理员）
const searchResults = await fetch('/api/admin/data/users/search?q=张&includeAdmins=true', {
  headers: { 'Authorization': `Bearer ${token}` }
});
```

## 错误处理

所有API遵循统一的错误响应格式：

```json
{
  "error": "错误描述信息"
}
```

常见HTTP状态码：
- `200` - 成功
- `400` - 请求参数错误
- `401` - 认证失败
- `403` - 权限不足
- `404` - 资源不存在
- `500` - 服务器内部错误

## 特性

1. **统一数据源**: 所有用户和管理员数据存储在同一数据库
2. **代理模式**: SystemConfigService通过代理访问UserManagementService
3. **统一接口**: 提供一致的API访问所有用户数据
4. **权限控制**: 所有操作需要管理员权限验证
5. **数据聚合**: 提供统计和搜索功能
6. **类型安全**: 完整的Scala类型定义和JSON编解码器
