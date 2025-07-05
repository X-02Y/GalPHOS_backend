# GalPHOS Exam Management Service

This is the Exam Management Service (ExamMS) for the GalPHOS (Galaxy Physics Online System). It provides comprehensive exam lifecycle management including exam creation, question scoring, file management, and submission handling.

## Features

- **Complete Exam Lifecycle Management**
  - Exam creation, modification, and deletion
  - Question score configuration and management
  - File upload and storage integration
  - Exam status tracking and publishing

- **Role-Based Access Control**
  - Admin: Full exam management capabilities
  - Student: Access to published exams and answer submission
  - Coach: Exam monitoring and proxy submission for students
  - Grader: Access to exams for grading purposes

- **File Storage Integration**
  - Integrated with FileStorageService for all file operations
  - Support for question files, answer files, and submission images
  - Automatic file type validation and size limits

- **Submission Management**
  - Student answer submission with image upload
  - Coach proxy submission for managed students
  - Submission status tracking and scoring

## Technology Stack

- **Language**: Scala 3.4.2
- **Framework**: HTTP4S with Cats Effect
- **Database**: PostgreSQL with HikariCP connection pooling
- **JSON**: Circe for JSON processing
- **Authentication**: JWT-based authentication
- **Build Tool**: SBT 1.9.7

## API Endpoints

### Admin APIs
- `GET /api/admin/exams` - Get all exams
- `POST /api/admin/exams` - Create new exam
- `PUT /api/admin/exams/{id}` - Update exam
- `DELETE /api/admin/exams/{id}` - Delete exam
- `POST /api/admin/exams/{id}/question-scores` - Set question scores
- `GET /api/admin/exams/{id}/question-scores` - Get question scores
- `PUT /api/admin/exams/{id}/question-scores/{number}` - Update question score
- `POST /api/admin/exams/{id}/publish` - Publish exam
- `POST /api/admin/exams/{id}/unpublish` - Unpublish exam

### Student APIs
- `GET /api/student/exams` - Get available exams
- `GET /api/student/exams/{id}` - Get exam details
- `POST /api/student/exams/{id}/submit` - Submit exam answers
- `GET /api/student/exams/{id}/submission` - Get submission status

### Coach APIs
- `GET /api/coach/exams` - Get exams with participation stats
- `GET /api/coach/exams/{id}` - Get exam details with stats
- `GET /api/coach/exams/{id}/score-stats` - Get score statistics
- `POST /api/coach/exams/{id}/submissions` - Submit answers for student
- `GET /api/coach/exams/{id}/submissions` - Get student submissions

### Grader APIs
- `GET /api/grader/exams` - Get gradable exams
- `GET /api/grader/exams/{id}` - Get exam details for grading
- `GET /api/grader/exams/{id}/progress` - Get grading progress

### File Upload APIs
- `POST /api/upload/exam-files` - Upload exam files
- `POST /api/upload/answer-image` - Upload answer images
- `POST /api/coach/exams/{id}/upload-answer` - Coach upload answer images

## Configuration

### Database Configuration
Configure your PostgreSQL connection in `server_config.json`:

```json
{
  "jdbcUrl": "jdbc:postgresql://localhost:5432/galphos_exam",
  "username": "galphos_user",
  "password": "galphos_password",
  "maximumPoolSize": 20
}
```

### File Storage Service Configuration
Configure the FileStorageService integration:

```json
{
  "fileStorageService": {
    "host": "localhost",
    "port": 3008,
    "internalApiKey": "your-internal-api-key-here",
    "timeout": 30000,
    "uploadMaxSize": 52428800,
    "allowedImageTypes": ["jpg", "jpeg", "png", "gif"],
    "allowedDocumentTypes": ["pdf", "doc", "docx"]
  }
}
```

## Installation & Setup

### Prerequisites
- Java 21 or higher
- SBT 1.9.7
- PostgreSQL 13 or higher
- File Storage Service running on port 3008

### Database Setup
1. Create the database:
```sql
CREATE DATABASE galphos_exam;
CREATE USER galphos_user WITH PASSWORD 'galphos_password';
GRANT ALL PRIVILEGES ON DATABASE galphos_exam TO galphos_user;
```

2. Run the initialization script:
```bash
psql -U galphos_user -d galphos_exam -f init_database.sql
```

### Configuration
1. Copy the configuration file:
```bash
cp server_config.json.example server_config.json
```

2. Edit `server_config.json` with your database and service configurations.

### Build & Run
```bash
# Build the project
sbt compile

# Run tests
sbt test

# Start the service
sbt run

# Or use the startup scripts
# Windows:
start.bat

# Linux/macOS:
chmod +x start.sh
./start.sh
```

## Service Integration

### File Storage Service
The ExamMS integrates with the FileStorageService for all file operations:

```
Frontend → ExamMS (3003) → FileStorageService (3008) → File System
```

### Authentication
All API endpoints require JWT authentication. The service validates tokens and enforces role-based access control.

### Database Schema
The service uses the following main tables:
- `exams` - Exam information
- `exam_questions` - Question scores
- `exam_submissions` - Student submissions
- `exam_answers` - Answer details
- `exam_files` - File metadata

## Development

### Project Structure
```
src/main/scala/
├── Config/           # Configuration management
├── Controllers/      # HTTP request handlers
├── Database/         # Database connection and utilities
├── Main/            # Application entry point
├── Models/          # Data models and DTOs
├── Process/         # Initialization and business processes
└── Services/        # Business logic services
```

### Testing
Run tests with:
```bash
sbt test
```

### Logging
The service uses SLF4J with Logback for logging. Configure logging levels in `src/main/resources/logback.xml`.

## Production Deployment

### Docker Support
Build and run with Docker:
```bash
sbt assembly
docker build -t galphos-exam-service .
docker run -p 3003:3003 galphos-exam-service
```

### Performance Considerations
- Connection pooling with HikariCP
- Optimized database queries with indexes
- Efficient file handling through FileStorageService
- Configurable memory settings in startup scripts

## API Documentation

For detailed API documentation, see [EXAM_MANAGEMENT_API.md](EXAM_MANAGEMENT_API.md).

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run tests and ensure they pass
6. Submit a pull request

## License

This project is part of the GalPHOS system and is licensed under the same terms as the main project.

## Support

For issues and questions:
1. Check the logs for error details
2. Review the API documentation
3. Ensure all dependencies are properly configured
4. Verify database connectivity and permissions
