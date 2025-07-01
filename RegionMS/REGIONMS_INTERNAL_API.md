| Method Name | Endpoint URL | HTTP Method | Query Parameters | Response | Description |
|-------------|--------------|-------------|------------------|----------|-------------|
| `GetProvinceAndSchoolByID` | `http://localhost:3007/internal/regions` | `GET` | `provinceId: string (UUID), schoolId: string (UUID)` | `{ provinceName: string, schoolName: string }` | Get province and school names by their IDs |

## Usage Examples

### GET /internal/regions

**Purpose**: Internal API endpoint for other microservices to retrieve province and school names by their IDs.

**Query Parameters**:
- `provinceId` (required): UUID of the province
- `schoolId` (required): UUID of the school

**Example Request**:
```
GET http://localhost:3007/internal/regions?provinceId=123e4567-e89b-12d3-a456-426614174000&schoolId=987fcdeb-51d2-43ab-8765-123456789abc
```

**Success Response (200)**:
```json
{
  "provinceName": "Bangkok",
  "schoolName": "Bangkok University"
}
```

**Error Responses**:

**400 Bad Request** - Missing or invalid parameters:
```json
{
  "error": "Missing or invalid required parameters: provinceId, schoolId"
}
```

**400 Bad Request** - Invalid UUID format:
```json
{
  "error": "Invalid UUID format for provinceId or schoolId"
}
```

**400 Bad Request** - Province not found:
```json
{
  "error": "Province not found with ID: 123e4567-e89b-12d3-a456-426614174000"
}
```

**400 Bad Request** - School not found:
```json
{
  "error": "School not found with ID: 987fcdeb-51d2-43ab-8765-123456789abc"
}
```

**400 Bad Request** - School doesn't belong to province:
```json
{
  "error": "School with ID 987fcdeb-51d2-43ab-8765-123456789abc does not belong to province with ID 123e4567-e89b-12d3-a456-426614174000"
}
```

**500 Internal Server Error** - Server error:
```json
{
  "error": "Failed to fetch region data: Database connection error"
}
```

## Integration Notes

- This endpoint is designed for internal microservice communication
- No authentication is required (internal network only)
- The endpoint validates that the school belongs to the specified province
- Both province and school must exist in the database
- UUIDs must be in valid format