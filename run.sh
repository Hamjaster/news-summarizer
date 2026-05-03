#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

CP_FILE="target/news-cp.txt"
if [ ! -f "$CP_FILE" ] || [ pom.xml -nt "$CP_FILE" ]; then
    mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
fi

if [ ! -d target/classes ]; then
    mvn -q compile
fi

CP="target/classes:$(cat "$CP_FILE")"
exec java --add-opens=java.base/java.util=ALL-UNNAMED -cp "$CP" com.example.newssummarizer.Main
