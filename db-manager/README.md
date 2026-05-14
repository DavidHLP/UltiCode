# UltiCode Database Manager

Flyway-based database migration management tool for UltiCode.

## Features

- **Migration Management**: Versioned migrations using Flyway 12.x
- **Docker Support**: Automatically uses Docker when Flyway CLI is not installed locally
- **CLI Interface**: Easy-to-use command-line interface with Rich formatting
- **Database Operations**: migrate, info, repair, baseline, clean, validate

## Requirements

One of the following:
- **Flyway Docker image**: `docker pull flyway/flyway` (auto-detected)
- **Flyway CLI**: Installed locally from https://flyway.net/

## Installation

```bash
# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -e .
```

## Quick Reference

| Command | Description |
|---------|-------------|
| `db-manager migrate` | Apply pending migrations |
| `db-manager migrate --dry-run` | Preview migrations without applying |
| `db-manager info` | Show migration status |
| `db-manager repair` | Fix metadata inconsistencies |
| `db-manager validate` | Validate migration state |
| `db-manager baseline` | Create baseline for existing database |
| `db-manager clean --force` | Drop all database objects (requires backup first) |

**Exit Codes**: 0=success, 1=general error, 2=migration error, 3=validation error

## Usage

### migrate

Apply all pending migrations to the database.

```bash
# Apply pending migrations
db-manager migrate

# Preview what would be applied (no changes made)
db-manager migrate --dry-run

# After a failed migration, repair and remigrate
db-manager repair
db-manager migrate
```

### info

Display the current migration state without making changes.

```bash
# Show all migrations and their states
db-manager info

# Example output:
# +---------------+---------------------------------+-----------+---------+
# | Version       | Description                     | State     | Installed On |
# +---------------+---------------------------------+-----------+---------+
# | 1             | core_schema                     | APPLIED   | 2024-01-15 10:30:00 |
# | 2             | problem_schema                  | APPLIED   | 2024-01-15 10:30:01 |
# | 3             | contest_schema                  | PENDING   |                  |
# +---------------+---------------------------------+-----------+---------+
```

### repair

Repair metadata table inconsistencies, including checksum mismatches and duplicate entries.

```bash
# Fix metadata inconsistencies
db-manager repair

# Use after:
# - Migration file was modified after being applied
# - Duplicate entries appear in schema_history
# - Checksum mismatch errors occur
```

### validate

Verify that the applied migrations match the files on disk.

```bash
# Validate migration state
db-manager validate

# Returns exit code 3 if validation fails
```

### baseline

Create a baseline for an existing database, marking all current objects as applied.

```bash
# Mark current state as baseline
db-manager baseline

# Use when:
# - Setting up Flyway on an existing database
# - Skipping historical migrations
```

### clean

Drop all database objects. **This is destructive and irreversible.**

```bash
# DANGER: Drop all tables, views, and objects
db-manager clean --force

# Always backup before running clean
docker exec ulticode-mysql mysqldump -u ulticode -pulticode ulticode > backup.sql

# After clean, reapply all migrations
db-manager migrate
```

## Exit Codes

| Code | Meaning | Example |
|------|---------|---------|
| 0 | Success | Migration completed without errors |
| 1 | General error | Connection failed, file not found |
| 2 | Migration error | Apply failed, checksum mismatch |
| 3 | Validation error | Applied migrations do not match files |

## Configuration

The tool reads database configuration from the `.env` file in the project root:

```
DB_HOST=localhost
DB_PORT=23306
DB_USER=ulticode
DB_PASSWORD=ulticode
DB_NAME=ulticode
```

Or via `DATABASE_URL`:
```
DATABASE_URL=mysql://user:pass@host:port/ulticode
```

## Migration Scripts

Migrations are stored in the `migrations/` directory using Flyway naming convention:

```
migrations/
├── V1__core_schema.sql
├── V2__problem_schema.sql
├── V3__contest_schema.sql
├── V4__forum_schema.sql
├── V5__subscription_schema.sql
├── V6__moderation_schema.sql
├── V7__recommendation_schema.sql
├── V8__collection_schema.sql
├── V9__solution_schema.sql
└── V10__edge_schema.sql
```

## Project Structure

```
db-manager/
├── migrations/                  # Flyway migration scripts
├── pyproject.toml
├── src/db_manager/
│   ├── __init__.py
│   ├── cli.py                   # CLI interface
│   ├── config.py                # Configuration management
│   ├── flyway_adapter.py        # Flyway wrapper (CLI + Docker)
│   └── operations/
│       ├── migrate.py           # Migration operation
│       ├── info.py              # Info operation
│       ├── repair.py            # Repair operation
│       ├── baseline.py          # Baseline operation
│       ├── clean.py             # Clean operation
│       └── validate.py          # Validate operation
└── tests/
```

## License

Internal use only.
