# DB-Manager AGENTS.md

## OVERVIEW
Python CLI wrapper around Flyway Java library for managing MySQL migrations. Uses `.venv/bin/python` — system Python will not work.

## STRUCTURE
```
db-manager/
├── migrations/                  # Flyway SQL migrations (V{version}__{description}.sql)
├── src/db_manager/
│   ├── cli.py                  # Entry point: migrate, info, repair, baseline, clean, validate
│   ├── config.py               # DB config from .env; get_jdbc_url() builds JDBC URL
│   ├── flyway_adapter.py       # Wrapper invoking Flyway Docker image
│   └── operations/             # One file per command
└── pyproject.toml              # Package: ulticode-db-manager v0.3.0
```

## WHERE TO LOOK
- **Config loading**: `src/db_manager/config.py` — `load_config()` searches upward for `.env`
- **JDBC URL encoding**: `get_jdbc_url()` — includes `useUnicode=true&characterEncoding=UTF-8` (critical fix for Chinese text)
- **Migrations**: `migrations/` — 100+ versions (V1–V105); each wraps in `SET FOREIGN_KEY_CHECKS=0/1`
- **Commands**: `src/db_manager/operations/` — `migrate.py`, `info.py`, `repair.py`, `clean.py`, `validate.py`, `baseline.py`

## CONVENTIONS
- Migration naming: `V{version}__{description}.sql` (double underscore)
- Each migration file wraps content in `SET FOREIGN_KEY_CHECKS=0` ... `SET FOREIGN_KEY_CHECKS=1`
- After modifying migrations: run `clean --force` then `migrate` (checksum tracking)
- Commands: `db-manager migrate`, `db-manager info`, `db-manager repair`, `db-manager validate`, `db-manager clean --force`, `db-manager baseline`

## ANTI-PATTERNS
- **System Python**: Always use `db-manager/.venv/bin/python` — system Python lacks dependencies
- **Out-of-order migrations**: NOT supported via CLI. Use `docker exec ulticode-mysql mysql ...` for manual fixes
- **JDBC URL encoding**: Missing `useUnicode=true` causes Chinese text corruption (e.g., `å¹¶å'ç¼–ç¨‹å…¥é—¨` instead of `并发编程入门`)
- **No Ruff/Black**: This project has no linter/formatter configured
