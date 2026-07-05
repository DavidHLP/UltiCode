---
title: Development Environment Overview
type: overview
tags: [ops, dev, map, type/overview]
status: living
updated: 2026-07-05
sources:
  - ecosystem.config.cjs
  - docker-compose.yml
  - docker-compose.dev.yml
  - scripts/dev/
  - infrastructure/arthas/
  - CLAUDE.md
  - backend-spring/src/main/resources/application.yml
---

# Development Environment Overview

> [!quote] Essence
> What runs locally, how it's orchestrated, and the ordering traps that bite.
> This is the **knowledge layer** — the operational command reference lives in
> `AGENTS.md` and `CLAUDE.md`. Runtime diagnostics deep-dive:
> [[concepts/arthas-diagnostics]]. When to use the **Preview MCP panel**
> vs PM2, and how to detect which mode you're in:
> [[concepts/process-management]].

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

> [!warning] Traps
> - **`pm2 restart --update-env` does not reload `envFromFile`** — it uses the
>   daemon cache. If `.env` changed and 9001 throws `RedisWrongPasswordException`
>   with ↺ climbing, force a re-read: `pm2 delete ulticode-9001 && pm2 start
>   ecosystem.config.cjs --only ulticode-9001`. Inspect the real env via
>   `/proc/$(pm2 pid ulticode-9001)/environ` (`pm2 env <id>` shows stale values).
> - **`up.sh` cold-start pause is expected** — the dev-admin bootstrap
>   (`spring-boot:run --web-application-type=none`) blocks ~105s before the timeout
>   reaps it ("Bootstrap JVM did not self-exit… continuing"). Don't intervene; the
>   real failure signal is the background output file's mtime stalling **plus** PM2
>   empty **plus** ports free.
> - **MySQL charset via `docker exec`** — the container defaults
>   `character_set_client=latin1`. Direct `docker exec mysql -e "INSERT 中文…"`
>   double-encodes. Always pass `--default-character-set=utf8mb4` (or `SET NAMES
>   utf8mb4;`). The JDBC URL already has `useUnicode=true&characterEncoding=UTF-8`,
>   so app/Flyway writes are fine — only manual `docker exec` is affected.
> - **No Spring Actuator** — don't use `/actuator/health` for readiness. Use a known
>   public API + the two frontend roots + PM2 status + container health checks.

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

> [!danger] Production-vs-dev port exposure
> - Dev override (`docker-compose.dev.yml`) binds infra ports **only to `127.0.0.1`**.
> - Base/prod compose publish **no** MySQL/Redis/Nacos/backend ports.
> - Nacos auth stays on; default `nacos/nacos` stays disabled.
> - Dev login `admin`/`admin123` is dev-profile-only bootstrap, off in prod.
>
> Invariants: [[concepts/security-invariants]]. Operational commands: `AGENTS.md`.

## WSL2 + Docker Desktop cold-start pitfalls

> [!warning] Scope
> Three traps that bite **only** when running UltiCode on **WSL2 with Docker
> Desktop** as the backend (the Fedora/Ubuntu WSL distro, Docker Desktop
> managing containers). Native Linux + `docker compose` directly, or macOS
> Docker Desktop, are unaffected. All three fixes are already committed to
> `main`; this section exists so the next session does not re-debug them.

### 1. `openssl` missing on Fedora 44 WSL, and `/usr/sbin` not on `PATH`

**Symptom.** `./scripts/dev/up.sh` exits 0 in <1s with a single line:
`Required command not found: openssl`. The script never reaches `docker compose up`.

**Root cause.** Two separate things stacked:
- Fresh Fedora 44 WSL installs ship without `openssl` (only `openssl-libs`).
  `scripts/dev/init-env.sh` calls `openssl rand -hex` / `openssl rand -base64`
  to generate DB / Redis / Nacos credentials into `.env`.
- Even after installing `openssl` (`sudo dnf install -y openssl`), the binary
  lands at `/usr/sbin/openssl`. Fedora's default `~/.bashrc` does **not** add
  `/usr/sbin` to `PATH` for non-root users, and `up.sh`'s subshells inherit
  the empty path → `command -v openssl` fails.

**Fix (one-shot, host-level).**
```bash
sudo dnf install -y openssl
sudo ln -sf /usr/sbin/openssl /usr/local/bin/openssl
```
The symlink is intentional: `/usr/local/bin` is on every shell's default `PATH`,
so `up.sh` and `init-env.sh` find it without modifying their `require_command`
checks.

### 2. MySQL 9.1 in-container IPv6-only binding + Docker Desktop port-mapping handshake loss

**Symptom.** `up.sh` reaches the Flyway step; `mvn flyway:migrate` hangs for
minutes with no progress. `jstack <pid>` on the mvn JVM shows the main thread
blocked in `com.mysql.cj.protocol.a.NativeProtocol.beforeHandshake →
readServerCapabilities` (a `tryRead` in a SocketInputStream). `flyway_schema_history`
never gets created; `mysql -h 127.0.0.1 -P 23306` from the host times out on
the first byte of the server handshake, even though `docker exec ulticode-mysql
mysql …` (over the unix socket) returns `SELECT 1` instantly. Same shape
reappears for Redis (`AUTH` timeout) and Nacos (gRPC handshake drop) when
upstream services run on the host.

**Root cause.** Two compounding bugs:
- MySQL 9.1 in this Docker image binds **IPv6 only** by default
  (`/proc/net/tcp6` shows `[::]:3306` LISTEN, `/proc/net/tcp` is empty). No
  IPv4 listener means Docker Desktop's userland-proxy has nothing on the IPv4
  side to forward into.
- Docker Desktop's IPv4 → container IPv6 port forwarding on WSL2 drops the
  MySQL handshake packet (verified: `python3` raw `socket.create_connection
  ('127.0.0.1', 23306)` receives zero bytes; same code against
  `172.18.0.2:3306` — the container's docker-network IP — gets the 77-byte
  handshake immediately).

**Fix (committed in `docker-compose.yml` and `.env`).**
- `docker-compose.yml` mysql service: add `command: ["--bind-address=0.0.0.0"]`
  so MySQL also listens on IPv4. Plus explicit `ipv4_address: 172.18.0.2/3/4`
  on mysql / redis / nacos with a `subnet: 172.18.0.0/24` ipam block, so the
  IPs are stable across `docker compose down && up`.
- `.env`: point `DB_HOST=172.18.0.2`, `DB_PORT=3306` (container-internal port,
  not 23306), `REDIS_HOST=172.18.0.3`, `REDIS_PORT=6379`,
  `NACOS_SERVER_ADDR=172.18.0.4:8848`. Hosts connect directly to the docker
  bridge IPs, bypassing the broken userland-proxy path entirely.
- The 23306 / 26379 / 28848 `127.0.0.1`-bound mappings in
  `docker-compose.dev.yml` are kept for `docker exec` debugging only — JDBC
  and Spring Boot no longer go through them.

> [!note] Why not "just use `localhost`?"
> Because the localhost → container hop is exactly what loses the handshake.
> Direct container-IP routing sidesteps Docker Desktop's broken IPv6
> forwarder entirely. Other backends (macOS Docker Desktop, native Linux
> dockerd) do not have this bug — the `.env` IPs work there too, but the
> original `localhost:23306` would also work. The fixed IPs are a
> WSL2-compatible superset.

### 3. JDK 17.0.2 + WSL2 cgroup v2 NPE on first Spring Boot boot

**Symptom.** Backend `BUILD SUCCESS` for `clean install`, then `mvn
spring-boot:run` logs `Tomcat started on port 9001` **followed immediately by**
`Application run failed`. The NPE is
`Cannot invoke "jdk.internal.platform.CgroupInfo.getMountPoint()" because
"anyController" is null`, thrown from
`jdk.internal.platform.cgroupv2.CgroupV2Subsystem.getInstance`, called via
`Container.metrics → Metrics.systemMetrics → CgroupSubsystemFactory.create`,
reached through one of:
- `SystemMetricsAutoConfiguration#processorMetrics` (micrometer ProcessorMetrics)
- `TomcatMetricsBinder#onApplicationEvent` (micrometer Tomcat metrics,
  triggered by `SpringApplicationRunListeners.started` after `Tomcat started`)

**Root cause.** [JDK-8286157](https://bugs.openjdk.org/browse/JDK-8286157):
on WSL2, `/sys/fs/cgroup` is mounted as `cgroup2fs`, but
`CgroupV2Subsystem.getInstance` reads `cgroup.controllers` and finds no
controller entry (because the WSL2 kernel exposes an empty controllers list
for non-root contexts). The NPE is thrown before the cgroup-v1 fallback
runs. Fixed upstream in **JDK 17.0.5+** and JDK 21 LTS.

**Fix (committed in `application-dev.yml` and `ecosystem.config.cjs` — dev profile only).**

> [!warning] Why dev-profile, not base `application.yml`?
> The original cold-start commit put these excludes in base `application.yml`,
> which loads in **every** profile including prod — silently disabling
> system/jvm/tomcat micrometer binders in production. The follow-up fix
> (commit `f175a17`) moved them into `application-dev.yml` so prod keeps full
> metrics; dev still gets the NPE suppression.

- `backend-spring/src/main/resources/application-dev.yml` (dev profile only):
  ```yaml
  spring:
    autoconfigure:
      exclude:
        # Spring Boot profile list-replace: restate base excludes here
        - org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
        - org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration
        - org.springframework.boot.actuate.autoconfigure.metrics.web.tomcat.TomcatMetricsAutoConfiguration
        - org.springframework.boot.actuate.autoconfigure.metrics.JvmMetricsAutoConfiguration
  management:
    metrics:
      enable:
        processor: false
        tomcat: false
        all: false
  ```
  Excluding the three auto-configs prevents the
  `ProcessorMetrics` / `TomcatMetrics` / `JvmMemoryMetrics` binders from being
  instantiated. The `management.metrics.enable.processor: false` /
  `tomcat: false` / `all: false` **does not** work alone — the bean is created
  before the enable flag is consulted, so the NPE fires regardless. Only
  autoconfig `exclude:` actually skips the constructor call. Both are kept as
  belts-and-braces. Actuator's health endpoints are unaffected. Base
  `application.yml` keeps only `ErrorMvcAutoConfiguration` in its exclude list;
  prod loads base only → prod keeps all metrics.
- `ecosystem.config.cjs` ulticode-9001 env: pre-set
  `JAVA_TOOL_OPTIONS: "-Djdk.management.operatingSystemProvider=Standard"`.
  This forces the Standard OperatingSystemMXBean provider (skipping cgroup
  probing) at the JVM level — a belt-and-braces guard alongside the dev-profile
  metric excludes, covering any non-micrometer code path that touches
  `ManagementFactory.getPlatformMBeanServer`. Required on WSL2 + JDK <17.0.5;
  redundant but harmless on newer JDKs / other OS. **dev-only** (PM2 host JVM,
  not containerized) — does not affect prod.

> [!danger] Avoid the "fix" of swapping to JDK 21 globally
> `JAVA_HOME` symlink in vfox is JDK 17.0.2 by project convention
> (`backend-spring/pom.xml` `<java.version>17</java.version>`). The cached
> `v-21.0.2+13` in `~/.vfox/cache/java/` is a partial JRE (missing
> `libjava.so`, so `java -version` errors with "could not find libjava.so"),
> and the corresponding `openjdk-21*.tar.gz` archives in the cache are
> truncated ("gzip: stdin: unexpected end of file"). Don't try to swap the
> vfox symlink to 21 — it won't compile `backend-spring`. The autoconfig
> exclude is the clean fix on the current JDK.

## Links out

> [!link] Related pages
> - [[overview/architecture-overview]]
> - [[concepts/arthas-diagnostics]] · [[concepts/security-invariants]]
> - [[concepts/theme-system]]
> - [[concepts/process-management]]