@echo off
chcp 65001 >nul
title GalPHOS Backend Services Startup
echo ====================================
echo    GalPHOS 微服务集群启动脚本
echo ====================================
echo.

REM 设置颜色
set "ESC="
set "GREEN=%ESC%[32m"
set "YELLOW=%ESC%[33m"
set "RED=%ESC%[31m"
set "BLUE=%ESC%[34m"
set "RESET=%ESC%[0m"

echo %BLUE%正在检查所有服务目录...%RESET%
echo.

REM 检查所有服务目录是否存在
set "SERVICES=UserAuthService UserManagementService ExamMS SubmissionService GradingService ScoreStatisticsService RegionMS FileStorageService SystemConfigService"
set "MISSING_SERVICES="

for %%s in (%SERVICES%) do (
    if not exist "%%s" (
        echo %RED%错误: 服务目录 %%s 不存在%RESET%
        set "MISSING_SERVICES=!MISSING_SERVICES! %%s"
    ) else (
        echo %GREEN%✓%RESET% 找到服务目录: %%s
    )
)

if defined MISSING_SERVICES (
    echo.
    echo %RED%错误: 以下服务目录缺失: %MISSING_SERVICES%%RESET%
    echo 请确保所有微服务都已正确部署到此目录下
    pause
    exit /b 1
)

echo.
echo %YELLOW%警告: 此脚本将启动所有微服务，请确保：%RESET%
echo   1. PostgreSQL 数据库服务已启动
echo   2. 所有数据库已初始化
echo   3. 各服务的 server_config.json 配置正确
echo   4. 端口 3001-3009 未被占用
echo.
echo %BLUE%服务端口映射：%RESET%
echo   - UserAuthService      : 3001
echo   - UserManagementService: 3002  
echo   - ExamMS               : 3003
echo   - SubmissionService    : 3004
echo   - GradingService       : 3005
echo   - ScoreStatisticsService: 3006
echo   - RegionMS             : 3007
echo   - FileStorageService   : 3008
echo   - SystemConfigService  : 3009
echo.

set /p "CONFIRM=确认启动所有服务? (y/N): "
if /i not "%CONFIRM%"=="y" (
    echo 取消启动
    pause
    exit /b 0
)

echo.
echo %BLUE%====================================
echo         开始启动微服务
echo ====================================%RESET%
echo.

REM 第一阶段：启动核心基础服务
echo %YELLOW%第一阶段: 启动核心基础服务%RESET%
echo.

echo %BLUE%[1/9] 启动 UserAuthService (认证服务)...%RESET%
cd UserAuthService
if exist "start.bat" (
    start "UserAuthService" cmd /c "start.bat"
    echo %GREEN%✓ UserAuthService 启动中 (端口 3001)%RESET%
) else (
    echo %RED%✗ UserAuthService start.bat 不存在%RESET%
)
cd ..

echo %BLUE%等待认证服务初始化... (20秒)%RESET%
timeout /t 20 /nobreak >nul

echo %BLUE%[2/9] 启动 FileStorageService (文件存储服务)...%RESET%
cd FileStorageService
if exist "start.bat" (
    start "FileStorageService" cmd /c "start.bat"
    echo %GREEN%✓ FileStorageService 启动中 (端口 3008)%RESET%
) else (
    echo %RED%✗ FileStorageService start.bat 不存在%RESET%
)
cd ..

echo %BLUE%等待文件存储服务初始化... (15秒)%RESET%
timeout /t 15 /nobreak >nul

REM 第二阶段：启动业务服务
echo.
echo %YELLOW%第二阶段: 启动业务服务%RESET%
echo.

echo %BLUE%[3/9] 启动 UserManagementService (用户管理服务)...%RESET%
cd UserManagementService
if exist "start.bat" (
    start "UserManagementService" cmd /c "start.bat"
    echo %GREEN%✓ UserManagementService 启动中 (端口 3002)%RESET%
) else (
    echo %RED%✗ UserManagementService start.bat 不存在%RESET%
)
cd ..

echo %BLUE%[4/9] 启动 RegionMS (地区管理服务)...%RESET%
cd RegionMS
if exist "start.bat" (
    start "RegionMS" cmd /c "start.bat"
    echo %GREEN%✓ RegionMS 启动中 (端口 3007)%RESET%
) else (
    echo %RED%✗ RegionMS start.bat 不存在%RESET%
)
cd ..

echo %BLUE%[5/9] 启动 ExamMS (考试管理服务)...%RESET%
cd ExamMS
if exist "start.bat" (
    start "ExamMS" cmd /c "start.bat"
    echo %GREEN%✓ ExamMS 启动中 (端口 3003)%RESET%
) else (
    echo %RED%✗ ExamMS start.bat 不存在%RESET%
)
cd ..

echo %BLUE%等待业务服务初始化... (15秒)%RESET%
timeout /t 15 /nobreak >nul

REM 第三阶段：启动依赖于业务服务的服务
echo.
echo %YELLOW%第三阶段: 启动依赖服务%RESET%
echo.

echo %BLUE%[6/9] 启动 SubmissionService (提交服务)...%RESET%
cd SubmissionService
if exist "start.bat" (
    start "SubmissionService" cmd /c "start.bat"
    echo %GREEN%✓ SubmissionService 启动中 (端口 3004)%RESET%
) else (
    echo %RED%✗ SubmissionService start.bat 不存在%RESET%
)
cd ..

echo %BLUE%[7/9] 启动 GradingService (评分服务)...%RESET%
cd GradingService
if exist "start.bat" (
    start "GradingService" cmd /c "start.bat"
    echo %GREEN%✓ GradingService 启动中 (端口 3005)%RESET%
) else (
    echo %RED%✗ GradingService start.bat 不存在%RESET%
)
cd ..

echo %BLUE%[8/9] 启动 ScoreStatisticsService (分数统计服务)...%RESET%
cd ScoreStatisticsService
if exist "start.bat" (
    start "ScoreStatisticsService" cmd /c "start.bat"
    echo %GREEN%✓ ScoreStatisticsService 启动中 (端口 3006)%RESET%
) else (
    echo %RED%✗ ScoreStatisticsService start.bat 不存在%RESET%
)
cd ..

echo %BLUE%等待评分和统计服务初始化... (15秒)%RESET%
timeout /t 15 /nobreak >nul

REM 第四阶段：最后启动系统配置服务
echo.
echo %YELLOW%第四阶段: 启动系统配置服务%RESET%
echo.

echo %BLUE%[9/9] 启动 SystemConfigService (系统配置服务)...%RESET%
cd SystemConfigService
if exist "start.bat" (
    start "SystemConfigService" cmd /c "start.bat"
    echo %GREEN%✓ SystemConfigService 启动中 (端口 3009)%RESET%
) else (
    echo %RED%✗ SystemConfigService start.bat 不存在%RESET%
)
cd ..

echo.
echo %GREEN%====================================
echo       所有微服务启动完成！
echo ====================================%RESET%
echo.
echo %BLUE%服务状态检查：%RESET%
echo   访问以下URL检查服务是否正常运行：
echo.
echo   - UserAuthService:       http://localhost:3001/health
echo   - UserManagementService: http://localhost:3002/health  
echo   - ExamMS:                http://localhost:3003/health
echo   - SubmissionService:     http://localhost:3004/health
echo   - GradingService:        http://localhost:3005/health
echo   - ScoreStatisticsService: http://localhost:3006/health
echo   - RegionMS:              http://localhost:3007/health
echo   - FileStorageService:    http://localhost:3008/health
echo   - SystemConfigService:   http://localhost:3009/health
echo.
echo %YELLOW%注意事项：%RESET%
echo   1. 如果某个服务启动失败，请检查对应的控制台窗口中的错误信息
echo   2. 确保PostgreSQL数据库服务正在运行
echo   3. 确保所有端口未被其他程序占用
echo   4. 各服务完全启动可能需要额外1-2分钟时间
echo.
echo %BLUE%关闭所有服务：%RESET%
echo   运行 stop_all_services.bat 脚本可以停止所有服务
echo.

pause
