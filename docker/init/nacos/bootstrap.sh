#!/bin/sh
set -e

ADDR="${NACOS_ADDR:-http://nacos:8848}"
U="${NACOS_USERNAME:-nacos}"
P="${NACOS_PASSWORD:-nacos}"

echo "Waiting Nacos @ $ADDR ..."
for i in $(seq 1 60); do
  if curl -fsS "$ADDR/nacos/actuator/health" | grep -q '"status":"UP"'; then
    break
  fi
  sleep 2
done

echo "Importing configs to Nacos..."

curl -fsS -X POST "$ADDR/nacos/v1/cs/configs" \
  --data-urlencode "dataId=gateway.yaml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "content=$(cat /init/nacos/configs/gateway.yaml)" \
  -u "$U:$P"

curl -fsS -X POST "$ADDR/nacos/v1/cs/configs" \
  --data-urlencode "dataId=other-service.yaml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "content=$(cat /init/nacos/configs/other-service.yaml)" \
  -u "$U:$P"

echo "Nacos configs imported."
