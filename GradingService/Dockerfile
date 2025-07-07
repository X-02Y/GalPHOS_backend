# 使用官方 OpenJDK 基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 安装必要的工具
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制项目文件
COPY . .

# 赋予启动脚本执行权限
RUN chmod +x start.sh

# 暴露端口
EXPOSE 3005

# 设置环境变量
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SBT_OPTS="-Xmx1g -XX:+UseG1GC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:3005/health || exit 1

# 启动命令
CMD ["./start.sh"]
