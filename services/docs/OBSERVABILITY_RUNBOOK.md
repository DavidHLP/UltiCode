# Observability Runbook

Task: `P2-OBS-001`.

This runbook covers the repository-provided observability overlay. It does not
replace the production telemetry platform, alert receiver, release approval,
or threshold-tuning owner.

## Start the disposable/local overlay

The overlay is opt-in. It keeps Prometheus, Alertmanager, Grafana, Tempo, Loki,
and the OpenTelemetry Collector off by default and binds their host ports to
loopback.

```bash
mkdir -p .local/observability/logs
MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces \
MANAGEMENT_OTLP_METRICS_ENABLED=true \
MANAGEMENT_OTLP_METRICS_ENDPOINT=http://otel-collector:4318/v1/metrics \
GRAFANA_ADMIN_PASSWORD="$GRAFANA_ADMIN_PASSWORD" \
docker compose -f docker-compose.yml -f docker-compose.dev.yml \
  -f docker-compose.observability.yml --profile observability up -d
```

The production invocation uses `docker-compose.prod.yml` and all of its
required owner/registry/TLS/image-ref variables. Do not put the Grafana
password or a production webhook in this repository.

Endpoints are `http://127.0.0.1:9090` (Prometheus),
`http://127.0.0.1:9093` (Alertmanager), `http://127.0.0.1:3000` (Grafana),
and OTLP on loopback `4317/4318`. Prometheus scrapes owner Actuator endpoints;
Search and Judge emit Micrometer metrics over OTLP because they intentionally
have no HTTP surface. The Collector exposes those metrics at `:9464` for the
Prometheus scrape.

## Data flow and correlation

```text
owner Actuator / worker OTLP metrics ─┐
HTTP/Dubbo/Streams OTLP traces        ├─> OpenTelemetry Collector ─> Prometheus/Grafana + Tempo
mounted application logs              ┘                         └─> Loki ─> Grafana derived TraceID link
Prometheus alerts ───────────────────────────────────────────────> Alertmanager route
release manifest ─> observability-release-annotation.sh ─────────> Grafana release annotation
```

Application log patterns include `traceId` and `spanId`. Mount sanitized
application log files under `OBSERVABILITY_LOG_DIR`; the Collector's `filelog`
receiver forwards them to Loki. Grafana's Loki datasource links a matching
`traceId=...` field to Tempo.

## SLOs and initial budgets

These are initial engineering targets, not production commitments. Re-tune
after 1–2 weeks of real traffic using p50/p95/p99 and error-budget review.

| Signal | PromQL / measurement | Initial target | Window / budget |
|---|---|---:|---|
| Owner HTTP availability | `1 - sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | ≥99.5% | 30d / 0.5% errors |
| Owner HTTP latency | `histogram_quantile(0.99, sum by (application, le) (rate(http_server_requests_seconds_bucket[5m])))` | <1s | 10m alert / 1% slow requests |
| Worker queue drain | `queue_lag` and `last_success_timestamp` in `worker-slo-alerts.yml` | lag <500 warning, <2000 critical | 10m/5m |
| Worker failures | `increase(<worker>_consume_failures_total[5m])` | ≤5 warning, ≤20 critical | 5m |
| Dubbo provider errors | `sum(rate(dubbo_provider_requests_total{result=~"error|failed"}[5m])) / clamp_min(sum(rate(dubbo_provider_requests_total[5m])), 1)` | <2% | 10m / 2% errors |
| Reconciliation | `1 - increase(reconciliation_failures_total[15m]) / increase(reconciliation_runs_total[15m])` | ≥99% successful runs | 15m / 1% failures |
| Owner migration | completed stages / manifest stages from the secret-free migration report | 100% | every release / zero failed stages |
| Backup/restore | measured `rpo_seconds` / `rto_seconds` from owner backup manifest | set after capacity baseline | scheduled drill |
| Stream/DLQ safety | worker lag, PEL age, DLQ depth, poison/deadletter counters | no sustained critical alert | 5m–30m / zero unexplained poison |
| Security | `http_server_requests_seconds_count{status=~"401|403"}` correlated with trace and route | no unexplained spike | 5m / investigate all unexplained changes |
| Scheduler/JVM/pools | executor active/queued/rejected tasks, `jvm_memory_*`, `jvm_gc_pause_seconds_*` | no rejection/heap/GC saturation | 5m / service-specific baseline |


### Admin use-case dependency metrics

Admin dependency-shape metrics use the fixed `admin.use_case` vocabulary:

```text
admin.use_case.logical_calls{use_case,owner}
admin.use_case.serial_rounds{use_case,owner="all"}
admin.use_case.duration{use_case,owner="all"}
admin.use_case.degradation{use_case,owner="all",degradation}
admin.use_case.freshness{use_case,owner="all",freshness}
```

Example PromQL for comparing the repository budget shape with observations:

```promql
max by (use_case, owner) (admin_use_case_logical_calls)
max by (use_case) (admin_use_case_serial_rounds)
histogram_quantile(0.95, sum by (use_case, le) (rate(admin_use_case_duration_seconds_bucket[5m])))
sum by (use_case, degradation) (increase(admin_use_case_degradation_total[5m]))
```

`use_case` values are the finite IDs in `P3-ADMIN-001`; account IDs, user IDs,
request IDs, and owner-specific identifiers are never labels. These meters are
best-effort observations: registry failures do not change the Admin result.
The values compare call shape and repository budgets; they are not production
latency, freshness, capacity, or availability evidence.
`-1` worker gauges mean the observation failed or is not applicable; they are
filtered from alerts and must not be treated as a healthy zero.

## Alert routing and response

Prometheus loads `worker-slo-alerts.yml` plus `observability-alerts.yml`.
Alertmanager groups by alert, owner, and application, routes critical alerts
with a one-hour repeat interval, and sends warning/critical notifications to
the documented local webhook placeholder. Replace that receiver in the
deployment-owned Alertmanager configuration with the approved on-call endpoint.

For a release annotation, use a secret-store token and the immutable manifest:

```bash
RELEASE_ID=release-20260831 \
OBS_ENVIRONMENT=production \
GRAFANA_URL=https://grafana.example.invalid \
GRAFANA_API_TOKEN="$GRAFANA_API_TOKEN" \
IMAGE_REF_MANIFEST=/path/to/release-manifest.txt \
./scripts/runbooks/observability-release-annotation.sh
```

The script sends only release/environment tags and the manifest to Grafana; it
never prints the bearer token. Correlate the annotation with a spike in HTTP
5xx/p99, RPC errors, queue lag, PEL age, DLQ depth, or reconciliation failures.

## Owner-specific recovery

- **Owner readiness:** check `/api/v1/{owner}/health/ready` and the matching
  `up{job="ulticode-owners"}` series. Do not use `/actuator/health` as the
  deployment readiness gate.
- **RPC:** correlate `RpcProviderErrorRateHigh` with Dubbo timeout/retry policy,
  Nacos registration, and the target owner readiness endpoint.
- **Reconciliation:** inspect the persisted FAILED record and its trace id;
  verify owner-facts order/count/page limits and the MySQL lease before rerun.
- **Backup:** use `owner-backup-restore.sh verify` before any restore; a restore
  drill must use a disposable database and emit measured RPO/RTO.
- **Streams:** use `WORKER_SLO_RUNBOOK.md` for lag, PEL, poison, DLQ, reclaim,
  and replay actions. Never flush a production stream.
- **Security:** correlate authentication/delegation failures with the shared
  security metrics/logs; do not weaken route, CSRF, Redis ACL, or caller policy
  to clear an alert.
- **Scheduler/JVM/pools:** inspect executor saturation, rejected-task counters,
  GC pauses, heap, and thread pool metrics before changing pool sizes. Each
  scheduled task remains owned by its service and must keep its lease/lock.
- **Migration/release:** keep the owner migration report, image digest manifest,
  Grafana release annotation, and deployment trace id together. A partial
  migration or missing annotation is a release failure, not a warning to hide.

## Validation and stop conditions

```bash
./scripts/test/observability-contract.sh
docker compose -f docker-compose.observability.yml config
```

A local overlay being healthy proves configuration and wiring only. It does not
prove production trace completeness, tuned thresholds, notification delivery,
or an SLO under real traffic; record those as external evidence.
