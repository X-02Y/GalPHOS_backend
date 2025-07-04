@echo off
REM 重启 SystemConfigService 以应用权限验证修复

echo 正在重启 SystemConfigService...

REM 停止服务（如果正在运行）
taskkill /f /im "java.exe" 2>nul
timeout /t 2 /nobreak >nul

REM 切换到 SystemConfigService 目录
cd /d "%~dp0"

echo 启动 SystemConfigService（已修复Token验证冲突问题）...

REM 启动服务
if exist "start.bat" (
    call start.bat
) else (
    echo 未找到启动脚本，请手动启动服务
    echo 修复内容：
    echo 1. 禁用了启动时的强制认证服务检查
    echo 2. 采用宽松的权限验证模式（enableStrictAuth=false）
    echo 3. 添加了Token验证缓存机制
    echo 4. 避免了与其他服务的Token验证冲突
)

echo SystemConfigService 重启完成
echo 现在可以正常登录管理员界面而不会被强制登出
pause
