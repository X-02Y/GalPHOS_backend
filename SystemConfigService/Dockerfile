# 使用OpenJDK 11作为基础镜像
FROM openjdk:11-jre-slim

# 设置工作目录
WORKDIR /app

# 安装必要的系统依赖
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

# 创建应用用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 复制SBT构建文件
COPY build.sbt ./
COPY project/ project/

# 复制源代码
COPY src/ src/
COPY server_config.json ./

# 安装SBT
RUN curl -L -o sbt.tgz https://github.com/sbt/sbt/releases/download/v1.9.6/sbt-1.9.6.tgz && \
    tar -xzf sbt.tgz && \
    mv sbt/bin/sbt /usr/local/bin/ && \
    mv sbt/bin/sbt-launch.jar /usr/local/bin/ && \
    rm -rf sbt sbt.tgz

# 编译应用程序
RUN sbt compile stage

# 切换到应用用户
USER appuser

# 暴露端口
EXPOSE 8085

# 设置环境变量
ENV JAVA_OPTS="-Xmx1G -XX:+UseG1GC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8085/api/system/health || exit 1

# 启动应用程序
CMD ["sbt", "run"]
