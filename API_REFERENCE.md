# GalPHOS API Reference

**Base URL**: `http://localhost:3001/api`

This document provides a comprehensive reference for all API endpoints in the GalPHOS (Galaxy Physics Online System) frontend application.

## Table of Contents

- [Authentication API](#authentication-api)
- [Student API](#student-api)
- [Coach API](#coach-api)
- [Grader API](#grader-api)
- [Admin API](#admin-api)
- [Base Configuration](#base-configuration)
- [Notes](#notes)

---

## Authentication API

**Base URL**: `/api/auth`

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `login` | `/api/auth/login` | POST | `LoginRequest: { role, username, password }` | `{ token: string, user: UserInfo, role: string }` | User login (student/coach/grader) |
| `register` | `/api/auth/register` | POST | `RegisterRequest: { username, phone, password, confirmPassword, role, province?, school? }` | `{ success: boolean, message: string, userId?: string }` | User registration |
| `adminLogin` | `/api/auth/admin-login` | POST | `{ username: string, password: string }` | `{ token: string, admin: AdminInfo }` | Admin login |
| `getProvincesAndSchools` | `/api/regions/provinces-schools` | GET | None | `{ provinces: Province[], schools: School[] }` | Get provinces and schools data |
| `validateToken` | `/api/auth/validate` | GET | `token: string (in Authorization header)` | `{ valid: boolean, user?: UserInfo }` | Validate JWT token |
| `logout` | `/api/auth/logout` | POST | None | `{ success: boolean, message: string }` | User logout |

---

## Student API

**Base URL**: `/api/student`

### Exam Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExams` | `/api/student/exams` | GET | None | `{ exams: Exam[], total: number }` | Get exam list |
| `getExamDetail` | `/api/student/exams/{examId}` | GET | `examId: string` | `{ exam: ExamDetail, questions: Question[] }` | Get exam details |
| `submitExamAnswers` | `/api/student/exams/{examId}/submit` | POST | `examId: string, answers: ExamAnswer[]` | `{ success: boolean, submissionId: string }` | Submit exam answers |
| `getExamSubmission` | `/api/student/exams/{examId}/submission` | GET | `examId: string` | `{ submission: ExamSubmission, answers: Answer[] }` | Get exam submission record |
| `uploadAnswerImage` | `/api/student/upload-answer-image` | POST | `file: File, examId: string, questionNumber: number` | `{ success: boolean, imageUrl: string }` | Upload answer image |

### Profile Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `updateProfile` | `/api/student/profile` | PUT | `profileData: object` | `{ success: boolean, profile: StudentProfile }` | Update profile |
| `changePassword` | `/api/student/change-password` | PUT | `{ oldPassword, newPassword }` | `{ success: boolean, message: string }` | Change password |
| `uploadAvatar` | `/api/student/upload-avatar` | POST | `file: File` | `{ success: boolean, avatarUrl: string }` | Upload avatar |

### Region Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `requestRegionChange` | `/api/student/region-change` | POST | `{ province, school, reason }` | `{ success: boolean, requestId: string }` | Request region change |
| `getRegionChangeStatus` | `/api/student/region-change-status` | GET | None | `{ status: string, request: RegionChangeRequest }` | Get region change status |

### Scores & Statistics

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getScores` | `/api/student/scores` | GET | `{ page?, limit?, examId?, status? }` | `{ scores: Score[], total: number, pagination: PaginationInfo }` | Get score list |
| `getScoreDetail` | `/api/student/exams/{examId}/score` | GET | `examId: string` | `{ score: DetailedScore, breakdown: ScoreBreakdown }` | Get detailed score |
| `getScoreRanking` | `/api/student/exams/{examId}/ranking` | GET | `examId: string` | `{ ranking: Ranking[], myRank: number, totalParticipants: number }` | Get score ranking |
| `getScoreStatistics` | `/api/student/scores/statistics` | GET | None | `{ averageScore: number, totalExams: number, statistics: ScoreStats }` | Get score statistics |
| `getDashboardData` | `/api/student/dashboard` | GET | None | `{ upcomingExams: Exam[], recentScores: Score[], notifications: Notification[] }` | Get dashboard data |

---

## Coach API

**Base URL**: `/api/coach`

### Student Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getStudents` | `/api/coach/students` | GET | `{ page?, limit?, search?, status? }` | `{ students: Student[], total: number, pagination: PaginationInfo }` | Get student list |
| `addStudent` | `/api/coach/students` | POST | `studentData: object` | `{ success: boolean, student: Student }` | Add student |
| `updateStudent` | `/api/coach/students/{studentId}` | PUT | `studentId: string, studentData: object` | `{ success: boolean, student: Student }` | Update student |
| `removeStudent` | `/api/coach/students/{studentId}` | DELETE | `studentId: string` | `{ success: boolean, message: string }` | Remove student |

### Exam Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExams` | `/api/coach/exams` | GET | `{ status?, timeRange? }` | `{ exams: Exam[], total: number }` | Get exam list |
| `getExamDetails` | `/api/coach/exams/{examId}` | GET | `examId: string` | `{ exam: ExamDetail, questions: Question[], statistics: ExamStats }` | Get exam details |
| `downloadExamFile` | `/api/coach/exams/{examId}/download` | GET | `examId: string, fileType: string` | `File (binary data)` | Download exam file |

### Answer Submission

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `submitAnswersForStudent` | `/api/coach/exams/{examId}/submit-for-student` | POST | `examId: string, submissionData: object` | `{ success: boolean, submissionId: string }` | Submit answers for student |
| `uploadAnswerImage` | `/api/coach/upload-answer-image` | POST | `examId: string, file: File, questionNumber: number, studentUsername: string` | `{ success: boolean, imageUrl: string }` | Upload answer image |
| `getSubmissions` | `/api/coach/exams/{examId}/submissions` | GET | `examId: string, studentUsername?: string` | `{ submissions: Submission[], total: number }` | Get submissions |

### Score Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getStudentScores` | `/api/coach/students/scores` | GET | `{ page?, limit?, studentId?, examId?, status?, search? }` | `{ scores: StudentScore[], total: number, pagination: PaginationInfo }` | Get student scores |
| `getStudentScoreDetail` | `/api/coach/students/{studentId}/exams/{examId}/score` | GET | `studentId: string, examId: string` | `{ score: DetailedScore, breakdown: ScoreBreakdown, ranking: number }` | Get student score detail |
| `getExamScoreStatistics` | `/api/coach/exams/{examId}/scores/statistics` | GET | `examId: string` | `{ statistics: ExamScoreStats, distribution: ScoreDistribution }` | Get exam score statistics |
| `getStudentRanking` | `/api/coach/exams/{examId}/ranking` | GET | `examId: string, studentId?: string` | `{ rankings: StudentRanking[], myStudents: StudentRank[] }` | Get student ranking |
| `exportScoreReport` | `/api/coach/exams/{examId}/scores/export` | POST | `examId: string, format: string` | `File (binary data)` | Export score report |
| `getGradesOverview` | `/api/coach/grades/overview` | GET | None | `{ overview: GradeOverview, trends: GradeTrend[] }` | Get grades overview |
| `getGradesDetails` | `/api/coach/grades/details` | GET | `{ examId?, studentId?, startDate?, endDate? }` | `{ grades: DetailedGrade[], summary: GradeSummary }` | Get detailed grades |

### Profile & Settings

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getProfile` | `/api/coach/profile` | GET | None | `{ profile: CoachProfile, statistics: CoachStats }` | Get profile |
| `updateProfile` | `/api/coach/profile` | PUT | `profileData: object` | `{ success: boolean, profile: CoachProfile }` | Update profile |
| `changePassword` | `/api/coach/change-password` | PUT | `{ oldPassword, newPassword }` | `{ success: boolean, message: string }` | Change password |
| `uploadAvatar` | `/api/coach/upload-avatar` | POST | `file: File` | `{ success: boolean, avatarUrl: string }` | Upload avatar |

### Region Change Requests

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `submitRegionChangeRequest` | `/api/coach/region-change-request` | POST | `{ province, school, reason }` | `{ success: boolean, requestId: string }` | Submit region change request |
| `getMyRegionChangeRequests` | `/api/coach/profile/change-region-requests` | GET | None | `{ requests: RegionChangeRequest[], total: number }` | Get my region change requests |

### Dashboard

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getDashboardStats` | `/api/coach/dashboard/stats` | GET | None | `{ students: StudentStats, exams: ExamStats, performance: PerformanceStats }` | Get dashboard statistics |

---

## Grader API

**Base URL**: `/api/grader`

### Grading Tasks

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getGradingTasks` | `/api/grader/tasks` | GET | `{ status?, page?, limit?, examId?, priority? }` | `{ tasks: GradingTask[], total: number, pagination: PaginationInfo }` | Get grading task list |
| `getGradingTaskDetail` | `/api/grader/tasks/{taskId}` | GET | `taskId: string` | `{ task: GradingTaskDetail, submission: Submission, exam: ExamInfo }` | Get grading task detail |
| `startGradingTask` | `/api/grader/tasks/{taskId}/start` | POST | `taskId: string` | `{ success: boolean, task: GradingTask }` | Start grading task |
| `submitGradingResult` | `/api/grader/tasks/{taskId}/submit` | POST | `taskId: string, gradingData: object` | `{ success: boolean, taskId: string, scores: ScoreResult }` | Submit grading result |
| `saveGradingProgress` | `/api/grader/tasks/{taskId}/progress` | PUT | `taskId: string, progressData: object` | `{ success: boolean, progress: GradingProgress }` | Save grading progress |
| `abandonGradingTask` | `/api/grader/tasks/{taskId}/abandon` | POST | `taskId: string` | `{ success: boolean, message: string }` | Abandon grading task |

### Question Grading

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExamQuestionScores` | `/api/grader/exams/{examId}/questions/scores` | GET | `examId: string` | `{ questions: QuestionScore[], totalScore: number }` | Get question scores config |
| `submitQuestionScore` | `/api/grader/tasks/{taskId}/questions/{questionNumber}/score` | POST | `taskId: string, questionNumber: number, scoreData: object` | `{ success: boolean, score: QuestionGrade }` | Submit question score |
| `getQuestionGradingHistory` | `/api/grader/tasks/{taskId}/questions/{questionNumber}/history` | GET | `taskId: string, questionNumber: number` | `{ history: GradingHistory[], averageScore: number }` | Get grading history |

### Exam Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getAvailableExams` | `/api/grader/exams` | GET | `{ status?, page?, limit?, subject? }` | `{ exams: GraderExam[], total: number, pagination: PaginationInfo }` | Get available exams |
| `getExamDetail` | `/api/grader/exams/{examId}` | GET | `examId: string` | `{ exam: ExamDetail, gradingInfo: GradingInfo, progress: GradingProgress }` | Get exam detail |
| `getExamGradingProgress` | `/api/grader/exams/{examId}/progress` | GET | `examId: string` | `{ progress: ExamGradingProgress, statistics: GradingStats }` | Get exam grading progress |

### Submission Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getSubmissionDetail` | `/api/grader/submissions/{submissionId}` | GET | `submissionId: string` | `{ submission: SubmissionDetail, answers: Answer[], student: StudentInfo }` | Get submission detail |
| `getAnswerImage` | `/api/grader/images` | GET | `{ url: string }` | `File (binary image data)` | Get answer image |

### Statistics & History

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getGradingStatistics` | `/api/grader/statistics` | GET | None | `{ totalGraded: number, averageTime: number, statistics: GraderStats }` | Get grading statistics |
| `getGradingHistory` | `/api/grader/history` | GET | None | `{ history: GradingHistoryItem[], total: number }` | Get grading history |

### Profile Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getProfile` | `/api/grader/profile` | GET | None | `{ profile: GraderProfile, statistics: GraderStats }` | Get profile |
| `updateProfile` | `/api/grader/profile` | PUT | `profileData: object` | `{ success: boolean, profile: GraderProfile }` | Update profile |
| `changePassword` | `/api/grader/change-password` | PUT | `{ oldPassword, newPassword }` | `{ success: boolean, message: string }` | Change password |

### File Operations

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `downloadFile` | `/api/grader/files/{fileId}` | GET | `fileId: string` | `File (binary data)` | Download file |

---

## Admin API

**Base URL**: `/api/admin`

### User Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getPendingUsers` | `/api/admin/users/pending` | GET | None | `{ users: PendingUser[], total: number }` | Get pending users |
| `approveUser` | `/api/admin/users/approve` | POST | `userId: string, action: string, reason?: string` | `{ success: boolean, user: User }` | Approve user |
| `getApprovedUsers` | `/api/admin/users/approved` | GET | `{ page?, limit?, role?, status?, search? }` | `{ users: User[], total: number, pagination: PaginationInfo }` | Get approved users |
| `updateUserStatus` | `/api/admin/users/status` | PUT | `userId: string, status: string` | `{ success: boolean, user: User }` | Update user status |
| `deleteUser` | `/api/admin/users/{userId}` | DELETE | `userId: string` | `{ success: boolean, message: string }` | Delete user |

### Coach-Student Relations

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getCoachStudents` | `/api/admin/coach-students` | GET | None | `{ relations: CoachStudentRelation[], total: number }` | Get coach-student relations |
| `getCoachStudentsStats` | `/api/admin/coach-students/stats` | GET | None | `{ statistics: CoachStudentStats }` | Get coach-student statistics |
| `createCoachStudentRelation` | `/api/admin/coach-students` | POST | `{ coachId, studentId }` | `{ success: boolean, relation: CoachStudentRelation }` | Create coach-student relation |
| `deleteCoachStudentRelation` | `/api/admin/coach-students/{relationId}` | DELETE | `relationId: string` | `{ success: boolean, message: string }` | Delete coach-student relation |

### Region Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getRegions` | `/api/admin/regions` | GET | None | `{ regions: Region[], provinces: Province[], schools: School[] }` | Get all regions |
| `getProvinces` | `/api/admin/regions/provinces` | GET | None | `{ provinces: Province[], total: number }` | Get provinces |
| `addProvince` | `/api/admin/regions/provinces` | POST | `provinceName: string` | `{ success: boolean, province: Province }` | Add province |
| `getSchoolsByProvince` | `/api/admin/regions/schools` | GET | `provinceId: string` | `{ schools: School[], total: number }` | Get schools by province |
| `addSchool` | `/api/admin/regions/schools` | POST | `provinceId: string, schoolName: string` | `{ success: boolean, school: School }` | Add school |
| `updateSchool` | `/api/admin/regions/schools/{schoolId}` | PUT | `schoolId: string, schoolData: object` | `{ success: boolean, school: School }` | Update school |
| `deleteSchool` | `/api/admin/regions/schools/{schoolId}` | DELETE | `schoolId: string` | `{ success: boolean, message: string }` | Delete school |
| `deleteProvince` | `/api/admin/regions/provinces/{provinceId}` | DELETE | `provinceId: string` | `{ success: boolean, message: string }` | Delete province |

### Region Change Requests

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getRegionChangeRequests` | `/api/admin/regions/change-requests` | GET | `{ status?, page?, limit? }` | `{ requests: RegionChangeRequest[], total: number, pagination: PaginationInfo }` | Get region change requests |
| `handleRegionChangeRequest` | `/api/admin/regions/change-requests/{requestId}` | POST | `requestId: string, action: string, reason?: string` | `{ success: boolean, request: RegionChangeRequest }` | Handle region change request |

### Exam Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getExams` | `/api/admin/exams` | GET | `{ page?, limit?, status? }` | `{ exams: AdminExam[], total: number, pagination: PaginationInfo }` | Get exam list |
| `createExam` | `/api/admin/exams` | POST | `examData: object` | `{ success: boolean, exam: Exam }` | Create exam |
| `updateExam` | `/api/admin/exams/{examId}` | PUT | `examId: string, examData: object` | `{ success: boolean, exam: Exam }` | Update exam |
| `publishExam` | `/api/admin/exams/{examId}/publish` | POST | `examId: string` | `{ success: boolean, exam: Exam }` | Publish exam |
| `unpublishExam` | `/api/admin/exams/{examId}/unpublish` | POST | `examId: string` | `{ success: boolean, exam: Exam }` | Unpublish exam |
| `deleteExam` | `/api/admin/exams/{examId}` | DELETE | `examId: string` | `{ success: boolean, message: string }` | Delete exam |
| `uploadExamFiles` | `/api/admin/exams/{examId}/files` | POST | `examId: string, files: File[]` | `{ success: boolean, files: ExamFile[] }` | Upload exam files |

### Question Score Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `setQuestionScores` | `/api/admin/exams/{examId}/questions/scores` | POST | `examId: string, scores: object` | `{ success: boolean, scores: QuestionScore[] }` | Set question scores |
| `getQuestionScores` | `/api/admin/exams/{examId}/questions/scores` | GET | `examId: string` | `{ scores: QuestionScore[], totalScore: number }` | Get question scores |

### Grading Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getGraders` | `/api/admin/graders` | GET | `{ page?, limit?, status?, expertise? }` | `{ graders: Grader[], total: number, pagination: PaginationInfo }` | Get graders list |
| `getGradingTasks` | `/api/admin/grading/tasks` | GET | `{ page?, limit?, examId?, graderId?, status? }` | `{ tasks: AdminGradingTask[], total: number, pagination: PaginationInfo }` | Get grading tasks |
| `assignGradingTask` | `/api/admin/grading/assign` | POST | `assignmentData: object` | `{ success: boolean, assignments: GradingAssignment[] }` | Assign grading task |
| `getGradingProgress` | `/api/admin/grading/progress/{examId}` | GET | `examId: string` | `{ progress: GradingProgress, statistics: GradingStats }` | Get grading progress |

### System Settings

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getSystemSettings` | `/api/admin/system/settings` | GET | None | `{ settings: SystemSettings }` | Get system settings |
| `updateSystemSettings` | `/api/admin/system/settings` | PUT | `settings: object` | `{ success: boolean, settings: SystemSettings }` | Update system settings |

### Admin Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getAdmins` | `/api/admin/system/admins` | GET | None | `{ admins: AdminUser[], total: number }` | Get admin list |
| `createAdmin` | `/api/admin/system/admins` | POST | `adminData: object` | `{ success: boolean, admin: AdminUser }` | Create admin |
| `updateAdmin` | `/api/admin/system/admins/{adminId}` | PUT | `adminId: string, adminData: object` | `{ success: boolean, admin: AdminUser }` | Update admin |
| `resetAdminPassword` | `/api/admin/system/admins/{adminId}/reset-password` | PUT | `adminId: string, newPassword: string` | `{ success: boolean, message: string }` | Reset admin password |
| `deleteAdmin` | `/api/admin/system/admins/{adminId}` | DELETE | `adminId: string` | `{ success: boolean, message: string }` | Delete admin |

### File Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `uploadAvatar` | `/api/admin/upload-avatar` | POST | `file: File` | `{ success: boolean, avatarUrl: string }` | Upload avatar |

### Student Registration Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getStudentRegistrations` | `/api/admin/student-registrations` | GET | `{ status?, page?, limit? }` | `{ registrations: StudentRegistration[], total: number, pagination: PaginationInfo }` | Get student registration requests |
| `reviewStudentRegistration` | `/api/admin/student-registrations/{requestId}/review` | POST | `requestId: string, { action, note? }` | `{ success: boolean, registration: StudentRegistration }` | Review student registration |

### Dashboard

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getDashboardStats` | `/api/admin/dashboard/stats` | GET | None | `{ users: UserStats, exams: ExamStats, grading: GradingStats, system: SystemStats }` | Get dashboard statistics |
| `getProfile` | `/api/admin/profile` | GET | None | `{ profile: AdminProfile, permissions: AdminPermissions }` | Get admin profile |

---

## Base Configuration

- **Base URL**: `http://localhost:3001/api` (configured in `.env`)
- **Authentication**: Bearer Token (JWT) in Authorization header
- **Content-Type**: `application/json` for most requests
- **Error Handling**: Unified error handling with 401 redirect to login
- **Type Safety**: All APIs use unified type system from `src/types/api.ts` and `src/types/common.ts`

## Notes

1. **Password Hashing**: All password fields are automatically hashed using `PasswordHasher` utility before sending
2. **File Uploads**: Use `multipart/form-data` content type
3. **Query Parameters**: Built using the `buildQueryParams` method from `BaseAPI`
4. **Token Validation**: All requests include automatic token validation and error handling
5. **Singleton Pattern**: The `apiClient` provides a singleton pattern for consistent configuration
6. **Error Handling**: Automatic 401 handling with redirect to login page
7. **Type Safety**: Full TypeScript support with comprehensive type definitions

---

**Last Updated**: June 27, 2025
**Version**: Frontend v1.0
**Backend URL**: `http://localhost:3001`
