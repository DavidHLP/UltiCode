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

## Usage

### Database Migration Commands

```bash
# Apply pending migrations
db-manager migrate

# Validate without applying (dry run)
db-manager migrate --dry-run

# Show migration status
db-manager info

# Repair metadata table
db-manager repair

# Create baseline for existing database
db-manager baseline

# Drop all database objects (DANGEROUS!)
db-manager clean --force

# Validate migration state
db-manager validate
```

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
