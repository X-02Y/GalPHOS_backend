# SubmissionService API Requirements

## Overview
Based on the frontend documentation analysis, the SubmissionService (port 3004) is responsible for answer submission and management functionalities. This document extracts all APIs that need to be implemented in the SubmissionService module.

## Service Responsibility
The SubmissionService handles:
- **Independent Student Account**: Self-submission of answers and submission status management
- **Non-Independent Student Account**: Coach proxy submission management with strict permission control
- **Submission Status Tracking**: Recording and querying submission states
- **Grader Access**: Providing submission details for grading process

## API Endpoints to Implement

### 1. Independent Student Submission APIs

#### 1.1 Submit Exam Answers
**Endpoint**: `POST /api/student/exams/{examId}/submit`

**Description**: Independent students submit or update exam answers autonomously

**Path Parameters**:
- `examId` (string): Exam ID

**Request Body**:
```typescript
{
  answers: [
    {
      questionNumber: number,
      imageUrl: string,
      uploadTime: string // ISO 8601 format
    }
  ]
}
```

**Response**:
```typescript
ApiResponse<{
  id: string,
  examId: string,
  studentUsername: string,
  answers: ExamAnswer[],
  submittedAt: string,
  status: "submitted" | "grading" | "graded"
}>
```

**Authentication**: Bearer Token (student role)

---

#### 1.2 Get Student Submission Status
**Endpoint**: `GET /api/student/exams/{examId}/submission`

**Description**: Independent students view their submission record for a specific exam

**Path Parameters**:
- `examId` (string): Exam ID

**Response**:
```typescript
ApiResponse<{
  id: string,
  examId: string,
  studentUsername: string,
  answers: ExamAnswer[],
  submittedAt: string,
  status: "submitted" | "grading" | "graded",
  score?: number
}>
```

**Authentication**: Bearer Token (student role)

---

#### 1.3 Upload Answer Image
**Endpoint**: `POST /api/student/upload/answer-image`

**Description**: Upload answer image file for independent students

**Request Type**: `multipart/form-data`

**Request Parameters**:
- `file` (File): Image file (supports jpg, jpeg, png, gif formats)
- `relatedId` (string): Exam ID
- `questionNumber` (number): Question number
- `category` (string): Always "answer-image"
- `timestamp` (string): Current timestamp

**Response**:
```typescript
ApiResponse<{
  fileId: string,
  fileName: string,
  fileUrl: string,
  fileSize: number,
  fileType: string,
  uploadTime: string
}>
```

**Authentication**: Bearer Token (student role)

---

### 2. Coach Proxy Submission APIs

#### 2.1 Coach Proxy Answer Upload
**Endpoint**: `POST /api/coach/exams/{examId}/upload-answer`

**Description**: Coach uploads answer images on behalf of non-independent students

**Path Parameters**:
- `examId` (string): Exam ID

**Request**: `multipart/form-data`
- `file` (File): Image file
- `questionNumber` (number): Question number
- `studentUsername` (string): Non-independent student username managed by coach
- `relatedId` (string): Exam ID (same as path parameter)
- `category` (string): Always "answer-image"
- `timestamp` (string): Current timestamp

**Response**:
```typescript
ApiResponse<{
  fileId: string,
  fileName: string,
  fileUrl: string,
  fileSize: number,
  fileType: string,
  uploadTime: string
}>
```

**Authentication**: Bearer Token (coach role)

---

#### 2.2 Coach Proxy Submission
**Endpoint**: `POST /api/coach/exams/{examId}/submissions`

**Description**: Coach submits exam answers on behalf of non-independent students (only submission method for non-independent students)

**Path Parameters**:
- `examId` (string): Exam ID

**Request Body**:
```typescript
{
  studentUsername: string, // Non-independent student username managed by coach
  answers: [
    {
      questionNumber: number,
      imageUrl: string,
      uploadTime: string
    }
  ]
}
```

**Response**:
```typescript
ApiResponse<ExamSubmission>
```

**Authentication**: Bearer Token (coach role)

**Notes**:
- Only for non-independent students under current coach's management
- Students cannot directly operate this interface
- Submission records are associated with coach account

---

#### 2.3 Get Coach-Managed Student Submissions
**Endpoint**: `GET /api/coach/exams/{examId}/submissions`

**Description**: Get submission records of non-independent students managed by coach

**Path Parameters**:
- `examId` (string): Exam ID

**Query Parameters**:
- `studentUsername?` (string): Specific student username
- `status?` (string): Submission status filter

**Response**:
```typescript
ApiResponse<ExamSubmission[]>
```

**Authentication**: Bearer Token (coach role)

**Notes**:
- All submissions are completed through coach proxy
- Students cannot view their submission records independently, only through coach

---

### 3. Grader Access APIs

#### 3.1 Get Student Submission Details
**Endpoint**: `GET /api/grader/submissions/{submissionId}`

**Description**: Grader views specific submission details for grading

**Path Parameters**:
- `submissionId` (string): Submission ID

**Response**:
```typescript
ApiResponse<ExamSubmission>
```

**Authentication**: Bearer Token (grader role)

---

#### 3.2 Get Exam Grading Progress
**Endpoint**: `GET /api/grader/exams/{examId}/progress`

**Description**: View grading progress for specific exam

**Path Parameters**:
- `examId` (string): Exam ID

**Response**:
```typescript
ApiResponse<{
  examId: string,
  examTitle: string,
  totalSubmissions: number,
  gradedSubmissions: number,
  pendingSubmissions: number,
  myCompletedTasks: number,
  progress: number // percentage
}>
```

**Authentication**: Bearer Token (grader role)

---

## Key Data Types

### ExamAnswer
```typescript
interface ExamAnswer {
  questionId: string;
  questionNumber: number;
  answer: string | string[];
  score?: number;
  maxScore: number;
  comments?: string;
  annotations?: any[];
  imageUrl?: string;
  uploadTime?: string;
  graderId?: string;
  graderName?: string;
  gradedAt?: string;
}
```

### ExamSubmission
```typescript
interface ExamSubmission {
  id: string;
  examId: string;
  studentId: string;
  studentUsername: string;
  studentName?: string;
  answers: ExamAnswer[];
  submittedAt: string;
  status: 'submitted' | 'grading' | 'graded';
  totalScore?: number;
  maxScore?: number;
  gradedAt?: string;
  gradedBy?: string;
  feedback?: string;
}
```

### ApiResponse
```typescript
interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
}
```

## Permission Control

### Student Account Types
1. **Independent Student Account**:
   - Complete autonomous operation permissions
   - Self-viewing exam information
   - Self-submitting answers
   - Viewing personal scores and rankings
   - Managing personal profile

2. **Non-Independent Student Account**:
   - No direct login capability
   - All exam operations done by coach proxy
   - Coach proxy submission of answers
   - Coach viewing their score information
   - Cannot modify personal information autonomously

### API Permission Mapping
- `/api/student/*` → Only for **Independent Student Accounts**
- `/api/coach/students/*` → Coach managing **Non-Independent Student Accounts**
- `/api/coach/exams/*/submissions` → Coach proxy submission for **Non-Independent Students**

## Service Dependencies

The SubmissionService needs to interact with:
- **ExamManagementService** (port 3003): Validate exam existence and status
- **UserManagementService** (port 3002): Validate user permissions and student-coach relationships
- **FileStorageService** (port 3008): Handle answer image uploads and storage
- **UserAuthService** (port 3001): Token validation and user authentication

## Implementation Notes

1. **Permission Validation**: Strict validation of student account types and coach-student relationships
2. **File Upload Integration**: Seamless integration with FileStorageService for answer image handling
3. **Status Management**: Proper tracking of submission status transitions
4. **Data Consistency**: Ensure submission data consistency across related services
5. **Error Handling**: Comprehensive error handling for edge cases and permission violations

## Frontend Implementation Verification

Based on the actual frontend code analysis, the following APIs are confirmed to be implemented:

### Student APIs (3 endpoints):
1. `POST /api/student/exams/{examId}/submit` - Submit exam answers
2. `GET /api/student/exams/{examId}/submission` - Get submission status  
3. `POST /api/student/upload/answer-image` - Upload answer images

### Coach APIs (3 endpoints):
1. `POST /api/coach/exams/{examId}/upload-answer` - Upload images for non-independent students
2. `POST /api/coach/exams/{examId}/submissions` - Submit answers for non-independent students
3. `GET /api/coach/exams/{examId}/submissions` - Get submissions of managed students

### Grader APIs (2 endpoints):
1. `GET /api/grader/submissions/{submissionId}` - View submission details
2. `GET /api/grader/exams/{examId}/progress` - View grading progress

**Total: 8 API endpoints** for SubmissionService implementation.
