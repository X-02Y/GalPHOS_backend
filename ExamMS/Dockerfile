FROM openjdk:11-jre-slim

# Install dependencies
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy application files
COPY target/scala-3.4.2/ExamManagementService-assembly-*.jar app.jar
COPY server_config.json server_config.json

# Create logs directory
RUN mkdir -p logs

# Expose port
EXPOSE 3003

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:3003/health || exit 1

# Run application
CMD ["java", "-jar", "app.jar"]
