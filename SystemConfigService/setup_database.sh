#!/bin/bash
echo "初始化 SystemConfigService 数据库..."

export PGPASSWORD=postgres
export PGUSER=postgres
export PGHOST=localhost
export PGPORT=5432
export PGDATABASE=galphos_systemconfig

echo "创建数据库..."
createdb -U $PGUSER -h $PGHOST -p $PGPORT $PGDATABASE

echo "执行初始化脚本..."
psql -U $PGUSER -h $PGHOST -p $PGPORT -d $PGDATABASE -f init_database.sql

echo "数据库初始化完成!"
