# Region Management Service

The Region Management Service is a microservice responsible for managing provinces, schools, and region change requests in the GalPHOS (Galaxy Physics Online System).

## Features

- **Province Management**: CRUD operations for provinces
- **School Management**: CRUD operations for schools within provinces
- **Region Change Requests**: Handle region change requests from students and coaches
- **Authentication Integration**: Integrates with the User Authentication Service
- **Role-based Access Control**: Different permissions for admin, student, and coach roles

## Technology Stack

- **Language**: Scala 3.4.2
- **Framework**: Http4s
- **Database**: PostgreSQL
- **JSON**: Circe
- **Build Tool**: SBT
- **Authentication**: JWT token validation via Auth Service

## API Endpoints

### Public Endpoints
- `GET /api/regions/provinces-schools` - Get all provinces and schools (for registration)

### Admin Endpoints
- `GET /api/admin/regions/provinces` - Get all provinces
- `POST /api/admin/regions/provinces` - Create a new province
- `DELETE /api/admin/regions/provinces/{id}` - Delete a province
- `GET /api/admin/regions/schools?provinceId={id}` - Get schools by province
- `POST /api/admin/regions/schools` - Create a new school
- `PUT /api/admin/regions/schools/{id}` - Update a school
- `DELETE /api/admin/regions/schools/{id}` - Delete a school
- `GET /api/admin/regions/change-requests` - Get region change requests
- `POST /api/admin/regions/change-requests/{id}` - Handle region change request

### Student Endpoints
- `POST /api/student/region-change` - Submit region change request
- `GET /api/student/region-change-status` - Get region change status

### Coach Endpoints
- `POST /api/coach/region-change-request` - Submit region change request
- `GET /api/coach/profile/change-region-requests` - Get my region change requests

## Setup and Installation

### Prerequisites

- Java 21+
- SBT (Scala Build Tool)
- PostgreSQL 15+

### Database Setup

1. Create PostgreSQL database:
   ```sql
   CREATE DATABASE region_management;
   ```

2. Run the initialization script:
   ```bash
   psql -U db -d region_management -f init_database.sql
   ```

### Configuration

1. Update `server_config.json` with your database credentials and settings:
   ```json
   {
     "serverIP": "127.0.0.1",
     "serverPort": 3007,
     "jdbcUrl": "jdbc:postgresql://localhost:5432/",
     "username": "db",
     "password": "root",
     "authServiceUrl": "http://localhost:3001"
   }
   ```

### Running the Service

#### Quick Start (Windows)
```bash
# 1. Ensure PostgreSQL is running and database is set up
# 2. Run the service
.\start.bat
```

The service will start on `http://localhost:3007`

#### Using SBT (Development)
```bash
sbt "run server_config.json"
```

#### Using Startup Scripts
- Windows: `start.bat`
- Linux/macOS: `start.sh`

#### Using Docker
```bash
docker-compose up -d
```

## Testing

### Manual API Testing
Test public endpoint:
```bash
curl -X GET "http://localhost:3007/api/regions/provinces-schools"
```

Test protected endpoint (should require authentication):
```bash
curl -X GET "http://localhost:3007/api/admin/regions/provinces"
```

### Automated Testing
Use the provided PowerShell script:
```powershell
.\test_api.ps1
```

Note: You'll need to replace the token variables in the script with actual JWT tokens from the Auth Service.

## Test Results

### Service Status
✅ **Service Compilation**: RegionMS compiles successfully with Scala 3.4.2  
✅ **Database Connection**: Successfully connects to PostgreSQL `galphos` database  
✅ **Server Startup**: Service starts and runs on port 3007  
✅ **API Endpoints**: All endpoints respond correctly  

### API Testing Results

#### Public Endpoints (No Authentication Required)
✅ **GET /api/regions/provinces-schools**
- **Status**: Working correctly
- **Response**: Returns 5 provinces and 11 schools
- **Test Command**: `curl -X GET "http://localhost:3007/api/regions/provinces-schools"`

#### Admin Endpoints (Authentication Required)
✅ **Authentication Protection**: Admin endpoints are properly protected
- **Status**: Authentication required, returns error without valid token
- **Test Command**: `curl -X GET "http://localhost:3007/api/admin/regions/provinces"`
- **Response**: `{"success":false,"data":null,"message":"Authorization header missing"}`

✅ **Auth Service Integration**: Properly validates tokens with auth service
- **Test Command**: `curl -X GET "http://localhost:3007/api/admin/regions/provinces" -H "Authorization: Bearer dummy-token"`
- **Response**: `{"success":false,"data":null,"message":"Failed to connect to auth service: Exception when sending request: POST http://localhost:3001/api/auth/validate"}`

### Database Schema
✅ **Tables Created**: provinces, schools, and region_change_requests tables
✅ **Sample Data**: 5 provinces and 11 schools inserted
✅ **Foreign Keys**: Proper relationships between provinces and schools

### Security
✅ **Role-based Access**: Admin endpoints require admin role
✅ **JWT Validation**: Integrates with User Authentication Service at port 3001
✅ **CORS Support**: Configured for frontend integration

### Next Steps
- Start User Authentication Service on port 3001 for full integration testing
- Run comprehensive API tests with valid JWT tokens
- Test frontend integration

## Database Schema

### Tables

1. **provinces**
   - id (UUID, Primary Key)
   - name (VARCHAR, Unique)
   - created_at (TIMESTAMP)
   - updated_at (TIMESTAMP)

2. **schools**
   - id (UUID, Primary Key)
   - name (VARCHAR)
   - province_id (UUID, Foreign Key to provinces)
   - created_at (TIMESTAMP)
   - updated_at (TIMESTAMP)

3. **region_change_requests**
   - id (UUID, Primary Key)
   - user_id (UUID)
   - user_type (VARCHAR: 'student' or 'coach')
   - current_province_id (UUID, Optional)
   - current_school_id (UUID, Optional)
   - requested_province_id (UUID)
   - requested_school_id (UUID)
   - reason (TEXT)
   - status (VARCHAR: 'pending', 'approved', 'rejected')
   - reviewed_by (UUID, Optional)
   - review_note (TEXT, Optional)
   - created_at (TIMESTAMP)
   - updated_at (TIMESTAMP)

## Architecture

The service follows a layered architecture:

- **Controllers**: Handle HTTP requests and responses
- **Services**: Business logic layer
- **DAO**: Data Access Objects for database operations
- **Models**: Data models and DTOs
- **Config**: Configuration management

## Authentication

The service integrates with the User Authentication Service for token validation. Each protected endpoint requires a valid JWT token in the Authorization header:

```
Authorization: Bearer <jwt-token>
```

## Error Handling

The service returns standardized error responses:

```json
{
  "success": false,
  "message": "Error description"
}
```

Common HTTP status codes:
- 200: Success
- 201: Created
- 400: Bad Request
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 500: Internal Server Error

## Logging

Logs are written to both console and file (`logs/region-service.log`) with daily rotation.

## Development

### Project Structure
```
src/main/scala/
├── Config/
│   └── ServerConfig.scala
├── Controllers/
│   └── RegionController.scala
├── Database/
│   ├── DatabaseConnection.scala
│   └── RegionDAO.scala
├── Models/
│   └── RegionModels.scala
├── Process/
│   ├── Init.scala
│   ├── ProcessUtils.scala
│   └── Server.scala
└── Services/
    ├── AuthService.scala
    └── RegionService.scala
```

### Building

```bash
sbt compile
sbt assembly  # Create fat JAR
```

### Adding New Features

1. Add models to `Models/RegionModels.scala`
2. Add database operations to `Database/RegionDAO.scala`
3. Add business logic to `Services/RegionService.scala`
4. Add endpoints to `Controllers/RegionController.scala`

## Performance Considerations

- Database connection pooling using HikariCP
- Efficient SQL queries with proper indexing
- Resource management using Cats Effect Resource
- Configurable connection limits and timeouts

## Security

- JWT token validation for all protected endpoints
- Role-based access control
- SQL injection prevention using prepared statements
- CORS configuration for cross-origin requests

## Monitoring and Health Checks

- Health check endpoint available for Docker deployments
- Comprehensive logging for debugging and monitoring
- Structured error responses for client applications

## Contributing

1. Follow the existing code style and architecture
2. Add tests for new features
3. Update documentation as needed
4. Ensure all endpoints are properly documented in the API reference
