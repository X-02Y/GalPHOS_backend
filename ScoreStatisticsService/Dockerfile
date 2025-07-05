FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制应用文件
COPY target/scala-3.4.2/ScoreStatisticsService-assembly-0.1.0-SNAPSHOT.jar app.jar
COPY server_config.json server_config.json

# 创建非root用户
RUN addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --gid 1001 appuser

# 更改文件所有者
RUN chown -R appuser:appgroup /app

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 3006

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:3006/health || exit 1

# 设置JVM参数
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC -XX:+UseStringDeduplication"

# 启动应用
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
