# File Storage Service

A microservice for handling file uploads, downloads, and management in the GalPHOS education platform.

## Features

- **File Upload**: Support for multiple file types (images, documents, PDFs)
- **File Download**: Secure file retrieval with authorization
- **File Management**: CRUD operations on file metadata
- **Multi-Category Support**: Different file categories (exam files, answer images, documents, avatars)
- **Role-Based Access**: Different access levels for admins, students, coaches, and graders
- **Database Integration**: PostgreSQL for metadata storage
- **Local File Storage**: Files stored on local filesystem

## API Endpoints

### Upload Endpoints
- `POST /api/admin/exams/{examId}/upload` - Admin exam file upload
- `POST /api/student/upload/answer-image` - Student answer image upload
- `POST /api/coach/exams/{examId}/upload-answer` - Coach proxy upload
- `POST /api/upload/document` - General document upload
- `POST /api/upload/file` - Generic file upload

### Download Endpoints
- `GET /api/files/{fileId}/download` - General file download
- `GET /api/student/files/{fileId}/download` - Student file access
- `GET /api/grader/files/{fileId}/download` - Grader file access

### Management Endpoints
- `GET /api/files/{fileId}` - Get file information
- `DELETE /api/files/{fileId}` - Delete file (admin only)
- `GET /api/files` - List files with filters
- `GET /api/grader/images` - Get grading images

### Internal Endpoints
- `POST /internal/upload` - Internal microservice communication

## Configuration

The service is configured via `server_config.json`:

```json
{
  "serverIP": "localhost",
  "serverPort": 3008,
  "jdbcUrl": "jdbc:postgresql://localhost:5432/galphos_filestorage",
  "username": "postgres",
  "password": "password",
  "storage": {
    "localPath": "./storage",
    "baseUrl": "http://localhost:3008",
    "maxFileSize": {
      "avatar": 5242880,
      "answer-image": 10485760,
      "exam-file": 52428800,
      "document": 10485760
    }
  }
}
```

## Prerequisites

- Java 11 or higher
- Scala 3.4.2
- SBT 1.9.8
- PostgreSQL 12 or higher

## Setup

1. **Database Setup**:
   ```bash
   # Windows
   setup_database.bat
   
   # Linux/Mac
   chmod +x setup_database.sh && ./setup_database.sh
   ```

2. **Configure Database**:
   - Update `server_config.json` with your PostgreSQL credentials
   - Ensure PostgreSQL is running

3. **Start Service**:
   ```bash
   # Windows
   start.bat
   
   # Linux/Mac
   chmod +x start.sh && ./start.sh
   ```

## File Storage

Files are stored in the local filesystem under the configured `storage.localPath` directory. The service generates unique IDs for each file and maintains metadata in PostgreSQL.

### File Categories

- **avatar**: User profile pictures (5MB limit)
- **answer-image**: Student answer images (10MB limit)
- **exam-file**: Exam questions/answers (50MB limit)
- **document**: General documents (10MB limit)

### Supported File Types

**Images**: JPEG, PNG, GIF, WebP, BMP
**Documents**: PDF, DOC, DOCX, XLS, XLSX, TXT

## Security

- JWT-based authentication
- Role-based access control
- File type validation
- File size limits
- Internal API key for microservice communication

## Development

### Build
```bash
sbt compile
```

### Test
```bash
sbt test
```

### Package
```bash
sbt assembly
```

### Run
```bash
sbt run
```

## Database Schema

The service uses a single `files` table:

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
  upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Logging

The service uses SLF4J with Logback for logging. Logs include:
- Request/response details
- File upload/download operations
- Error conditions
- Authentication events

## Integration

This service integrates with other GalPHOS microservices:
- **ExamMS**: Exam file management
- **UserManagementService**: Authentication
- **SubmissionService**: Answer image storage

## Troubleshooting

### Common Issues

1. **Database Connection Failed**:
   - Check PostgreSQL is running
   - Verify credentials in `server_config.json`
   - Ensure database exists

2. **File Upload Failed**:
   - Check file size limits
   - Verify file type is allowed
   - Ensure storage directory exists and is writable

3. **Authentication Failed**:
   - Verify JWT secret matches other services
   - Check token expiration
   - Ensure proper Authorization header format

### Logs Location
Logs are written to console and can be redirected to files as needed.

## License

Part of the GalPHOS education platform.
