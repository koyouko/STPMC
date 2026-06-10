#!/bin/bash
# STP Kafka Mission Control - One-line installer
# Usage: curl -sL <url>/install.sh | bash
#    or: bash install.sh
set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "  ============================================="
echo "   STP Kafka Mission Control - Installer"
echo "  ============================================="
echo ""

INSTALL_DIR="$(pwd)/stpmc"
REPO="https://github.com/koyouko/STPMC.git"
BRANCH="codex/initial-import"

# Check git
if ! command -v git &>/dev/null; then
  echo -e "  ${RED}[ERROR]${NC} git not found. Install: sudo yum install git"
  exit 1
fi
echo -e "  ${GREEN}[OK]${NC} git found"

# Clone
if [ -d "$INSTALL_DIR" ]; then
  echo "  Directory $INSTALL_DIR already exists. Updating..."
  cd "$INSTALL_DIR"
  git pull origin "$BRANCH" 2>&1 | tail -3
else
  echo "  Cloning into $INSTALL_DIR..."
  git clone --branch "$BRANCH" --single-branch "$REPO" "$INSTALL_DIR" 2>&1 | tail -3
  cd "$INSTALL_DIR"
fi

# Reassemble JAR from split parts if needed
if [ ! -f deploy/mission-control.jar ] || [ "$(wc -c < deploy/mission-control.jar)" -lt 1000000 ]; then
  echo "  Assembling JAR from split parts..."
  bash deploy/assemble.sh
fi

JAR_SIZE=$(du -h deploy/mission-control.jar | cut -f1)
echo -e "  ${GREEN}[OK]${NC} JAR assembled ($JAR_SIZE)"

# Make scripts executable
chmod +x deploy/start.sh

echo ""
echo "  ============================================="
echo -e "  ${GREEN}Installation complete!${NC}"
echo "  ============================================="
echo ""
echo "  Location: $INSTALL_DIR"
echo ""
echo "  To start:"
echo "    cd $INSTALL_DIR/deploy"
echo "    ./start.sh start"
echo ""
echo "  To stop:"
echo "    ./start.sh stop"
echo ""
echo "  To check status:"
echo "    ./start.sh status"
echo ""
echo "  For production databases, run this migration first:"
echo "    ALTER TABLE clusters ADD COLUMN jmx_cluster_id VARCHAR(255);"
echo ""
echo "  ============================================="
echo ""
