# GalPHOS Region & Province API Reference

**Base URL**: `http://localhost:3007/api`

This document provides a comprehensive reference for all region and province related API endpoints in the GalPHOS (Galaxy Physics Online System) application.

## Table of Contents

- [GalPHOS Region \& Province API Reference](#galphos-region--province-api-reference)
  - [Table of Contents](#table-of-contents)
  - [Authentication API (Region Related)](#authentication-api-region-related)
  - [Student Region Management API](#student-region-management-api)
    - [Region Management](#region-management)
  - [Coach Region Management API](#coach-region-management-api)
    - [Region Change Requests](#region-change-requests)
  - [Admin Region Management API](#admin-region-management-api)
    - [Region Management](#region-management-1)
    - [School Management](#school-management)
    - [Region Change Request Management](#region-change-request-management)
  - [Base Configuration](#base-configuration)
  - [Notes](#notes)
    - [Data Types](#data-types)
    - [API Usage Patterns](#api-usage-patterns)
    - [Authentication Requirements](#authentication-requirements)
    - [Error Handling](#error-handling)

---

## Authentication API (Region Related)

**Base URL**: `/api/auth`

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getProvincesAndSchools` | `/api/regions/provinces-schools` | GET | None | `{ provinces: Province[], schools: School[] }` | Get provinces and schools data for registration |

---

## Student Region Management API

**Base URL**: `/api/student`

### Region Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `requestRegionChange` | `/api/student/region-change` | POST | `{ province, school, reason }` | `{ success: boolean, requestId: string }` | Request region change |
| `getRegionChangeStatus` | `/api/student/region-change-status` | GET | None | `{ status: string, request: RegionChangeRequest }` | Get region change status |

---

## Coach Region Management API

**Base URL**: `/api/coach`

### Region Change Requests

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `submitRegionChangeRequest` | `/api/coach/region-change-request` | POST | `{ province, school, reason }` | `{ success: boolean, requestId: string }` | Submit region change request |
| `getMyRegionChangeRequests` | `/api/coach/profile/change-region-requests` | GET | None | `{ requests: RegionChangeRequest[], total: number }` | Get my region change requests |

---

## Admin Region Management API

**Base URL**: `/api/admin`

### Region Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getRegions` | `/api/admin/regions` | GET | None | `{ regions: Region[], provinces: Province[], schools: School[] }` | Get all regions |
| `getProvinces` | `/api/admin/regions/provinces` | GET | None | `{ provinces: Province[], total: number }` | Get provinces |
| `addProvince` | `/api/admin/regions/provinces` | POST | `provinceName: string` | `{ success: boolean, province: Province }` | Add province |
| `deleteProvince` | `/api/admin/regions/provinces/{provinceId}` | DELETE | `provinceId: string` | `{ success: boolean, message: string }` | Delete province |

### School Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getSchoolsByProvince` | `/api/admin/regions/schools` | GET | `provinceId: string` | `{ schools: School[], total: number }` | Get schools by province |
| `addSchool` | `/api/admin/regions/schools` | POST | `provinceId: string, schoolName: string` | `{ success: boolean, school: School }` | Add school |
| `updateSchool` | `/api/admin/regions/schools/{schoolId}` | PUT | `schoolId: string, schoolData: object` | `{ success: boolean, school: School }` | Update school |
| `deleteSchool` | `/api/admin/regions/schools/{schoolId}` | DELETE | `schoolId: string` | `{ success: boolean, message: string }` | Delete school |

### Region Change Request Management

| Method Name | Endpoint URL | HTTP Method | Passed Args | Fetched Args | Description |
|-------------|--------------|-------------|-------------|-------------|-------------|
| `getRegionChangeRequests` | `/api/admin/regions/change-requests` | GET | `{ status?, page?, limit? }` | `{ requests: RegionChangeRequest[], total: number, pagination: PaginationInfo }` | Get region change requests |
| `handleRegionChangeRequest` | `/api/admin/regions/change-requests/{requestId}` | POST | `requestId: string, action: string, reason?: string` | `{ success: boolean, request: RegionChangeRequest }` | Handle region change request |

---

## Base Configuration

- **Base URL**: `http://localhost:3007/api` (configured in `.env`)
- **Authentication**: Bearer Token (JWT) in Authorization header
- **Content-Type**: `application/json` for most requests
- **Error Handling**: Unified error handling with 401 redirect to login
- **Type Safety**: All APIs use unified type system from `src/types/api.ts` and `src/types/common.ts`

## Notes

### Data Types

**Province**
```typescript
interface Province {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
}
```

**School**
```typescript
interface School {
  id: string;
  name: string;
  provinceId: string;
  province?: Province;
  createdAt: string;
  updatedAt: string;
}
```

**Region**
```typescript
interface Region {
  id: string;
  name: string;
  provinces: Province[];
  schools: School[];
}
```

**RegionChangeRequest**
```typescript
interface RegionChangeRequest {
  id: string;
  userId: string;
  currentProvince: string;
  currentSchool: string;
  requestedProvince: string;
  requestedSchool: string;
  reason: string;
  status: 'pending' | 'approved' | 'rejected';
  reviewedBy?: string;
  reviewNote?: string;
  createdAt: string;
  updatedAt: string;
}
```

### API Usage Patterns

1. **Region Lookup**: Use `getProvincesAndSchools` for dropdown population in registration forms
2. **Region Management**: Admin APIs for managing provinces and schools hierarchy
3. **Region Change Workflow**: Student/Coach request → Admin review → Approval/Rejection
4. **Hierarchical Structure**: Provinces contain Schools, Regions contain both

### Authentication Requirements

- **Public APIs**: `getProvincesAndSchools` (used in registration)
- **Student Role**: Region change request and status APIs
- **Coach Role**: Region change request submission and viewing own requests
- **Admin Role**: Full region management and change request handling

### Error Handling

- **404**: Region/Province/School not found
- **403**: Insufficient permissions for admin operations
- **400**: Invalid data format or missing required fields
- **409**: Duplicate name conflicts when adding provinces/schools

---

**Last Updated**: June 28, 2025
**Version**: Frontend v1.0
**Backend URL**: `http://localhost:3007`
