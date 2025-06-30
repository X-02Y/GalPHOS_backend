# 答题提交服务API参考文档

**服务端口**: 3004  
**服务标识**: `submission`  
**基础URL**: `http://localhost:3004/api`

本文档提供答题提交服务的完整API接口参考，基于前端API文档和微服务路由架构。

## 服务职责

答题提交服务处理考试答题卡的提交和管理，包括：
- 学生自主答题提交
- 教练代理非独立学生提交
- 答题文件上传和管理
- 提交记录查询和状态跟踪
- 阅卷进度统计

## 权限说明

### 🔐 学生账号类型区分

系统中存在两种不同类型的学生账号：

#### 独立学生账号
- **特征**：学生自主注册的账号，拥有完整登录凭据
- **权限**：完全自主操作权限
- **API使用**：使用 `/api/student/*` 接口进行自主提交

#### 非独立学生账号  
- **特征**：教练添加的学生账号，无登录能力
- **权限**：仅限教练代理操作
- **API使用**：教练使用 `/api/coach/*` 接口代理操作

## API接口按角色分类

### 学生端API（独立学生账号）

#### 自主答题提交

**接口**: `POST /api/student/exams/{examId}/submit`

**描述**: 独立学生自主提交考试答案

**认证**: Bearer Token (student role)

**路径参数**:
- `examId` (string): 考试ID

**请求体**:
```json
{
  "answers": [
    {
      "questionNumber": 1,
      "imageUrl": "https://example.com/answer1.jpg",
      "uploadTime": "2024-03-20T10:30:00.000Z"
    },
    {
      "questionNumber": 2,
      "imageUrl": "https://example.com/answer2.jpg", 
      "uploadTime": "2024-03-20T10:35:00.000Z"
    }
  ]
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "id": "submission001",
    "examId": "exam001",
    "studentId": "student001",
    "studentUsername": "student001",
    "coachId": null,
    "isProxySubmission": false,
    "submissionTime": "2024-03-20T11:45:00.000Z",
    "status": "submitted",
    "totalScore": null,
    "maxScore": null,
    "feedback": null,
    "answers": [
      {
        "questionNumber": 1,
        "questionId": null,
        "answerText": null,
        "answerImageUrl": "https://example.com/answer1.jpg",
        "uploadTime": "2024-03-20T10:30:00.000Z",
        "score": null,
        "maxScore": null,
        "graderFeedback": null
      }
    ]
  },
  "message": "答案提交成功"
}
```

#### 获取提交记录

**接口**: `GET /api/student/exams/{examId}/submission`

**描述**: 获取当前学生在指定考试中的提交记录

**认证**: Bearer Token (student role)

**路径参数**:
- `examId` (string): 考试ID

**响应**:
```json
{
  "success": true,
  "data": {
    "id": "submission001",
    "examId": "exam001",
    "studentId": "student001",
    "studentUsername": "student001",
    "coachId": null,
    "isProxySubmission": false,
    "submissionTime": "2024-03-20T11:45:00.000Z",
    "status": "submitted",
    "totalScore": 85.5,
    "maxScore": 100.0,
    "feedback": "整体表现良好",
    "answers": [
      {
        "questionNumber": 1,
        "answerImageUrl": "https://example.com/answer1.jpg",
        "uploadTime": "2024-03-20T10:30:00.000Z",
        "score": 8.5,
        "maxScore": 10.0,
        "graderFeedback": "解答正确，步骤清晰"
      }
    ]
  },
  "message": "获取提交记录成功"
}
```

**无提交记录时**:
```json
{
  "success": true,
  "data": null,
  "message": "未找到提交记录"
}
```

---

### 教练端API（代理非独立学生）

#### 查看代管学生提交记录

**接口**: `GET /api/coach/exams/{examId}/submissions`

**描述**: 教练查看自己代管学生的提交记录

**认证**: Bearer Token (coach role)

**路径参数**:
- `examId` (string): 考试ID

**查询参数**:
- `studentUsername` (string, optional): 特定学生用户名，不提供则返回所有代管学生

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": "submission002",
      "examId": "exam001",
      "studentId": "managed_student001",
      "studentUsername": "managed_student001",
      "coachId": "coach001",
      "isProxySubmission": true,
      "submissionTime": "2024-03-20T14:30:00.000Z",
      "status": "submitted",
      "totalScore": null,
      "maxScore": null,
      "feedback": null,
      "answers": [
        {
          "questionNumber": 1,
          "answerImageUrl": "https://example.com/coach_answer1.jpg",
          "uploadTime": "2024-03-20T14:25:00.000Z",
          "score": null,
          "maxScore": null,
          "graderFeedback": null
        }
      ]
    }
  ],
  "message": "获取提交记录成功"
}
```

#### 代理非独立学生提交答卷

**接口**: `POST /api/coach/exams/{examId}/upload-answer`

**描述**: 教练代替非独立学生上传答案文件

**认证**: Bearer Token (coach role)

**路径参数**:
- `examId` (string): 考试ID

**请求**: FormData
- `file` (File): 答案文件（支持jpg、jpeg、png、pdf）
- `questionNumber` (number): 题目编号
- `studentUsername` (string): 被代理学生的用户名

**响应**:
```json
{
  "success": true,
  "data": {
    "imageUrl": "https://example.com/uploaded_answer.jpg"
  },
  "message": "上传成功"
}
```

**错误响应**:
```json
{
  "success": false,
  "message": "不支持的文件类型"
}
```

---

### 阅卷员API

#### 查看具体提交详情

**接口**: `GET /api/grader/submissions/{submissionId}`

**描述**: 阅卷员查看具体的提交详情用于阅卷

**认证**: Bearer Token (grader role)

**路径参数**:
- `submissionId` (string): 提交记录ID

**响应**:
```json
{
  "success": true,
  "data": {
    "id": "submission001",
    "examId": "exam001",
    "studentId": "student001",
    "studentUsername": "student001",
    "coachId": null,
    "isProxySubmission": false,
    "submissionTime": "2024-03-20T11:45:00.000Z",
    "status": "submitted",
    "totalScore": null,
    "maxScore": null,
    "feedback": null,
    "answers": [
      {
        "questionNumber": 1,
        "answerImageUrl": "https://example.com/answer1.jpg",
        "uploadTime": "2024-03-20T10:30:00.000Z",
        "score": null,
        "maxScore": null,
        "graderFeedback": null
      }
    ]
  },
  "message": "获取提交详情成功"
}
```

#### 查看阅卷进度

**接口**: `GET /api/grader/exams/{examId}/progress`

**描述**: 查看指定考试的阅卷进度统计

**认证**: Bearer Token (grader role)

**路径参数**:
- `examId` (string): 考试ID

**响应**:
```json
{
  "success": true,
  "data": {
    "examId": "exam001",
    "totalSubmissions": 150,
    "gradedSubmissions": 120,
    "averageScore": 78.5,
    "gradingStats": {}
  },
  "message": "获取阅卷进度成功"
}
```

---

## 数据模型

### ExamSubmission (考试提交记录)
```typescript
interface ExamSubmission {
  id: string;                    // 提交记录ID
  examId: string;                // 考试ID
  studentId: string;             // 学生ID
  studentUsername: string;       // 学生用户名
  coachId?: string;              // 教练ID（代理提交时）
  isProxySubmission: boolean;    // 是否为代理提交
  submissionTime: string;        // 提交时间
  status: "submitted" | "graded" | "cancelled";  // 提交状态
  totalScore?: number;           // 总分
  maxScore?: number;             // 满分
  feedback?: string;             // 总体反馈
  answers: SubmissionAnswer[];   // 答案列表
}
```

### SubmissionAnswer (提交答案)
```typescript
interface SubmissionAnswer {
  questionNumber: number;        // 题目编号
  questionId?: string;           // 题目ID
  answerText?: string;           // 文本答案
  answerImageUrl?: string;       // 答案图片URL
  uploadTime: string;            // 上传时间
  score?: number;                // 得分
  maxScore?: number;             // 满分
  graderFeedback?: string;       // 阅卷员反馈
}
```

### GradingProgress (阅卷进度)
```typescript
interface GradingProgress {
  examId: string;                // 考试ID
  totalSubmissions: number;      // 总提交数
  gradedSubmissions: number;     // 已阅卷数
  averageScore?: number;         // 平均分
  gradingStats: object;          // 其他统计信息
}
```

## 服务依赖

### 上游服务依赖
- **认证服务 (3001)**: `/api/auth/validate` - Token验证
- **考试服务 (3003)**: `/api/admin/exams/{examId}` - 考试信息和权限验证
- **文件存储服务 (3008)**: `/api/internal/upload` - 文件上传和存储

### 数据库依赖
- **PostgreSQL**: 共享galphos数据库
- 表：`exam_submissions`, `submission_answers`, `submission_files`

## 错误处理

### 统一错误响应格式
```json
{
  "success": false,
  "message": "错误描述信息"
}
```

### 常见错误场景

1. **认证相关错误**
   - `401`: Token无效或过期
   - `403`: 权限不足

2. **业务逻辑错误**
   - `400`: 考试未发布或已结束
   - `400`: 不支持的文件类型
   - `400`: 文件大小超出限制

3. **资源不存在**
   - `404`: 考试不存在
   - `404`: 提交记录不存在

4. **服务依赖错误**
   - `500`: 认证服务不可用
   - `500`: 考试服务不可用
   - `500`: 文件存储服务不可用

## 权限控制规则

### 学生权限
- ✅ 只能提交自己的答案
- ✅ 只能查看自己的提交记录
- ❌ 不能查看其他学生的提交
- ❌ 不能进行代理操作

### 教练权限
- ✅ 可以代理非独立学生提交答案
- ✅ 可以查看自己代管学生的提交记录
- ❌ 不能查看其他教练学生的提交
- ❌ 不能操作独立学生账号

### 阅卷员权限
- ✅ 可以查看所有提交记录详情
- ✅ 可以查看阅卷进度统计
- ❌ 不能修改提交内容
- ❌ 不能代理提交答案

## 业务流程

### 学生自主提交流程
1. 学生登录系统获取Token
2. 上传答案图片到文件存储服务
3. 调用提交接口，包含图片URL和题目信息
4. 系统验证考试状态和学生权限
5. 创建或更新提交记录
6. 返回提交结果

### 教练代理提交流程
1. 教练登录系统获取Token
2. 选择被代管的学生
3. 上传学生答案文件
4. 系统验证教练权限和学生关联关系
5. 创建代理提交记录
6. 标记为代理提交并记录教练信息

### 阅卷查看流程
1. 阅卷员登录系统获取Token
2. 查询指定考试的提交记录
3. 获取详细的答案内容和图片
4. 查看阅卷进度统计信息

## 健康检查

**接口**: `GET /health`

**描述**: 服务健康状态检查

**响应**: `OK` (HTTP 200)

## 版本信息

- **API版本**: v1.0.0
- **服务版本**: 1.0.0
- **最后更新**: 2024-06-30
- **兼容性**: 支持GalPHOS v1.2.0微服务架构

## 注意事项

1. **文件上传限制**
   - 最大文件大小：10MB
   - 支持格式：jpg, jpeg, png, pdf
   - 文件通过文件存储服务统一管理

2. **提交时间限制**
   - 只能在考试有效时间内提交
   - 考试状态必须为"published"

3. **代理权限验证**
   - 教练只能操作自己添加的非独立学生
   - 系统会验证教练与学生的关联关系

4. **并发控制**
   - 支持同一学生多次提交（更新模式）
   - 最新提交会覆盖之前的答案

5. **数据一致性**
   - 提交记录与答案详情保持事务一致性
   - 文件引用与提交记录关联

这个API参考文档提供了答题提交服务的完整接口定义，可以用于前端开发和服务集成。
