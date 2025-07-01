# 答题提交服务微服务集成示例

本文档提供与答题提交服务集成的示例代码，展示如何从其他微服务或客户端调用答题提交服务的API。

## 服务信息

- **服务名称**: SubmissionService (答题提交服务)
- **端口**: 3004
- **基础URL**: `http://localhost:3004`
- **服务标识**: `submission`

## 1. Node.js/Express 集成示例

### 依赖安装
```bash
npm install axios form-data
```

### 基础配置
```javascript
const axios = require('axios');
const FormData = require('form-data');

const SUBMISSION_SERVICE_URL = 'http://localhost:3004';

// 创建带认证的axios实例
const createAuthenticatedClient = (token) => {
  return axios.create({
    baseURL: SUBMISSION_SERVICE_URL,
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  });
};
```

### 学生自主提交答案
```javascript
// 学生提交答案示例
async function submitStudentAnswers(examId, answers, studentToken) {
  try {
    const client = createAuthenticatedClient(studentToken);
    
    const submitRequest = {
      answers: answers.map(answer => ({
        questionNumber: answer.questionNumber,
        imageUrl: answer.imageUrl,
        uploadTime: new Date().toISOString()
      }))
    };

    const response = await client.post(`/api/student/exams/${examId}/submit`, submitRequest);
    
    if (response.data.success) {
      console.log('学生提交成功:', response.data.data);
      return { success: true, submission: response.data.data };
    } else {
      console.error('学生提交失败:', response.data.message);
      return { success: false, error: response.data.message };
    }
  } catch (error) {
    console.error('学生提交请求失败:', error.message);
    return { success: false, error: error.message };
  }
}

// 使用示例
const studentAnswers = [
  {
    questionNumber: 1,
    imageUrl: 'https://example.com/student_answer1.jpg'
  },
  {
    questionNumber: 2,
    imageUrl: 'https://example.com/student_answer2.jpg'
  }
];

submitStudentAnswers('exam_123', studentAnswers, 'student_jwt_token')
  .then(result => console.log('提交结果:', result));
```

### 获取学生提交记录
```javascript
// 获取学生提交记录
async function getStudentSubmission(examId, studentToken) {
  try {
    const client = createAuthenticatedClient(studentToken);
    const response = await client.get(`/api/student/exams/${examId}/submission`);
    
    if (response.data.success) {
      return { success: true, submission: response.data.data };
    } else {
      return { success: false, error: response.data.message };
    }
  } catch (error) {
    console.error('获取提交记录失败:', error.message);
    return { success: false, error: error.message };
  }
}

// 使用示例
getStudentSubmission('exam_123', 'student_jwt_token')
  .then(result => {
    if (result.success && result.submission) {
      console.log('找到提交记录:', result.submission);
    } else {
      console.log('未找到提交记录或出错:', result.error);
    }
  });
```

### 教练代理文件上传
```javascript
// 教练代理上传文件
async function coachUploadAnswer(examId, file, questionNumber, studentUsername, coachToken) {
  try {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('questionNumber', questionNumber.toString());
    formData.append('studentUsername', studentUsername);

    const response = await axios.post(
      `${SUBMISSION_SERVICE_URL}/api/coach/exams/${examId}/upload-answer`,
      formData,
      {
        headers: {
          'Authorization': `Bearer ${coachToken}`,
          ...formData.getHeaders()
        }
      }
    );

    if (response.data.success) {
      console.log('教练上传成功:', response.data.data.imageUrl);
      return { success: true, imageUrl: response.data.data.imageUrl };
    } else {
      return { success: false, error: response.data.message };
    }
  } catch (error) {
    console.error('教练上传失败:', error.message);
    return { success: false, error: error.message };
  }
}

// 使用示例（需要文件对象）
const fs = require('fs');
const fileBuffer = fs.readFileSync('./student_answer.jpg');
coachUploadAnswer('exam_123', fileBuffer, 1, 'managed_student001', 'coach_jwt_token')
  .then(result => console.log('上传结果:', result));
```

### 教练查看提交记录
```javascript
// 教练查看管理学生提交记录
async function getCoachSubmissions(examId, studentUsername, coachToken) {
  try {
    const client = createAuthenticatedClient(coachToken);
    
    let url = `/api/coach/exams/${examId}/submissions`;
    if (studentUsername) {
      url += `?studentUsername=${encodeURIComponent(studentUsername)}`;
    }
    
    const response = await client.get(url);
    
    if (response.data.success) {
      return { success: true, submissions: response.data.data };
    } else {
      return { success: false, error: response.data.message };
    }
  } catch (error) {
    console.error('获取教练提交记录失败:', error.message);
    return { success: false, error: error.message };
  }
}

// 使用示例
getCoachSubmissions('exam_123', null, 'coach_jwt_token') // 获取所有代管学生
  .then(result => {
    if (result.success) {
      console.log(`找到 ${result.submissions.length} 条提交记录`);
      result.submissions.forEach(submission => {
        console.log(`学生 ${submission.studentUsername}: ${submission.status}`);
      });
    }
  });
```

## 2. Python Flask 集成示例

### 依赖安装
```bash
pip install requests
```

### 基础配置
```python
import requests
import json
from datetime import datetime

SUBMISSION_SERVICE_URL = 'http://localhost:3004'

class SubmissionServiceClient:
    def __init__(self, token):
        self.token = token
        self.headers = {
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        }
```

### 学生提交答案
```python
def submit_student_answers(self, exam_id, answers):
    """学生提交答案"""
    url = f'{SUBMISSION_SERVICE_URL}/api/student/exams/{exam_id}/submit'
    
    submit_request = {
        'answers': [
            {
                'questionNumber': answer['questionNumber'],
                'imageUrl': answer['imageUrl'],
                'uploadTime': datetime.utcnow().isoformat() + 'Z'
            }
            for answer in answers
        ]
    }
    
    try:
        response = requests.post(url, headers=self.headers, json=submit_request)
        result = response.json()
        
        if result.get('success'):
            return {'success': True, 'submission': result['data']}
        else:
            return {'success': False, 'error': result.get('message', '提交失败')}
    except Exception as e:
        return {'success': False, 'error': str(e)}

# 使用示例
student_client = SubmissionServiceClient('student_jwt_token')
answers = [
    {'questionNumber': 1, 'imageUrl': 'https://example.com/answer1.jpg'},
    {'questionNumber': 2, 'imageUrl': 'https://example.com/answer2.jpg'}
]

result = student_client.submit_student_answers('exam_123', answers)
print('提交结果:', result)
```

### 阅卷员查看进度
```python
def get_grading_progress(self, exam_id):
    """获取阅卷进度"""
    url = f'{SUBMISSION_SERVICE_URL}/api/grader/exams/{exam_id}/progress'
    
    try:
        response = requests.get(url, headers=self.headers)
        result = response.json()
        
        if result.get('success'):
            return {'success': True, 'progress': result['data']}
        else:
            return {'success': False, 'error': result.get('message', '获取进度失败')}
    except Exception as e:
        return {'success': False, 'error': str(e)}

# 使用示例
grader_client = SubmissionServiceClient('grader_jwt_token')
progress = grader_client.get_grading_progress('exam_123')

if progress['success']:
    data = progress['progress']
    print(f"考试 {data['examId']}:")
    print(f"  总提交数: {data['totalSubmissions']}")
    print(f"  已阅卷数: {data['gradedSubmissions']}")
    print(f"  平均分: {data.get('averageScore', 'N/A')}")
```

## 3. React/TypeScript 前端集成示例

### 类型定义
```typescript
// 提交相关类型定义
interface SubmissionAnswer {
  questionNumber: number;
  imageUrl: string;
  uploadTime: string;
}

interface StudentSubmitRequest {
  answers: SubmissionAnswer[];
}

interface ExamSubmission {
  id: string;
  examId: string;
  studentId: string;
  studentUsername: string;
  coachId?: string;
  isProxySubmission: boolean;
  submissionTime: string;
  status: 'submitted' | 'graded' | 'cancelled';
  totalScore?: number;
  maxScore?: number;
  feedback?: string;
  answers: SubmissionAnswerDetail[];
}

interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
}
```

### API客户端类
```typescript
class SubmissionAPI {
  private baseURL = 'http://localhost:3004/api';
  
  private async request<T>(
    endpoint: string, 
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    const token = localStorage.getItem('authToken');
    
    const response = await fetch(`${this.baseURL}${endpoint}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        ...options.headers,
      },
    });
    
    return response.json();
  }

  // 学生提交答案
  async submitStudentAnswers(
    examId: string, 
    request: StudentSubmitRequest
  ): Promise<ApiResponse<ExamSubmission>> {
    return this.request(`/student/exams/${examId}/submit`, {
      method: 'POST',
      body: JSON.stringify(request),
    });
  }

  // 获取学生提交记录
  async getStudentSubmission(
    examId: string
  ): Promise<ApiResponse<ExamSubmission | null>> {
    return this.request(`/student/exams/${examId}/submission`);
  }

  // 教练查看提交记录
  async getCoachSubmissions(
    examId: string,
    studentUsername?: string
  ): Promise<ApiResponse<ExamSubmission[]>> {
    const query = studentUsername ? `?studentUsername=${studentUsername}` : '';
    return this.request(`/coach/exams/${examId}/submissions${query}`);
  }

  // 教练上传文件
  async coachUploadAnswer(
    examId: string,
    file: File,
    questionNumber: number,
    studentUsername: string
  ): Promise<ApiResponse<{ imageUrl: string }>> {
    const token = localStorage.getItem('authToken');
    const formData = new FormData();
    formData.append('file', file);
    formData.append('questionNumber', questionNumber.toString());
    formData.append('studentUsername', studentUsername);

    const response = await fetch(`${this.baseURL}/coach/exams/${examId}/upload-answer`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
      },
      body: formData,
    });

    return response.json();
  }
}
```

### React Hook使用示例
```typescript
// 自定义Hook
import { useState, useEffect } from 'react';

export const useSubmission = (examId: string) => {
  const [submission, setSubmission] = useState<ExamSubmission | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  
  const submissionAPI = new SubmissionAPI();

  const loadSubmission = async () => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await submissionAPI.getStudentSubmission(examId);
      if (response.success) {
        setSubmission(response.data || null);
      } else {
        setError(response.message || '获取提交记录失败');
      }
    } catch (err) {
      setError('网络错误');
    } finally {
      setLoading(false);
    }
  };

  const submitAnswers = async (answers: SubmissionAnswer[]) => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await submissionAPI.submitStudentAnswers(examId, { answers });
      if (response.success) {
        setSubmission(response.data!);
        return { success: true };
      } else {
        setError(response.message || '提交失败');
        return { success: false, error: response.message };
      }
    } catch (err) {
      const error = '网络错误';
      setError(error);
      return { success: false, error };
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (examId) {
      loadSubmission();
    }
  }, [examId]);

  return {
    submission,
    loading,
    error,
    submitAnswers,
    reload: loadSubmission
  };
};

// 组件中使用
const ExamSubmissionPage: React.FC<{ examId: string }> = ({ examId }) => {
  const { submission, loading, error, submitAnswers } = useSubmission(examId);

  const handleSubmit = async (answers: SubmissionAnswer[]) => {
    const result = await submitAnswers(answers);
    if (result.success) {
      message.success('提交成功！');
    } else {
      message.error(result.error || '提交失败');
    }
  };

  if (loading) return <div>加载中...</div>;
  if (error) return <div>错误: {error}</div>;

  return (
    <div>
      {submission ? (
        <div>已提交，状态: {submission.status}</div>
      ) : (
        <div>尚未提交</div>
      )}
    </div>
  );
};
```

## 4. 错误处理最佳实践

### 统一错误处理
```javascript
// 创建统一的错误处理函数
function handleSubmissionError(error, context) {
  console.error(`${context}失败:`, error);
  
  if (error.response) {
    // 服务器返回错误响应
    const { status, data } = error.response;
    
    switch (status) {
      case 401:
        return '认证失败，请重新登录';
      case 403:
        return '权限不足，无法执行此操作';
      case 404:
        return '资源不存在';
      case 400:
        return data.message || '请求参数错误';
      case 500:
        return '服务器内部错误，请稍后重试';
      default:
        return `请求失败 (${status})`;
    }
  } else if (error.request) {
    // 网络错误
    return '网络连接失败，请检查网络设置';
  } else {
    // 其他错误
    return error.message || '未知错误';
  }
}

// 使用示例
try {
  await submitStudentAnswers(examId, answers, token);
} catch (error) {
  const errorMessage = handleSubmissionError(error, '提交答案');
  showNotification(errorMessage, 'error');
}
```

### 重试机制
```javascript
// 实现重试机制
async function submitWithRetry(submitFunction, maxRetries = 3, delay = 1000) {
  let lastError;
  
  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      return await submitFunction();
    } catch (error) {
      lastError = error;
      
      if (attempt < maxRetries) {
        console.log(`提交失败，${delay}ms后进行第${attempt + 1}次重试...`);
        await new Promise(resolve => setTimeout(resolve, delay));
        delay *= 2; // 指数退避
      }
    }
  }
  
  throw lastError;
}

// 使用示例
try {
  const result = await submitWithRetry(
    () => submitStudentAnswers(examId, answers, token),
    3,  // 最多重试3次
    1000 // 初始延迟1秒
  );
  console.log('提交成功:', result);
} catch (error) {
  console.error('重试后仍然失败:', error);
}
```

## 5. 监控和日志

### 客户端日志记录
```javascript
// 创建日志记录器
class SubmissionLogger {
  static log(level, message, data = {}) {
    const logEntry = {
      timestamp: new Date().toISOString(),
      level,
      message,
      service: 'submission-service',
      ...data
    };
    
    console.log(`[${level.toUpperCase()}]`, message, data);
    
    // 可以发送到日志收集服务
    // sendToLogService(logEntry);
  }
  
  static info(message, data) {
    this.log('info', message, data);
  }
  
  static error(message, data) {
    this.log('error', message, data);
  }
  
  static warn(message, data) {
    this.log('warn', message, data);
  }
}

// 使用示例
SubmissionLogger.info('开始提交学生答案', { examId, studentId });

try {
  const result = await submitStudentAnswers(examId, answers, token);
  SubmissionLogger.info('学生答案提交成功', { examId, submissionId: result.submission.id });
} catch (error) {
  SubmissionLogger.error('学生答案提交失败', { examId, error: error.message });
}
```

## 总结

这些集成示例展示了如何从不同的技术栈和平台调用答题提交服务的API。关键要点：

1. **认证**: 所有API调用都需要有效的JWT Token
2. **错误处理**: 实现统一的错误处理和重试机制
3. **文件上传**: 使用FormData处理文件上传，注意Content-Type设置
4. **权限控制**: 确保使用正确的角色Token调用对应的API
5. **监控**: 实现适当的日志记录和错误追踪

根据具体的业务需求和技术栈，可以基于这些示例进行适当的调整和扩展。
