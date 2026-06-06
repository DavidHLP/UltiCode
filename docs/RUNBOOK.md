# UltiCode Operations Runbook

Generated 2026-06-06. Source-of-truth: `scripts/dev/*.sh`, `ecosystem.config.cjs`,
`docker-compose*.yml`, `init-db/migrations/`, PM2 process list, and the
`ulticode-*` Docker containers.

---

## 1. Topology

| Service     | Port  | Process / Container                | Owner |
| ----------- | ----- | ---------------------------------- | ----- |
| Backend     | 9001  | PM2 `ulticode-9001` (Spring Boot)  | app   |
| Console FE  | 9002  | PM2 `ulticode-9002` (Vite dev/prod) | app  |
| Management  | 9003  | PM2 `ulticode-9003` (Vite dev/prod) | app  |
| Arthas MCP  | 8563  | PM2 `ulticode-arthas` (in-JVM)     | debug |
| MySQL 9.1   | 23306 | container `ulticode-mysql`         | infra |
| Redis 7     | 26379 | container `ulticode-redis`         | infra |
| Nacos 2.3.2 | 28848 | container `ulticode-nacos`         | infra |

> Production compose does **not** publish MySQL / Redis / Nacos / Backend ports
> externally. Only `docker-compose.dev.yml` may bind those ports to loopback.

---

## 2. Health Checks

| Layer       | Check                                                       | Pass criterion |
| ----------- | ----------------------------------------------------------- | -------------- |
| Backend     | `lsof -ti :9001` (HTTP root may 302/401)                    | PID returned   |
| Console     | `curl -fsS -o /dev/null -w "%{http_code}\n" http://localhost:9002/` | `200`          |
| Management  | `curl -fsS -o /dev/null -w "%{http_code}\n" http://localhost:9003/` | `200`          |
| MySQL       | `docker exec ulticode-mysql mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD"` | `mysqld is alive` |
| Redis       | `docker exec ulticode-redis redis-cli -a "$REDIS_PASSWORD" ping` | `PONG`         |
| Nacos       | `curl -fsS -o /dev/null -w "%{http_code}\n" http://localhost:28848/nacos/` | `200`          |

> **Do not** use `/actuator/health` — the project does not expose Spring
> Actuator. Use the table above.

---

## 3. Common Operations

### 3.1 Restart the backend

```bash
pm2 restart ulticode-9001
pm2 logs ulticode-9001 --lines 100
```

If env changed:

```bash
pm2 restart ulticode-9001 --update-env
```

### 3.2 Restart a frontend

```bash
pm2 restart ulticode-9002   # console
pm2 restart ulticode-9003   # management
```

Vite hot-reloads on file save; restart only when `vite.config.ts` or
`package.json` changes.

### 3.3 Run database migrations

```bash
./scripts/dev/migrate.sh info       # view status
./scripts/dev/migrate.sh migrate    # apply pending
./scripts/dev/migrate.sh validate   # verify hash
```

CI validates migrations on a fresh MySQL container before deploy.

### 3.4 Regenerate `.env` (destructive)

```bash
pm2 delete ulticode-9001 ulticode-9002 ulticode-9003
docker compose --env-file .env \
  -f docker-compose.yml -f docker-compose.dev.yml down -v
rm .env
./scripts/dev/init-env.sh
./scripts/dev/up.sh
```

> Only run when local data may be deleted. `init-env.sh` will not overwrite
> an existing `.env`.

### 3.5 Attach Arthas to the backend JVM

```bash
# Via PM2 (recommended)
pm2 start ecosystem.config.cjs --only ulticode-arthas

# Manual
java -jar tools/arthas-boot.jar --attach-only --http-port 8563 <PID>
# Validate MCP endpoint
curl -s -X POST http://localhost:8563/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

Useful Arthas commands: `dashboard`, `thread -n 5`, `trace <class> <method>`,
`watch <class> <method> '{params, returnObj}'`, `jad <class>`,
`ognl '<expr>'`, `heapdump`.

---

## 4. Common Issues

### 4.1 Chinese characters show as `???` or `æžå¨œ`

`ulticode-mysql` defaults to `character_set_client=latin1`. Always pass
`--default-character-set=utf8mb4` to ad-hoc `docker exec mysql ...` calls.

```bash
docker exec -e MYSQL_PWD="$DB_PASSWORD" ulticode-mysql \
  mysql --default-character-set=utf8mb4 -u "$DB_USER" "$DB_NAME"
```

The Spring Boot JDBC URL already includes `useUnicode=true&characterEncoding=UTF-8`,
so application-level reads are unaffected. The issue is only in manual
`docker exec` writes. To fix double-encoded rows, re-`UPDATE` with
`SET NAMES utf8mb4;` and the correct UTF-8 string.

### 4.2 `flyway:validate` fails after a PR

You edited an applied migration. **Never** edit a migration that may have been
applied. Add a new migration with a larger timestamp:

```
V{YYYYMMDDHHMMSS}__{Description}.sql
```

Use `init-db/validate-migration.sh` to lint naming before commit.

### 4.3 Frontend dev server fails to start (`EADDRINUSE`)

Another Vite is bound. Find the listener:

```bash
lsof -i :9002
lsof -i :9003
pm2 stop ulticode-9002 ulticode-9003
pm2 start all
```

### 4.4 JWT cookie not set after login

- `JWT_COOKIE_SECURE` must be `false` for plain HTTP (default in dev).
- `CORS_ALLOWED_ORIGINS` must include the frontend origin. Spring rejects the
  Set-Cookie response otherwise.
- Browser must allow third-party cookies for the API origin.

### 4.5 Nacos "user not found" or "token invalid"

The default `nacos/nacos` account is **disabled** by `bootstrap-nacos-user.sh`.
Use the `NACOS_USERNAME` / `NACOS_PASSWORD` from your generated `.env`. If the
credentials were rotated, run `bootstrap-nacos-user.sh` again with the new
password.

### 4.6 Arthas MCP not responding

- Confirm the agent is attached: `pm2 status ulticode-arthas`.
- Re-attach: `pm2 restart ulticode-arthas`.
- Verify HTTP port: `curl -fsS http://localhost:8563/mcp -X POST ...`.
- Java version mismatch (agent 21 vs target JVM 17) is logged as a WARN but
  does not break functionality.

---

## 5. Rollback Procedures

### 5.1 Bad frontend deploy

```bash
# Roll back to previous image tag from GHCR
docker pull ghcr.io/davidhlp/ulticode-public-next/console:<previous-sha>
docker pull ghcr.io/davidhlp/ulticode-public-next/management:<previous-sha>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 5.2 Bad backend deploy

```bash
# Roll back JAR
docker pull ghcr.io/davidhlp/ulticode-public-next/backend:<previous-sha>
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend
# Watch for healthcheck success
docker compose logs -f backend
```

### 5.3 Bad database migration

Migrations are forward-only in Flyway. To roll back:

1. Restore the most recent MySQL backup:
   ```bash
   docker exec -i ulticode-mysql mysql --default-character-set=utf8mb4 \
     -u root -p"$MYSQL_ROOT_PASSWORD" "$DB_NAME" < /path/to/backup.sql
   ```
2. **Bump `flyway_schema_history`** to a state ahead of the bad migration so
   Flyway does not re-apply it. Coordinate with the team before this step.
3. Open a follow-up migration that re-applies the intended effect.

`docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md` is the canonical reference
for the security migration applied 2026-06-06; consult it before touching
`refresh_tokens` or seed account data.

---

## 6. Alerting & Escalation

| Severity | Trigger                                              | First responder |
| -------- | ---------------------------------------------------- | --------------- |
| SEV-1    | Backend down > 5 min OR data-loss signal in logs     | Backend on-call |
| SEV-2    | Single service degraded but data safe                | Backend on-call |
| SEV-3    | Cosmetic / non-functional regression                | Frontend on-call |

Log sources to inspect in order:

1. `pm2 logs <service>` — application logs
2. `docker logs ulticode-mysql` / `ulticode-redis` / `ulticode-nacos` — infra logs
3. Arthas `thread -b` for deadlock, `dashboard` for heap + GC

Escalation path: Backend on-call → Tech lead → Project owner.

---

## 7. Backup & Recovery

- **MySQL backups**: configured via the `backup` module; defaults to
  `BACKUP_DIR=/tmp/ulticode-backups`. Verify cron / systemd timer in production.
- **Audit export limit**: `AUDIT_EXPORT_LIMIT=10000` (rows per export).
- **Restore drill**: schedule quarterly. Use
  `docs/SECURITY_REMEDIATION_RUNBOOK_2026-06-06.md` as a worked example for
  restoring from a security-migration snapshot.

---

## 8. Production Boundary Reminders

- `JWT_COOKIE_SECURE=true` — required in production.
- All infrastructure host ports bound to `127.0.0.1` in `docker-compose.dev.yml`
  only. Base + production compose publishes **no** MySQL/Redis/Nacos/backend
  ports.
- Nacos auth enabled; default `nacos/nacos` disabled.
- Refresh tokens are database-hashed, not recoverable. Rotation requires a new
  login.
- WebSocket auth uses the `access_token` cookie only — no query / URL token.
