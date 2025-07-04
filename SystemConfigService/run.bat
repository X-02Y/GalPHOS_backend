@echo off
setlocal enabledelayedexpansion

if "%1"=="test-datetime" (
    echo Running ZonedDateTime test...
    call sbt "runMain com.galphos.systemconfig.TestZonedDateTime"
) else (
    echo Building System Configuration Service...
    call sbt clean compile
    
    echo Starting System Configuration Service...
    call sbt "runMain com.galphos.systemconfig.SystemConfigServiceMain"
)
