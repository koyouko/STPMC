#!/bin/bash
# STP Kafka Mission Control - RHEL 8 / Linux Launcher
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/mission-control.jar"
PID_FILE="$SCRIPT_DIR/.mc.pid"
LOG_FILE="$SCRIPT_DIR/mission-control.log"
PORT="${MC_PORT:-8080}"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_banner() {
  echo ""
  echo "  ============================================="
  echo "   STP Kafka Mission Control"
  echo "  ============================================="
  echo ""
}

check_java() {
  if ! command -v java &>/dev/null; then
    echo -e "  ${RED}[ERROR]${NC} Java not found."
    echo "  Install on RHEL 8:"
    echo "    sudo yum install java-17-openjdk"
    echo "  Or Java 21:"
    echo "    sudo yum install java-21-openjdk"
    exit 1
  fi
  JAVA_VER=$(java -version 2>&1 | head -1)
  echo -e "  ${GREEN}[OK]${NC} $JAVA_VER"
}

check_jar() {
  if [ ! -f "$JAR" ]; then
    echo -e "  ${RED}[ERROR]${NC} mission-control.jar not found in $SCRIPT_DIR"
    echo "  Copy the JAR here or run build-and-start.sh to build from source."
    exit 1
  fi
  echo -e "  ${GREEN}[OK]${NC} JAR found ($(du -h "$JAR" | cut -f1))"
}

start_server() {
  # Check if already running
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo -e "  ${YELLOW}[WARN]${NC} Server already running (PID $(cat "$PID_FILE"))"
    echo "  URL: http://$(hostname):$PORT"
    echo "  Run: $0 stop"
    exit 0
  fi

  echo ""
  echo "  Starting on port $PORT..."
  nohup java -jar "$JAR" \
    --server.port="$PORT" \
    --app.security.allowed-origins=http://localhost:$PORT,http://$(hostname):$PORT \
    > "$LOG_FILE" 2>&1 &

  echo $! > "$PID_FILE"
  echo "  PID: $(cat "$PID_FILE")"

  # Wait for startup
  echo "  Waiting for server to be ready..."
  for i in $(seq 1 30); do
    if curl -sf "http://localhost:$PORT/actuator/health" &>/dev/null; then
      echo -e "  ${GREEN}[OK]${NC} Server is ready"
      echo ""
      echo "  ============================================="
      echo "   URL:      http://$(hostname):$PORT"
      echo "   Log:      $LOG_FILE"
      echo "   PID:      $(cat "$PID_FILE")"
      echo "   Database: H2 (in-memory)"
      echo ""
      echo "   Stop:     $0 stop"
      echo "   Status:   $0 status"
      echo "   Logs:     tail -f $LOG_FILE"
      echo "  ============================================="
      echo ""
      return 0
    fi
    sleep 2
  done

  echo -e "  ${RED}[ERROR]${NC} Server failed to start within 60s"
  echo "  Check logs: tail -50 $LOG_FILE"
  exit 1
}

stop_server() {
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "  Stopping server (PID $PID)..."
      kill "$PID"
      sleep 2
      if kill -0 "$PID" 2>/dev/null; then
        kill -9 "$PID" 2>/dev/null
      fi
      echo -e "  ${GREEN}[OK]${NC} Server stopped"
    else
      echo "  Server not running (stale PID file)"
    fi
    rm -f "$PID_FILE"
  else
    echo "  No PID file found. Server may not be running."
    echo "  Check: ps aux | grep mission-control"
  fi
}

show_status() {
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    PID=$(cat "$PID_FILE")
    echo -e "  ${GREEN}[RUNNING]${NC} PID $PID"
    echo "  URL: http://$(hostname):$PORT"
    HEALTH=$(curl -sf "http://localhost:$PORT/actuator/health" 2>/dev/null || echo "unreachable")
    echo "  Health: $HEALTH"
  else
    echo -e "  ${RED}[STOPPED]${NC} Server is not running"
  fi
}

# ── Main ─────────────────────────────────────────────────
print_banner
case "${1:-start}" in
  start)
    check_java
    check_jar
    start_server
    ;;
  stop)
    stop_server
    ;;
  restart)
    stop_server
    sleep 1
    check_java
    check_jar
    start_server
    ;;
  status)
    show_status
    ;;
  *)
    echo "  Usage: $0 {start|stop|restart|status}"
    echo ""
    echo "  Environment variables:"
    echo "    MC_PORT=8080              Server port"
    echo "    APP_LOCAL_KAFKA_BOOTSTRAP Bootstrap servers"
    echo "    APP_SEED_DEMO_DATA=true   Seed demo data"
    exit 1
    ;;
esac
