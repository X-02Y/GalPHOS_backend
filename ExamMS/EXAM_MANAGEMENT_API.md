# GalPHOS Exam Management Service API Reference

**Base URL**: `http://localhost:3003/api`

This document provides a comprehensive reference for all exam management related API endpoints in the GalPHOS (Galaxy Physics Online System) application.

## Table of Contents

- [GalPHOS Exam Management Service API Reference](#galphos-exam-management-service-api-reference)
  - [Table of Contents](#table-of-contents)
  - [Overview](#overview)
  - [Authentication](#authentication)
    - [Authentication Headers](#authentication-headers)
    - [Role-Based Access Control](#role-based-access-control)
  - [Internal File Storage Integration](#internal-file-storage-integration)
    - [File Upload Flow](#file-upload-flow)
    - [Supported File Types](#supported-file-types)
    - [Internal File Storage API Calls](#internal-file-storage-api-calls)
  - [Admin Exam Management API](#admin-exam-management-api)
    - [Exam CRUD Operations](#exam-crud-operations)
      - [Get Exam List](#get-exam-list)
      - [Create Exam (Step 1 of 3)](#create-exam-step-1-of-3)
      - [Update Exam](#update-exam)
      - [Delete Exam](#delete-exam)
    - [Exam Question Score Management](#exam-question-score-management)
      - [Set Question Scores (Step 2 of 3)](#set-question-scores-step-2-of-3)
      - [Get Question Scores](#get-question-scores)
      - [Update Single Question Score](#update-single-question-score)
    - [Exam File Management](#exam-file-management)
      - [Upload Exam Files](#upload-exam-files)
    - [Exam Status Management](#exam-status-management)
      - [Publish Exam (Step 3 of 3)](#publish-exam-step-3-of-3)
      - [Unpublish Exam](#unpublish-exam)
  - [Student Exam Management API](#student-exam-management-api)
    - [Exam Access and Participation](#exam-access-and-participation)
      - [Get Available Exams](#get-available-exams)
      - [Get Exam Details](#get-exam-details)
    - [Exam Submission Management](#exam-submission-management)
      - [Submit Exam Answers](#submit-exam-answers)
      - [Get Submission Status](#get-submission-status)
      - [Upload Answer Images](#upload-answer-images)
  - [Coach Exam Management API](#coach-exam-management-api)
    - [Exam Monitoring and Statistics](#exam-monitoring-and-statistics)
      - [Get Exam List](#get-exam-list-1)
      - [Get Exam Details with Stats](#get-exam-details-with-stats)
      - [Get Exam Score Statistics](#get-exam-score-statistics)
    - [Proxy Submission Management](#proxy-submission-management)
      - [Submit Answers for Student](#submit-answers-for-student)
      - [Upload Answer Images for Student](#upload-answer-images-for-student)
      - [Get Student Submissions](#get-student-submissions)
  - [Grader Exam Management API](#grader-exam-management-api)
    - [Exam Grading Operations](#exam-grading-operations)
      - [Get Gradable Exams](#get-gradable-exams)
      - [Get Exam Details for Grading](#get-exam-details-for-grading)
      - [Get Exam Grading Progress](#get-exam-grading-progress)
  - [Data Types](#data-types)
    - [Core Exam Types](#core-exam-types)
    - [Response Types](#response-types)
  - [Error Handling](#error-handling)
    - [Standard Error Response Format](#standard-error-response-format)
    - [Common Error Codes](#common-error-codes)
  - [Usage Examples](#usage-examples)
    - [Creating a Complete Exam (3-Step Process)](#creating-a-complete-exam-3-step-process)
    - [Student Exam Submission](#student-exam-submission)
    - [Coach Proxy Submission](#coach-proxy-submission)
  - [Notes](#notes)
    - [Architecture Integration](#architecture-integration)
    - [Performance Considerations](#performance-considerations)
    - [Security Considerations](#security-considerations)

---

## Overview

The Exam Management Service (ExamMS) is responsible for the complete lifecycle management of exams in the GalPHOS system, including:

- Exam creation, modification, and deletion
- Question score configuration and management
- File upload and storage integration
- Exam status tracking and publishing
- Cross-service integration with submission and grading services

**Service Port**: 3003  
**Service Identifier**: `examManagement`  
**Total API Endpoints**: 8

---

## Authentication

All API endpoints require authentication using JWT tokens. The authentication requirements vary by user role:

### Authentication Headers
```typescript
{
  "Authorization": "Bearer <token>",
  "Content-Type": "application/json"
}
```

### Role-Based Access Control
- **Admin**: Full access to all exam management operations
- **Student**: Read-only access to published exams and submission operations
- **Coach**: Read access to exams, proxy submission for managed students
- **Grader**: Read access to exams for grading purposes

---

## Internal File Storage Integration

ExamMS integrates with the File Storage Service (port 3008) for all file operations:

### File Upload Flow
```
Frontend → ExamMS (3003) → FileStorageService (3008) → File System
```

### Supported File Types
- **Question Files**: PDF, DOC, DOCX
- **Answer Files**: PDF, DOC, DOCX  
- **Answer Sheet Files**: PDF, DOC, DOCX
- **Submission Files**: JPG, JPEG, PNG, PDF

### Internal File Storage API Calls
```typescript
// Upload file to storage service
POST http://localhost:3008/internal/upload
{
  "originalName": "exam_questions.pdf",
  "fileContent": [/* Base64 encoded content */],
  "fileType": "pdf",
  "mimeType": "application/pdf",
  "uploadUserId": "admin123",
  "uploadUserType": "admin",
  "examId": "exam001",
  "description": "Exam question file",
  "category": "exam"
}

// Download file from storage service
POST http://localhost:3008/internal/download
{
  "fileId": "file_uuid_123",
  "requestUserId": "user123",
  "requestUserType": "student",
  "purpose": "download"
}

// Delete file from storage service
POST http://localhost:3008/internal/delete
{
  "fileId": "file_uuid_123",
  "requestUserId": "admin123",
  "requestUserType": "admin",
  "reason": "Exam deleted"
}
```

---

## Admin Exam Management API

**Base URL**: `/api/admin`

### Exam CRUD Operations

#### Get Exam List
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/exams` | Get all exams list |

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    id: string,
    title: string,
    description: string,
    questionFile?: ExamFile,
    answerFile?: ExamFile,
    answerSheetFile?: ExamFile,
    startTime: string,
    endTime: string,
    status: 'draft' | 'published' | 'ongoing' | 'grading' | 'completed',
    totalQuestions?: number,
    duration?: number,
    createdAt: string,
    createdBy: string
  }>
}
```

#### Create Exam (Step 1 of 3)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/exams` | Create new exam with basic information |

**Request Body**:
```typescript
{
  title: string,                    // Exam title
  description: string,              // Exam description
  startTime: string,               // Start time (ISO format)
  endTime: string,                 // End time (ISO format)
  totalQuestions: number,          // Total number of questions
  duration: number,                // Duration in minutes
  status: 'draft'                  // Initial status
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    title: string,
    description: string,
    startTime: string,
    endTime: string,
    totalQuestions: number,
    duration: number,
    status: 'draft',
    createdAt: string,
    createdBy: string
  },
  message: "Exam created successfully"
}
```

#### Update Exam
| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/admin/exams/{examId}` | Update exam information |

**Request Body**:
```typescript
{
  title?: string,
  description?: string,
  startTime?: string,
  endTime?: string,
  totalQuestions?: number,
  duration?: number,
  resetScoresIfNeeded?: boolean   // Reset scores if question count changes
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    title: string,
    description: string,
    startTime: string,
    endTime: string,
    totalQuestions: number,
    duration: number,
    status: string,
    updatedAt: string,
    scoreConfigChanged?: boolean
  },
  message: "Exam updated successfully"
}
```

#### Delete Exam
| Method | Endpoint | Description |
|--------|----------|-------------|
| DELETE | `/api/admin/exams/{examId}` | Delete exam and associated files |

**Response**:
```typescript
{
  success: boolean,
  message: "Exam deleted successfully"
}
```

### Exam Question Score Management

#### Set Question Scores (Step 2 of 3)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/exams/{examId}/question-scores` | Set question scores for exam |

**Request Body**:
```typescript
{
  questions: Array<{
    number: number,                // Question number (1-N)
    score: number                  // Score for this question
  }>
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    examId: string,
    totalQuestions: number,
    totalScore: number,
    questions: Array<{
      id: string,
      number: number,
      score: number
    }>
  },
  message: "Question scores set successfully"
}
```

#### Get Question Scores
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/admin/exams/{examId}/question-scores` | Get question scores configuration |

**Response**:
```typescript
{
  success: boolean,
  data: {
    examId: string,
    totalQuestions: number,
    totalScore: number,
    questions: Array<{
      id: string,
      number: number,
      score: number,
      maxScore?: number
    }>
  }
}
```

#### Update Single Question Score
| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/admin/exams/{examId}/question-scores/{questionNumber}` | Update specific question score |

**Request Body**:
```typescript
{
  score: number
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    examId: string,
    questionNumber: number,
    score: number,
    totalScore: number
  },
  message: "Question score updated successfully"
}
```

### Exam File Management

#### Upload Exam Files
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/upload/exam-files` | Upload exam files (questions, answers, sheets) |

**Request Type**: `multipart/form-data`

**Form Data**:
```typescript
{
  file: File,                     // File to upload
  examId: string,                 // Exam ID
  fileType: 'question' | 'answer' | 'answerSheet'
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    fileId: string,
    originalName: string,
    url: string,
    size: number,
    uploadTime: string
  },
  message: "File uploaded successfully"
}
```

### Exam Status Management

#### Publish Exam (Step 3 of 3)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/exams/{examId}/publish` | Publish exam with files |

**Request Body**:
```typescript
{
  questionFileId?: string,
  answerFileId?: string,
  answerSheetFileId?: string
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    title: string,
    status: 'published',
    publishedAt: string
  },
  message: "Exam published successfully"
}
```

#### Unpublish Exam
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/exams/{examId}/unpublish` | Unpublish exam |

**Response**:
```typescript
{
  success: boolean,
  message: "Exam unpublished successfully"
}
```

---

## Student Exam Management API

**Base URL**: `/api/student`

### Exam Access and Participation

#### Get Available Exams
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/student/exams` | Get exams available for student |

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    id: string,
    title: string,
    description: string,
    questionFile?: ExamFile,
    answerSheetFile?: ExamFile,
    startTime: string,
    endTime: string,
    status: 'published' | 'ongoing' | 'completed',
    totalQuestions?: number,
    duration?: number
  }>,
  message: "Exams retrieved successfully"
}
```

#### Get Exam Details
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/student/exams/{examId}` | Get detailed exam information |

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    title: string,
    description: string,
    questionFile?: ExamFile,
    answerFile?: ExamFile,
    answerSheetFile?: ExamFile,
    startTime: string,
    endTime: string,
    status: string,
    totalQuestions?: number,
    duration?: number,
    createdAt: string,
    updatedAt: string
  },
  message: "Exam details retrieved successfully"
}
```

### Exam Submission Management

#### Submit Exam Answers
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/student/exams/{examId}/submit` | Submit or update exam answers |

**Request Body**:
```typescript
{
  answers: Array<{
    questionNumber: number,
    imageUrl: string,
    uploadTime: string
  }>
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    examId: string,
    studentUsername: string,
    answers: Array<{
      questionNumber: number,
      imageUrl: string,
      uploadTime: string
    }>,
    submittedAt: string,
    status: 'submitted'
  },
  message: "Answers submitted successfully"
}
```

#### Get Submission Status
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/student/exams/{examId}/submission` | Get student's submission record |

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    examId: string,
    studentUsername: string,
    answers: Array<{
      questionNumber: number,
      imageUrl: string,
      uploadTime: string
    }>,
    submittedAt: string,
    status: 'submitted' | 'graded',
    score?: number
  },
  message: "Submission retrieved successfully"
}
```

#### Upload Answer Images
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/upload/answer-image` | Upload answer images |

**Request Type**: `multipart/form-data`

**Form Data**:
```typescript
{
  file: File,
  examId: string,
  questionNumber: number
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    imageUrl: string,
    fileName: string,
    fileSize: number,
    uploadTime: string
  },
  message: "Image uploaded successfully"
}
```

---

## Coach Exam Management API

**Base URL**: `/api/coach`

### Exam Monitoring and Statistics

#### Get Exam List
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/coach/exams` | Get exams with participation stats |

**Query Parameters**:
- `status?` (string): Filter by status (published, ongoing, completed)
- `timeRange?` (string): Filter by time range (current, past, upcoming)

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    id: string,
    title: string,
    description: string,
    startTime: string,
    endTime: string,
    duration: number,
    status: string,
    totalQuestions: number,
    maxScore: number,
    questionFile?: ExamFile,
    answerSheetFile?: ExamFile,
    totalParticipants: number,
    myStudentsParticipated: number,
    myStudentsTotal: number
  }>
}
```

#### Get Exam Details with Stats
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/coach/exams/{examId}` | Get exam details with participation stats |

**Response**:
```typescript
{
  success: boolean,
  data: {
    exam: {
      // Exam details
    },
    participationStats: {
      totalStudents: number,
      submittedStudents: number,
      gradedStudents: number,
      avgScore: number,
      submissions: Array<{
        studentId: string,
        studentName: string,
        submittedAt: string,
        status: string,
        score?: number,
        rank?: number
      }>
    }
  }
}
```

#### Get Exam Score Statistics
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/coach/exams/{examId}/score-stats` | Get score statistics for exam |

**Response**:
```typescript
{
  success: boolean,
  data: {
    totalStudents: number,
    submittedStudents: number,
    averageScore: number,
    scores: Array<{
      studentId: string,
      studentName: string,
      score: number,
      submittedAt: string
    }>
  }
}
```

### Proxy Submission Management

#### Submit Answers for Student
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/coach/exams/{examId}/submissions` | Submit answers on behalf of managed student |

**Request Body**:
```typescript
{
  studentUsername: string,
  answers: Array<{
    questionNumber: number,
    imageUrl: string,
    uploadTime: string
  }>
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    examId: string,
    studentUsername: string,
    submittedBy: string,         // Coach username
    answers: Array<{
      questionNumber: number,
      imageUrl: string,
      uploadTime: string
    }>,
    submittedAt: string,
    status: 'submitted'
  },
  message: "Answers submitted successfully"
}
```

#### Upload Answer Images for Student
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/coach/exams/{examId}/upload-answer` | Upload answer images for managed student |

**Request Type**: `multipart/form-data`

**Form Data**:
```typescript
{
  file: File,
  questionNumber: number,
  studentUsername: string
}
```

**Response**:
```typescript
{
  success: boolean,
  data: {
    imageUrl: string,
    questionNumber: number,
    studentUsername: string
  },
  message: "Image uploaded successfully"
}
```

#### Get Student Submissions
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/coach/exams/{examId}/submissions` | Get submissions for managed students |

**Query Parameters**:
- `studentUsername?` (string): Filter by specific student
- `status?` (string): Filter by submission status

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    id: string,
    examId: string,
    studentUsername: string,
    submittedBy: string,
    answers: Array<{
      questionNumber: number,
      imageUrl: string,
      uploadTime: string
    }>,
    submittedAt: string,
    status: string,
    score?: number
  }>
}
```

---

## Grader Exam Management API

**Base URL**: `/api/grader`

### Exam Grading Operations

#### Get Gradable Exams
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/grader/exams` | Get exams available for grading |

**Query Parameters**:
- `page?` (number): Page number
- `limit?` (number): Items per page
- `status?` (string): Filter by status (grading, completed)
- `province?` (string): Filter by province
- `subject?` (string): Filter by subject

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    id: string,
    title: string,
    description: string,
    startTime: string,
    endTime: string,
    status: string,
    maxScore: number,
    subject?: string,
    totalSubmissions: number,
    pendingGrading: number
  }>
}
```

#### Get Exam Details for Grading
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/grader/exams/{examId}` | Get exam details for grading |

**Response**:
```typescript
{
  success: boolean,
  data: {
    id: string,
    title: string,
    description: string,
    maxScore: number,
    subject?: string,
    instructions?: string,
    questions: Array<{
      id: string,
      number: number,
      score: number,
      maxScore: number
    }>
  }
}
```

#### Get Exam Grading Progress
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/grader/exams/{examId}/progress` | Get grading progress for exam |

**Response**:
```typescript
{
  success: boolean,
  data: {
    examId: string,
    examTitle: string,
    totalSubmissions: number,
    gradedSubmissions: number,
    pendingSubmissions: number,
    myCompletedTasks: number,
    progress: number
  }
}
```

---

## Data Types

### Core Exam Types

```typescript
interface ExamFile {
  id: string;
  name: string;
  filename?: string;
  originalName?: string;
  url: string;
  size: number;
  uploadTime: string;
  mimetype?: string;
  type?: 'exam' | 'answer' | 'template' | 'question' | 'answerSheet';
}

interface BaseExam {
  id: string;
  title: string;
  description: string;
  startTime: string;
  endTime: string;
  status: 'draft' | 'published' | 'ongoing' | 'grading' | 'completed';
  createdAt: string;
  updatedAt: string;
  duration?: number;
}

interface Exam extends BaseExam {
  questionFile?: ExamFile;
  answerFile?: ExamFile;
  answerSheetFile?: ExamFile;
  createdBy: string;
  participants?: string[];
  totalQuestions?: number;
  maxScore?: number;
  subject?: string;
  instructions?: string;
}

interface Question {
  id: string;
  number: number;
  score: number;
  maxScore?: number;
  content?: string;
  examId: string;
}

interface ExamAnswer {
  questionNumber: number;
  imageUrl: string;
  uploadTime: string;
}

interface ExamSubmission {
  id: string;
  examId: string;
  studentUsername: string;
  submittedBy?: string;
  answers: ExamAnswer[];
  submittedAt: string;
  status: 'submitted' | 'graded';
  score?: number;
  rank?: number;
}
```

### Response Types

```typescript
interface ApiResponse<T> {
  success: boolean;
  data?: T;
  message?: string;
}

interface PaginatedResponse<T> {
  success: boolean;
  data: T[];
  pagination: {
    page: number;
    limit: number;
    total: number;
    pages: number;
  };
}
```

---

## Error Handling

### Standard Error Response Format

```typescript
{
  success: false,
  message: "Error description",
  error?: {
    code: string,
    details?: any
  }
}
```

### Common Error Codes

| Code | Description | HTTP Status |
|------|-------------|-------------|
| `EXAM_NOT_FOUND` | Exam not found | 404 |
| `UNAUTHORIZED` | User not authorized | 401 |
| `FORBIDDEN` | Access denied | 403 |
| `VALIDATION_ERROR` | Invalid input data | 400 |
| `FILE_UPLOAD_ERROR` | File upload failed | 500 |
| `EXAM_ALREADY_PUBLISHED` | Exam already published | 409 |
| `EXAM_NOT_PUBLISHED` | Exam not published | 409 |
| `SUBMISSION_DEADLINE_PASSED` | Submission deadline passed | 409 |
| `DUPLICATE_SUBMISSION` | Duplicate submission | 409 |

---

## Usage Examples

### Creating a Complete Exam (3-Step Process)

```typescript
// Step 1: Create exam basic info
const examResponse = await fetch('/api/admin/exams', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <admin_token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    title: '2024 Physics Competition',
    description: 'Annual physics competition exam',
    startTime: '2024-04-01T09:00:00Z',
    endTime: '2024-04-01T12:00:00Z',
    totalQuestions: 20,
    duration: 180
  })
});

const exam = await examResponse.json();
const examId = exam.data.id;

// Step 2: Set question scores
await fetch(`/api/admin/exams/${examId}/question-scores`, {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <admin_token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    questions: [
      { number: 1, score: 5 },
      { number: 2, score: 5 },
      // ... more questions
      { number: 20, score: 10 }
    ]
  })
});

// Step 3: Upload files and publish
const formData = new FormData();
formData.append('file', questionFile);
formData.append('examId', examId);
formData.append('fileType', 'question');

const uploadResponse = await fetch('/api/upload/exam-files', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <admin_token>'
  },
  body: formData
});

const uploadResult = await uploadResponse.json();
const questionFileId = uploadResult.data.fileId;

// Publish exam
await fetch(`/api/admin/exams/${examId}/publish`, {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <admin_token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    questionFileId: questionFileId
  })
});
```

### Student Exam Submission

```typescript
// Get available exams
const examsResponse = await fetch('/api/student/exams', {
  headers: {
    'Authorization': 'Bearer <student_token>'
  }
});

const exams = await examsResponse.json();

// Submit answers
const submissionResponse = await fetch(`/api/student/exams/${examId}/submit`, {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <student_token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    answers: [
      {
        questionNumber: 1,
        imageUrl: 'https://example.com/answer1.jpg',
        uploadTime: '2024-04-01T10:30:00Z'
      },
      {
        questionNumber: 2,
        imageUrl: 'https://example.com/answer2.jpg',
        uploadTime: '2024-04-01T10:45:00Z'
      }
    ]
  })
});
```

### Coach Proxy Submission

```typescript
// Submit answers for managed student
const proxySubmissionResponse = await fetch(`/api/coach/exams/${examId}/submissions`, {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <coach_token>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    studentUsername: 'student001',
    answers: [
      {
        questionNumber: 1,
        imageUrl: 'https://example.com/answer1.jpg',
        uploadTime: '2024-04-01T10:30:00Z'
      }
    ]
  })
});
```

---

## Notes

### Architecture Integration

The ExamMS integrates with multiple other services:

1. **FileStorageService (3008)**: For all file operations
2. **SubmissionService (3004)**: For answer submission processing
3. **GradingService (3005)**: For grading task management
4. **UserManagementService (3002)**: For user authentication
5. **RegionManagementService (3007)**: For geographic filtering

### Performance Considerations

- File uploads are handled asynchronously through the FileStorageService
- Large file operations may require streaming support
- Database queries are optimized for exam listing and filtering
- Caching is implemented for frequently accessed exam data

### Security Considerations

- All file uploads are validated for type and size
- User permissions are checked at multiple levels
- Sensitive exam data is protected until publication
- Audit logging is maintained for all operations

---

*This documentation is maintained as part of the GalPHOS v1.3.0 system and should be updated with any API changes.*