# Region Management Service API Test Script

$baseUrl = "http://localhost:3007/api"
$adminToken = "your-admin-jwt-token"
$studentToken = "your-student-jwt-token"
$coachToken = "your-coach-jwt-token"

# Headers
$headers = @{
    "Content-Type" = "application/json"
}

$authHeaders = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $adminToken"
}

Write-Host "Testing Region Management Service APIs" -ForegroundColor Green
Write-Host "=======================================" -ForegroundColor Green

# Test 1: Get provinces and schools (public endpoint)
Write-Host "`n1. Testing Get Provinces and Schools (Public)" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/regions/provinces-schools" -Method GET -Headers $headers
    Write-Host "✓ Success: Found $($response.provinces.Count) provinces and $($response.schools.Count) schools" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 3
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 2: Get all provinces (admin)
Write-Host "`n2. Testing Get All Provinces (Admin)" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/provinces" -Method GET -Headers $authHeaders
    Write-Host "✓ Success: Found $($response.total) provinces" -ForegroundColor Green
    $response.provinces | ForEach-Object { Write-Host "  - $($_.name)" }
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 3: Create a new province (admin)
Write-Host "`n3. Testing Create Province (Admin)" -ForegroundColor Yellow
$newProvince = @{
    provinceName = "测试省份"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/provinces" -Method POST -Headers $authHeaders -Body $newProvince
    Write-Host "✓ Success: Created province '$($response.data.name)'" -ForegroundColor Green
    $createdProvinceId = $response.data.id
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 4: Create a new school (admin)
if ($createdProvinceId) {
    Write-Host "`n4. Testing Create School (Admin)" -ForegroundColor Yellow
    $newSchool = @{
        provinceId = $createdProvinceId
        schoolName = "测试学校"
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/schools" -Method POST -Headers $authHeaders -Body $newSchool
        Write-Host "✓ Success: Created school '$($response.data.name)'" -ForegroundColor Green
        $createdSchoolId = $response.data.id
    } catch {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Test 5: Get schools by province
if ($createdProvinceId) {
    Write-Host "`n5. Testing Get Schools by Province" -ForegroundColor Yellow
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/schools?provinceId=$createdProvinceId" -Method GET -Headers $headers
        Write-Host "✓ Success: Found $($response.total) schools in province" -ForegroundColor Green
        $response.schools | ForEach-Object { Write-Host "  - $($_.name)" }
    } catch {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Test 6: Submit region change request (student)
if ($createdProvinceId -and $createdSchoolId) {
    Write-Host "`n6. Testing Submit Region Change Request (Student)" -ForegroundColor Yellow
    $studentHeaders = @{
        "Content-Type" = "application/json"
        "Authorization" = "Bearer $studentToken"
    }
    
    $regionChangeRequest = @{
        provinceId = $createdProvinceId
        schoolId = $createdSchoolId
        reason = "Transfer for better educational opportunities"
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/student/region-change" -Method POST -Headers $studentHeaders -Body $regionChangeRequest
        Write-Host "✓ Success: Submitted region change request" -ForegroundColor Green
        $requestId = $response.data.requestId
    } catch {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Test 7: Get region change requests (admin)
Write-Host "`n7. Testing Get Region Change Requests (Admin)" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/change-requests?status=pending&page=1&limit=10" -Method GET -Headers $authHeaders
    Write-Host "✓ Success: Found $($response.total) pending requests" -ForegroundColor Green
    $response.requests | ForEach-Object { 
        Write-Host "  - Request $($_.id): $($_.userType) wants to change to $($_.requestedProvince.name)" 
    }
} catch {
    Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 8: Handle region change request (admin)
if ($requestId) {
    Write-Host "`n8. Testing Handle Region Change Request (Admin)" -ForegroundColor Yellow
    $handleRequest = @{
        action = "approve"
        reason = "Approved by admin for testing"
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/change-requests/$requestId" -Method POST -Headers $authHeaders -Body $handleRequest
        Write-Host "✓ Success: Handled region change request" -ForegroundColor Green
        Write-Host "  Status: $($response.data.status)"
    } catch {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Cleanup: Delete created test data
if ($createdSchoolId) {
    Write-Host "`n9. Cleanup: Deleting test school" -ForegroundColor Yellow
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/schools/$createdSchoolId" -Method DELETE -Headers $authHeaders
        Write-Host "✓ Success: Deleted test school" -ForegroundColor Green
    } catch {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

if ($createdProvinceId) {
    Write-Host "`n10. Cleanup: Deleting test province" -ForegroundColor Yellow
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/admin/regions/provinces/$createdProvinceId" -Method DELETE -Headers $authHeaders
        Write-Host "✓ Success: Deleted test province" -ForegroundColor Green
    } catch {
        Write-Host "✗ Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n=======================================" -ForegroundColor Green
Write-Host "API Testing Complete!" -ForegroundColor Green
Write-Host "Note: Replace the token variables with actual JWT tokens for full testing" -ForegroundColor Cyan
