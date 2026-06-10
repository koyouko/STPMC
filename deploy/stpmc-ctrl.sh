#!/bin/bash
# STP Kafka Mission Control - control script (start/stop/restart/status/logs)
#
# Supports log-level selection via --debug, --trace, --info, --warn (or
# LOG_LEVEL env var). All app.* env vars that the JAR honors are documented
# at the bottom under `usage`.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/mission-control.jar"

# Auto-assemble JAR from split parts if needed
if [ ! -f "$JAR" ] || [ "$(wc -c < "$JAR" 2>/dev/null)" -lt 1000000 ]; then
    bash "$SCRIPT_DIR/assemble.sh"
fi
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

# ── Log-level parsing ────────────────────────────────────
# Defaults: root=INFO, app=INFO. Env LOG_LEVEL bumps app. CLI flag overrides
# env. --debug keeps framework at INFO and raises only app code to DEBUG
# (the usual troubleshooting setting). --all-debug raises everything.
APP_LOG_LEVEL="${LOG_LEVEL:-INFO}"
ROOT_LOG_LEVEL="INFO"

parse_log_flag() {
  case "$1" in
    --debug)     APP_LOG_LEVEL=DEBUG ;;
    --trace)     APP_LOG_LEVEL=TRACE ;;
    --info)      APP_LOG_LEVEL=INFO ;;
    --warn)      APP_LOG_LEVEL=WARN ;;
    --all-debug) APP_LOG_LEVEL=DEBUG; ROOT_LOG_LEVEL=DEBUG ;;
    "" ) ;;
    *) echo -e "  ${YELLOW}[WARN]${NC} Unknown flag '$1' ignored" ;;
  esac
}

check_java() {
  if command -v java &>/dev/null; then
    JAVA_VER_NUM=$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')
    JAVA_VER=$(java -version 2>&1 | head -1)
    if [ "$JAVA_VER_NUM" -ge 17 ] 2>/dev/null; then
      echo -e "  ${GREEN}[OK]${NC} $JAVA_VER"
      return 0
    else
      echo -e "  ${YELLOW}[WARN]${NC} Java $JAVA_VER_NUM found but 17+ required ($JAVA_VER)"
    fi
  else
    echo -e "  ${YELLOW}[WARN]${NC} Java not found"
  fi

  echo "  Attempting to install Java 17..."
  if command -v yum &>/dev/null; then
    sudo yum install -y java-17-openjdk 2>&1 | tail -3
  elif command -v dnf &>/dev/null; then
    sudo dnf install -y java-17-openjdk 2>&1 | tail -3
  elif command -v apt-get &>/dev/null; then
    sudo apt-get update -qq && sudo apt-get install -y openjdk-17-jre-headless 2>&1 | tail -3
  else
    echo -e "  ${RED}[ERROR]${NC} Cannot auto-install. Install manually:"
    echo "    RHEL/CentOS: sudo yum install java-17-openjdk"
    echo "    Ubuntu/Debian: sudo apt install openjdk-17-jre-headless"
    exit 1
  fi

  if ! command -v java &>/dev/null; then
    echo -e "  ${RED}[ERROR]${NC} Java installation failed"
    exit 1
  fi
  JAVA_VER=$(java -version 2>&1 | head -1)
  echo -e "  ${GREEN}[OK]${NC} Installed $JAVA_VER"
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
  if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    echo -e "  ${YELLOW}[WARN]${NC} Server already running (PID $(cat "$PID_FILE"))"
    echo "  URL: http://$(hostname):$PORT"
    echo "  Run: $0 stop"
    exit 0
  fi

  # Auto-detect PostgreSQL profile from DB_URL
  PROFILES="${SPRING_PROFILES_ACTIVE:-}"
  if [ -n "${DB_URL:-}" ] && echo "$DB_URL" | grep -qi "postgresql" && ! echo "$PROFILES" | grep -q "postgres"; then
    PROFILES="${PROFILES:+$PROFILES,}postgres"
  fi
  PROFILE_ARG=""
  if [ -n "$PROFILES" ]; then
    PROFILE_ARG="--spring.profiles.active=$PROFILES"
  fi

  if echo "${PROFILES:-}" | grep -q "postgres"; then
    DB_DISPLAY="PostgreSQL (${DB_URL:-docker-compose defaults})"
  else
    DB_DISPLAY="H2 (in-memory)"
  fi

  echo ""
  echo "  Starting on port $PORT..."
  echo "  Database:  $DB_DISPLAY"
  echo "  Log level: root=$ROOT_LOG_LEVEL  app=$APP_LOG_LEVEL"

  nohup java -jar "$JAR" \
    --server.port="$PORT" \
    --app.security.allowed-origins=http://localhost:$PORT,http://$(hostname):$PORT,http://$(hostname -f 2>/dev/null || hostname):$PORT${APP_ALLOWED_ORIGIN:+,$APP_ALLOWED_ORIGIN} \
    --logging.level.root="$ROOT_LOG_LEVEL" \
    --logging.level.com.stp.missioncontrol="$APP_LOG_LEVEL" \
    $PROFILE_ARG \
    > "$LOG_FILE" 2>&1 &

  echo $! > "$PID_FILE"
  echo "  PID: $(cat "$PID_FILE")"

  echo "  Waiting for server to be ready..."
  for i in $(seq 1 30); do
    if curl -sf "http://localhost:$PORT/actuator/health" &>/dev/null; then
      echo -e "  ${GREEN}[OK]${NC} Server is ready"
      echo ""
      echo "  ============================================="
      echo "   URL:       http://$(hostname):$PORT"
      echo "   Log:       $LOG_FILE  (level $APP_LOG_LEVEL)"
      echo "   PID:       $(cat "$PID_FILE")"
      echo "   Database:  $DB_DISPLAY"
      echo ""
      echo "   Stop:      $0 stop"
      echo "   Status:    $0 status"
      echo "   Logs:      $0 logs      (tail -f)"
      echo "   Restart:   $0 restart [--debug|--info]"
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
    echo "  URL:     http://$(hostname):$PORT"
    HEALTH=$(curl -sf "http://localhost:$PORT/actuator/health" 2>/dev/null || echo "unreachable")
    echo "  Health:  $HEALTH"
    CONFIG=$(curl -sf "http://localhost:$PORT/api/platform/metrics/config" 2>/dev/null || echo "{}")
    echo "  Metrics: $CONFIG"
  else
    echo -e "  ${RED}[STOPPED]${NC} Server is not running"
  fi
}

tail_logs() {
  if [ ! -f "$LOG_FILE" ]; then
    echo -e "  ${RED}[ERROR]${NC} Log file not found: $LOG_FILE"
    exit 1
  fi
  exec tail -f "$LOG_FILE"
}

usage() {
  cat <<USAGE
  Usage: $0 {start|stop|restart|status|logs} [--debug|--trace|--info|--warn|--all-debug]

  Log-level flags (apply to 'start' and 'restart'):
    --debug        com.stp.missioncontrol=DEBUG (framework stays INFO)
    --trace        com.stp.missioncontrol=TRACE
    --info         com.stp.missioncontrol=INFO (default)
    --warn         com.stp.missioncontrol=WARN
    --all-debug    Everything at DEBUG (noisy - Spring, Hibernate, etc.)
    LOG_LEVEL=...  Env override for the app logger (CLI flag wins if both set)

  Common environment variables:
    MC_PORT=8080                           HTTP port (default 8080)
    SPRING_PROFILES_ACTIVE=postgres        Use PostgreSQL instead of H2
    APP_SECURITY_MODE=saml                 Auth mode (saml|development)
    APP_SECRETS_BASE_DIR=/etc/secrets      Base dir for secret files
    APP_ALLOWED_ORIGIN=http://host:port    Extra CORS allowed origin
    APP_LOCAL_KAFKA_BOOTSTRAP=host:9092    Bootstrap servers (dev seed)
    APP_SEED_DEMO_DATA=true                Seed demo data on empty DB
    APP_SEED_LOCAL_DEV_CLUSTER=true        Seed the 'Local Kafka Dev' cluster
    DB_URL=jdbc:postgresql://...           Database JDBC URL
    DB_USERNAME / DB_PASSWORD              Database credentials

  Scraper tuning (see /api/platform/metrics/config for live values):
    APP_METRICS_SCRAPE_TIMEOUT_MS=150000   Per-request HTTP timeout (ms)
    APP_METRICS_SCRAPE_INTERVAL_MS=60000   Auto-scrape interval (0 disables)
    APP_METRICS_SCRAPE_INITIAL_DELAY_MS=30000
                                           Delay before first auto-scrape

  Cluster health poll:
    APP_HEALTH_POLL_INTERVAL_MS=60000      Kafka AdminClient probe interval
    APP_HEALTH_STALE_AFTER_MS=180000       Snapshot staleness window
    APP_HEALTH_REFRESH_COOLDOWN_MS=60000   Min time between manual refreshes
    APP_KAFKA_TIMEOUT_MS=4000              Kafka client timeout
    APP_PROBE_TIMEOUT_MS=3000              HTTP probe timeout (SR, C3, etc.)

  Examples:
    $0 start                               Start with defaults (INFO)
    $0 start --debug                       Start with DEBUG for our code
    LOG_LEVEL=DEBUG $0 start               Same effect via env var
    APP_METRICS_SCRAPE_TIMEOUT_MS=180000 $0 restart --debug
    $0 logs                                tail -f the server log
    $0 status                              PID + /actuator/health + /config
USAGE
}

# ── Main ─────────────────────────────────────────────────
print_banner
COMMAND="${1:-start}"
FLAG="${2:-}"
parse_log_flag "$FLAG"

case "$COMMAND" in
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
  logs)
    tail_logs
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
