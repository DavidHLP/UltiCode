#!/usr/bin/env bash
# Deprecated compatibility alias. DevStack is the only supported development launcher.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/dev/stop.sh" "$@"
