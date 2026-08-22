-- Add additive `authz_version` column to `users` and session-family
-- columns to `refresh_tokens`. P2-AUTH-003 (Phase 2 / RBAC data and
-- change propagation).
-- ------------------------------------------------------------
-- Background: PROJECT_DOCUMENTATION.md §7.5 (Phase 2 RBAC data
-- and change propagation), §8.2 (refresh rotation CAS), §8.3
-- (migration-time consistency mechanism), and the Phase 2 Database
-- Changes paragraph ("new provider identity / session family / authz
-- version additive tables and columns").
--
-- This is the EXPAND phase. No row is rewritten. No backfill is
-- performed in SQL. The migration only adds columns and indexes.
-- Per guide §15 ("expand → backfill → verify → cut over → observe →
-- contract") the backfill and cut-over are owned by P2-AUTH-001-G
-- (backend-auth standalone integration test suite) once the writer
-- code is in place; this migration is intentionally minimal so the
-- safe path back out is also minimal.
--
-- Why these four columns:
--
-- 1. `users.authz_version` — long counter bumped every time the
--    account's effective role / direct permissions change. App and
--    Admin cache `(sub, authzVersion)` and invalidate on version
--    mismatch (guide §7.5 step 4). Default 0 so existing rows do
--    not need a backfill scan; consumer code treats 0 as "force a
--    fresh snapshot on first read".
--
-- 2. `refresh_tokens.family_id` — opaque id assigned to a chain of
--    refresh tokens that all originate from the same login. A
--    successful login creates one family; every rotation inserts a
--    sibling row in the same family; "log out everywhere" revokes
--    every row whose family_id matches. Reuse detection: when a
--    token arrives whose row is already marked revoked, Auth looks
--    up the family and revokes the whole family (token-theft
--    indicator). Nullable in the EXPAND phase because legacy rows
--    have no known family and assigning empty-string `''` would
--    silently cluster every legacy token into one bogus family
--    (the use case that previously poisoned reuse detection).
--
-- 3. `refresh_tokens.replaced_by_token_id` — id of the sibling row
--    that replaced this one. Forms a singly-linked chain inside a
--    family, used to confirm "this old token was rotated, not
--    stolen". Nullable in the EXPAND phase; backfilled on rotation.
--
-- 4. `refresh_tokens.previous_token_id` — id of the sibling row
--    that preceded the current one. Inverse of replaced_by;
--    nullable in the EXPAND phase; backfilled on rotation.
--
-- Why NOT the existing `token` / `revoked_at` orphan columns:
--
-- The DDL in V20260602_120000 creates `refresh_tokens.token
-- varchar(255) NOT NULL` and `refresh_tokens.revoked_at datetime(3)
-- DEFAULT NULL`. Neither has a corresponding field on the
-- RefreshToken entity (which uses `token_hash` exclusively) and a
-- `grep` over backend-legacy/src confirms there is no active writer
-- for the `token` column. Both are orphan columns that the project
-- plans to drop in Phase 7 (P7-DB-001). This migration does NOT
-- touch them — additive-only contract per guide §15.
--
-- Phase 0 writes:
--   None. Phase 2 (P2-AUTH-003) is the first time these columns
--   exist. Application code (P2-AUTH-001-G) will start writing.
--
-- Phase 0 / future evidence required before any contract:
--   * Backfill (separate task, owned by P2-AUTH-001-G): after the
--     writer lands, Auth issues a per-user login that backfills
--     family_id for active sessions; revocation of family chains
--     does not happen until after backfill.
--   * Checksum: `SELECT COUNT(*) FROM users;` and `SELECT COUNT(*)
--     FROM refresh_tokens;` are expected to be identical before
--     and after the migration (no rows added or removed). Recorded
--     in P2-AUTH-003 evidence.
--   * Orphan: every pre-existing `refresh_tokens` row has
--     `family_id IS NULL` and `replaced_by_token_id IS NULL` and
--     `previous_token_id IS NULL` after this migration. This is
--     the documented "unknown family" state; the writer at
--     P2-AUTH-001-G is responsible for filling them in. The
--     expected count is `SELECT COUNT(*) FROM refresh_tokens WHERE
--     family_id IS NULL` equals the pre-migration row count.
--   * Shadow read: no application code reads the new columns yet,
--     so the migration is invisible to the running system. The
--     first writer is expected in P2-AUTH-001-G; the first reader
--     in Phase 4 (P4-RPC-001 / P4-CUTOVER-001).
--
-- Rollback (non-destructive, IF EXISTS-guarded):
--
--   ALTER TABLE `users`            DROP COLUMN IF EXISTS `authz_version`;
--   ALTER TABLE `users`            DROP INDEX  IF EXISTS `idx_users_authz_version`;
--   ALTER TABLE `refresh_tokens`  DROP COLUMN IF EXISTS `previous_token_id`;
--   ALTER TABLE `refresh_tokens`  DROP COLUMN IF EXISTS `replaced_by_token_id`;
--   ALTER TABLE `refresh_tokens`  DROP COLUMN IF EXISTS `family_id`;
--   ALTER TABLE `refresh_tokens`  DROP INDEX  IF EXISTS `idx_refresh_tokens_family`;
--
--   Each `DROP COLUMN` and `DROP INDEX` is independently guarded by
--   IF EXISTS so a partial state (column added, index not yet
--   added, or vice versa) does not fail the rollback. Order
--   matters: family_id is dropped last because the composite index
--   `idx_refresh_tokens_family (family_id, user_id)` is auto-dropped
--   by the storage engine only when the underlying column is
--   dropped, so a separate `DROP INDEX` after the column drop is
--   safe; we run it before the column drop to make the rollback
--   order independent of MySQL version. This rollback is the
--   inverse of the additive ALTER list and is lossless because no
--   row data was written in this migration (the application writer
--   has not landed yet).
--
-- Expand-phase contract: until P2-AUTH-001-G is done, no row
-- carries a non-NULL family_id / replaced_by_token_id /
-- previous_token_id. Consumers that read these columns must treat
-- NULL as "unknown — fall back to the legacy rotation path".

ALTER TABLE `users`
  ADD COLUMN `authz_version` BIGINT NOT NULL DEFAULT 0
    COMMENT 'Bumped on every role/permission change. App/Admin cache (sub, authzVersion) and invalidate on mismatch. Default 0 = "unknown version, force fresh snapshot".';

ALTER TABLE `users`
  ADD INDEX `idx_users_authz_version` (`authz_version`);

ALTER TABLE `refresh_tokens`
  ADD COLUMN `family_id` VARCHAR(40) DEFAULT NULL
    COMMENT 'Opaque id grouping all rotation-chain siblings from one login. NULL in the EXPAND phase (legacy rows have no known family); backfilled by P2-AUTH-001-G.';

ALTER TABLE `refresh_tokens`
  ADD COLUMN `replaced_by_token_id` VARCHAR(40) DEFAULT NULL
    COMMENT 'Id of the sibling row that replaced this one (rotation chain forward link). NULL in the EXPAND phase.';

ALTER TABLE `refresh_tokens`
  ADD COLUMN `previous_token_id` VARCHAR(40) DEFAULT NULL
    COMMENT 'Id of the sibling row that preceded this one (rotation chain backward link). NULL in the EXPAND phase.';

ALTER TABLE `refresh_tokens`
  ADD INDEX `idx_refresh_tokens_family` (`family_id`, `user_id`);
