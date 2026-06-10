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

# Verify JAR magic bytes
if ! file mission-control.jar | grep -q "Java archive"; then
    echo "[ERROR] Assembly failed — not a valid JAR"
    rm -f mission-control.jar
    exit 1
fi

# Verify checksum if available
if [ -f mission-control.jar.sha256 ]; then
    if command -v sha256sum &>/dev/null; then
        sha256sum -c mission-control.jar.sha256 || { echo "[ERROR] Checksum mismatch"; rm -f mission-control.jar; exit 1; }
    elif command -v shasum &>/dev/null; then
        shasum -a 256 -c mission-control.jar.sha256 || { echo "[ERROR] Checksum mismatch"; rm -f mission-control.jar; exit 1; }
    else
        echo "[WARN] No sha256sum/shasum available — skipping integrity check"
    fi
fi

echo "[OK] mission-control.jar assembled successfully ($(du -h mission-control.jar | cut -f1))"
