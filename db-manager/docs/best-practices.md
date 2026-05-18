# Database Management Best Practices

Essential guidelines for safe database migration operations.

## Backup Procedures

### Before Any Migration

**Always backup before running `migrate`, `clean`, or `baseline` commands.**

```bash
# Create a SQL dump of the entire database
docker exec ulticode-mysql mysqldump \
  -u ulticode \
  -pulticode \
  --single-transaction \
  --quick \
  --lock-tables=false \
  ulticode > backup_$(date +%Y%m%d_%H%M%S).sql

# Verify the backup file was created
ls -la backup_*.sql
```

### Backup Verification

```bash
# Verify backup is valid by checking its size
wc -l backup_*.sql

# A valid backup should have:
# - CREATE DATABASE / USE statements
# - Table definitions (CREATE TABLE)
# - Data inserts (INSERT INTO)
```

### Retention

- Keep at least 3 daily backups in rotation
- Store backups on a separate volume from the database
- Test restore procedure periodically

## Encoding Fix Procedure

Chinese text corruption (e.g., `å¹¶å'ç¼–ç¨‹å…¥é—´` instead of `并发编程入门`) occurs when the JDBC URL lacks encoding parameters.

### Root Cause

The Flyway JDBC connection was missing `useUnicode=true&characterEncoding=UTF-8` in the connection URL.

### Verification

Check `src/db_manager/config.py` contains the correct JDBC URL:

```python
jdbc_url = f"jdbc:mysql://{host}:{port}/{database}?useUnicode=true&characterEncoding=UTF-8"
```

### If Corruption Already Exists

**For existing corrupted data**:
- Data in `problem_lists` was partially fixed in V26/V27
- Remaining tables (`problem_tags`, `problems`, `forum_*`, `solutions`, `users`) still have corrupted data
- Manual table-by-table fixes using correct values from migration files are required

**For new inserts**:
- Backend and db-manager now have proper encoding config
- New Chinese text inserts correctly

## Rollback Procedures

Flyway does not support automatic rollback of applied migrations. Follow these procedures for recovery.

### Scenario 1: Migration Fails During Apply

```bash
# 1. Check the current state
db-manager info

# 2. Flyway automatically marks failed migrations
#    Repair metadata to clear the failure state
db-manager repair

# 3. Fix the migration file if it has an error
# 4. Reapply
db-manager migrate
```

### Scenario 2: Migration Applied But Need to Revert

**Option A: Manual Fix with New Migration**

```bash
# 1. Create a new migration VXXX__fix_previous.sql
# 2. Apply it
db-manager migrate
```

**Option B: Clean and Remigrate**

```bash
# 1. Backup first
docker exec ulticode-mysql mysqldump -u ulticode -pulticode ulticode > backup.sql

# 2. Clean the database
db-manager clean --force

# 3. Restore from backup
docker exec -i ulticode-mysql mysql -u ulticode -pulticode ulticode < backup.sql

# 4. If you need the corrupted migrations too, remigrate
db-manager migrate
```

### Scenario 3: Selective Revert

```bash
# 1. Connect to MySQL directly
docker exec -it ulticode-mysql mysql -u ulticode -pulticode ulticode

# 2. Delete the specific migration record
DELETE FROM schema_history WHERE version = 'XXX';

# 3. Manually revert the changes (restore table structures/data)
#    This requires careful manual work

# 4. Exit and verify
EXIT;
db-manager info
```

## Migration Naming Conventions

Follow Flyway naming rules strictly:

```
V{version}__{description}.sql
```

| Element | Rule | Example |
|---------|------|---------|
| Prefix | Always `V` | `V` |
| Version | Numeric, unique | `1`, `2`, `105` |
| Separator | Double underscore `__` | `__` |
| Description | Lowercase with underscores | `add_user_index` |

**Correct**: `V1__core_schema.sql`, `V26__fix_problem_lists.sql`

**Incorrect**: `V1_core_schema.sql` (single underscore), `V01__Add Users.sql` (spaces)

## Checksum Management

Checksums detect when migration files change after being applied.

### When Checksums Matter

| Situation | Action |
|-----------|--------|
| File changed unintentionally | Restore original file, run `repair` |
| File changed intentionally | Run `repair`, then `migrate` to reapply |
| Checksum mismatch after repair | Check for duplicate V104 entries |

### Repair vs Clean --force

| Command | Use When |
|---------|----------|
| `repair` | Metadata table has inconsistencies, checksum mismatches, duplicate entries |
| `clean --force` | Need to drop all objects and start fresh (requires backup first) |

### V104 Duplicate Entry Issue

V104 may appear multiple times in `schema_history`. To fix:

```bash
# 1. Check for duplicates
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode \
  -e "SELECT version, description, installed_on FROM schema_history WHERE version = '104';"

# 2. Delete duplicates (keep one)
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode \
  -e "DELETE FROM schema_history WHERE version = '104' AND installed_on != 'YYYY-MM-DD HH:MM:SS';"

# 3. Repair metadata
db-manager repair

# 4. Verify
db-manager info
```

## Environment Configuration

### Configuration Precedence

db-manager reads configuration in this order (later sources override earlier):

1. Default values (localhost, 23306, ulticode)
2. `.env` file in project root
3. Environment variables (DB_HOST, DB_PORT, etc.)
4. `DATABASE_URL` environment variable

### Recommended Setup

Create a `.env` file in the project root:

```
DB_HOST=localhost
DB_PORT=23306
DB_USER=ulticode
DB_PASSWORD=your_secure_password
DB_NAME=ulticode
```

### Security Notes

- Never commit `.env` to version control
- Use strong passwords for production databases
- Pass sensitive values via environment variables, not CLI arguments

### Docker Environment

When running via Docker wrapper, ensure environment is properly set:

```bash
# Check current configuration
db-manager info

# If running in a fresh container, set env vars
docker exec -e DB_PASSWORD=secret ulticode-db-manager migrate
```

## Related

- [Migration Workflow](./migration-workflow.md) - Detailed workflow procedures
