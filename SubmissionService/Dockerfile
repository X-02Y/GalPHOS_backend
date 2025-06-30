FROM openjdk:11-jre-slim

WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制应用文件
COPY target/scala-3.4.2/SubmissionService-assembly-*.jar app.jar
COPY server_config.json .

# 创建日志目录
RUN mkdir -p logs

# 暴露端口
EXPOSE 3004

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:3004/health || exit 1

# 启动应用
CMD ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]
