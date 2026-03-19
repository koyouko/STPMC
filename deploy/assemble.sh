#!/usr/bin/env bash
# Reassemble mission-control.jar from split parts
set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

if [ -f mission-control.jar ] && [ "$(wc -c < mission-control.jar)" -gt 1000000 ]; then
    echo "[OK] mission-control.jar already assembled ($(du -h mission-control.jar | cut -f1))"
    exit 0
fi

echo "Assembling mission-control.jar from parts..."
cat mission-control.jar.part.* > mission-control.jar

# Verify
if file mission-control.jar | grep -q "Java archive"; then
    echo "[OK] mission-control.jar assembled successfully ($(du -h mission-control.jar | cut -f1))"
else
    echo "[ERROR] Assembly failed — not a valid JAR"
    rm -f mission-control.jar
    exit 1
fi
