---
title: Runbook — Operations & Incident Response
tags: [runbook, devops, incident, reference]
status: living
updated: 2026-06-19
owner: devops
---

> **中文导读** | On-call 事故响应手册。**正文为英文**（上游脚本生成），本节提供章节地图与速查指针。
>
> | 章节 | 主题 | 何时看 |
> |---|---|---|
> | §0 Quick Reference | PM2 / MySQL / Redis / Nacos / Arthas 速查表 | **事故第一秒** |
> | §1 Startup Order | Docker → init-db → 9001 → 前端 → Arthas 启动顺序 | 冷启动 / 启动崩溃 |
> | §2 PM2 Apps | 5 个 app 列表 + env cache 陷阱 | `pm2` 异常 |
> | §3 Health Checks | `lsof :9001` / `curl /auth/me` / 容器健康 | 排障起点 |
> | §4 Common Issues | 后端崩溃循环 / 字符集 / Arthas / Flyway / 沙箱 / 前端端口 / Nacos | **90% 事故在此** |
> | §5 Rollback | 代码 / 迁移 / 容器三种回滚 | 发版事故 |
> | §6 Backup | `BackupService` / mysqldump / 完整性校验 | 数据问题 |
> | §7 Monitoring & Alerts | 外部监控建议（Gitleaks / 容器健康） | 部署后 |
> | §8 CI / Failure Triage | 路径触发的 CI job 与排查顺序 | CI 红 |
> | §9 Escalation | SEV-1/2/3 分级 + 必带证据 | 升级判断 |
> | §10 Operational Cheatsheet | 常用一行命令 | 日常随手 |
>
> **强相关 ADR**：[[0005-rolling-deploy-rollback|adr/0005]]（回滚演练）、[[0008-websocket-cookie-auth|adr/0008]]（STOMP 401）。

# Runbook — Operations & Incident Response

<!-- Generated: 2026-06-19 | Source: CLAUDE.md, AGENTS.md, ecosystem.config.cjs, scripts/, infra/ -->

> **Audience**: on-call engineer / SRE responding to an incident or running a
> routine operation. **Read top-to-bottom** for context on the first incident;
> thereafter, use the table of contents to jump to the relevant section.

## 0. Quick Reference

| Resource         | URL / Command                                          |
| ---------------- | ------------------------------------------------------ |
| PM2 status       | `pm2 status`                                           |
| Backend logs     | `pm2 logs ulticode-9001 --nostream --lines 200`        |
| Backend raw log  | `/tmp/ulticode-9001-out.log` / `-error.log`            |
| Arthas status    | `scripts/arthas-cli.sh status`                         |
| Arthas MCP       | `http://localhost:8563/mcp` (STATELESS)                |
| MySQL container  | `docker ps --filter name=ulticode-mysql`               |
| MySQL exec       | `docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME"` |
| Redis CLI        | `docker exec -it ulticode-redis redis-cli -a "$REDIS_PASSWORD"` |
| Nacos UI         | http://localhost:28848/nacos (user: see `NACOS_USERNAME` in `.env`) |
| Container health | `docker inspect --format='{{.State.Health.Status}}' ulticode-{mysql,redis,nacos}` |
| Backend build    | `cd backend-spring && ./mvnw clean install -DskipTests` |
| Backend restart  | `pm2 restart ulticode-9001` (env cache caveat below)    |
| Frontend rebuild | `cd console && pnpm build-only` (after `pnpm type-check`) |

## 1. Startup Order (CRITICAL)

PM2 does **not** retry dependencies. If infrastructure is not Up/Healthy when
`ulticode-init-db` or `ulticode-9001` tries to start, you get cascading
`Connection refused` and rapid restart loops (`↺` in `pm2 status`).

**Correct order** (handled automatically by `./scripts/dev/up.sh`):

1. `docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis nacos`
2. Wait for all three to report `healthy` (`docker inspect --format='{{.State.Health.Status}}' …`)
3. `pm2 start ulticode-init-db` — runs `./scripts/dev/migrate.sh migrate`, exits 0
4. `pm2 start ulticode-9001` — Spring Boot boots
5. `pm2 start ulticode-9002 ulticode-9003 ulticode-arthas` — frontends + Arthas MCP

**Manual recovery** when init-db or 9001 is crash-looping:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml ps
pm2 restart ulticode-init-db     # re-runs Flyway
pm2 restart ulticode-9001
```

**up.sh cold-start is expected to stall ~105s** on the dev-admin bootstrap step
(`spring-boot:run --web-application-type=none`) — non-daemon Redisson/scheduler
threads prevent clean exit. The `timeout` wrapper takes over and continues.
**Do not interrupt.** Real failure: `up.sh` output mtime stops advancing for
several minutes while PM2 apps remain stopped.

## 2. PM2 Apps

| Name                  | Type        | Port | Notes                                              |
| --------------------- | ----------- | ---- | -------------------------------------------------- |
| `ulticode-init-db`    | one-shot    | —    | Runs `./scripts/dev/migrate.sh migrate`, exits 0    |
| `ulticode-9001`       | long-lived  | 9001 | Spring Boot — first to start after init-db         |
| `ulticode-9002`       | long-lived  | 9002 | Vite dev (binds 127.0.0.1 explicitly to dodge IPv6) |
| `ulticode-9003`       | long-lived  | 9003 | Vite dev (binds 127.0.0.1 explicitly)              |
| `ulticode-arthas`     | long-lived  | 8563 | Arthas 4.2.2 MCP STATELESS — start AFTER 9001      |

Logs: `/tmp/ulticode-<name>-out.log` and `-error.log` (20 MB rotation × 5 files).

### 2.1 PM2 env cache trap

`pm2 restart --update-env` **does not re-read `envFromFile` in `ecosystem.config.cjs`**.
PM2 caches the environment at `start` time. If you change `.env` and the backend
reports `RedisWrongPasswordException` / DB auth errors:

```bash
pm2 delete ulticode-9001
pm2 start ecosystem.config.cjs --only ulticode-9001
```

To inspect what the process actually sees:

```bash
tr '\0' '\n' < /proc/$(pm2 pid ulticode-9001)/environ | grep -E 'DB_|REDIS_|JWT_'
```

(`pm2 env <id>` shows stale data and is **not** trustworthy.)

具体触发案例:改 `.env` 后验证审核统计卡片时命中此陷阱 → [[moderation-stats-and-seed|审核模块运维深读]] §PM2 env 缓存陷阱。

## 3. Health Checks

This project **does not** expose Spring Actuator. Use:

| Check                                  | Expected                                       |
| -------------------------------------- | ---------------------------------------------- |
| `lsof -ti :9001`                       | non-empty (process bound)                      |
| `curl -fsS http://localhost:9001/auth/me` | `200` with `Result` envelope                 |
| `curl -fsS http://localhost:9002/`     | `200` (HTML)                                   |
| `curl -fsS http://localhost:9003/`     | `200` (HTML)                                   |
| `pm2 status`                           | all `online`, init-db `stopped` (normal)       |
| `docker inspect --format='{{.State.Health.Status}}' ulticode-mysql` | `healthy`     |
| `docker inspect --format='{{.State.Health.Status}}' ulticode-nacos` | `healthy`     |
| `scripts/arthas-cli.sh status`         | port 8563 listening                            |

## 4. Common Issues

### 4.1 Backend crash-loops on startup (`↺` count rising)

| Symptom                                              | Cause                              | Fix |
| ---------------------------------------------------- | ---------------------------------- | --- |
| `Connection refused` on MySQL                        | MySQL not yet healthy              | Wait for `docker inspect` healthy, then `pm2 restart ulticode-9001` |
| `RedisWrongPasswordException`                        | `.env` changed; PM2 env stale      | `pm2 delete ulticode-9001 && pm2 start ecosystem.config.cjs --only ulticode-9001` |
| `FlywayValidateException: Detected failed migration` | Drift between DB and `init-db/migrations/` | `./scripts/dev/migrate.sh repair` then `migrate` |
| `JWT secret must be at least 32 chars`               | `JWT_SECRET` placeholder still set | `rm .env && ./scripts/dev/init-env.sh` |
| `Failed to bind to 0.0.0.0:9001`                     | Port collision                     | `lsof -ti :9001` → kill stale process or change `SERVER_PORT` |
| `JAVA_HOME not set` / wrong Java                     | vfox not activated                 | `vfox use -g java@21.0.2+13` (or 17 per `.vfox.toml`) and `pm2 restart --update-env` |

### 4.2 `docker exec mysql` shows Chinese as `???` or mojibake

Container defaults to `character_set_client=latin1`. Application path is fine
(JDBC forces UTF-8); manual `docker exec` is the only affected path.

**Always** pass `--default-character-set=utf8mb4`:

```bash
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME"
```

**Repair already-double-encoded rows** (e.g. name showing as `æžå¨œ`):

```sql
SET NAMES utf8mb4;
UPDATE users SET name = '正确的姓名' WHERE id = '...';
```

Verify a correct UTF-8 byte sequence for a Chinese name should be 9 bytes (3 chars × 3 bytes) — e.g. `王明` → `E78E8BE6988E` (12 hex chars).

### 4.3 Arthas MCP returns `Session ID required`

`mcp__arthas-mcp__*` calls fail with a 4 KB stack trace mentioning
`McpStreamableHttpRequestHandler` and `mcp-session-id`.

**Cause**: arthas-boot 4.2.2 default is `STREAMABLE`, which requires the MCP
client to maintain a session. Claude Code's MCP client is STATELESS.

**Fix** — confirm project config and re-attach:

```bash
grep mcpProtocol infrastructure/arthas/arthas.properties   # must be STATELESS
pm2 restart ulticode-9001                                 # forces wrapper re-attach
scripts/arthas-cli.sh status                               # verify :8563 up
```

The `infrastructure/arthas/arthas.properties` is the **source of truth**; the
wrapper `scripts/start-arthas.sh` syncs it to
`~/.arthas/lib/4.2.2/arthas/arthas.properties` on every attach.

### 4.4 Arthas blocking commands timeout (30s)

`dashboard` / `trace` / `watch` / `monitor` / `tt` block forever in the STATELESS
MCP. **Don't retry** — descend the priority ladder:

1. **First**: `pm2 logs ulticode-9001 --nostream --lines 200` — most problems visible in app logs
2. **Then**: `pm2 logs ulticode-9001 --nostream --lines 200 --raw` — unformatted stack traces
3. **Then**: `scripts/arthas-cli.sh` interactive telnet (no MCP 30s cap)
4. **Then**: `./mvnw -Dtest='*IT' test -B` — verify N+1 or duplicate-add logic
5. **Then**: `mcp__plugin_context-mode_context-mode__ctx_execute` for Java reflection / grep (no MCP timeout)

Always pass `numberOfExecutions=N` (N ≤ 5) on enhanced commands.

### 4.5 Sandbox verdicts — all error

| Pattern                                              | Cause                              | Fix |
| ---------------------------------------------------- | ---------------------------------- | --- |
| All `SE` + `Cannot fork`                             | host/cgroup pressure               | `sysctl kernel.pid_max`; check `pids.current` |
| All `SE` + `Unable to find image`                    | Image missing                      | `docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox/` |
| All `TLE` on trivial problem                         | harness pre-import missing         | Verify `harness.py::build_solution_preamble()` injected `typing.__all__` etc. |
| All `RE` after adding a new Python file              | Missing from `build_<lang>()` `cp` | Re-run `./docker/sandbox/harness/build.sh python` (rebuilds image) |

### 4.6 Flyway checksum mismatch

If a previously-applied migration was edited, or pulled changes drift from
`flyway_schema_history`, the next `mvn flyway:migrate` aborts with
`Migration checksum mismatch`.

```bash
./scripts/dev/migrate.sh repair
./scripts/dev/migrate.sh migrate
```

The Flyway CLI is **only** available via this wrapper (not on PATH).

### 4.7 Frontend dev port (9002/9003) refuses to bind

Vite defaults to `localhost` which on Linux resolves to IPv6 `::1` only. PM2
already passes `--host 127.0.0.1` to bind IPv4. If you see connection refused
on `127.0.0.1`, check `/etc/hosts` for `::1 localhost` and `pm2 restart`.

### 4.8 Nacos auth error after `init-env.sh` re-run

`NACOS_AUTH_TOKEN` is a server-side secret; rotating the local `.env` value
without restarting the Nacos container leaves the server holding the old token.
After rotating client-side, **either** restart Nacos (`docker compose restart
nacos`) **or** reset it via `scripts/security/bootstrap-nacos-user.sh`.

## 5. Rollback

### 5.1 Code rollback (single commit)

```bash
git revert <commit-sha>
git push                                     # explicit user approval required
pm2 restart ulticode-9001                    # env cache caveat: see §2.1
```

### 5.2 Migration rollback

Flyway does not auto-rollback DDL. Procedure:

1. **Forward-fix preferred** — write a new migration that undoes the change
   (e.g. `ALTER TABLE … DROP COLUMN …`)
2. For data corruption, restore from `BACKUP_DIR` (configured in `.env`):

   ```bash
   ls -lh "$BACKUP_DIR"                      # find latest dump
   gunzip -c "$BACKUP_DIR/<dump>.sql.gz" | \
     docker exec -i ulticode-mysql mysql --default-character-set=utf8mb4 -u root -p"$MYSQL_ROOT_PASSWORD"
   ```

3. For schema-level: drop the schema, drop the volume, re-migrate from scratch
   (`docker compose down -v` then `./scripts/dev/up.sh`)

### 5.3 Container rollback (prod)

```bash
gh workflow run cd-rollback.yml -f ref=<previous-image-tag>
```

(`cd-rollback.yml` is the project-defined rollback workflow — see
`docs/adr/ADR-005-rolling-deploy-playbook.md` for the full procedure.)

## 6. Backup

`BackupService` writes to `BACKUP_DIR` (default `/tmp/ulticode-backups`).
Schedule: `BackupScheduler` runs nightly. Verify a backup's integrity before
trusting it:

```bash
gunzip -t "$BACKUP_DIR"/ulticode-*.sql.gz && echo "OK" || echo "CORRUPT"
```

Manual dump:

```bash
docker exec ulticode-mysql mysqldump \
  --default-character-set=utf8mb4 \
  -u root -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers \
  "$DB_NAME" | gzip > "$BACKUP_DIR/manual-$(date +%F).sql.gz"
```

## 7. Monitoring & Alerts

- `spring-boot-starter-actuator` is on the classpath but the project does **not**
  expose `/actuator/**` publicly. Internal Micrometer metrics go to
  `micrometer-registry-prometheus` — only scrape from the same network.
- Recommended external checks (Prometheus / Blackbox / Pingdom):
  - `GET http://<host>:9001/auth/me` returns 200 (anonymous 401 is also a valid signal)
  - Container health on `ulticode-mysql` and `ulticode-nacos`
  - PM2 app `status == online` for all long-lived apps
  - Disk usage on `BACKUP_DIR` (alert at 80%)

## 8. CI / Failure Triage

GitHub Actions path-based detection triggers only relevant jobs:

| Path changed                              | Job set |
| ----------------------------------------- | ------- |
| `backend-spring/**`, `init-db/**`         | Maven build + test (ci) + Flyway validation + Gitleaks |
| `console/**`, `shared/**`                 | Console lint + type-check + test + prod-dep audit |
| `management/**`, `shared/**`              | Management lint + type-check + test + i18n key validation + prod-dep audit |
| `docker/**`, `Dockerfile*`                | Docker build verification |
| Migrations under `init-db/migrations/`    | Fresh MySQL 9.1 container runs `mvn flyway:migrate` |

Triage order on CI failure:

1. **Gitleaks** first — fails fast on any leaked secret
2. **Flyway** failure → run locally on fresh MySQL: `./scripts/dev/up.sh --skip-install` after `docker compose down -v`
3. **Prod dep audit** → `pnpm audit --prod --audit-level high` in the affected package

## 9. Escalation

| Severity | Examples                                                       | Channel                                    |
| -------- | -------------------------------------------------------------- | ------------------------------------------ |
| SEV-1    | Backend down, sandbox broken for all users, data loss          | Page on-call; commit incident to `docs/incidents/` |
| SEV-2    | Partial degradation, single feature broken                     | Slack #ulticode-ops; on-call investigates  |
| SEV-3    | Cosmetic, single-user-only, dev-environment only                | File issue; address in next sprint         |

For SEV-1/SEV-2, attach:

- `pm2 status` output
- `pm2 logs ulticode-9001 --nostream --lines 500` (or relevant app)
- `docker ps` and container health
- The git SHA currently running (`pm2 jlist | jq '.[].pm2_env.version` — or check `git rev-parse HEAD`)
- If applicable: `scripts/arthas-cli.sh status`

## 10. Operational Cheatsheet

```bash
# Restart everything (after .env / config change)
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml up -d
pm2 delete all
pm2 start ecosystem.config.cjs

# Tail backend logs
pm2 logs ulticode-9001 --lines 200

# Inspect a thread
mcp__arthas-mcp__thread {"topN": 5}
# or interactive
scripts/arthas-cli.sh start
java -jar tools/arthas-boot.jar   # then: dashboard -n 1, thread -n 3, jad <class>

# MySQL quick query
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" \
  -e "SELECT id, status, created_at FROM submissions ORDER BY created_at DESC LIMIT 10;"

# Inspect CSRF tokens in Redis
docker exec -it ulticode-redis redis-cli -a "$REDIS_PASSWORD" KEYS 'csrf:*'

# Tail Nacos config diff
curl -s -u "$NACOS_USERNAME:$NACOS_PASSWORD" \
  "http://localhost:28848/nacos/v1/cs/configs?dataId=ulticode-common.yaml&group=DEFAULT_GROUP" | head -50
```
