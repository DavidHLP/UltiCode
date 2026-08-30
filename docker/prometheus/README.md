# Prometheus — Worker SLO rules

`worker-slo-alerts.yml` holds Prometheus alerting rules for `search.worker`, `judge.streams`, `notification.inbox` (see `services/docs/WORKER_SLO_RUNBOOK.md`).

## Mounting

Host Prometheus (`prometheus.yml`):

```yaml
rule_files:
  - /etc/prometheus/rules/worker-slo-alerts.yml
```

The repository overlay `docker-compose.observability.yml` supplies Prometheus,
Alertmanager, Grafana, Tempo, Loki, and the OpenTelemetry Collector. Combine it
with the base plus environment Compose file and opt in with `--profile
observability`; it binds host ports to loopback only.

The Prometheus service mounts the rules:

```yaml
volumes:
  - ./docker/prometheus/worker-slo-alerts.yml:/etc/prometheus/rules/worker-slo-alerts.yml:ro
```

Reload: `kill -HUP <prometheus-pid>` or `curl -X POST http://prometheus:9090/-/reload`.

Validate: `./scripts/test/observability-contract.sh` (it uses the pinned
Prometheus and Alertmanager digests and validates the Collector config).
