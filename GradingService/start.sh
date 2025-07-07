#!/bin/bash

echo "Starting GradingService..."
echo

echo "Checking if SBT is available..."
if ! command -v sbt &> /dev/null; then
    echo "Error: SBT is not installed or not in PATH"
    exit 1
fi

sbt -version
echo

echo "Compiling and running GradingService..."
sbt "run"

if [ $? -ne 0 ]; then
    echo
    echo "Error: Failed to start GradingService"
    exit 1
fi

echo
echo "GradingService stopped."
