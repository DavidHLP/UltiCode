---
title: Development Environment Overview
type: overview
tags: [ops, dev, map]
status: living
updated: 2026-06-21
sources:
  - ecosystem.config.cjs
  - docker-compose.yml
  - docker-compose.dev.yml
  - scripts/dev/
  - infrastructure/arthas/
  - CLAUDE.md
---

# Development Environment Overview

What runs locally, how it's orchestrated, and the ordering traps that bite. This
is the **knowledge layer** — the operational command reference lives in
`AGENTS.md` and `CLAUDE.md`. Runtime diagnostics deep-dive:
[[concepts/arthas-diagnostics]].

## What runs

**PM2 apps** (`ecosystem.config.cjs`):

| App | Port | What |
|-----|------|------|
| `ulticode-9001` | 9001 | Spring Boot backend |
| `ulticode-9002` | 9002 | console (Vite dev) |
| `ulticode-9003` | 9003 | management (Vite dev) |
| `ulticode-arthas` | 8563 | Arthas MCP wrapper (main path) |

**Docker infrastructure** (`docker-compose.yml` + `docker-compose.dev.yml`):

| Container | Port | Notes |
|-----------|------|-------|
| `ulticode-mysql` | 23306 | MySQL 9.1; host port bound `127.0.0.1` only in dev |
| `ulticode-redis` | 26379 | Redis 7; session, CSRF, judge stream |
| `ulticode-nacos` | 28848 | Nacos 2.3.2 console at `/nacos`; auth on |

**One-shot**: `ulticode-init-db` runs Flyway then stops (success = `BUILD SUCCESS`
in its logs). The `.env` at repo root is the single source of truth for credentials
— never commit it.

## Orchestration (`scripts/dev/`)

- `init-env.sh` — first run: generate random creds into `.env`.
- `up.sh` — the full sequence: docker compose up → Nacos bootstrap → Flyway
  migrate → PM2 start. `--skip-install` reuses installed deps.
- `migrate.sh {migrate|repair}` — Flyway wrapper (raw `flyway` not on PATH).
- `test.sh {quick|full|integration}` — `quick` skips `*IT.java`.
- `typography-guard.sh` — font-consistency check (LXGW WenKai, see
  [[concepts/theme-system]]).

## Startup ordering contract

```
docker (mysql/redis/nacos) Healthy
        │
        ▼
ulticode-init-db  (Flyway)
        │
        ▼
ulticode-9001  (Spring Boot)
        │
        ▼
9002 / 9003 / arthas
```

**Why it matters**: starting PM2 while containers are `Exited` → init-db hits
"connection refused" → 9001 crash-loops → 8563 never comes up. Symptom: PM2
restart-count (↺) climbing on `ulticode-9001` while `lsof -ti :9001` is empty.
Fix path: `up.sh --skip-install` re-runs the sequence in order.

## Known traps

- **`pm2 restart --update-env` does not reload `envFromFile`** — it uses the
  daemon cache. If `.env` changed and 9001 throws `RedisWrongPasswordException`
  with ↺ climbing, force a re-read: `pm2 delete ulticode-9001 && pm2 start
  ecosystem.config.cjs --only ulticode-9001`. Inspect the real env via
  `/proc/$(pm2 pid ulticode-9001)/environ` (`pm2 env <id>` shows stale values).
- **`up.sh` cold-start pause is expected** — the dev-admin bootstrap
  (`spring-boot:run --web-application-type=none`) blocks ~105s before the timeout
  reaps it ("Bootstrap JVM did not self-exit… continuing"). Don't intervene; the
  real failure signal is the background output file's mtime stalling **plus** PM2
  empty **plus** ports free.
- **MySQL charset via `docker exec`** — the container defaults
  `character_set_client=latin1`. Direct `docker exec mysql -e "INSERT 中文…"`
  double-encodes. Always pass `--default-character-set=utf8mb4` (or `SET NAMES
  utf8mb4;`). The JDBC URL already has `useUnicode=true&characterEncoding=UTF-8`,
  so app/Flyway writes are fine — only manual `docker exec` is affected.
- **No Spring Actuator** — don't use `/actuator/health` for readiness. Use a known
  public API + the two frontend roots + PM2 status + container health checks.

## Arthas runtime diagnostics

`tools/arthas-boot.jar` (4.2.2) attaches to the running JVM; the MCP endpoint at
`http://localhost:8563/mcp` (STATELESS, project-pinned) lets the LLM call
dashboard/thread/watch/trace/ognl directly.

- **Three-way mutual exclusion**: PM2 / Claude Code hook / CLI — any one that's up
  wins; the others skip (PID file `PID\nLAUNCHER` + port `:8563` detection).
- **STATELESS is mandatory**: 4.2.2's default STREAMABLE demands an
  `mcp-session-id` the Claude Code client doesn't send → blocking commands
  (dashboard/trace/watch/monitor) time out at 30s. Pinned in
  `infrastructure/arthas/arthas.properties`; changing it requires
  `pm2 restart ulticode-9001` to re-attach.
- **Blocking commands need `-n N` (N ≤ 5)** and a concurrent trigger to fire the
  target method; otherwise the 30s MCP timeout hits. Degrade path: app logs →
  raw logs → interactive telnet → integration test. Full playbook:
  [[concepts/arthas-diagnostics]].

## Security posture (dev)

- Dev override (`docker-compose.dev.yml`) binds infra ports **only to `127.0.0.1`**.
- Base/prod compose publish **no** MySQL/Redis/Nacos/backend ports.
- Nacos auth stays on; default `nacos/nacos` stays disabled.
- Dev login `admin`/`admin123` is dev-profile-only bootstrap, off in prod.

Invariants: [[concepts/security-invariants]]. Operational commands: `AGENTS.md`.
