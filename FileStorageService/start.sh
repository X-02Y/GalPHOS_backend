#!/bin/bash

echo "Starting File Storage Service..."

echo "Setting up database..."
psql -U postgres -h localhost -f init_database.sql

echo "Compiling application..."
sbt clean compile

echo "Starting server..."
sbt run
