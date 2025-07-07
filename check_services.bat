@echo off
chcp 65001 >nul
title GalPHOS Backend Services Status Check
echo ====================================
echo   GalPHOS 微服务状态检查脚本
echo ====================================
echo.

REM 设置颜色
set "ESC="
set "GREEN=%ESC%[32m"
set "YELLOW=%ESC%[33m"
set "RED=%ESC%[31m"
set "BLUE=%ESC%[34m"
set "RESET=%ESC%[0m"

echo %BLUE%正在检查所有微服务状态...%RESET%
echo.

REM 定义服务信息
set "SERVICE_NAMES=UserAuthService UserManagementService ExamMS SubmissionService GradingService ScoreStatisticsService RegionMS FileStorageService SystemConfigService"
set "SERVICE_PORTS=3001 3002 3003 3004 3005 3006 3007 3008 3009"

REM 将服务名和端口对应起来
set "UserAuthService_PORT=3001"
set "UserManagementService_PORT=3002"
set "ExamMS_PORT=3003"
set "SubmissionService_PORT=3004"
set "GradingService_PORT=3005"
set "ScoreStatisticsService_PORT=3006"
set "RegionMS_PORT=3007"
set "FileStorageService_PORT=3008"
set "SystemConfigService_PORT=3009"

set "RUNNING_COUNT=0"
set "TOTAL_COUNT=9"

echo %BLUE%服务状态检查结果：%RESET%
echo ================================================================
echo  服务名称                    端口    状态      健康检查
echo ================================================================

for %%s in (%SERVICE_NAMES%) do (
    call set "PORT=%%"%%s"_PORT"%%"
    call :check_service "%%s" "!PORT!"
)

echo ================================================================
echo.

if %RUNNING_COUNT% equ %TOTAL_COUNT% (
    echo %GREEN%✓ 所有服务 (%RUNNING_COUNT%/%TOTAL_COUNT%) 正在运行%RESET%
) else (
    echo %YELLOW%⚠ 部分服务运行中 (%RUNNING_COUNT%/%TOTAL_COUNT%)%RESET%
    if %RUNNING_COUNT% equ 0 (
        echo %RED%✗ 没有服务在运行%RESET%
        echo.
        echo %BLUE%建议：运行 start_all_services.bat 启动所有服务%RESET%
    )
)

echo.
echo %BLUE%详细信息：%RESET%

REM 显示Java进程
echo.
echo %YELLOW%当前运行的 Java 进程：%RESET%
tasklist /FI "IMAGENAME eq java.exe" /FO TABLE 2>nul | findstr /v "INFO:"
if %errorlevel% neq 0 (
    echo   没有找到 Java 进程
)

echo.
echo %YELLOW%端口占用情况：%RESET%
for %%p in (%SERVICE_PORTS%) do (
    echo   端口 %%p:
    netstat -ano | findstr :%%p | findstr LISTENING
    if !errorlevel! neq 0 (
        echo     未占用
    )
)

echo.
pause
goto :eof

:check_service
set "SERVICE_NAME=%~1"
set "PORT=%~2"
set "STATUS=停止"
set "HEALTH=N/A"

REM 检查端口是否被占用
netstat -ano | findstr :%PORT% | findstr LISTENING >nul 2>&1
if %errorlevel% equ 0 (
    set "STATUS=%GREEN%运行%RESET%"
    set /a RUNNING_COUNT+=1
    
    REM 尝试健康检查
    curl -s --connect-timeout 3 http://localhost:%PORT%/health >nul 2>&1
    if !errorlevel! equ 0 (
        set "HEALTH=%GREEN%正常%RESET%"
    ) else (
        REM 尝试访问根路径
        curl -s --connect-timeout 3 http://localhost:%PORT%/ >nul 2>&1
        if !errorlevel! equ 0 (
            set "HEALTH=%YELLOW%可访问%RESET%"
        ) else (
            set "HEALTH=%RED%无响应%RESET%"
        )
    )
) else (
    set "STATUS=%RED%停止%RESET%"
    set "HEALTH=%RED%--/%RESET%"
)

REM 格式化输出服务名（补齐到28个字符）
set "PADDED_NAME=%SERVICE_NAME%                            "
set "PADDED_NAME=%PADDED_NAME:~0,28%"

echo  %PADDED_NAME% %PORT%     %STATUS%     %HEALTH%
goto :eof
