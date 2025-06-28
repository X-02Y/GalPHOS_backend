FROM openjdk:21-slim

# Install required packages
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy build file and source code
COPY build.sbt .
COPY project ./project
COPY src ./src

# Install SBT
RUN curl -L -o sbt.deb https://repo.scala-sbt.org/scalasbt/debian/sbt-1.9.7.deb && \
    dpkg -i sbt.deb && \
    rm sbt.deb

# Build the application
RUN sbt assembly

# Copy the built JAR
RUN cp target/scala-3.4.2/RegionManagementService-assembly-0.1.0-SNAPSHOT.jar region-service.jar

# Copy configuration
COPY server_config.json .

# Create logs directory
RUN mkdir -p logs

# Expose port
EXPOSE 3007

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:3007/api/regions/provinces-schools || exit 1

# Start the service
CMD ["java", "-jar", "region-service.jar", "server_config.json"]
