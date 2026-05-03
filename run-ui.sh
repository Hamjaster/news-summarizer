#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
exec mvn -q javafx:run
