#!/bin/bash
echo "Starting Score Statistics Service..."
cd "$(dirname "$0")"
sbt "run"
