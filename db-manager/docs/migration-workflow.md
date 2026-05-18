# Migration Workflow Guide

This guide covers standard and recovery workflows for managing database migrations with db-manager.

## Standard Workflow

The typical migration workflow follows three steps:

```bash
# Step 1: Check current state
db-manager info

# Step 2: Apply pending migrations
db-manager migrate

# Step 3: Verify migration success
db-manager validate
```

### Understanding Migration States

Each migration can be in one of these states:

| State | Description |
|-------|-------------|
| `PENDING` | Migration exists but not yet applied |
| `APPLIED` | Migration successfully applied |
| `FAILED` | Migration encountered an error |
| `MISSING` | Migration recorded in history but not found on disk |

## Recovery Workflows

### Checksum Mismatch Repair

When Flyway detects a checksum mismatch (migration file was modified after applying):

```bash
# 1. Review the current state
db-manager info

# 2. Repair metadata inconsistencies
db-manager repair

# 3. Reapply migrations
db-manager migrate
```

### V104 Duplicate Cleanup

V104 migration may appear multiple times in `schema_history` due to past corruption. To fix:

```bash
# 1. Identify duplicate entries
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode \
  -e "SELECT version, description, type, installed_on FROM schema_history WHERE version = '104' ORDER BY installed_on;"

# 2. Remove duplicate rows (keep the oldest/valid entry)
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode \
  -e "DELETE FROM schema_history WHERE version = '104' AND installed_on > 'YYYY-MM-DD HH:MM:SS';"

# 3. Verify the fix
db-manager info
```

Replace `YYYY-MM-DD HH:MM:SS` with the timestamp of the valid V104 entry.

### Out-of-Order Migration Fix

The CLI does not support out-of-order migrations. For manual fixes:

```bash
# 1. Connect directly to MySQL
docker exec -it ulticode-mysql mysql -u ulticode -pulticode ulticode

# 2. Manually insert the missing migration record
INSERT INTO schema_history (version, description, type, installed_on, success)
VALUES ('105', 'description_of_migration', 'SQL', NOW(), 1);

# 3. Exit and verify
EXIT;
db-manager info
```

### Full Reset (Clean Then Migrate)

When the database is in a severely inconsistent state:

```bash
# 1. Backup the database first (see best-practices.md)
# 2. Drop all objects
db-manager clean --force

# 3. Reapply all migrations
db-manager migrate

# 4. Verify the result
db-manager validate
```

**Warning**: `clean --force` destroys all database objects. Always backup before proceeding.

## Troubleshooting

### Connection Errors

```
Unable to obtain connection from database
```

Solutions:
- Verify MySQL container is running: `docker ps | grep mysql`
- Check port: `DB_PORT=23306` (not the default 3306)
- Test connectivity: `docker exec ulticode-mysql mysql -u ulticode -pulticode -e "SELECT 1"`

### Checksum Mismatches

```
Migration checksum mismatch
```

Causes:
- Migration file was edited after applying
- Different line endings (Windows vs Unix)

Solutions:
1. If file was legitimately changed, run `repair` then `migrate`
2. If unintentional, restore the original file from version control

### Encoding Issues

Chinese text appears as garbled characters like `å¹¶å'ç¼–ç¨‹å…¥é—´`.

Cause: JDBC URL missing encoding parameters.

Fix: Ensure `config.py` contains:
```python
jdbc_url = f"jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8"
```

### Pending Migrations Not Applied

Migrations show as `PENDING` but `migrate` does not apply them.

Solutions:
- Check if migration version already exists in `schema_history` with a different version number
- Use `repair` to recalculate checksums
- Manually insert missing records via `docker exec`

## Version Management

### Checking Pending Migrations

Before applying any migration, always check what will be applied:

```bash
db-manager info

# Or dry-run to see what would happen
db-manager migrate --dry-run
```

The output shows:
- Version number
- Description
- Installed on (timestamp)
- State (PENDING, APPLIED, etc.)

### Understanding Migration Output

```
Database: mysql @ localhost:23306 (ulticode)
+---------------+---------------------------------+-----------+---------+
| Version       | Description                     | State     | Installed On |
+---------------+---------------------------------+-----------+---------+
| 1             | core_schema                     | APPLIED   | 2024-01-15 10:30:00 |
| 2             | problem_schema                  | APPLIED   | 2024-01-15 10:30:01 |
| 3             | contest_schema                  | PENDING   |                  |
+---------------+---------------------------------+-----------+---------+
```

## Common Error Solutions

| Error | Cause | Solution |
|-------|-------|----------|
| `Access denied` | Wrong credentials | Check DB_USER, DB_PASSWORD in .env |
| `Unknown database` | Database not created | Run `docker compose up -d mysql` first |
| `Connection refused` | MySQL not running | `pm2 start docker-up` or `docker start ulticode-mysql` |
| `Lock wait timeout` | Concurrent migration | Wait and retry, or check for stuck Flyway processes |
| `Duplicate entry` | Prior partial migration | Run `repair`, then `migrate` |

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | General error |
| 2 | Migration error (apply failed) |
| 3 | Validation error |

## Related

- [Best Practices](./best-practices.md) - Backup, rollback, and encoding procedures
