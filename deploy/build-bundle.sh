#!/usr/bin/env bash
# Builds a single JAR that includes both the backend API and frontend UI.
# The resulting JAR serves everything on one port (default 8080).
#
# Usage:
#   bash deploy/build-bundle.sh
#
# Output:
#   deploy/mission-control.jar  (bundled JAR)
#
# Prerequisites:
#   - Java 17+
#   - Node.js 20+ with npm
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo ""
echo "  ============================================="
echo "   Building Mission Control (bundled JAR)"
echo "  ============================================="
echo ""

# ── Step 1: Build frontend ────────────────────────────────────
echo "  [1/4] Building frontend..."
# Load nvm if available (macOS/Linux dev machines)
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
[ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"
cd "$ROOT_DIR/frontend"
npm install --silent 2>&1 | tail -1
npm run build 2>&1 | tail -3
echo -e "  ${GREEN}[OK]${NC} Frontend built"

# ── Step 2: Copy dist → backend static ────────────────────────
echo "  [2/4] Copying frontend to backend resources..."
STATIC_DIR="$ROOT_DIR/backend/src/main/resources/static"
rm -rf "$STATIC_DIR"
cp -r "$ROOT_DIR/frontend/dist" "$STATIC_DIR"
echo -e "  ${GREEN}[OK]${NC} Copied to $STATIC_DIR"

# ── Step 3: Build JAR ─────────────────────────────────────────
echo "  [3/4] Building backend JAR..."
cd "$ROOT_DIR/backend"
./mvnw package -DskipTests -q
echo -e "  ${GREEN}[OK]${NC} JAR built"

# ── Step 4: Copy to deploy ────────────────────────────────────
echo "  [4/4] Copying JAR to deploy/..."
cp target/mission-control-*.jar "$SCRIPT_DIR/mission-control.jar"

# Clean up — remove static dir so it stays gitignored
rm -rf "$STATIC_DIR"

# Re-split for git if needed
cd "$SCRIPT_DIR"
rm -f mission-control.jar.part.*
split -b 24m mission-control.jar mission-control.jar.part.
if command -v sha256sum &>/dev/null; then
    sha256sum mission-control.jar > mission-control.jar.sha256
elif command -v shasum &>/dev/null; then
    shasum -a 256 mission-control.jar > mission-control.jar.sha256
fi

JAR_SIZE=$(du -h mission-control.jar | cut -f1)
echo ""
echo "  ============================================="
echo -e "  ${GREEN}Build complete!${NC}"
echo "  ============================================="
echo "   JAR:  $SCRIPT_DIR/mission-control.jar ($JAR_SIZE)"
echo "   Mode: Bundled (API + UI on single port)"
echo ""
echo "   Start: ./stpmc-ctrl.sh start              (or: --debug / --trace)"
echo "   Logs:  ./stpmc-ctrl.sh logs"
echo "   URL:   http://localhost:8080"
echo "  ============================================="
echo ""
