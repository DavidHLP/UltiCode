# UltiCode Runbook

Operational guide for deploying and maintaining UltiCode.

---

## Service Overview

| Service | Port | Name | Description |
|---------|------|------|-------------|
| Backend Spring | 9001 | ulticode-9001 | REST API, auth, WebSocket |
| Console Frontend | 9002 | ulticode-9002 | User-facing SPA |
| Management Frontend | 9003 | ulticode-9003 | Admin dashboard SPA |
| Recommend Provider | 20881 | ulticode-9004 | Dubbo RPC provider |
| Recommend Web | 9005 | ulticode-9005 | Recommendation REST gateway |
| MySQL | 23306 | - | Primary database |
| Redis | 26379 | - | Session cache, rate limiting |
| Nacos | 28848 | - | Service discovery |

---

## Deployment Procedures

### Production Deployment

#### 1. Pre-deployment Checklist

- [ ] All migrations applied and validated
- [ ] Environment variables configured in `.env`
- [ ] Strong passwords set for DB and Redis
- [ ] JWT_SECRET is at least 32 characters
- [ ] Backup completed

#### 2. Database Migration

```bash
# Validate migrations before deploying
cd db-manager
source .venv/bin/activate
db-manager validate

# Apply migrations (if needed)
db-manager migrate --dry-run  # Preview first
db-manager migrate
```

#### 3. Build Artifacts

```bash
# Backend
cd backend-spring
./mvnw package -DskipTests

# Frontends
cd console && pnpm build
cd management && pnpm build
```

#### 4. Deploy with Docker Compose

```bash
# Production mode
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d

# Verify all services healthy
docker-compose ps
```

#### 5. PM2 Deployment (Alternative)

```bash
# First time
pm2 start ecosystem.config.cjs

# Subsequent deployments
pm2 restart all
pm2 save
```

---

## Health Checks

### Backend Health

```bash
curl http://localhost:9001/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

### Frontend Health

```bash
curl -I http://localhost:9002
curl -I http://localhost:9003
```

Expected: HTTP 200

### Database Health

```bash
# MySQL
docker exec <mysql-container> mysqladmin ping -h localhost -u root -p

# Redis
docker exec <redis-container> redis-cli ping
```

---

## Common Issues & Fixes

### Backend Won't Start

**Symptom**: PM2 shows error status

**Diagnosis**:
```bash
pm2 logs ulticode-9001
```

**Common causes**:
1. Database connection failure - Check `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`
2. Redis connection failure - Check `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
3. Migration not applied - Run `db-manager info`

**Fix**:
```bash
# Verify infrastructure
docker-compose ps

# Check database connectivity
docker exec -it <mysql-container> mysql -u root -p -e "SELECT 1"

# Apply migrations
cd db-manager && db-manager migrate

# Restart backend
pm2 restart ulticode-9001
```

### Frontend Build Fails

**Symptom**: `pnpm build` errors

**Diagnosis**:
```bash
cd console && pnpm type-check
```

**Common causes**:
1. Type errors - Run `pnpm type-check` to identify issues
2. Missing dependencies - Run `pnpm install`

**Fix**:
```bash
cd console
pnpm install
pnpm type-check  # Fix any type errors
pnpm build
```

### Database Migration Fails

**Symptom**: `db-manager migrate` errors

**Diagnosis**:
```bash
db-manager info
db-manager validate
```

**Common causes**:
1. Schema drift - Run `db-manager repair`
2. Missing migration - Check `db-manager/migrations/` for pending files

**Fix**:
```bash
# Repair metadata
db-manager repair

# Retry migration
db-manager migrate --dry-run
db-manager migrate
```

---

## Monitoring

### PM2 Monitoring

```bash
pm2 status           # List all services
pm2 logs             # Tail all logs
pm2 monit            # Real-time dashboard
```

### Docker Monitoring

```bash
docker-compose logs -f
docker stats
```

### Application Logs

| Service | Log Location |
|---------|--------------|
| Backend | PM2 logs (`pm2 logs ulticode-9001`) |
| MySQL | Docker logs |
| Redis | Docker logs |
| Frontend | Browser console |

---

## Rollback Procedures

### Database Rollback

> **Warning**: Migration rollbacks can cause data loss.

```bash
# Check migration status
db-manager info

# Baseline to previous state (if needed)
db-manager baseline --version=<previous-version>

# Manual rollback - restore from backup
# (See backup procedure below)
```

### Application Rollback

```bash
# Restore previous PM2 state
pm2 resurrect

# Or restart specific service
pm2 restart ulticode-9001
```

### Frontend Rollback

```bash
# Redeploy previous build
cd console
git checkout <previous-commit>
pnpm build
pm2 restart ulticode-9002

# Same for management
```

---

## Backup Procedures

### Database Backup

```bash
# Create backup
docker exec <mysql-container> mysqldump -u root -p ulticode > backup_$(date +%Y%m%d).sql

# Compress backup
gzip backup_$(date +%Y%m%d).sql
```

### Redis Backup

```bash
# Trigger Redis SAVE
docker exec <redis-container> redis-cli SAVE

# Copy dump file
docker cp <redis-container>:/data/dump.rdb ./redis_backup_$(date +%Y%m%d).rdb
```

### Restore from Backup

```bash
# Restore MySQL
docker exec -i <mysql-container> mysql -u root -p ulticode < backup_YYYYMMDD.sql

# Restore Redis
docker cp redis_backup_YYYYMMDD.rdb <redis-container>:/data/dump.rdb
docker exec <redis-container> redis-cli SHUTDOWN NOSAVE
docker start <redis-container>
```

---

## Alerting

### Critical Alert Conditions

| Condition | Severity | Action |
|-----------|----------|--------|
| Backend health check fails | CRITICAL | Restart service, check logs |
| Database unavailable | CRITICAL | Escalate immediately |
| Redis unavailable | HIGH | Check connection, restart if needed |
| High error rate (>5%) | HIGH | Check logs, identify root cause |
| Disk space <20% | HIGH | Clean logs, rotate if needed |

---

## Infrastructure Commands Reference

### Docker Compose

```bash
docker-compose up -d           # Start all services
docker-compose down            # Stop all services
docker-compose restart         # Restart all services
docker-compose logs -f [svc]   # Follow logs
docker-compose ps             # Status
```

### PM2

```bash
pm2 start ecosystem.config.cjs   # Start all services
pm2 stop all                     # Stop all
pm2 restart all                  # Restart all
pm2 restart ulticode-9001        # Restart specific
pm2 logs --nostream             # Show logs
pm2 save                         # Save current state
pm2 resurrect                   # Restore saved state
pm2 list                         # List all processes
```

### Database Migrations

```bash
db-manager migrate              # Apply migrations
db-manager migrate --dry-run    # Preview
db-manager info                 # Show status
db-manager validate              # Validate
db-manager repair               # Fix metadata
```

---

## Contact & Escalation

For issues not covered here, refer to:
- Project documentation in `docs/CODEMAPS/`
- Architecture docs in `docs/CODEMAPS/architecture.md`
- Environment variables in `.env.example`
