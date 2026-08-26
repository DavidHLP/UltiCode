# Prometheus — Worker SLO rules

`worker-slo-alerts.yml` holds Prometheus alerting rules for `search.worker`, `judge.streams`, `notification.inbox` (see `services/docs/WORKER_SLO_RUNBOOK.md`).

## Mounting

Host Prometheus (`prometheus.yml`):

```yaml
rule_files:
  - /etc/prometheus/rules/worker-slo-alerts.yml
```

Production compose — add to the `prometheus` service:

```yaml
volumes:
  - ./docker/prometheus/worker-slo-alerts.yml:/etc/prometheus/rules/worker-slo-alerts.yml:ro
```

Reload: `kill -HUP <prometheus-pid>` or `curl -X POST http://prometheus:9090/-/reload`.

Validate: `docker run --rm -v "$PWD/docker/prometheus:/rules:ro" prom/prometheus:latest promtool check rules /rules/worker-slo-alerts.yml`
