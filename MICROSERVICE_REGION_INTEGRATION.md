# GalPHOS微服务地区数据集成指南

## 概述
本文档描述了GalPHOS微服务系统中地区数据的统一管理和获取方式。所有地区数据（省份、学校）统一由RegionMS微服务提供，其他微服务通过RegionMS的内部API获取地区名称信息。

## 地区数据架构

### 数据源唯一性
- **唯一数据源**: RegionMS微服务
- **存储位置**: RegionMS数据库的`province_table`和`school_table`
- **主键格式**: UUID

### 其他微服务的地区数据处理
- **存储方式**: 仅存储`province_id`和`school_id`（UUID格式）
- **名称获取**: 通过RegionMS内部API动态获取
- **本地地区表**: 已全部移除

## RegionMS内部API

### 端点信息
- **URL**: `http://localhost:3007/internal/regions`
- **方法**: GET
- **用途**: 根据省份ID和学校ID获取对应的名称

### 请求参数
| 参数名 | 类型 | 必需 | 描述 |
|--------|------|------|------|
| provinceId | string (UUID) | 是 | 省份唯一标识 |
| schoolId | string (UUID) | 是 | 学校唯一标识 |

### 响应格式

#### 成功响应 (200)
```json
{
  "provinceName": "Bangkok",
  "schoolName": "Bangkok University"
}
```

#### 错误响应 (400)
```json
{
  "error": "Province not found with ID: 123e4567-e89b-12d3-a456-426614174000"
}
```

## 各微服务集成状态

### 1. UserAuthService (端口: 3001)
**状态**: ✅ 已集成

**配置要求**:
```json
{
  "regionServiceUrl": "http://localhost:3007"
}
```

**集成点**:
- `RegionServiceClient`: 实现内部API调用
- `UserService.getUserInfo()`: 异步获取地区名称
- 数据库: 仅保留`province_id`、`school_id`字段

### 2. UserManagementService (端口: 3002)  
**状态**: ✅ 已集成

**配置要求**:
```json
{
  "regionServiceUrl": "http://localhost:3007"
}
```

**集成点**:
- `RegionServiceClient`: 实现内部API调用
- `UserManagementService`: 用户转换时异步获取地区名称
- 数据库: 仅保留`province_id`、`school_id`字段

### 3. FileStorageService (端口: 3008)
**状态**: ✅ 数据库已清理

**说明**: 
- 已移除本地地区表
- 当前版本不需要显示地区名称
- 如需扩展，可参考UserAuthService的集成方式

### 4. ExamMS (端口: 3003)
**状态**: ✅ 无需集成

**说明**: 
- 考试管理不直接涉及地区信息
- 通过用户ID间接关联地区

### 5. SubmissionService (端口: 3004)
**状态**: ✅ 无需集成

**说明**: 
- 答题提交不直接涉及地区信息
- 通过学生ID间接关联地区

### 6. RegionMS (端口: 3007)
**状态**: ✅ 数据源服务

**功能**: 
- 提供公共API供前端调用
- 提供内部API供其他微服务调用
- 维护唯一的地区数据源

## 集成实现指南

### 步骤1: 添加配置
在微服务的`server_config.json`中添加:
```json
{
  "regionServiceUrl": "http://localhost:3007"
}
```

### 步骤2: 创建RegionServiceClient
```scala
// 响应模型
case class RegionNamesResponse(
  provinceName: String,
  schoolName: String
)

case class RegionErrorResponse(
  error: String
)

trait RegionServiceClient {
  def getProvinceAndSchoolNamesByIds(provinceId: String, schoolId: String): IO[Either[String, RegionNamesResponse]]
}
```

### 步骤3: 实现HTTP调用
```scala
override def getProvinceAndSchoolNamesByIds(provinceId: String, schoolId: String): IO[Either[String, RegionNamesResponse]] = {
  IO.blocking {
    try {
      val request = HttpRequest.newBuilder()
        .uri(URI.create(s"$regionServiceUrl/internal/regions?provinceId=$provinceId&schoolId=$schoolId"))
        .header("Content-Type", "application/json")
        .GET()
        .build()

      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      
      if (response.statusCode() == 200) {
        // 解析成功响应
        parse(response.body()).flatMap(_.as[RegionNamesResponse]) match {
          case Right(regionNames) => Right(regionNames)
          case Left(error) => Left(s"解析响应失败: ${error.getMessage}")
        }
      } else {
        // 处理错误响应
        Left(s"API调用失败，状态码: ${response.statusCode()}")
      }
    } catch {
      case ex: Exception => Left(s"调用异常: ${ex.getMessage}")
    }
  }
}
```

### 步骤4: 在业务逻辑中使用
```scala
// 异步获取地区名称
for {
  regionResult <- regionServiceClient.getProvinceAndSchoolNamesByIds(provinceId, schoolId)
  userInfo <- regionResult match {
    case Right(regionNames) =>
      // 使用regionNames.provinceName和regionNames.schoolName
      IO.pure(createUserInfoWithRegion(regionNames))
    case Left(error) =>
      // 处理错误，可选择返回不含地区名称的用户信息
      logger.warn(s"获取地区名称失败: $error")
      IO.pure(createUserInfoWithoutRegion())
  }
} yield userInfo
```

## 错误处理策略

### 网络异常
- 记录警告日志
- 返回不含地区名称的响应
- 不阻断主要业务流程

### 地区不存在
- 记录警告日志
- 返回空的地区名称字段
- 提示用户检查地区配置

### RegionMS服务不可用
- 实现重试机制（可选）
- 降级处理：返回ID而非名称
- 监控告警

## 数据库迁移清单

### 已完成的清理
- ✅ UserAuthService: 移除`province_table`、`school_table`
- ✅ UserManagementService: 移除本地地区表
- ✅ FileStorageService: 移除本地地区表
- ✅ 统一所有微服务的数据库初始化脚本为`init_database.sql`

### 保留的字段
- `province_id` (UUID): 引用RegionMS的省份ID
- `school_id` (UUID): 引用RegionMS的学校ID

## 测试验证

### RegionMS内部API测试
```bash
# 测试成功案例
curl "http://localhost:3007/internal/regions?provinceId=123e4567-e89b-12d3-a456-426614174000&schoolId=987fcdeb-51d2-43ab-8765-123456789abc"

# 测试错误案例
curl "http://localhost:3007/internal/regions?provinceId=invalid-uuid&schoolId=987fcdeb-51d2-43ab-8765-123456789abc"
```

### 微服务集成测试
1. 启动RegionMS服务
2. 启动目标微服务
3. 验证地区名称获取功能
4. 测试RegionMS不可用时的降级处理

## 最佳实践

### 1. 异步处理
- 地区名称获取应使用异步操作
- 避免阻塞主要业务流程

### 2. 缓存策略
- 考虑在微服务中实现地区名称缓存
- 减少对RegionMS的频繁调用

### 3. 错误处理
- 实现优雅的降级机制
- 记录详细的错误日志

### 4. 监控告警
- 监控RegionMS的可用性
- 设置地区数据获取失败率告警

## 维护指南

### 添加新省份/学校
1. 在RegionMS中添加数据
2. 其他微服务自动支持新地区

### 修改地区名称
1. 在RegionMS中修改
2. 其他微服务下次调用时自动获取新名称

### 地区数据一致性
- 所有地区数据变更都在RegionMS中进行
- 保证整个系统的数据一致性

---

**注意**: 本集成指南基于当前的微服务架构设计，如有变更请及时更新此文档。
