#!/bin/bash

echo "Starting SubmissionService..."
echo

echo "Checking if PostgreSQL is running..."
if pgrep -x "postgres" > /dev/null; then
    echo "PostgreSQL is running."
else
    echo "Warning: PostgreSQL is not running. Please start PostgreSQL first."
    echo "You can start it with: sudo systemctl start postgresql"
    exit 1
fi

echo
echo "Compiling and starting the service..."
sbt "runMain Main.SubmissionServiceApp"

if [ $? -ne 0 ]; then
    echo
    echo "Error: Failed to start SubmissionService"
    exit 1
fi

echo
echo "SubmissionService stopped"
