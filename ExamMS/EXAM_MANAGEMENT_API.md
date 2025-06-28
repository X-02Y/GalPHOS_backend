# Exam Management Service API Reference

**Service Port**: 3003  
**Service Identifier**: `examManagement`  
**Base URL**: `http://localhost:3003/api`

This document provides a comprehensive reference for all API endpoints that belong to the Exam Management Service, extracted from the frontend API documentation and microservice routing architecture.

## Service Responsibility

The Exam Management Service handles the complete lifecycle of exams including:
- Exam creation, publishing, and management
- Question configuration and scoring
- Exam status control
- Exam information retrieval for all user roles
- Exam file management

## API Endpoints by Role

### Student APIs

#### Exam Viewing and Information

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExams` | `/api/student/exams` | GET | None | `{ exams: Exam[], total: number }` | Get exam list (view only, no submission) |
| `getExamDetail` | `/api/student/exams/{examId}` | GET | `examId: string` | `{ exam: ExamDetail, questions: Question[] }` | Get exam details |

#### Score and Ranking Information

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getScoreDetail` | `/api/student/exams/{examId}/score` | GET | `examId: string` | `{ score: DetailedScore, breakdown: ScoreBreakdown }` | Get detailed score for specific exam |
| `getScoreRanking` | `/api/student/exams/{examId}/ranking` | GET | `examId: string` | `{ ranking: Ranking[], myRank: number, totalParticipants: number }` | Get score ranking for specific exam |

### Coach APIs

#### Exam Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExams` | `/api/coach/exams` | GET | `{ status?, timeRange? }` | `{ exams: Exam[], total: number }` | Get exam list with filtering options |
| `getExamDetails` | `/api/coach/exams/{examId}` | GET | `examId: string` | `{ exam: ExamDetail, questions: Question[], statistics: ExamStats }` | Get comprehensive exam details with statistics |
| `downloadExamFile` | `/api/coach/exams/{examId}/download` | GET | `examId: string, fileType: string` | `File (binary data)` | Download exam-related files |

#### Score Management and Statistics

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExamScoreStatistics` | `/api/coach/exams/{examId}/scores/statistics` | GET | `examId: string` | `{ statistics: ExamScoreStats, distribution: ScoreDistribution }` | Get comprehensive exam score statistics |
| `getStudentRanking` | `/api/coach/exams/{examId}/ranking` | GET | `examId: string, studentId?: string` | `{ rankings: StudentRanking[], myStudents: StudentRank[] }` | Get student ranking for specific exam |
| `exportScoreReport` | `/api/coach/exams/{examId}/scores/export` | POST | `examId: string, format: string` | `File (binary data)` | Export exam score report |

### Grader APIs

#### Exam Information for Grading

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getAvailableExams` | `/api/grader/exams` | GET | `{ status?, page?, limit?, subject? }` | `{ exams: GraderExam[], total: number, pagination: PaginationInfo }` | Get exams available for grading |
| `getExamDetail` | `/api/grader/exams/{examId}` | GET | `examId: string` | `{ exam: ExamDetail, gradingInfo: GradingInfo, progress: GradingProgress }` | Get exam details with grading information |
| `getExamGradingProgress` | `/api/grader/exams/{examId}/progress` | GET | `examId: string` | `{ progress: ExamGradingProgress, statistics: GradingStats }` | Get exam grading progress and statistics |

#### Question Score Configuration

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExamQuestionScores` | `/api/grader/exams/{examId}/questions/scores` | GET | `examId: string` | `{ questions: QuestionScore[], totalScore: number }` | Get question score configuration for exam |

### Admin APIs

#### Exam CRUD Operations

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExams` | `/api/admin/exams` | GET | `{ page?, limit?, status? }` | `{ exams: AdminExam[], total: number, pagination: PaginationInfo }` | Get comprehensive exam list with admin privileges |
| `createExam` | `/api/admin/exams` | POST | `examData: object` | `{ success: boolean, exam: Exam }` | Create new exam |
| `updateExam` | `/api/admin/exams/{examId}` | PUT | `examId: string, examData: object` | `{ success: boolean, exam: Exam }` | Update existing exam |
| `deleteExam` | `/api/admin/exams/{examId}` | DELETE | `examId: string` | `{ success: boolean, message: string }` | Delete exam |

#### Exam Publishing and Status Control

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `publishExam` | `/api/admin/exams/{examId}/publish` | POST | `examId: string` | `{ success: boolean, exam: Exam }` | Publish exam to make it available |
| `unpublishExam` | `/api/admin/exams/{examId}/unpublish` | POST | `examId: string` | `{ success: boolean, exam: Exam }` | Unpublish exam to make it unavailable |

#### Exam File Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `uploadExamFiles` | `/api/admin/exams/{examId}/files` | POST | `examId: string, files: File[]` | `{ success: boolean, files: ExamFile[] }` | Upload files related to exam (question papers, answer keys, etc.) |

#### Question Score Configuration

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `setQuestionScores` | `/api/admin/exams/{examId}/questions/scores` | POST | `examId: string, scores: object` | `{ success: boolean, scores: QuestionScore[] }` | Configure scoring for exam questions |
| `getQuestionScores` | `/api/admin/exams/{examId}/questions/scores` | GET | `examId: string` | `{ scores: QuestionScore[], totalScore: number }` | Get current question score configuration |

## API Routing Rules

According to the microservice routing architecture, the following URL patterns are automatically routed to the Exam Management Service (localhost:3003):

### Route Patterns
```
/api/admin/exams*
/api/admin/questions*
/api/student/exams (查看，不包含提交)
/api/student/exams/*/score
/api/student/exams/*/ranking
/api/coach/exams*
/api/grader/exams*
```

### Exclusions
- **Submission-related endpoints** are routed to the Submission Service (localhost:3004)
- **File upload/download endpoints** may be routed to the File Storage Service (localhost:3008)
- **Grading task management** is routed to the Grading Service (localhost:3005)

## Data Models

### Core Types

#### Exam
```typescript
interface Exam {
  id: string;
  title: string;
  description: string;
  subject: string;
  startTime: Date;
  endTime: Date;
  duration: number; // in minutes
  status: ExamStatus;
  totalScore: number;
  questionCount: number;
  createdAt: Date;
  updatedAt: Date;
}
```

#### ExamDetail
```typescript
interface ExamDetail extends Exam {
  instructions: string;
  questions: Question[];
  files: ExamFile[];
  settings: ExamSettings;
}
```

#### Question
```typescript
interface Question {
  number: number;
  content: string;
  type: QuestionType;
  score: number;
  options?: string[]; // for multiple choice
  correctAnswer?: string;
}
```

#### QuestionScore
```typescript
interface QuestionScore {
  questionNumber: number;
  maxScore: number;
  partialScoring?: boolean;
  scoringCriteria?: ScoringCriteria[];
}
```

### Enums

#### ExamStatus
```typescript
enum ExamStatus {
  DRAFT = 'draft',
  PUBLISHED = 'published',
  ACTIVE = 'active',
  COMPLETED = 'completed',
  CANCELLED = 'cancelled'
}
```

#### QuestionType
```typescript
enum QuestionType {
  MULTIPLE_CHOICE = 'multiple_choice',
  SHORT_ANSWER = 'short_answer',
  ESSAY = 'essay',
  CALCULATION = 'calculation'
}
```

## Service Dependencies

### Upstream Dependencies
- **User Authentication Service** (localhost:3001): User validation and permissions
- **User Management Service** (localhost:3002): User role verification

### Downstream Dependencies
- **Submission Service** (localhost:3004): Exam submission handling
- **Grading Service** (localhost:3005): Grading task creation
- **Score Statistics Service** (localhost:3006): Score calculation and ranking
- **File Storage Service** (localhost:3008): Exam file management

## Error Handling

### Common Error Codes
- `400`: Bad Request - Invalid exam data or parameters
- `401`: Unauthorized - Invalid or missing authentication token
- `403`: Forbidden - Insufficient permissions for the operation
- `404`: Not Found - Exam or question not found
- `409`: Conflict - Exam status conflicts (e.g., trying to edit published exam)
- `422`: Unprocessable Entity - Validation errors in exam data

### Error Response Format
```json
{
  "error": true,
  "message": "Error description",
  "code": "ERROR_CODE",
  "details": {
    "field": "validation error details"
  }
}
```

## Authentication & Authorization

### Required Headers
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Role-Based Access Control

#### Student Role
- **Read-only access** to published exams
- Can view exam details and scores
- Cannot modify any exam data

#### Coach Role
- **Read access** to exams relevant to their students
- Can download exam files
- Can view detailed statistics and rankings
- Cannot create or modify exams

#### Grader Role
- **Read access** to exams assigned for grading
- Can view question score configurations
- Cannot modify exam content or settings

#### Admin Role
- **Full access** to all exam operations
- Can create, update, delete exams
- Can manage exam publishing status
- Can configure question scores
- Can upload exam files

## Implementation Notes

1. **Exam Status Workflow**: Draft → Published → Active → Completed
2. **File Handling**: Large files should be handled via the File Storage Service
3. **Real-time Updates**: Consider WebSocket connections for exam status changes
4. **Caching**: Implement caching for frequently accessed exam data
5. **Validation**: Strict validation for exam timing and scoring configurations
6. **Audit Trail**: Log all exam modifications for compliance and debugging

## Version Information

- **Document Version**: 1.0.0
- **Service Version**: Based on frontend API documentation
- **Last Updated**: June 28, 2025
- **Microservice Architecture**: Port 3003 allocation
- **Compatible Frontend**: GalPHOS Frontend v1.0

---

*This document was automatically generated from the frontend API documentation and microservice routing specifications.*
