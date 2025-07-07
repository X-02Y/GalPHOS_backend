# FileStorageService API Requirements

## Overview

The FileStorageService is responsible for handling all file upload, storage, download, and management operations in the GalPHOS system. Based on the frontend API documentation analysis, this service needs to implement the following APIs.

**Service Port**: `localhost:3008`

---

## 1. File Upload APIs

### 1.1 Upload Exam Files (Admin)
**Endpoint**: `POST /api/admin/exams/{examId}/upload`

**Description**: Upload exam-related files (questions, answers, answer sheets) by admin

**Request Type**: `multipart/form-data`

**Request Parameters**:
- `file` (File): The file to upload
- `category` (string): Always "exam-file"
- `relatedId` (string): Exam ID (same as path parameter)
- `timestamp` (string): Upload timestamp

**Response**:
```typescript
{
  success: boolean,
  data: {
    fileId: string,
    fileName: string,
    fileUrl: string,
    fileSize: number,
    fileType: string,
    uploadTime: string
  },
  message: string
}
```

### 1.2 Upload Avatar (Handled by Profile APIs)
**Note**: Avatar uploads are handled by each role's profile API internally, not directly by file storage service.

**Frontend Implementation**: The frontend converts files to base64 and sends them through profile APIs:
- `/api/admin/profile` (for admin avatars)
- `/api/student/profile` (for student avatars)  
- `/api/coach/profile` (for coach avatars)
- `/api/grader/profile` (for grader avatars)

### 1.3 Upload Answer Image (Student Submission)
**Endpoint**: `POST /api/student/upload/answer-image`

**Description**: Upload student answer images for exam submissions

**Request Type**: `multipart/form-data`

**Request Parameters**:
- `file` (File): Image file (jpg, jpeg, png, gif formats)
- `category` (string): Always "answer-image"
- `relatedId` (string): Exam ID
- `questionNumber` (number): Question number
- `timestamp` (string): Upload timestamp

**Response**:
```typescript
{
  success: boolean,
  data: {
    fileId: string,
    fileName: string,
    fileUrl: string,
    fileSize: number,
    fileType: string,
    uploadTime: string
  },
  message: string
}
```

### 1.4 Upload Answer Image (Coach Proxy)
**Endpoint**: `POST /api/coach/exams/{examId}/upload-answer`

**Description**: Coach uploads answer images on behalf of students

**Request Type**: `multipart/form-data`

**Request Parameters**:
- `file` (File): Image file
- `questionNumber` (number): Question number  
- `studentUsername` (string): Student username being helped
- `category` (string): Always "answer-image"
- `relatedId` (string): Exam ID
- `timestamp` (string): Upload timestamp

**Response**:
```typescript
{
  success: boolean,
  data: {
    fileId: string,
    fileName: string,
    fileUrl: string,
    fileSize: number,
    fileType: string,
    uploadTime: string
  },
  message: string
}
```

### 1.5 Upload Document (General Purpose)
**Endpoint**: `POST /api/upload/document`

**Description**: General document upload for various purposes

**Request Type**: `multipart/form-data`

**Request Parameters**:
- `file` (File): Document file
- `category` (string): Always "document"
- `timestamp` (string): Upload timestamp

**Response**:
```typescript
{
  success: boolean,
  data: {
    fileId: string,
    fileName: string,
    fileUrl: string,
    fileSize: number,
    fileType: string,
    uploadTime: string
  },
  message: string
}
```

### 1.6 General File Upload
**Endpoint**: `POST /api/upload/file`

**Description**: Generic file upload endpoint

**Request Type**: `multipart/form-data`

**Request Parameters**:
- `file` (File): File to upload
- `category` (string): File category
- `timestamp` (string): Upload timestamp

**Response**:
```typescript
{
  success: boolean,
  data: {
    fileId: string,
    fileName: string,
    fileUrl: string,
    fileSize: number,
    fileType: string,
    uploadTime: string
  },
  message: string
}
```

---

## 2. File Download APIs

### 2.1 Download File (General)
**Endpoint**: `GET /api/files/{fileId}/download`

**Description**: General file download endpoint used by FileUploadService

**Path Parameters**:
- `fileId` (string): File ID to download

**Response**: Direct file stream or redirect to download URL

### 2.2 Download File (Student Access)
**Endpoint**: `GET /api/student/files/{fileId}/download` (Specific pattern from docs)

**Description**: Download exam files accessible to students

**Path Parameters**:
- `fileId` (string): File ID to download

**Response**: Direct file stream or redirect to download URL

### 2.3 Download Exam File (Grader Access)
**Endpoint**: `GET /api/grader/files/{fileId}/download`

**Description**: Download exam files for graders with type specification

**Query Parameters**:
- `type` (string, required): File type - "question" | "answer" | "answer_sheet"

**Response**: Direct file stream or redirect to download URL

---

## 3. File Management APIs

### 3.1 Delete File
**Endpoint**: `DELETE /api/files/{fileId}`

**Description**: Delete a file from storage (used by FileUploadService)

**Path Parameters**:
- `fileId` (string): File ID to delete

**Authorization**: Admin level required

**Response**:
```typescript
{
  success: boolean,
  message: string
}
```

### 3.2 Get File Info
**Endpoint**: `GET /api/files/{fileId}`

**Description**: Get file metadata information (used by FileUploadService)

**Path Parameters**:
- `fileId` (string): File ID

**Response**:
```typescript
{
  success: boolean,
  data: {
    fileId: string,
    fileName: string,
    fileUrl: string,
    fileSize: number,
    fileType: string,
    uploadTime: string
  },
  message: string
}
```

### 3.3 List Files
**Endpoint**: `GET /api/files`

**Description**: List files with filtering options

**Query Parameters**:
- `category?` (string): Filter by category
- `examId?` (string): Filter by exam ID
- `fileType?` (string): Filter by file type
- `uploadedBy?` (string): Filter by uploader
- `page?` (number): Page number (default: 1)
- `limit?` (number): Items per page (default: 20)

**Response**:
```typescript
{
  success: boolean,
  data: {
    files: Array<{
      fileId: string,
      fileName: string,
      fileUrl: string,
      fileSize: number,
      fileType: string,
      uploadTime: string,
      category?: string,
      examId?: string
    }>,
    pagination: {
      page: number,
      limit: number,
      total: number,
      totalPages: number
    }
  },
  message: string
}
```

---

## 4. Grader Image Management

### 4.1 View Grading Images
**Endpoint**: `GET /api/grader/images`

**Description**: Get images for grading interface (mentioned in microservice routing)

**Query Parameters**:
- `examId` (string): Exam ID
- `studentId?` (string): Specific student ID
- `questionNumber?` (number): Specific question number

**Response**:
```typescript
{
  success: boolean,
  data: Array<{
    imageUrl: string,
    fileName: string,
    examId: string,
    studentId: string,
    questionNumber: number,
    uploadTime: string
  }>,
  message: string
}
```

---

## 5. File Type Definitions

### FileUploadResult Type (Frontend Interface)
```typescript
interface FileUploadResult {
  fileId: string;
  fileName: string;
  fileUrl: string;
  fileSize: number;
  fileType: string;
  uploadTime: string;
}
```

### ExamFile Type (Frontend Interface)
```typescript
interface ExamFile {
  id: string;
  name: string;
  filename?: string;
  originalName?: string;
  url: string;
  size: number;
  uploadTime: string;
  uploadedAt?: string;
  mimetype?: string;
  type?: 'exam' | 'answer' | 'template' | 'question' | 'answerSheet';
}
```

### FileUploadOptions Type (Frontend Interface)
```typescript
interface FileUploadOptions {
  category: 'avatar' | 'answer-image' | 'exam-file' | 'document' | 'other';
  relatedId?: string;
  questionNumber?: number;
  studentUsername?: string;
  maxSize?: number;
  allowedTypes?: string[];
  showProgress?: boolean;
  onProgress?: (progress: number) => void;
}
```

---

## 6. Security and Access Control

### Authentication
- All endpoints require valid JWT tokens
- Token validation should be handled by the service
- User role and permissions should be extracted from token

### File Access Permissions
- **Admin**: Full access to all files, can upload exam files
- **Coach**: Access to exam files for exams they manage, can upload answer images on behalf of students
- **Grader**: Access to exam files for assigned grading tasks, can view grading images
- **Student**: Access to published exam files and can upload their own answer images

### File Size Limits (Based on Frontend Implementation)
- Avatar images: 5MB maximum (handled by profile APIs)
- Answer images: 10MB maximum (default)
- Exam files: 50MB maximum
- General documents: 10MB maximum (default)

### Supported File Types (From Frontend FileUploadService)
- **Images**: image/jpeg, image/jpg, image/png, image/gif, image/webp
- **Documents**: application/pdf, application/msword, application/vnd.openxmlformats-officedocument.wordprocessingml.document, application/vnd.ms-excel, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, text/plain

### Upload Request Structure (From Frontend Analysis)
All file uploads include these FormData fields:
- `file`: The actual file
- `category`: File category (avatar/answer-image/exam-file/document/other)
- `timestamp`: Upload timestamp to avoid caching issues
- Additional fields based on category (relatedId, questionNumber, studentUsername)

---

## 7. Error Handling

### Common Error Responses
```typescript
{
  success: false,
  message: string,
  error?: {
    code: string,
    details?: any
  }
}
```

### Error Codes
- `FILE_TOO_LARGE`: File exceeds size limit
- `INVALID_FILE_TYPE`: File type not supported
- `FILE_NOT_FOUND`: Requested file does not exist
- `ACCESS_DENIED`: User lacks permission to access file
- `UPLOAD_FAILED`: File upload process failed
- `STORAGE_ERROR`: Internal storage system error

---

## 8. Database Schema Requirements

### Files Table
```sql
CREATE TABLE files (
  id VARCHAR(36) PRIMARY KEY,
  file_name VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  file_url VARCHAR(500) NOT NULL,
  file_size BIGINT NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  file_type VARCHAR(50),
  category VARCHAR(50),
  exam_id VARCHAR(36),
  question_number INTEGER,
  student_id VARCHAR(36),
  uploaded_by VARCHAR(100) NOT NULL,
  upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_exam_id (exam_id),
  INDEX idx_student_id (student_id),
  INDEX idx_uploaded_by (uploaded_by),
  INDEX idx_file_type (file_type),
  INDEX idx_category (category)
);
```

### Exam Files Association Table (if separate from main files table)
```sql
CREATE TABLE exam_files (
  id VARCHAR(36) PRIMARY KEY,
  exam_id VARCHAR(36) NOT NULL,
  file_id VARCHAR(36) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  file_url VARCHAR(500) NOT NULL,
  file_size BIGINT NOT NULL,
  file_type VARCHAR(50) NOT NULL, -- 'question', 'answer', 'answerSheet'
  mime_type VARCHAR(100) NOT NULL,
  uploaded_by VARCHAR(100) NOT NULL,
  upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
  INDEX idx_exam_file_type (exam_id, file_type)
);
```

---

## 9. Integration Points

### With ExamMS
- File metadata should be sent to ExamMS when exam files are uploaded
- ExamMS will store file references in exam records

### With UserManagementService
- Avatar URLs should be updated in user profiles
- User authentication and authorization

### With SubmissionService
- Answer image files should be linked to submission records
- File metadata for grading purposes

---

## 10. Configuration Requirements

### Storage Configuration
- Local file storage path configuration
- CDN/Cloud storage integration (if applicable)
- Backup and cleanup policies

### Service Configuration
```json
{
  "storage": {
    "localPath": "/app/storage",
    "maxFileSize": {
      "avatar": 5242880,
      "answer": 10485760,
      "exam": 52428800,
      "document": 10485760
    },
    "allowedTypes": {
      "image": ["image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"],
      "document": [
        "application/pdf",
        "application/msword", 
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "text/plain"
      ]
    }
  },
  "database": {
    "url": "postgresql://user:password@localhost:5432/galphos_storage"
  },
  "server": {
    "port": 3008,
    "host": "localhost",
    "timeout": 300000
  }
}
```

## Summary of Key Changes Based on Frontend Analysis

### 📋 **API Endpoint Corrections**:
1. **Admin exam file upload**: `POST /api/admin/exams/{examId}/upload` (not `/api/upload/exam-files`)
2. **Student answer upload**: `POST /api/student/upload/answer-image` 
3. **Coach proxy upload**: `POST /api/coach/exams/{examId}/upload-answer`
4. **Avatar uploads**: Handled by profile APIs, not direct file upload endpoints
5. **General download**: `GET /api/files/{fileId}/download` (primary endpoint used by frontend)

### 🔧 **Implementation Details**:
1. **FormData Structure**: Always includes `file`, `category`, `timestamp`, plus context-specific fields
2. **Response Format**: Simplified to match frontend `FileUploadResult` interface
3. **File Size Limits**: Updated to match frontend configuration (5MB avatars, 10MB default, 50MB exams)
4. **Timeout Handling**: 5-minute timeout for uploads as implemented in frontend
5. **Progress Tracking**: Support for upload progress callbacks via XMLHttpRequest

### 🎯 **Service Routing**:
Based on microservice router analysis:
- File storage service handles: `/api/upload/file*`, `/api/upload/document*`, `/api/files*`, `/api/student/files/*`, `/api/grader/images*`
- Avatar uploads are routed to respective profile APIs in UserManagementService
- Exam file uploads go through ExamManagementService then delegate to FileStorageService

This comprehensive API specification now accurately reflects the actual frontend implementation and provides the correct endpoints for implementing the FileStorageService.
