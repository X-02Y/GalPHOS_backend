# Test Exam Management Service API Endpoints
# Make sure the service is running on port 3003

$baseUrl = "http://localhost:3003"
$token = "your-test-token-here"

Write-Host "Testing Exam Management Service API..." -ForegroundColor Green

# Test health check
Write-Host "`nTesting health check..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
    Write-Host "Health check: $response" -ForegroundColor Green
} catch {
    Write-Host "Health check failed: $_" -ForegroundColor Red
}

# Test service info
Write-Host "`nTesting service info..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/info" -Method Get
    Write-Host "Service info: $($response | ConvertTo-Json)" -ForegroundColor Green
} catch {
    Write-Host "Service info failed: $_" -ForegroundColor Red
}

# Test student exams endpoint (without token)
Write-Host "`nTesting student exams (without token)..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/student/exams" -Method Get
    Write-Host "Student exams: $($response | ConvertTo-Json)" -ForegroundColor Green
} catch {
    Write-Host "Student exams failed (expected): $_" -ForegroundColor Yellow
}

# Test admin exams endpoint (without token)
Write-Host "`nTesting admin exams (without token)..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/admin/exams" -Method Get
    Write-Host "Admin exams: $($response | ConvertTo-Json)" -ForegroundColor Green
} catch {
    Write-Host "Admin exams failed (expected): $_" -ForegroundColor Yellow
}

# Test with token (if provided)
if ($token -ne "your-test-token-here") {
    Write-Host "`nTesting with authentication token..." -ForegroundColor Yellow
    $headers = @{ Authorization = "Bearer $token" }
    
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/student/exams" -Method Get -Headers $headers
        Write-Host "Student exams with token: $($response | ConvertTo-Json)" -ForegroundColor Green
    } catch {
        Write-Host "Student exams with token failed: $_" -ForegroundColor Red
    }
}

Write-Host "`nAPI testing completed!" -ForegroundColor Green
