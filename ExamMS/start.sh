#!/bin/bash

echo "Starting Exam Management Service..."

echo "Compiling project..."
sbt compile

echo "Running service..."
sbt run
