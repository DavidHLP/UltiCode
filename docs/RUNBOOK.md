# UltiCode Runbook

Operational reference for deploying, monitoring, and recovering the UltiCode
stack. Targets the PM2 + Docker Compose dev topology described in
`docs/ENV.md` and the architecture in `docs/CODEMAPS/`.

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

> **PM2 app count = 4** (including `ulticode-arthas`). PM2 logs rotate at 20 MB
> with 5 retained files (see `ecosystem.config.cjs`).

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
  `docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md` (the security-fix
  migration `V20260606130000` is the canonical reference).
- **Cross-stack DTO / API alignment**: invoke
  `cross-stack-dto-granularity-alignment` skill from
  `.agents/skills/`.
- **Runtime JVM issue**: `scripts/arthas-cli.sh` interactive telnet
  (`dashboard -n 1`, `thread -n 3`, `trace <Class> <method> -n 3`).
- **Unrecoverable corruption**: dispose local data with the
  "Hard reset" recipe in §2 and re-import from backup.
