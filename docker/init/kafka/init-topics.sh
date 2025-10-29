#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP="${BOOTSTRAP:-kafka:9092}"
BIN="/opt/kafka/bin/kafka-topics.sh"

create_topic() {
  local name="$1"; local rf="${2:-1}"; local p="${3:-3}"
  $BIN --bootstrap-server "$BOOTSTRAP" --create \
       --if-not-exists --topic "$name" \
       --replication-factor "$rf" --partitions "$p" || true
}

echo "Creating Kafka topics on $BOOTSTRAP ..."
create_topic "app.user.events" 1 3
create_topic "app.audit.logs" 1 3
create_topic "app.judger.jobs" 1 3
echo "Kafka topics ready."
