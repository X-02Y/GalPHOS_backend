# Makefile for SystemConfigService

.PHONY: all clean compile run package test db-init start help

all: compile

help:
	@echo "GalPHOS 系统配置服务 (SystemConfigService) 开发命令:"
	@echo "  make compile    - 编译项目"
	@echo "  make run        - 运行服务"
	@echo "  make package    - 打包项目"
	@echo "  make test       - 运行测试"
	@echo "  make db-init    - 初始化数据库"
	@echo "  make docker-up  - 启动 Docker 容器"
	@echo "  make docker-down - 停止 Docker 容器"
	@echo "  make clean      - 清理编译文件"

compile:
	sbt compile

run:
	sbt "runMain com.galphos.systemconfig.SystemConfigServiceMain"

package:
	sbt assembly

test:
	sbt test

db-init:
	@echo "初始化数据库..."
	@if [ "$(OS)" = "Windows_NT" ]; then \
		setup_database.bat; \
	else \
		chmod +x setup_database.sh && ./setup_database.sh; \
	fi

docker-up:
	docker-compose up -d

docker-down:
	docker-compose down

clean:
	sbt clean
