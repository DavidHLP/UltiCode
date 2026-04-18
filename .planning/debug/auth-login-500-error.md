---
status: resolved
created: 2026-04-16
updated: 2026-04-16
trigger: "POST http://localhost:9001/auth/login 500 (Internal Server Error) x3, then 429 (Too Many Requests). request.ts:426 and request.ts:349"
---

## Symptoms

- **Expected**: Login succeeds with valid credentials
- **Actual**: POST /auth/login returns 500 Internal Server Error, then 429 Too Many Requests after retries
- **Error messages**:
  - `POST http://localhost:9001/auth/login 500 (Internal Server Error)` at request.ts:426
  - `POST http://localhost:9001/auth/login 500 (Internal Server Error)` at request.ts:349 (x3)
  - `POST http://localhost:9001/auth/login 429 (Too Many Requests)` at request.ts:349
- **Timeline**: Current session, not previously observed
- **Reproduction**: Attempt login via frontend console app

## Current Focus

hypothesis: "RESOLVED"
next_action: "none"

## Evidence

- timestamp: 2026-04-16T19:14:48
  source: pm2 logs ulticode-9001
  detail: "SQLSyntaxErrorException: Unknown column 'password_reset_token_hash' in 'field list' — UserMapper.java SELECT includes password_reset_token_hash and password_reset_expires_at columns that do not exist in the users table"
- timestamp: 2026-04-16T19:14:49
  source: pm2 logs ulticode-9001
  detail: "429 rate limit triggered after initial 500 — frontend retries exhausted the rate limit"
- timestamp: 2026-04-16T19:17
  source: flyway_schema_history query
  detail: "Migrations V1-V19 applied; V20 (add password reset columns) missing"
- timestamp: 2026-04-16T19:17
  source: SHOW COLUMNS FROM users
  detail: "Table has 27 columns; password_reset_token_hash and password_reset_expires_at absent"
- timestamp: 2026-04-16T19:18
  source: User.java entity inspection
  detail: "@TableField annotations for password_reset_token_hash (line 158) and password_reset_expires_at (line 164) present in entity"

## Eliminated

- Database connectivity: MySQL and Redis containers running, backend connects successfully
- Security config: Not related — auth filter chain passes through to controller
- Frontend request.ts: Correctly retries and eventually hits 429 due to rate limiter

## Resolution

root_cause: "V20 Flyway migration (add_password_reset_columns.sql) was not applied to the database. The User entity references password_reset_token_hash and password_reset_expires_at columns via @TableField annotations, but these columns were missing from the users table. MyBatis-Plus included them in every SELECT, causing SQLSyntaxErrorException on all user queries including login."
fix: "Applied V20 migration manually (ALTER TABLE users ADD COLUMN password_reset_token_hash, password_reset_expires_at; CREATE INDEX). Registered V20 in flyway_schema_history. Restarted backend."
verification: "POST /auth/login with admin credentials returns code=0, success with csrfToken and user data. Backend health check returns 200."
files_changed:
  - "db (runtime): users table — added password_reset_token_hash VARCHAR(255), password_reset_expires_at DATETIME(3), index idx_users_password_reset_token"
  - "db (runtime): flyway_schema_history — inserted V20 record"
