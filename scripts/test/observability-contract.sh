#!/usr/bin/env bash
set -euo pipefail

# P2-OBS-001 contract: validate the optional Collector/Prometheus/
# Alertmanager/Grafana/Tempo/Loki overlay and its release annotation boundary.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OBS_DIR="$ROOT_DIR/infrastructure/observability"
COMPOSE_FILE="$ROOT_DIR/docker-compose.observability.yml"

fail() {
  echo "observability-contract: FAIL: $*" >&2
  exit 1
}

contains() {
  local file="$1" text="$2"
  grep -Fq -- "$text" "$ROOT_DIR/$file" \
    || fail "$file is missing: $text"
}

[[ -d "$OBS_DIR" ]] || fail "observability config directory is missing"
[[ -f "$COMPOSE_FILE" ]] || fail "observability Compose overlay is missing"

for config in \
  infrastructure/observability/prometheus.yml \
  infrastructure/observability/observability-alerts.yml \
  infrastructure/observability/alertmanager.yml \
  infrastructure/observability/otel-collector.yml \
  infrastructure/observability/tempo.yml \
  infrastructure/observability/loki.yml \
  infrastructure/observability/grafana/provisioning/datasources/datasources.yml \
  infrastructure/observability/grafana/provisioning/dashboards/dashboards.yml; do
  [[ -s "$ROOT_DIR/$config" ]] || fail "missing observability config: $config"
done

while IFS= read -r image_line; do
  [[ "$image_line" == *'@sha256:'* ]] \
    || fail "observability image is not digest-pinned: $image_line"
done < <(grep -E '^[[:space:]]+image:' "$COMPOSE_FILE" | sed 's/^[[:space:]]*image:[[:space:]]*//')

contains docker-compose.observability.yml 'profiles: [observability]'
contains docker-compose.observability.yml 'GRAFANA_ADMIN_PASSWORD is required'
contains infrastructure/observability/prometheus.yml '/etc/prometheus/rules/worker-slo-alerts.yml'
contains infrastructure/observability/prometheus.yml 'backend-auth:9101'
contains infrastructure/observability/prometheus.yml 'otel-collector:9464'
contains infrastructure/observability/otel-collector.yml 'otlp/tempo'
contains infrastructure/observability/otel-collector.yml 'otlphttp/loki'
contains infrastructure/observability/otel-collector.yml 'prometheus:'
contains infrastructure/observability/grafana/provisioning/datasources/datasources.yml 'datasourceUid: tempo'
contains infrastructure/observability/grafana/provisioning/datasources/datasources.yml "matcherRegex: 'traceId=([A-Za-z0-9_-]+)'"
contains docker-compose.prod.yml 'MANAGEMENT_OTLP_METRICS_ENABLED=${MANAGEMENT_OTLP_METRICS_ENABLED:-false}'
contains docker-compose.prod.yml 'MANAGEMENT_OTLP_METRICS_ENDPOINT=${MANAGEMENT_OTLP_METRICS_ENDPOINT:-http://otel-collector:4318/v1/metrics}'
contains services/search/pom.xml '<finalName>backend-search</finalName>'
contains .github/services-matrix.json '"artifact": "backend-search-exec.jar"'
contains scripts/runbooks/observability-release-annotation.sh '/api/annotations'
contains scripts/runbooks/observability-release-annotation.sh 'image-reference-policy.sh'
contains services/docs/OBSERVABILITY_RUNBOOK.md 'P2-OBS-001'
contains services/docs/OBSERVABILITY_RUNBOOK.md 'MANAGEMENT_OTLP_METRICS_ENABLED=true'

valid_digest="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
valid_refs=""
for service in \
  backend-auth backend-admin backend-app backend-submission backend-search \
  backend-notification backend-judge console management; do
  variable="${service^^}"
  variable="${variable//-/_}_IMAGE_REF"
  valid_refs+="${variable}=ghcr.io/example/ulticode/${service}@sha256:${valid_digest}"$'\n'
done

python3 - "$OBS_DIR" <<'PY'
import json
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
dashboard = root / "grafana/dashboards/ulticode-overview.json"
json.loads(dashboard.read_text(encoding="utf-8"))
print("Grafana dashboard JSON: PASS")
PY

compose_output="$(mktemp)"
fake_bin="$(mktemp -d)"
trap 'rm -f "$compose_output"; rm -rf "$fake_bin"' EXIT
MYSQL_ROOT_PASSWORD=contract-root-password \
DB_PASSWORD=contract-db-password \
HEALTH_REDIS_PASSWORD=contract-health-password \
NACOS_AUTH_TOKEN=contract-nacos-token \
NACOS_AUTH_IDENTITY_KEY=contract-identity-key \
NACOS_AUTH_IDENTITY_VALUE=contract-identity-value \
MEILI_MASTER_KEY=contract-meili-key \
GRAFANA_ADMIN_PASSWORD=contract-password \
  docker compose -f "$ROOT_DIR/docker-compose.yml" \
  -f "$ROOT_DIR/docker-compose.dev.yml" \
  -f "$COMPOSE_FILE" --profile observability config > "$compose_output"
printf 'merged observability Compose config: PASS\n'

PROMETHEUS_IMAGE='prom/prometheus@sha256:2659f4c2ebb718e7695cb9b25ffa7d6be64db013daba13e05c875451cf51b0d3'
ALERTMANAGER_IMAGE='prom/alertmanager@sha256:e13b6ed5cb929eeaee733479dce55e10eb3bc2e9c4586c705a4e8da41e5eacf5'
OTEL_IMAGE='otel/opentelemetry-collector-contrib@sha256:af0f72b2d2493fc17f06cf3bc136548240cd7ebb2c8b1c8a7be6f3eb03068389'

docker run --rm --entrypoint /bin/promtool \
  -v "$OBS_DIR/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  -v "$ROOT_DIR/docker/prometheus/worker-slo-alerts.yml:/etc/prometheus/rules/worker-slo-alerts.yml:ro" \
  -v "$OBS_DIR/observability-alerts.yml:/etc/prometheus/rules/observability-alerts.yml:ro" \
  "$PROMETHEUS_IMAGE" check config /etc/prometheus/prometheus.yml >/dev/null
docker run --rm --entrypoint /bin/promtool \
  -v "$ROOT_DIR/docker/prometheus/worker-slo-alerts.yml:/etc/prometheus/rules/worker-slo-alerts.yml:ro" \
  -v "$OBS_DIR/observability-alerts.yml:/etc/prometheus/rules/observability-alerts.yml:ro" \
  "$PROMETHEUS_IMAGE" check rules \
  /etc/prometheus/rules/worker-slo-alerts.yml \
  /etc/prometheus/rules/observability-alerts.yml >/dev/null
printf 'Prometheus config and alert rules: PASS\n'

docker run --rm --entrypoint /bin/amtool \
  -v "$OBS_DIR/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro" \
  "$ALERTMANAGER_IMAGE" check-config /etc/alertmanager/alertmanager.yml >/dev/null
printf 'Alertmanager routing config: PASS\n'

docker run --rm -e OBS_ENVIRONMENT=contract \
  -v "$OBS_DIR/otel-collector.yml:/etc/otelcol-contrib/config.yaml:ro" \
  "$OTEL_IMAGE" validate --config=/etc/otelcol-contrib/config.yaml >/dev/null
printf 'OpenTelemetry Collector config: PASS\n'

release_manifest="$(mktemp)"
trap 'rm -f "$compose_output" "$release_manifest"; rm -rf "$fake_bin"' EXIT
printf '%s' "$valid_refs" > "$release_manifest"
if RELEASE_ID=release-1 \
  GRAFANA_URL=not-an-origin \
  GRAFANA_API_TOKEN=contract-token \
  IMAGE_REF_MANIFEST="$release_manifest" \
  "$ROOT_DIR/scripts/runbooks/observability-release-annotation.sh" >/dev/null 2>&1; then
  fail "release annotation accepted an invalid Grafana URL"
fi
printf 'release annotation input guard: PASS\n'
fake_curl="$fake_bin/curl"
cat > "$fake_curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
exit 0
EOF
chmod +x "$fake_curl"
annotation_output="$(RELEASE_ID=release-1 \
  GRAFANA_URL=https://grafana.example.invalid \
  GRAFANA_API_TOKEN=contract-token \
  IMAGE_REF_MANIFEST="$release_manifest" \
  CURL_BIN="$fake_curl" \
  "$ROOT_DIR/scripts/runbooks/observability-release-annotation.sh")"
grep -Fq '[observability-release] PASS release=release-1' <<< "$annotation_output" \
  || fail "release annotation did not complete with the fake Grafana endpoint"
! grep -Fq 'contract-token' <<< "$annotation_output" \
  || fail "release annotation leaked its API token to stdout"
printf 'release annotation success path and secret-redaction guard: PASS\n'
printf 'observability-contract: PASS\n'
