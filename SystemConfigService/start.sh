#!/bin/bash
echo "Starting System Configuration Service..."
cd "$(dirname "$0")"
sbt "runMain com.galphos.systemconfig.SystemConfigServiceMain"
