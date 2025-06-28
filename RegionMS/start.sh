#!/bin/bash

echo "Starting Region Management Service..."

# Set Java options
export JAVA_OPTS="-Xmx2g -Xms512m -XX:+UseG1GC"

# Check if sbt is available
if ! command -v sbt &> /dev/null; then
    echo "SBT not found. Please install SBT first."
    exit 1
fi

# Run the service
echo "Starting Region Management Service on port 3007..."
sbt "run server_config.json"
