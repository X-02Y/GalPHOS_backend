@echo off
chcp 65001 >nul
title GalPHOS Backend Services Shutdown
echo ====================================
echo    GalPHOS 微服务集群停止脚本
echo ====================================
echo.

REM 设置颜色
set "ESC="
set "GREEN=%ESC%[32m"
set "YELLOW=%ESC%[33m"
set "RED=%ESC%[31m"
set "BLUE=%ESC%[34m"
set "RESET=%ESC%[0m"

echo %YELLOW%警告: 此脚本将停止所有 GalPHOS 微服务%RESET%
echo.
set /p "CONFIRM=确认停止所有服务? (y/N): "
if /i not "%CONFIRM%"=="y" (
    echo 取消停止操作
    pause
    exit /b 0
)

echo.
echo %BLUE%正在停止所有微服务...%RESET%
echo.

REM 停止所有服务相关的Java进程
echo %BLUE%正在查找并停止 Java 进程...%RESET%

REM 根据端口查找并停止对应的进程
set "PORTS=3001 3002 3003 3004 3005 3006 3007 3008 3009"

for %%p in (%PORTS%) do (
    echo %YELLOW%检查端口 %%p...%RESET%
    for /f "tokens=5" %%i in ('netstat -ano ^| findstr :%%p') do (
        if not "%%i"=="0" (
            echo %BLUE%停止端口 %%p 上的进程 PID: %%i%RESET%
            taskkill /PID %%i /F >nul 2>&1
            if !errorlevel! equ 0 (
                echo %GREEN%✓ 成功停止进程 %%i%RESET%
            ) else (
                echo %YELLOW%⚠ 进程 %%i 可能已经停止%RESET%
            )
        )
    )
)

echo.
echo %BLUE%正在关闭服务窗口...%RESET%

REM 关闭所有标题包含服务名的命令行窗口
set "SERVICES=UserAuthService UserManagementService ExamMS SubmissionService GradingService ScoreStatisticsService RegionMS FileStorageService SystemConfigService"

for %%s in (%SERVICES%) do (
    echo %YELLOW%关闭 %%s 窗口...%RESET%
    taskkill /FI "WindowTitle eq %%s" /F >nul 2>&1
)

REM 额外检查：停止所有包含这些关键字的Java进程
echo.
echo %BLUE%执行额外清理...%RESET%

REM 停止所有Scala/SBT相关进程
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /FO CSV ^| findstr /v "PID"') do (
    set "PID=%%i"
    set "PID=!PID:"=!"
    if not "!PID!"=="PID" (
        echo %YELLOW%停止 Java 进程 PID: !PID!%RESET%
        taskkill /PID !PID! /F >nul 2>&1
    )
)

REM 停止可能的SBT进程
taskkill /IM "sbt.bat" /F >nul 2>&1
taskkill /IM "sbt" /F >nul 2>&1

echo.
echo %GREEN%====================================
echo         服务停止完成！
echo ====================================%RESET%
echo.
echo %BLUE%验证服务已停止：%RESET%
echo.

REM 检查端口是否还在使用
set "ACTIVE_PORTS="
for %%p in (%PORTS%) do (
    for /f %%i in ('netstat -ano ^| findstr :%%p ^| find /c ":"') do (
        if %%i gtr 0 (
            set "ACTIVE_PORTS=!ACTIVE_PORTS! %%p"
        )
    )
)

if defined ACTIVE_PORTS (
    echo %YELLOW%⚠ 以下端口仍在使用: %ACTIVE_PORTS%%RESET%
    echo   可能需要手动停止相关进程或重启计算机
) else (
    echo %GREEN%✓ 所有服务端口已释放%RESET%
)

echo.
echo %BLUE%如果需要重新启动服务，请运行: start_all_services.bat%RESET%
echo.

pause
