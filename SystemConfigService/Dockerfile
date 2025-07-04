FROM hseeberger/scala-sbt:17.0.2_1.6.2_3.1.1

WORKDIR /app

# 复制项目文件
COPY build.sbt ./
COPY project ./project/
COPY src ./src/
COPY server_config.json ./

# 创建日志目录
RUN mkdir -p /app/logs

# 编译应用
RUN sbt compile

# 暴露端口
EXPOSE 3009

# 启动服务
CMD ["sbt", "runMain", "com.galphos.systemconfig.SystemConfigServiceMain"]
