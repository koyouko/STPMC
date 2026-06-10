#!/bin/bash
# Backward-compatible launcher. The main Linux control script was renamed to
# stpmc-ctrl.sh; keep start.sh for existing deployment notes and runbooks.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec "$SCRIPT_DIR/stpmc-ctrl.sh" "$@"
