@echo off
chcp 65001 >nul
title GalPHOS Backend Management Console

REM 设置颜色
set "ESC="
set "GREEN=%ESC%[32m"
set "YELLOW=%ESC%[33m"
set "RED=%ESC%[31m"
set "BLUE=%ESC%[34m"
set "CYAN=%ESC%[36m"
set "RESET=%ESC%[0m"

:main_menu
cls
echo %CYAN%====================================
echo     GalPHOS 微服务管理控制台
echo ====================================%RESET%
echo.
echo %BLUE%当前目录: %CD%%RESET%
echo.
echo %GREEN%请选择操作：%RESET%
echo.
echo   %YELLOW%1.%RESET% 启动所有微服务
echo   %YELLOW%2.%RESET% 停止所有微服务  
echo   %YELLOW%3.%RESET% 检查服务状态
echo   %YELLOW%4.%RESET% 重启所有微服务
echo   %YELLOW%5.%RESET% 查看服务日志
echo   %YELLOW%6.%RESET% 数据库管理
echo   %YELLOW%7.%RESET% 帮助信息
echo   %YELLOW%0.%RESET% 退出
echo.
set /p "CHOICE=请输入选项 (0-7): "

if "%CHOICE%"=="1" goto start_services
if "%CHOICE%"=="2" goto stop_services
if "%CHOICE%"=="3" goto check_services
if "%CHOICE%"=="4" goto restart_services
if "%CHOICE%"=="5" goto view_logs
if "%CHOICE%"=="6" goto database_menu
if "%CHOICE%"=="7" goto help_info
if "%CHOICE%"=="0" goto exit_script

echo %RED%无效选项，请重新选择%RESET%
timeout /t 2 >nul
goto main_menu

:start_services
cls
echo %BLUE%启动所有微服务...%RESET%
echo.
call start_all_services.bat
goto main_menu

:stop_services
cls
echo %BLUE%停止所有微服务...%RESET%
echo.
call stop_all_services.bat
goto main_menu

:check_services
cls
echo %BLUE%检查服务状态...%RESET%
echo.
call check_services.bat
goto main_menu

:restart_services
cls
echo %YELLOW%重启所有微服务...%RESET%
echo.
echo %BLUE%第一步: 停止所有服务%RESET%
call stop_all_services.bat
echo.
echo %BLUE%等待5秒...%RESET%
timeout /t 5 /nobreak >nul
echo.
echo %BLUE%第二步: 启动所有服务%RESET%
call start_all_services.bat
goto main_menu

:view_logs
cls
echo %BLUE%====================================
echo         服务日志查看
echo ====================================%RESET%
echo.
echo %GREEN%可用的日志目录：%RESET%
echo.

set "LOG_COUNT=0"
for %%d in (ExamMS RegionMS SubmissionService GradingService ScoreStatisticsService SystemConfigService) do (
    if exist "%%d\logs" (
        set /a LOG_COUNT+=1
        echo   %YELLOW%!LOG_COUNT!.%RESET% %%d\logs
    )
)

if %LOG_COUNT% equ 0 (
    echo %RED%没有找到日志目录%RESET%
    echo.
    pause
    goto main_menu
)

echo.
set /p "LOG_CHOICE=请选择要查看的服务日志 (1-%LOG_COUNT%): "

set "CURRENT_COUNT=0"
for %%d in (ExamMS RegionMS SubmissionService GradingService ScoreStatisticsService SystemConfigService) do (
    if exist "%%d\logs" (
        set /a CURRENT_COUNT+=1
        if "!CURRENT_COUNT!"=="%LOG_CHOICE%" (
            echo %BLUE%打开 %%d 日志目录...%RESET%
            explorer "%%d\logs"
            goto log_menu_end
        )
    )
)

echo %RED%无效选项%RESET%
:log_menu_end
echo.
pause
goto main_menu

:database_menu
cls
echo %BLUE%====================================
echo         数据库管理
echo ====================================%RESET%
echo.
echo %GREEN%请选择数据库操作：%RESET%
echo.
echo   %YELLOW%1.%RESET% 初始化所有数据库
echo   %YELLOW%2.%RESET% 初始化指定服务数据库
echo   %YELLOW%3.%RESET% 检查数据库连接
echo   %YELLOW%4.%RESET% 查看数据库状态
echo   %YELLOW%0.%RESET% 返回主菜单
echo.
set /p "DB_CHOICE=请输入选项 (0-4): "

if "%DB_CHOICE%"=="1" goto init_all_db
if "%DB_CHOICE%"=="2" goto init_specific_db
if "%DB_CHOICE%"=="3" goto check_db_connection
if "%DB_CHOICE%"=="4" goto view_db_status
if "%DB_CHOICE%"=="0" goto main_menu

echo %RED%无效选项%RESET%
timeout /t 2 >nul
goto database_menu

:init_all_db
cls
echo %BLUE%初始化所有数据库...%RESET%
echo.
echo %YELLOW%注意: 这将重新创建所有数据库表，现有数据将丢失%RESET%
echo.
set /p "CONFIRM=确认继续? (y/N): "
if /i not "%CONFIRM%"=="y" goto database_menu

echo %BLUE%正在初始化数据库...%RESET%
echo.

REM 初始化各服务数据库
for %%s in (UserAuthService UserManagementService ExamMS FileStorageService RegionMS SubmissionService GradingService ScoreStatisticsService SystemConfigService) do (
    if exist "%%s\init_database.sql" (
        echo %YELLOW%初始化 %%s 数据库...%RESET%
        cd %%s
        psql -h localhost -p 5432 -U db -f init_database.sql
        cd ..
        echo.
    ) else (
        echo %RED%跳过 %%s (无初始化脚本)%RESET%
    )
)

echo %GREEN%所有数据库初始化完成%RESET%
pause
goto database_menu

:init_specific_db
cls
echo %BLUE%选择要初始化的服务数据库：%RESET%
echo.

set "DB_SERVICES=UserAuthService UserManagementService ExamMS FileStorageService RegionMS SubmissionService GradingService ScoreStatisticsService SystemConfigService"
set "DB_COUNT=0"

for %%s in (%DB_SERVICES%) do (
    if exist "%%s\init_database.sql" (
        set /a DB_COUNT+=1
        echo   %YELLOW%!DB_COUNT!.%RESET% %%s
    )
)

echo.
set /p "SERVICE_CHOICE=请选择服务 (1-%DB_COUNT%): "

set "CURRENT_COUNT=0"
for %%s in (%DB_SERVICES%) do (
    if exist "%%s\init_database.sql" (
        set /a CURRENT_COUNT+=1
        if "!CURRENT_COUNT!"=="%SERVICE_CHOICE%" (
            echo %BLUE%初始化 %%s 数据库...%RESET%
            cd %%s
            psql -h localhost -p 5432 -U db -f init_database.sql
            cd ..
            echo %GREEN%%%s 数据库初始化完成%RESET%
            goto specific_db_end
        )
    )
)

echo %RED%无效选项%RESET%
:specific_db_end
pause
goto database_menu

:check_db_connection
cls
echo %BLUE%检查数据库连接...%RESET%
echo.
psql -h localhost -p 5432 -U db -c "SELECT version();"
if %errorlevel% equ 0 (
    echo %GREEN%✓ 数据库连接正常%RESET%
) else (
    echo %RED%✗ 数据库连接失败%RESET%
    echo %YELLOW%请检查：%RESET%
    echo   1. PostgreSQL 服务是否启动
    echo   2. 用户名密码是否正确 (db/root)
    echo   3. 端口 5432 是否可访问
)
echo.
pause
goto database_menu

:view_db_status
cls
echo %BLUE%查看数据库状态...%RESET%
echo.
psql -h localhost -p 5432 -U db -c "\l"
echo.
pause
goto database_menu

:help_info
cls
echo %BLUE%====================================
echo           帮助信息
echo ====================================%RESET%
echo.
echo %GREEN%GalPHOS 微服务架构说明：%RESET%
echo.
echo %YELLOW%核心服务：%RESET%
echo   • UserAuthService (3001)      - 用户认证服务
echo   • FileStorageService (3008)   - 文件存储服务
echo.
echo %YELLOW%业务服务：%RESET%
echo   • UserManagementService (3002) - 用户管理服务
echo   • ExamMS (3003)               - 考试管理服务
echo   • RegionMS (3007)             - 地区管理服务
echo.
echo %YELLOW%功能服务：%RESET%
echo   • SubmissionService (3004)    - 提交服务
echo   • GradingService (3005)       - 评分服务
echo   • ScoreStatisticsService (3006) - 分数统计服务
echo.
echo %YELLOW%配置服务：%RESET%
echo   • SystemConfigService (3009)  - 系统配置服务
echo.
echo %GREEN%启动顺序要求：%RESET%
echo   1. 核心服务 (认证、文件存储)
echo   2. 业务服务 (用户管理、考试管理、地区管理)
echo   3. 功能服务 (提交、评分、统计)
echo   4. 配置服务 (系统配置)
echo.
echo %GREEN%常见问题：%RESET%
echo   • 端口被占用: 使用 netstat -ano | findstr :端口号 查看
echo   • 数据库连接失败: 检查 PostgreSQL 服务状态
echo   • 服务启动失败: 查看对应的控制台窗口错误信息
echo.
echo %GREEN%文件说明：%RESET%
echo   • start_all_services.bat  - 启动所有服务
echo   • stop_all_services.bat   - 停止所有服务
echo   • check_services.bat      - 检查服务状态
echo   • manage_services.bat     - 本管理控制台
echo.
pause
goto main_menu

:exit_script
cls
echo %GREEN%感谢使用 GalPHOS 微服务管理控制台%RESET%
echo.
exit /b 0
