---
title: Monitoring
type: entity
tags: [monitoring, platform, ops]
status: living
updated: 2026-06-21
sources:
  - backend-spring/src/main/java/com/ulticode/modules/monitoring/
  - backend-spring/src/main/java/com/ulticode/modules/monitoring/controller/MonitoringController.java
aliases: [系统监控]
---

# Monitoring

Admin-only read surface for runtime telemetry — system info, resource usage,
DB/queue/Redis stats, and a health check. Not an APM; the module is a thin
aggregator over MySQL/Redis/JVM that the management dashboard polls.

## Responsibility

Expose a single `/monitoring/**` namespace that returns server-side
operational data. Every endpoint is `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`.
No writes; this is a read-only telemetry surface.

## Key tables

None. Monitoring reads from `information_schema`, Redis (`INFO`/`DBSIZE`),
JVM `Runtime` / `OperatingSystemMXBean`, and the
[[entities/judge-queue|judge queue]] status. The module is intentionally
stateless.

## Controllers

`MonitoringController` → `/monitoring`:

| Endpoint | Returns |
|----------|---------|
| `GET /monitoring/system` | `SystemInfoVO` (OS, Java, hostname, uptime) |
| `GET /monitoring/resources` | `ResourceUsageVO` (CPU, memory, disk) |
| `GET /monitoring/database` | `DatabaseStatsVO` (connections, slow queries, sizes) |
| `GET /monitoring/queues` | `List<QueueStatsVO>` (judge-queue depth, lag) |
| `GET /monitoring/redis` | `RedisStatsVO` (memory, keys, hit rate) |
| `GET /monitoring/health` | `SystemHealthVO` (overall pass/warn/fail) |

## Flow

dashboard polls `/monitoring/health` for status colour → on click drills into
per-resource endpoint. Each endpoint runs cheap SQL / `INFO` calls; the
controller is *not* cached because the volume is low and the latency budget
is human-scale.

## Source files

- `backend-spring/.../modules/monitoring/` (controller, service + impl, dto).

## Cross-links

- [[entities/admin]] (the dashboard that consumes these endpoints)
- [[entities/judge-queue]] (queue stats source)
- [[overview/backend-modules-overview]] · [[overview/dev-environment-overview]]

## Gotchas

- The module's endpoints require ADMIN role; do not expose `/monitoring/**`
  publicly. Spring Security must be configured to require auth *before* the
  `@PreAuthorize` check — a missing auth entry would 401, not bypass the role.
- `RedisStatsVO` is a point-in-time snapshot. Don't chart it as a time-series
  by polling faster than the underlying `INFO` round-trip.
- `SystemHealthVO` is **not** a substitute for the liveness probe; the project
  does not run Spring Actuator (`/actuator/health`) per
  `AGENTS.md` — use the documented public APIs + container health checks
  instead.
