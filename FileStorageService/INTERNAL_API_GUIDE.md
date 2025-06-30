# FileStorageService 内部通信接口使用指南

## 概述

FileStorageService 提供了内部通信接口，允许其他微服务通过HTTP请求进行文件操作。前端的文件上传请求会先到达相应的业务微服务，然后由业务微服务调用FileStorageService的内部接口完成实际的文件存储。

## 架构设计

```
前端请求 -> 业务微服务 -> FileStorageService内部接口 -> 文件系统
```

### 文件上传流程分配

根据 MICROSERVICE_ROUTING.md，文件上传请求的分配如下：

1. **考试文件上传** (试题、答案、答题卡)
   - 前端API: `/api/admin/exams` (POST/PUT)
   - 处理服务: ExamManagementService (3003)
   - 内部调用: FileStorageService (3008)

2. **学生答题卡上传**
   - 前端API: `/api/student/exams/{examId}/submit`
   - 处理服务: SubmissionService (3004)
   - 内部调用: FileStorageService (3008)

3. **教练代理提交**
   - 前端API: `/api/coach/exams/{examId}/upload-answer`
   - 处理服务: SubmissionService (3004)
   - 内部调用: FileStorageService (3008)

4. **题目分数导入文件**
   - 前端API: `/api/admin/exams/{examId}/questions/scores/import`
   - 处理服务: GradingService (3005)
   - 内部调用: FileStorageService (3008)

5. **用户头像等个人文件**
   - 前端API: `/api/*/profile` (各角色)
   - 处理服务: UserManagementService (3002)
   - 内部调用: FileStorageService (3008)

## 内部通信接口

### Base URL
```
http://localhost:3008/internal/
```

### 1. 文件上传接口

**接口**: `POST /internal/upload`

**请求体**:
```json
{
  "originalName": "exam_questions.pdf",
  "fileContent": [/* Base64编码的文件内容字节数组 */],
  "fileType": "pdf",
  "mimeType": "application/pdf",
  "uploadUserId": "user123",
  "uploadUserType": "admin",
  "examId": "exam001",
  "submissionId": null,
  "description": "2024年春季数学竞赛试题",
  "category": "exam"
}
```

**响应**:
```json
{
  "success": true,
  "fileId": "file_uuid_123",
  "originalName": "exam_questions.pdf",
  "fileSize": 2048576,
  "uploadTime": "2024-03-15T09:00:00",
  "error": null
}
```

### 2. 文件下载接口

**接口**: `POST /internal/download`

**请求体**:
```json
{
  "fileId": "file_uuid_123",
  "requestUserId": "user123",
  "requestUserType": "student",
  "purpose": "download"
}
```

**响应**:
```json
{
  "success": true,
  "fileId": "file_uuid_123",
  "originalName": "exam_questions.pdf",
  "fileContent": [/* 文件内容字节数组 */],
  "mimeType": "application/pdf",
  "error": null
}
```

### 3. 文件删除接口

**接口**: `POST /internal/delete`

**请求体**:
```json
{
  "fileId": "file_uuid_123",
  "requestUserId": "user123",
  "requestUserType": "admin",
  "reason": "考试结束，清理文件"
}
```

### 4. 文件信息查询接口

**接口**: `GET /internal/info/{fileId}?userId=xxx&userType=xxx`

**响应**:
```json
{
  "success": true,
  "data": {
    "fileId": "file_uuid_123",
    "originalName": "exam_questions.pdf",
    "storedName": "stored_file_name.pdf",
    "fileType": "pdf",
    "fileSize": 2048576,
    "mimeType": "application/pdf",
    // ... 其他文件元数据
  }
}
```

### 5. 批量操作接口

**接口**: `POST /internal/batch`

**请求体**:
```json
{
  "operation": "delete",
  "fileIds": ["file1", "file2", "file3"],
  "requestUserId": "admin123",
  "requestUserType": "admin",
  "reason": "批量清理过期文件"
}
```

### 6. 健康检查接口

**接口**: `GET /internal/health`

## 客户端使用示例

### 在 Scala 微服务中使用

```scala
import Utils.FileStorageServiceClient

class ExamManagementService {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext = system.dispatcher
  
  val fileStorageClient = new FileStorageServiceClient("http://localhost:3008")
  
  def uploadExamFile(examFile: ExamFile, adminId: String): Future[Either[String, String]] = {
    fileStorageClient.uploadFile(
      originalName = examFile.name,
      fileContent = examFile.content,
      fileType = extractFileExtension(examFile.name),
      mimeType = detectMimeType(examFile.name),
      uploadUserId = Some(adminId),
      uploadUserType = Some("admin"),
      examId = Some(examFile.examId),
      description = Some("考试试题文件"),
      category = "exam"
    ).map {
      case Right(response) => Right(response.fileId.get)
      case Left(error) => Left(error)
    }
  }
}
```

### 在其他语言微服务中使用

#### Node.js 示例

```javascript
const axios = require('axios');

class FileStorageServiceClient {
  constructor(baseUrl = 'http://localhost:3008') {
    this.baseUrl = baseUrl;
  }

  async uploadFile(uploadRequest) {
    try {
      const response = await axios.post(`${this.baseUrl}/internal/upload`, uploadRequest, {
        headers: { 'Content-Type': 'application/json' }
      });
      return { success: true, data: response.data };
    } catch (error) {
      return { success: false, error: error.response?.data?.error || error.message };
    }
  }

  async downloadFile(fileId, userId, userType, purpose = 'download') {
    try {
      const response = await axios.post(`${this.baseUrl}/internal/download`, {
        fileId, requestUserId: userId, requestUserType: userType, purpose
      });
      return { success: true, data: response.data };
    } catch (error) {
      return { success: false, error: error.response?.data?.error || error.message };
    }
  }
}

// 使用示例
const fileClient = new FileStorageServiceClient();

// 在考试管理服务中上传文件
app.post('/api/admin/exams', async (req, res) => {
  const { examData, files } = req.body;
  
  // 处理考试文件上传
  if (files.questionFile) {
    const uploadResult = await fileClient.uploadFile({
      originalName: files.questionFile.name,
      fileContent: Array.from(files.questionFile.buffer),
      fileType: path.extname(files.questionFile.name).slice(1),
      mimeType: files.questionFile.mimetype,
      uploadUserId: req.user.id,
      uploadUserType: 'admin',
      examId: examData.id,
      description: '考试试题文件',
      category: 'exam'
    });

    if (uploadResult.success) {
      examData.questionFileId = uploadResult.data.fileId;
    } else {
      return res.status(400).json({ error: uploadResult.error });
    }
  }
  
  // 保存考试信息...
});
```

## 权限控制

### 上传权限
- **admin**: 可以上传所有类型的文件
- **coach**: 可以上传考试相关文件和代理学生提交
- **student**: 只能上传答题卡
- **grader**: 可以上传评分相关文件

### 下载权限
- **admin**: 可以下载所有文件
- **coach**: 可以下载自己上传的文件和学生提交
- **student**: 只能下载公开文件和自己上传的文件
- **grader**: 可以下载评分相关文件

### 删除权限
- **admin**: 可以删除所有文件
- **coach/student**: 只能删除自己上传的文件
- **grader**: 可以删除评分相关的临时文件

## 错误处理

### 常见错误码

| HTTP状态码 | 错误类型 | 描述 |
|-----------|---------|------|
| 400 | Bad Request | 请求参数错误或文件格式不支持 |
| 401 | Unauthorized | 认证失败 |
| 403 | Forbidden | 权限不足 |
| 404 | Not Found | 文件不存在 |
| 413 | Payload Too Large | 文件大小超过限制 |
| 500 | Internal Server Error | 服务器内部错误 |

### 错误响应格式

```json
{
  "success": false,
  "error": "File size exceeds maximum limit of 100MB"
}
```

## 配置说明

### FileStorageService 配置

```json
{
  "fileStoragePath": "./storage/files",
  "maxFileSize": 104857600,
  "allowedFileTypes": [
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "jpg", "jpeg", "png", "gif", "bmp",
    "txt", "csv", "zip", "rar"
  ]
}
```

### 业务微服务配置

各个业务微服务需要配置 FileStorageService 的地址：

```bash
# 环境变量
FILE_STORAGE_SERVICE_URL=http://localhost:3008

# 或在配置文件中
fileStorageService:
  url: "http://localhost:3008"
  timeout: 30000
  retries: 3
```

## 监控和日志

### 日志记录
FileStorageService 会记录所有内部通信操作：
- 文件上传/下载/删除操作
- 访问用户和权限检查
- 操作结果和错误信息
- 文件访问统计

### 健康检查
```bash
# 检查 FileStorageService 是否正常
curl http://localhost:3008/internal/health
```

### 性能监控
- 文件操作响应时间
- 存储空间使用情况
- 并发连接数
- 错误率统计

## 注意事项

1. **文件大小限制**: 默认最大100MB，可在配置中调整
2. **文件类型限制**: 只允许配置中指定的文件类型
3. **权限验证**: 每个操作都会进行权限检查
4. **错误重试**: 建议在客户端实现重试机制
5. **超时设置**: 大文件上传需要适当增加超时时间
6. **存储清理**: 定期清理已删除的文件以释放存储空间

## 版本信息

- **接口版本**: v1.0.0
- **兼容性**: 支持所有 GalPHOS 微服务
- **更新日期**: 2025年6月29日
