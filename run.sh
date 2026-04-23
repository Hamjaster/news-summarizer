#!/usr/bin/env bash
# Simple wrapper to build (if necessary) and run the news-summarizer app with NEWS_API_KEY
# Usage: ./run.sh "your query here"

set -euo pipefail

if [ -z "${NEWS_API_KEY-}" ]; then
  echo "Please set NEWS_API_KEY env var. Example: export NEWS_API_KEY=\"your_key\"" >&2
  exit 1
fi

# Build the project (creates a shaded jar that includes dependencies)
mvn -DskipTests package

JAR=target/news-summarizer-1.0.0-SNAPSHOT.jar
if [ ! -f "$JAR" ]; then
  echo "Jar not found: $JAR" >&2
  exit 1
fi

QUERY="$*"
if [ -z "$QUERY" ]; then
  echo "No query provided; using default inside application." 
  java -jar "$JAR"
else
  java -jar "$JAR" "$QUERY"
fi
