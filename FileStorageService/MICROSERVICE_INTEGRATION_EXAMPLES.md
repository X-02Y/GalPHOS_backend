# 其他微服务集成FileStorageService示例

## 1. ExamManagementService (端口3003) 集成示例

### Scala 实现

```scala
// ExamManagementService 中的文件上传处理
package services

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.{ExecutionContext, Future}
import Utils.FileStorageServiceClient

class ExamService(implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext) {
  
  private val fileStorageClient = new FileStorageServiceClient("http://localhost:3008")
  
  // 处理前端考试创建请求
  def createExam(examData: CreateExamRequest, adminUserId: String): Future[Either[String, Exam]] = {
    for {
      // 上传试题文件
      questionFileResult <- examData.questionFile.map { file =>
        fileStorageClient.uploadFile(
          originalName = file.originalName,
          fileContent = file.content,
          fileType = extractFileExtension(file.originalName),
          mimeType = file.mimeType,
          uploadUserId = Some(adminUserId),
          uploadUserType = Some("admin"),
          examId = Some(examData.examId),
          description = Some("考试试题文件"),
          category = "exam"
        )
      }.getOrElse(Future.successful(Right(null)))
      
      // 上传答案文件
      answerFileResult <- examData.answerFile.map { file =>
        fileStorageClient.uploadFile(
          originalName = file.originalName,
          fileContent = file.content,
          fileType = extractFileExtension(file.originalName),
          mimeType = file.mimeType,
          uploadUserId = Some(adminUserId),
          uploadUserType = Some("admin"),
          examId = Some(examData.examId),
          description = Some("考试答案文件"),
          category = "exam"
        )
      }.getOrElse(Future.successful(Right(null)))
      
      // 上传答题卡模板
      answerSheetResult <- examData.answerSheetFile.map { file =>
        fileStorageClient.uploadFile(
          originalName = file.originalName,
          fileContent = file.content,
          fileType = extractFileExtension(file.originalName),
          mimeType = file.mimeType,
          uploadUserId = Some(adminUserId),
          uploadUserType = Some("admin"),
          examId = Some(examData.examId),
          description = Some("答题卡模板"),
          category = "exam"
        )
      }.getOrElse(Future.successful(Right(null)))
      
    } yield {
      // 检查所有文件上传是否成功
      val allResults = List(questionFileResult, answerFileResult, answerSheetResult).filter(_ != null)
      val failures = allResults.filter(_.isLeft)
      
      if (failures.nonEmpty) {
        Left(s"文件上传失败: ${failures.map(_.left.get).mkString(", ")}")
      } else {
        // 创建考试记录
        val exam = Exam(
          id = examData.examId,
          title = examData.title,
          description = examData.description,
          questionFileId = questionFileResult.toOption.flatMap(_.fileId),
          answerFileId = answerFileResult.toOption.flatMap(_.fileId),
          answerSheetFileId = answerSheetResult.toOption.flatMap(_.fileId),
          // ... 其他字段
        )
        
        Right(exam)
      }
    }
  }
  
  // 处理考试文件下载
  def getExamFile(fileId: String, requestUserId: String, requestUserType: String): Future[Either[String, FileContent]] = {
    fileStorageClient.downloadFile(fileId, Some(requestUserId), Some(requestUserType), "download").map {
      case Right(response) =>
        Right(FileContent(
          originalName = response.originalName.get,
          content = response.fileContent.get,
          mimeType = response.mimeType.get
        ))
      case Left(error) => Left(error)
    }
  }
}
```

### 路由配置

```scala
// ExamManagementService 的路由
val examRoutes: Route = {
  pathPrefix("api") {
    concat(
      // 管理员创建考试 (前端上传文件会在这里处理)
      path("admin" / "exams") {
        post {
          // 接收多部分表单数据
          fileUpload("questionFile") { (fileInfo, fileSource) =>
            fileUpload("answerFile") { (answerInfo, answerSource) =>
              formField("examData") { examDataJson =>
                extractExecutionContext { implicit ec =>
                  // 读取文件内容
                  val questionContent = fileSource.runFold(ByteString.empty)(_ ++ _)
                  val answerContent = answerSource.runFold(ByteString.empty)(_ ++ _)
                  
                  onComplete(for {
                    qContent <- questionContent
                    aContent <- answerContent
                    examData = parseExamData(examDataJson)
                    result <- examService.createExam(
                      CreateExamRequest(
                        examId = UUID.randomUUID().toString,
                        title = examData.title,
                        description = examData.description,
                        questionFile = Some(FileUpload(fileInfo.fileName, qContent.toArray, fileInfo.contentType.toString)),
                        answerFile = Some(FileUpload(answerInfo.fileName, aContent.toArray, answerInfo.contentType.toString))
                      ),
                      extractUserId()
                    )
                  } yield result) {
                    case Success(Right(exam)) =>
                      complete(StatusCodes.Created, exam)
                    case Success(Left(error)) =>
                      complete(StatusCodes.BadRequest, ErrorResponse(error))
                    case Failure(ex) =>
                      complete(StatusCodes.InternalServerError, ErrorResponse(ex.getMessage))
                  }
                }
              }
            }
          }
        }
      }
    )
  }
}
```

## 2. SubmissionService (端口3004) 集成示例

### Node.js 实现

```javascript
// SubmissionService 中的答题卡上传处理
const express = require('express');
const multer = require('multer');
const FileStorageServiceClient = require('./utils/FileStorageServiceClient');

const app = express();
const upload = multer({ storage: multer.memoryStorage() });
const fileClient = new FileStorageServiceClient('http://localhost:3008');

// 学生答题卡提交
app.post('/api/student/exams/:examId/submit', upload.single('answerSheet'), async (req, res) => {
  try {
    const { examId } = req.params;
    const userId = req.user.id; // 从JWT中提取
    const file = req.file;
    
    if (!file) {
      return res.status(400).json({ error: '请上传答题卡文件' });
    }
    
    // 验证文件类型
    const allowedTypes = ['image/jpeg', 'image/png', 'application/pdf'];
    if (!allowedTypes.includes(file.mimetype)) {
      return res.status(400).json({ error: '不支持的文件类型' });
    }
    
    // 上传文件到FileStorageService
    const uploadResult = await fileClient.uploadFile({
      originalName: file.originalname,
      fileContent: Array.from(file.buffer),
      fileType: path.extname(file.originalname).slice(1),
      mimeType: file.mimetype,
      uploadUserId: userId,
      uploadUserType: 'student',
      examId: examId,
      submissionId: null, // 稍后生成
      description: '学生答题卡',
      category: 'submission'
    });
    
    if (!uploadResult.success) {
      return res.status(500).json({ error: uploadResult.error });
    }
    
    // 创建提交记录
    const submission = await createSubmission({
      examId,
      studentId: userId,
      answerSheetFileId: uploadResult.data.fileId,
      submissionTime: new Date(),
      status: 'submitted'
    });
    
    // 更新文件记录中的submissionId
    // 这里可以调用内部接口更新文件元数据...
    
    res.json({
      success: true,
      submission: {
        id: submission.id,
        examId: submission.examId,
        submissionTime: submission.submissionTime,
        fileId: uploadResult.data.fileId,
        fileName: uploadResult.data.originalName,
        status: submission.status
      }
    });
    
  } catch (error) {
    console.error('答题卡提交失败:', error);
    res.status(500).json({ error: '提交失败，请重试' });
  }
});

// 教练代理学生提交
app.post('/api/coach/exams/:examId/upload-answer', upload.single('answerSheet'), async (req, res) => {
  try {
    const { examId } = req.params;
    const { studentId } = req.body;
    const coachId = req.user.id;
    const file = req.file;
    
    // 验证教练权限：检查学生是否属于该教练
    const hasPermission = await verifyCoachStudentRelation(coachId, studentId);
    if (!hasPermission) {
      return res.status(403).json({ error: '无权限代理该学生提交' });
    }
    
    // 上传文件
    const uploadResult = await fileClient.uploadFile({
      originalName: file.originalname,
      fileContent: Array.from(file.buffer),
      fileType: path.extname(file.originalname).slice(1),
      mimeType: file.mimetype,
      uploadUserId: coachId, // 上传者是教练
      uploadUserType: 'coach',
      examId: examId,
      description: `教练代理学生${studentId}提交答题卡`,
      category: 'submission'
    });
    
    if (!uploadResult.success) {
      return res.status(500).json({ error: uploadResult.error });
    }
    
    // 创建代理提交记录
    const submission = await createProxySubmission({
      examId,
      studentId,
      coachId,
      answerSheetFileId: uploadResult.data.fileId,
      submissionTime: new Date(),
      status: 'submitted',
      isProxySubmission: true
    });
    
    res.json({
      success: true,
      message: '代理提交成功',
      submission
    });
    
  } catch (error) {
    console.error('代理提交失败:', error);
    res.status(500).json({ error: '代理提交失败，请重试' });
  }
});
```

## 3. UserManagementService (端口3002) 集成示例

### Python Flask 实现

```python
# UserManagementService 中的头像上传处理
from flask import Flask, request, jsonify
import requests
import base64

app = Flask(__name__)
FILE_STORAGE_SERVICE_URL = 'http://localhost:3008'

class FileStorageServiceClient:
    def __init__(self, base_url):
        self.base_url = base_url
    
    def upload_file(self, upload_request):
        try:
            response = requests.post(
                f'{self.base_url}/internal/upload',
                json=upload_request,
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            return response.json()
        except Exception as e:
            return {'success': False, 'error': str(e)}
    
    def delete_file(self, file_id, user_id, user_type, reason=None):
        try:
            response = requests.post(
                f'{self.base_url}/internal/delete',
                json={
                    'fileId': file_id,
                    'requestUserId': user_id,
                    'requestUserType': user_type,
                    'reason': reason
                },
                headers={'Content-Type': 'application/json'},
                timeout=30
            )
            return response.json()
        except Exception as e:
            return {'success': False, 'error': str(e)}

file_client = FileStorageServiceClient(FILE_STORAGE_SERVICE_URL)

@app.route('/api/student/profile', methods=['PUT'])
def update_student_profile():
    try:
        user_id = get_current_user_id()  # 从JWT中提取
        data = request.json
        
        # 处理头像上传
        if 'avatar' in data and data['avatar']:
            # 假设前端发送base64编码的图片
            avatar_data = data['avatar']
            if avatar_data.startswith('data:image/'):
                # 解析base64数据
                header, encoded = avatar_data.split(',', 1)
                file_content = base64.b64decode(encoded)
                
                # 确定文件类型
                if 'jpeg' in header:
                    file_type, mime_type = 'jpg', 'image/jpeg'
                elif 'png' in header:
                    file_type, mime_type = 'png', 'image/png'
                else:
                    return jsonify({'error': '不支持的图片格式'}), 400
                
                # 删除旧头像
                old_avatar_id = get_user_avatar_file_id(user_id)
                if old_avatar_id:
                    file_client.delete_file(
                        old_avatar_id, user_id, 'student', '更新头像，删除旧文件'
                    )
                
                # 上传新头像
                upload_result = file_client.upload_file({
                    'originalName': f'avatar_{user_id}.{file_type}',
                    'fileContent': list(file_content),
                    'fileType': file_type,
                    'mimeType': mime_type,
                    'uploadUserId': user_id,
                    'uploadUserType': 'student',
                    'examId': None,
                    'submissionId': None,
                    'description': '用户头像',
                    'category': 'avatar'
                })
                
                if not upload_result['success']:
                    return jsonify({'error': upload_result['error']}), 500
                
                # 更新用户记录
                update_user_avatar(user_id, upload_result['fileId'])
        
        # 更新其他个人信息
        update_user_profile(user_id, data)
        
        return jsonify({'success': True, 'message': '个人资料更新成功'})
        
    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/api/coach/profile', methods=['PUT'])
def update_coach_profile():
    # 类似的实现，但用户类型为 'coach'
    pass
```

## 4. GradingService (端口3005) 集成示例

### Go 实现

```go
// GradingService 中的分数导入文件处理
package main

import (
    "bytes"
    "encoding/json"
    "fmt"
    "io"
    "net/http"
    "path/filepath"
    "time"
)

type FileStorageClient struct {
    BaseURL string
    Client  *http.Client
}

type InternalUploadRequest struct {
    OriginalName   string  `json:"originalName"`
    FileContent    []byte  `json:"fileContent"`
    FileType       string  `json:"fileType"`
    MimeType       string  `json:"mimeType"`
    UploadUserId   *string `json:"uploadUserId"`
    UploadUserType *string `json:"uploadUserType"`
    ExamId         *string `json:"examId"`
    SubmissionId   *string `json:"submissionId"`
    Description    *string `json:"description"`
    Category       string  `json:"category"`
}

type InternalUploadResponse struct {
    Success    bool      `json:"success"`
    FileId     *string   `json:"fileId"`
    OriginalName *string `json:"originalName"`
    FileSize   *int64    `json:"fileSize"`
    UploadTime *string   `json:"uploadTime"`
    Error      *string   `json:"error"`
}

func NewFileStorageClient(baseURL string) *FileStorageClient {
    return &FileStorageClient{
        BaseURL: baseURL,
        Client:  &http.Client{Timeout: 30 * time.Second},
    }
}

func (c *FileStorageClient) UploadFile(req InternalUploadRequest) (*InternalUploadResponse, error) {
    jsonData, err := json.Marshal(req)
    if err != nil {
        return nil, err
    }
    
    resp, err := c.Client.Post(
        c.BaseURL+"/internal/upload",
        "application/json",
        bytes.NewBuffer(jsonData),
    )
    if err != nil {
        return nil, err
    }
    defer resp.Body.Close()
    
    var result InternalUploadResponse
    if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
        return nil, err
    }
    
    return &result, nil
}

// 处理题目分数导入
func handleScoreImport(w http.ResponseWriter, r *http.Request) {
    examId := getExamIdFromPath(r.URL.Path)
    userId := getUserIdFromJWT(r)
    
    // 解析多部分表单
    err := r.ParseMultipartForm(10 << 20) // 10MB
    if err != nil {
        http.Error(w, "文件过大", http.StatusBadRequest)
        return
    }
    
    file, handler, err := r.FormFile("scoreFile")
    if err != nil {
        http.Error(w, "请选择文件", http.StatusBadRequest)
        return
    }
    defer file.Close()
    
    // 验证文件类型
    ext := filepath.Ext(handler.Filename)
    if ext != ".xlsx" && ext != ".csv" {
        http.Error(w, "只支持Excel和CSV文件", http.StatusBadRequest)
        return
    }
    
    // 读取文件内容
    fileContent, err := io.ReadAll(file)
    if err != nil {
        http.Error(w, "读取文件失败", http.StatusInternalServerError)
        return
    }
    
    // 上传到FileStorageService
    fileClient := NewFileStorageClient("http://localhost:3008")
    userIdStr := userId
    userType := "admin"
    examIdStr := examId
    description := "题目分数导入文件"
    
    uploadReq := InternalUploadRequest{
        OriginalName:   handler.Filename,
        FileContent:    fileContent,
        FileType:       ext[1:], // 去掉点号
        MimeType:       handler.Header.Get("Content-Type"),
        UploadUserId:   &userIdStr,
        UploadUserType: &userType,
        ExamId:         &examIdStr,
        Description:    &description,
        Category:       "score_import",
    }
    
    uploadResp, err := fileClient.UploadFile(uploadReq)
    if err != nil {
        http.Error(w, "文件上传失败: "+err.Error(), http.StatusInternalServerError)
        return
    }
    
    if !uploadResp.Success {
        http.Error(w, "文件上传失败: "+*uploadResp.Error, http.StatusInternalServerError)
        return
    }
    
    // 解析和导入分数数据
    scores, err := parseScoreFile(fileContent, ext)
    if err != nil {
        http.Error(w, "文件格式错误: "+err.Error(), http.StatusBadRequest)
        return
    }
    
    // 批量更新题目分数
    err = updateQuestionScores(examId, scores)
    if err != nil {
        http.Error(w, "分数更新失败: "+err.Error(), http.StatusInternalServerError)
        return
    }
    
    // 返回成功响应
    response := map[string]interface{}{
        "success": true,
        "message": "分数导入成功",
        "fileId":  *uploadResp.FileId,
        "importedCount": len(scores),
    }
    
    w.Header().Set("Content-Type", "application/json")
    json.NewEncoder(w).Encode(response)
}
```

## 配置说明

### 环境变量配置

各微服务需要配置 FileStorageService 的地址：

```bash
# .env 文件
FILE_STORAGE_SERVICE_URL=http://localhost:3008

# 或在 Docker Compose 中
services:
  exam-management:
    environment:
      - FILE_STORAGE_SERVICE_URL=http://file-storage:3008
  
  submission-service:
    environment:
      - FILE_STORAGE_SERVICE_URL=http://file-storage:3008
```

### 错误处理最佳实践

```javascript
// 统一的错误处理
class FileStorageError extends Error {
  constructor(message, code, details) {
    super(message);
    this.code = code;
    this.details = details;
  }
}

async function safeFileOperation(operation) {
  try {
    const result = await operation();
    return { success: true, data: result };
  } catch (error) {
    console.error('File operation failed:', error);
    
    if (error.code === 'ECONNREFUSED') {
      return { success: false, error: 'FileStorageService unavailable' };
    } else if (error.code === 'TIMEOUT') {
      return { success: false, error: 'Operation timeout' };
    } else {
      return { success: false, error: error.message };
    }
  }
}
```

这个集成方案确保了：

1. **职责分离**: 前端文件上传请求由相应的业务微服务处理
2. **内部通信**: 业务微服务通过内部接口与FileStorageService通信
3. **权限控制**: 每个操作都有适当的权限验证
4. **错误处理**: 完善的错误处理和重试机制
5. **扩展性**: 支持多种编程语言和框架的微服务
