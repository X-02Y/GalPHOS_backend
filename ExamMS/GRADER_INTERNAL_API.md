# Grader Internal API Documentation

This document describes the internal API endpoints for the grader service in the ExamMS (Exam Management Service).

## Authentication
All internal API endpoints require an internal API key to be provided in the request headers.

### Headers Required:
- `X-Internal-API-Key`: `your-internal-api-key-here`

---

## Endpoints

### 1. Get Grader Exam Information

**Endpoint**: `GET http://localhost:3003/api/internal/grader/exams`

**Description**: Retrieves exam information for graders, including published exams with their participating students and question counts. This endpoint returns only exams that have been published and includes the list of students who have submitted answers for each exam.

**Query Parameters**: None

**Headers**:
- `X-Internal-API-Key` (string, required): Internal API key for authentication
- `Content-Type`: `application/json`

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    examId: string,        // Unique identifier for the exam
    studentIds: string[],  // Array of student usernames who joined this exam
    totalQuestions: number // Total number of questions in the exam
  }>,
  message: string
}
```

**Example Request**:
```http
GET /api/internal/grader/exams HTTP/1.1
Host: localhost:3003
X-Internal-API-Key: your-internal-api-key-here
Content-Type: application/json
```

**Example Response**:
```json
{
  "success": true,
  "data": [
    {
      "examId": "exam_001",
      "studentIds": ["student1", "student2", "student3"],
      "totalQuestions": 25
    },
    {
      "examId": "exam_002", 
      "studentIds": ["student2", "student4", "student5"],
      "totalQuestions": 30
    },
    {
      "examId": "exam_003",
      "studentIds": [],
      "totalQuestions": 20
    }
  ],
  "message": "评卷员考试信息获取成功"
}
```

**Error Responses**:

- **403 Forbidden** - Invalid or missing API key:
```json
{
  "success": false,
  "data": null,
  "message": "无效的内部API密钥"
}
```

- **403 Forbidden** - Missing API key:
```json
{
  "success": false,
  "data": null,
  "message": "缺少内部API密钥"
}
```

- **500 Internal Server Error** - Server error:
```json
{
  "success": false,
  "data": null,
  "message": "获取评卷员考试信息失败"
}
```

---

### 2. View Grading Images

**Endpoint**: `GET http://localhost:3003/api/grader/images`

**Description**: Get images for grading interface (mentioned in microservice routing). This endpoint retrieves answer images submitted by students for grading purposes.

**Query Parameters**:
- `examId` (string, required): Exam ID
- `studentId` (string, optional): Specific student ID to filter images
- `questionNumber` (number, optional): Specific question number to filter images

**Headers**:
- `X-Internal-API-Key` (string, required): Internal API key for authentication
- `Content-Type`: `application/json`

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    imageUrl: string,      // URL to access the image
    fileName: string,      // Original filename of the uploaded image
    examId: string,        // Exam identifier
    studentId: string,     // Student identifier who submitted the image
    questionNumber: number, // Question number this image answers
    uploadTime: string     // ISO timestamp when image was uploaded
  }>,
  message: string
}
```

**Example Request**:
```http
GET localhost:3008/api/grader/images?examId=exam_001&studentId=student1 HTTP/1.1
Host: localhost:3008
X-Internal-API-Key: your-internal-api-key-here
Content-Type: application/json
```

**Example Response**:
```json
{
  "success": true,
  "data": [
    {
      "imageUrl": "http://localhost:3008/images/exam_001_student1_q1.jpg",
      "fileName": "answer_question_1.jpg",
      "examId": "exam_001",
      "studentId": "student1",
      "questionNumber": 1,
      "uploadTime": "2025-07-08T13:45:30Z"
    },
    {
      "imageUrl": "http://localhost:3008/images/exam_001_student1_q2.jpg", 
      "fileName": "answer_question_2.jpg",
      "examId": "exam_001",
      "studentId": "student1",
      "questionNumber": 2,
      "uploadTime": "2025-07-08T13:47:15Z"
    }
  ],
  "message": "获取评分图片成功"
}
```

**Error Responses**:

- **403 Forbidden** - Invalid or missing API key:
```json
{
  "success": false,
  "data": null,
  "message": "无效的内部API密钥"
}
```

- **400 Bad Request** - Missing required examId parameter:
```json
{
  "success": false,
  "data": null,
  "message": "缺少考试ID参数"
}
```

- **500 Internal Server Error** - Server error:
```json
{
  "success": false,
  "data": null,
  "message": "获取评分图片失败"
}
```

---

## Notes

1. **Exam Status Filter**: Only exams with status "Published" are included in the response.

2. **Student Participation**: The `studentIds` array contains usernames of students who have submitted answers for the exam. If no students have submitted answers yet, the array will be empty.

3. **Total Questions**: The `totalQuestions` field represents the total number of questions configured for the exam. If no questions are configured, this will be 0.

4. **Image Access**: The grading images endpoint (localhost:3008) is served by a different microservice, likely the FileStorageService or a dedicated image service.

5. **Security**: This is an internal API meant for service-to-service communication. The API key should be kept secure and not exposed to client applications.

6. **Rate Limiting**: Consider implementing rate limiting for production use to prevent abuse.

## Database Dependencies

This API relies on the following database tables:
- `exams` - Contains exam information and status
- `exam_submissions` - Contains student submission records  
- `exam_answers` - Contains answer image information
- The API joins these tables to determine which students have participated in each exam

## Service Dependencies

- **ExamService**: Used to retrieve exam and submission data
- **FileStorageService**: Handles image storage and retrieval (port 3008)
- **Database**: PostgreSQL database for data persistence
