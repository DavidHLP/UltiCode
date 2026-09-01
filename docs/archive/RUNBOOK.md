# UltiCode Runbook

Operational reference for deploying, monitoring, and recovering the UltiCode
stack. Targets the PM2 + Docker Compose dev topology described in
`docs/ENV.md` and the architecture in `docs/CODEMAPS/`.

> 📌 文档总入口：[README.md](./README.md) · 文档规范：[DOCS-SPEC.md](./DOCS-SPEC.md)

---

## 1. Stack Overview

| Component | Port | Process | Logs |
| --- | --- | --- | --- |
| Backend (Spring Boot) | 9001 | `pm2: ulticode-9001` | `/tmp/ulticode-9001-{out,error}.log` |
| Console (Vite) | 9002 | `pm2: ulticode-9002` | `/tmp/ulticode-9002-{out,error}.log` |
| Management (Vite) | 9003 | `pm2: ulticode-9003` | `/tmp/ulticode-9003-{out,error}.log` |
| Arthas MCP | 8563 | `pm2: ulticode-arthas` (or `SessionStart` hook) | `/tmp/ulticode-arthas-{out,error}.log` |
| MySQL 9.1 | 23306 | Docker `ulticode-mysql` | `docker logs ulticode-mysql` |
| Redis 7 | 26379 | Docker `ulticode-redis` | `docker logs ulticode-redis` |
| Nacos 2.3.2 | 28848 | Docker `ulticode-nacos` | `docker logs ulticode-nacos` |

> **PM2 defines 5 apps**: 4 long-running (`ulticode-9001`, `ulticode-9002`,
> `ulticode-9003`, `ulticode-arthas`) plus the one-shot `ulticode-init-db`
> migration task (runs once, then `stopped` — see §4.6). PM2 logs rotate at
> 20 MB with 5 retained files (see `ecosystem.config.cjs`).

---

## 2. Startup / Shutdown

### Cold start (fresh checkout)

```bash
./scripts/dev/init-env.sh            # generate private .env (random creds)
./scripts/dev/up.sh                  # infra + migrate + install + pm2 start
./scripts/dev/up.sh --skip-install   # reuse installed deps on subsequent runs
```

`up.sh` is **order-sensitive** (Docker healthy → Flyway migrate → pm2 start).
If you start PM2 first, `ulticode-9001` crashes with `Connection refused`
on MySQL and `pm2 logs ulticode-9001` shows the connection error.

### Daily start (env already exists)

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis nacos
pm2 start ecosystem.config.cjs        # or `pm2 start all` after first run
pm2 status
```

### Graceful shutdown

```bash
pm2 stop all                          # stop PM2 processes (SIGTERM, then SIGKILL after 1.6s)
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml stop
```

### Hard reset (data loss)

```bash
pm2 delete ulticode-9001 ulticode-9002 ulticode-9003
docker compose --env-file .env -f docker-compose.yml -f docker-compose.dev.yml down -v
rm .env
./scripts/dev/init-env.sh
./scripts/dev/up.sh
```

> ⚠️ `down -v` removes MySQL/Redis volumes. **Never** run it when local
> data must be preserved.

---

## 3. Health Checks

### Quick liveness probe (no curl required)

```bash
lsof -ti :9001                        # backend listening?
lsof -ti :9002                        # console listening?
lsof -ti :9003                        # management listening?
docker inspect --format='{{.State.Health.Status}}' ulticode-mysql
docker inspect --format='{{.State.Health.Status}}' ulticode-redis
docker inspect --format='{{.State.Health.Status}}' ulticode-nacos
```

> **Note**: Spring Boot Actuator is **not** exposed. Do not probe
> `/actuator/health`; use the public API or PM2 status instead.

### Auth probe (real round-trip)

```bash
curl -s http://localhost:9001/auth/me | jq '.code'    # expect 401 unauthenticated
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9002
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:9003
```

### Log-based health signals

```bash
pm2 logs ulticode-9001 --nostream --lines 200 | grep -E 'Started|ERROR|Connection'
pm2 logs ulticode-9001 --nostream --lines 200 --raw | grep -i 'unsatisfiedlink\|class not found'
```

---

## 4. Common Issues

### 4.1 `ulticode-9001` restart-looping with `Connection refused` on MySQL

**Symptom**: `pm2 list` shows rapidly incrementing ↺ on `ulticode-9001`; `lsof -ti :9001` returns empty.

**Cause**: PM2 started before MySQL was healthy. `application-dev.yml`
points at `localhost:23306`; if MySQL is not yet accepting connections,
Spring Boot exits with `com.mysql.cj.jdbc.exceptions.CommunicationsException`.

**Fix**:
```bash
docker inspect --format='{{.State.Health.Status}}' ulticode-mysql   # wait for "healthy"
pm2 restart ulticode-9001 --update-env
```

Or one-shot: `./scripts/dev/up.sh --skip-install`.

### 4.2 `docker exec mysql` writes garbled Chinese (`æžå¨œ`)

**Symptom**: Manual `INSERT` of Chinese characters into MySQL shows as
`Mojibake` when read back through the application.

**Cause**: The `ulticode-mysql` container's `character_set_client` defaults
to `latin1`. The Spring Boot JDBC URL already sets
`useUnicode=true&characterEncoding=UTF-8`, so the application is fine —
the **manual** `docker exec mysql` path is the only one affected.

**Fix** (use one of the two forms):
```bash
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME"

# or inline
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" \
  -e "SET NAMES utf8mb4; INSERT INTO t (name) VALUES ('正确');"
```

**Verify** the byte sequence is correct UTF-8 (`E78E8BE6988E` for "王明"):

```bash
docker exec ulticode-mysql mysql --default-character-set=utf8mb4 -u ulticode -p"$DB_PASSWORD" \
  ulticode -e "SELECT HEX(name) FROM users WHERE username='xxx'"
```

**Recover** from already-double-encoded rows:
```sql
SET NAMES utf8mb4;
UPDATE users SET name = '正确的姓名' WHERE id = '...';
```

### 4.3 Arthas MCP returns `Session ID required` for every command

**Symptom**: `mcp__arthas-mcp__*` calls fail with a 4 KB stack trace mentioning
`McpStreamableHttpRequestHandler` and `mcp-session-id`.

**Cause**: The Arthas agent is running with the default `STREAMABLE`
protocol, which requires the MCP client to maintain a session. Claude Code's
MCP client does not (it uses STATELESS).

**Fix** — confirm project-level config is locked to STATELESS and re-attach:

```bash
grep mcpProtocol infrastructure/arthas/arthas.properties    # must be STATELESS
pm2 restart ulticode-9001                                  # forces wrapper re-attach
scripts/arthas-cli.sh status                                # verify MCP up
```

If `mcpProtocol` is missing or wrong, edit
`infrastructure/arthas/arthas.properties`, set
`arthas.mcpProtocol=STATELESS`, then re-attach. The wrapper
`scripts/start-arthas.sh` syncs this file to
`~/.arthas/lib/<version>/arthas/arthas.properties` on every attach.

### 4.4 `pm2 start ulticode-9001` builds with BUILD FAILURE / `UnsatisfiedLinkError`

**Symptom**: PM2 logs show `BUILD FAILURE` after `mvn clean install`, or
the JVM logs `UnsatisfiedLinkError` on first request.

**Cause**: Java version mismatch. The project is pinned to Java 17 for
the backend JVM, but the system default may be 21 (arthas-boot side) or
older (8/11). vfox manages the project's toolchain.

**Fix**:
```bash
java -version                                              # check current
vfox use -g java@17.0.11+9                                 # match project pin
pm2 restart ulticode-9001 --update-env                     # re-evaluate env
```

> See `.java-version` (or `pom.xml`'s `<java.version>17</java.version>`)
> for the pinned version.

### 4.5 Flyway migration fails with `outOfOrder` or `validateOnMigrate` mismatch

**Symptom**: `migrate.sh migrate` exits non-zero; `flyway_schema_history`
shows drift.

**Cause**: Either (a) `init-db/migrations/` was edited after a migration
was applied (forbidden — see `AGENTS.md`), or (b) a developer ran a
different MySQL container against the same volume.

**Fix**:
```bash
./scripts/dev/migrate.sh validate
git diff init-db/migrations/                                # must be empty for applied versions
```

If the schema is genuinely out of sync, **never** delete an applied
migration. Add a new forward-only migration with a later timestamp.

### 4.6 `pm2 status` shows `ulticode-init-db` in `stopped` state

**Symptom**: PM2 lists `ulticode-init-db` with status `stopped` after a
successful `up.sh`.

**Cause**: This is **expected**. `ulticode-init-db` is a one-shot Flyway
task; it runs migrations once and exits. Verify success:

```bash
pm2 logs ulticode-init-db --nostream | grep "BUILD SUCCESS"
```

### 4.7 `pnpm test` fails in console with `auth-core` test errors

**Symptom**: Console's `pnpm test` reports failures in `shared/auth-core`.

**Cause**: Console's test script intentionally excludes `**/auth-core/**`.
If failures appear, the test path is wrong. Re-run with:

```bash
cd shared/auth-core && pnpm test    # run auth-core tests in its own package
```

> The exclusion is **per the project's CI matrix** — see
> `docs/CONTRIBUTING.md` "Test scope difference" note.

### 4.8 Vite dev server fails with `crypto.hash is not a function`

**Symptom**: Console or management dev server exits with
`TypeError: crypto.hash is not a function` from a Vite plugin.

**Cause**: Node.js version is below the engines pin (`^20.19.0 || >=22.12.0`).
Node 18 lacks `crypto.hash` (added in 20.19 / 22.12).

**Fix**:
```bash
node --version                                            # check
nvm use 22.12.0  # or 20.19.0
cd console && rm -rf node_modules && pnpm install
```

---

## 5. Arthas MCP

The Arthas MCP server exposes JVM diagnostics over `http://localhost:8563/mcp`.
The wrapper uses a three-launcher mutex: PM2, SessionStart hook, or `arthas-cli.sh`
— whichever attaches first wins; others detect `:8563` listening and skip.

```bash
scripts/arthas-cli.sh status     # port, PID, MCP, launcher
scripts/arthas-cli.sh logs       # tail wrapper log
scripts/arthas-cli.sh restart    # stop + start
```

> **Blocking-command timeout**: `dashboard`, `trace`, `watch`, `monitor`,
> `tt` block the synchronous MCP context for the 30 s tool timeout. Always
> pass `-n N` (N ≤ 5) to bound invocations. For deep traces, prefer
> `pm2 logs ulticode-9001 --nostream --lines 200` or drop into the
> interactive telnet (`scripts/arthas-cli.sh`).

---

## 6. Database Operations

### Manual SQL (Chinese-aware)

```bash
set -a; source .env; set +a
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME" \
  -e "SELECT id, name, HEX(name) FROM users LIMIT 5;"
```

### Inspect migrations

```bash
./scripts/dev/migrate.sh info
# or
cd init-db && mvn flyway:info
```

### Backup (DB module, optional)

Set `BACKUP_DIR=/tmp/ulticode-backups` (default). Trigger via the
`/admin/backups` endpoints, or:

```bash
docker exec ulticode-mysql mysqldump \
  --default-character-set=utf8mb4 -u ulticode -p"$DB_PASSWORD" \
  ulticode | gzip > "$BACKUP_DIR/ulticode-$(date +%Y%m%d).sql.gz"
```

### Redis state inspection

```bash
docker exec ulticode-redis redis-cli KEYS "csrf:*"          # active CSRF tokens
docker exec ulticode-redis redis-cli KEYS "ulticode:*"      # cache keys
docker exec ulticode-redis redis-cli GET "csrf:{userId}:{tokenId}"
```

---

## 7. Rollback Procedures

### Code rollback (frontend)

```bash
pm2 stop ulticode-9002                                    # or ulticode-9003
cd console && git checkout <previous-sha>                 # or use a tag
pnpm install && pnpm build
pm2 restart ulticode-9002
```

### Code rollback (backend)

```bash
pm2 stop ulticode-9001
cd backend-spring && git checkout <previous-sha>
./mvnw clean install -DskipTests
pm2 restart ulticode-9001
```

### Migration rollback

**Never** delete an applied migration. To roll back a destructive change:

1. Add a new migration that reverses the change (e.g. drop added column,
   restore old data from backup).
2. Bump the migration timestamp to a value greater than the latest applied
   version.
3. Run `./scripts/dev/migrate.sh migrate`.
4. The previous migration remains in `flyway_schema_history` (so the
   `validate` step stays green).

### Container image rollback (production)

GHCR images are immutable. Roll back by re-tagging the previous SHA as
the active tag and re-deploying:

```bash
# See deployment runbook for the exact platform steps; this is a stub.
```

> Production deployment topology is **out of scope** for this runbook —
> the local dev / staging flow is the focus.

---

## 8. CI / Failure Triage

GitHub Actions path-based detection triggers only relevant jobs:

| Path changed | Job set |
| --- | --- |
| `backend-spring/**`, `init-db/**` | Maven build + test (ci) + Flyway validation + Gitleaks |
| `console/**`, `shared/**` | Console lint + type-check + test + prod dep audit |
| `management/**`, `shared/**` | Management lint + type-check + test + i18n key validation + prod dep audit |
| `docker/**`, `Dockerfile*` | Docker build verification |
| Migrations under `init-db/migrations/` | Fresh MySQL 9.1 container runs `mvn flyway:migrate` |

If a CI job fails:

1. Check the **Gitleaks** job first (fails-fast on any leaked secret).
2. For Flyway, run the migration locally on a fresh MySQL container
   (`./scripts/dev/up.sh --skip-install` after `docker compose down -v`).
3. For prod dep audit, run `pnpm audit --prod --audit-level high` in the
   affected package.

---

## 9. Escalation

- **Authentication / refresh token / seed account** issues: see
  `CLAUDE.md` → **Security Invariants** (canonical). The standalone
  `SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md` was consolidated into this
  RUNBOOK (§6 Database, §7 Rollback) and `CLAUDE.md` (commit `9ce22f921`);
  migration `V20260606130000` remains the canonical schema reference.
- **Cross-stack DTO / API alignment**: invoke
  `cross-stack-dto-granularity-alignment` skill from
  `.agents/skills/`.
- **Runtime JVM issue**: `scripts/arthas-cli.sh` interactive telnet
  (`dashboard -n 1`, `thread -n 3`, `trace <Class> <method> -n 3`).
- **Unrecoverable corruption**: dispose local data with the
  "Hard reset" recipe in §2 and re-import from backup.

---

## 10. Feature Flag 切换手册

> 本节是 ADR-005 §4 #3 的落地. flag 定义在
> `backend-spring/src/main/java/com/ulticode/common/config/FeatureFlagsProperties.java`,
> 全部以 `app.features.*` 为 prefix. 切换走 `pm2 reload ulticode-9001`
> (重启级, 详见 ADR-005 §2.8 F10 修订 + §2.6). Nacos Config client 集成是
> ADR-008 范围, 不在本节.

<a id="101-flag-总览-10-个-5-产品--5-cutover"></a>
### 10.1 Flag 总览 (10 个, 5 产品 + 5 cutover)

| Flag key (yml)                                              | Env var                                | 默认    | 切换场景                              | 风险 |
|-------------------------------------------------------------|----------------------------------------|---------|---------------------------------------|------|
| `app.features.use-new-contest-system`                       | `USE_NEW_CONTEST_SYSTEM`               | `false` | 新计分系统启用                        | 影响 contest verdict 流程 |
| `app.features.realtime-ranking-enabled`                     | `REALTIME_RANKING_ENABLED`             | `true`  | WebSocket 实时榜                      | 客户端断连需 fallback 轮询 |
| `app.features.first-solve-notifications-enabled`            | `FIRST_SOLVE_NOTIFICATIONS_ENABLED`    | `true`  | 首杀通知                              | WS 推送失败需 retry |
| `app.features.anticheat-enabled`                            | `ANTICHEAT_ENABLED`                    | `false` | 反作弊检测                            | 高 CPU 开销, peak 风险 |
| `app.features.contest-analytics-enabled`                    | `CONTEST_ANALYTICS_ENABLED`            | `true`  | 比赛分析生成                          | 慢查询可能影响榜单 |
| `app.features.use-judge-outbox`                             | `USE_JUDGE_OUTBOX`                     | `false` | M3a → M3c cutover 后                  | 双 producer 重复入队 (M3c 前禁止) |
| `app.features.use-generation-fence`                         | `USE_GENERATION_FENCE`                 | `false` | M3b 后                                | stale result 落地 (M3a 阴影期禁止) |
| `app.features.judge-queue.use-port`                         | `JUDGE_QUEUE_USE_PORT`                 | `false` | M3c cutover                           | Redisson Streams 与旧 RQueue 路由不一致 |
| `app.features.judge-queue.envelope-version`                 | `JUDGE_QUEUE_ENVELOPE_VERSION`         | `1`     | M3c-3 fence-aware 切 `2`              | v2 写入但 worker 还是 v1 路径会反序列化失败 |
| `app.features.use-notification-intent`                      | `USE_NOTIFICATION_INTENT`              | `false` | M4a → M4b caller 迁移完               | 老 path 漏迁移会导致 notification 静默丢失 |

注: env var 名取自 `application.yml` 中 `${XXX:default}` 占位符 — 项目自定义了
非 Spring Boot 默认的 `APP_FEATURES_*` 转换. CI 矩阵见 `.github/workflows/ci.yml`
中 `backend-test-features-off` / `backend-test-features-on` 两个 job.

### 10.2 切换流程 (单 flag 切 1 次)

1. 改 `backend-spring/src/main/resources/application-{dev,prod}.yml` (或
   `ecosystem.config.cjs` env 段写 env var)
2. 单 commit 切换: `flag(judge-queue.use-port): false → true, ADR-005 M3c cutover step 1`
3. 推 PR, 等 CI 中 `backend-test` / `backend-test-features-off` /
   `backend-test-features-on` 3 个 job 全绿
4. `pm2 reload ulticode-9001 --update-env` (zero-downtime)
5. `pm2 logs ulticode-9001 --nostream --lines 100 | grep -E "Started|ERROR|app.features"`
   验证启动 + flag 加载
6. Canary gate 24h: 见 ADR-005 §2.5

### 10.3 紧急回滚

```bash
# 1. 找上次 flag 切换的 commit
git log --oneline -5 -- backend-spring/src/main/resources/application-*.yml
# 2. revert
git revert <commit-sha> --no-edit
# 3. push, CI 重跑后 pm2 reload
pm2 reload ulticode-9001 --update-env
# 4. 记录到 ADR-005 §2.6 表
```

### 10.4 演练 (rollback drill)

每次部署到 dev 拓扑后, 至少跑 1 次对应 milestone 的 rollback drill, 记录实际耗时.
详见 ADR-005 §2.6 + `docs/adr/ADR-005a-rollback-drill-protocol.md` (ADR-005 的子协议, 新).

### 10.5 启动日志确认 (临时, 等 ADR-008)

```bash
pm2 logs ulticode-9001 --nostream --lines 200 | grep -E "app\.features|FeatureFlagsStartupLogger"
```

如果看不到 flag 启动打印, 确认 `application.yml` 暴露 `app.features` 段
(在 `backend-spring/src/main/resources/application.yml:213-246`).

### 10.6 产品功能 flag → 关联模块（仅 §10.1 未覆盖列）

> 默认值 / 切换场景 / 风险统一见 [§10.1](#101-flag-总览-10-个-5-产品--5-cutover)；本节只保留 §10.1 没有的「关联模块 / 文件」映射，避免与 §10.1 重复列。

| Flag key | 关联模块 / 文件 |
|----------|-----------------|
| `app.features.use-new-contest-system` | contest verdict 路径 |
| `app.features.realtime-ranking-enabled` | `WebSocketRealtimeRankingService` |
| `app.features.first-solve-notifications-enabled` | `NotificationDispatcher` → WS/email |
| `app.features.anticheat-enabled` | `AnticheatService` |
| `app.features.contest-analytics-enabled` | `ContestAnalyticsJob` |

### 10.7 Sandbox 切换 (不在 FeatureFlagsProperties 范围, 单独节)

> 此 flag 体系**不**走 §10.1 - §10.6 通用协议. sandbox 切换自 ADR-002 落地,
> 文档来源 `backend-spring/src/main/resources/application.yml:130-165`
> (Section "Code Execution Sandbox Configuration").

**唯一相关 flag** (env var):

| Flag (yml)                                          | Env var                  | 默认   | 实际作用 |
|-----------------------------------------------------|--------------------------|--------|----------|
| `code-execution.sandbox.d-form.enabled`            | `SANDBOX_DFORM_ENABLED`  | `true` | **不切换 dispatcher**. 仅验证 CodeExecutionService 收到的 language 集合 vs 实际可执行 harness (java + python only) |

**D-form 不可热回滚** (R-A2 修复后明确):

- 自 commit `8c13ec61f` (Phase 5.5 D-form refactor) 起, D-form 是**唯一**
  dispatch 路径, Form A (per-request bash wrapper) 旧 path 已删
- 设 `SANDBOX_DFORM_ENABLED=false` **不再** toggle dispatcher 路径,
  只影响 language validation
- 真要回滚 D-form → Form A 需:
  ```bash
  git revert 8c13ec61f 095a01fd5
  cd docker/sandbox && ./harness/build.sh
  docker build -t ulticode-sandbox:latest .
  pm2 reload ulticode-9001 --update-env
  ```
  端到端 ≥ 15min, 不在本 ADR "5min 热回滚" 演练范围
- 详见 ADR-005 §2.6 表脚注 ¹

**M2a 演练替代项**: 因 M2a 不可热回滚, 演练协议 (ADR-005a-rollback-drill-protocol.md)
矩阵中 M2a 行 strike through, 替换为 "演练 git revert + 重建 sandbox image" 的
"重建演练" 协议, 时间窗口 30min.
