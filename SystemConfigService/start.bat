@echo off
echo Starting System Configuration Service...
cd "%~dp0"
sbt "runMain com.galphos.systemconfig.SystemConfigServiceMain"
pause
