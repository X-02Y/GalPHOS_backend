# Exam Management Service

A comprehensive Scala-based microservice for managing exams in the GalPHOS education platform.

## Features

- **Role-based API endpoints** for Students, Coaches, Graders, and Administrators
- **Complete exam lifecycle management** (create, publish, manage, delete)
- **Question and scoring configuration**
- **File upload and management** for exam materials
- **JWT-based authentication** with role verification
- **PostgreSQL database** with connection pooling
- **RESTful API** with JSON responses
- **CORS support** for frontend integration

## Architecture

The service follows a clean architecture pattern:

```
src/main/scala/
├── Config/           # Configuration classes
├── Controllers/      # HTTP request handlers
├── Database/         # Database connection and operations
├── Models/           # Data models and DTOs
├── Process/          # Server initialization and utilities
└── Services/         # Business logic layer
```

## Prerequisites

- **Scala 3.4.2**
- **SBT 1.10.6**
- **PostgreSQL** with database `exam_service`
- **Java 11 or higher**

## Database Setup

1. Make sure PostgreSQL is running with user `db` and password `root`
2. Run the database setup script:
   ```cmd
   setup_database.bat
   ```

This will create the `exam_service` database with the required tables:
- `exams` - Main exam information
- `questions` - Exam questions
- `question_scores` - Scoring configuration
- `exam_files` - File attachments
- `exam_permissions` - Role-based access control

## Configuration

Edit `server_config.json` to configure:
- Server IP and port (default: localhost:3003)
- Database connection settings
- External service URLs (auth, file storage, etc.)

## Running the Service

### Development Mode
```cmd
start.bat
```

### Manual Steps
```cmd
sbt compile
sbt run
```

## API Endpoints

### Student APIs
- `GET /api/student/exams` - Get exam list (view only)
- `GET /api/student/exams/{examId}` - Get exam details
- `GET /api/student/exams/{examId}/score` - Get detailed score
- `GET /api/student/exams/{examId}/ranking` - Get score ranking

### Coach APIs
- `GET /api/coach/exams` - Get exam list with filtering
- `GET /api/coach/exams/{examId}` - Get exam details with statistics
- `GET /api/coach/exams/{examId}/download` - Download exam files
- `GET /api/coach/exams/{examId}/scores/statistics` - Get score statistics
- `GET /api/coach/exams/{examId}/ranking` - Get student ranking
- `POST /api/coach/exams/{examId}/scores/export` - Export score report

### Grader APIs
- `GET /api/grader/exams` - Get exams available for grading
- `GET /api/grader/exams/{examId}` - Get exam details with grading info
- `GET /api/grader/exams/{examId}/progress` - Get grading progress
- `GET /api/grader/exams/{examId}/questions/scores` - Get question scores

### Admin APIs
- `GET /api/admin/exams` - Get comprehensive exam list
- `POST /api/admin/exams` - Create new exam
- `PUT /api/admin/exams/{examId}` - Update exam
- `DELETE /api/admin/exams/{examId}` - Delete exam
- `POST /api/admin/exams/{examId}/publish` - Publish exam
- `POST /api/admin/exams/{examId}/unpublish` - Unpublish exam
- `POST /api/admin/exams/{examId}/files` - Upload exam files
- `POST /api/admin/exams/{examId}/questions/scores` - Set question scores
- `GET /api/admin/exams/{examId}/questions/scores` - Get question scores

## Authentication

All API endpoints require JWT authentication via the `Authorization` header:
```
Authorization: Bearer <JWT_TOKEN>
```

Role-based access control:
- **Student**: Access to own exam viewing and scores
- **Coach**: Access to exam monitoring and statistics
- **Grader**: Access to grading-related endpoints
- **Admin**: Full access to all endpoints

## Testing

Run the API test script:
```powershell
./test_api.ps1
```

## Integration with Other Services

The Exam Management Service integrates with:

- **User Authentication Service** (port 3001) - Token validation
- **Submission Service** (port 3004) - Exam submissions
- **Grading Service** (port 3005) - Grading tasks
- **Score Statistics Service** (port 3006) - Score calculations
- **File Storage Service** (port 3008) - File management

## Error Handling

The service returns consistent error responses:

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

Common error codes:
- `UNAUTHORIZED` - Missing or invalid authentication
- `EXAM_NOT_FOUND` - Requested exam doesn't exist
- `FETCH_EXAMS_ERROR` - Database query failed
- `CREATE_EXAM_ERROR` - Exam creation failed
- `UPLOAD_FILES_ERROR` - File upload failed

## Logging

Logs are written to:
- Console output
- `logs/exam-service.log` (rolling daily, max 100MB per file)

Log levels can be configured in `src/main/resources/logback.xml`.

## Development

### Adding New Endpoints

1. Add the route to the appropriate controller
2. Implement the business logic in the service layer
3. Add any required database operations
4. Update the API documentation

### Database Schema Changes

1. Update `init_database.sql`
2. Add migration scripts if needed
3. Update the model classes
4. Test with existing data

## Production Deployment

1. Build the application:
   ```cmd
   sbt assembly
   ```

2. The JAR file will be created in `target/scala-3.4.2/`

3. Run with:
   ```cmd
   java -jar target/scala-3.4.2/ExamManagementService-assembly-0.1.0-SNAPSHOT.jar
   ```

## Version History

- **v1.0.0** - Initial release with full API implementation
- Role-based access control
- File upload support
- Database integration
- JWT authentication

## License

Part of the GalPHOS education platform.

## Support

For issues and questions, check the logs in `logs/exam-service.log` and verify:
1. PostgreSQL is running and accessible
2. Database `exam_service` exists and is properly configured
3. Authentication service is available (if using token validation)
4. All required dependencies are installed
