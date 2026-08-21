-- V20260820180000__Narrow_Auth_Users_To_Account_Ownership.sql
-- AR20260820-004 contract phase: Auth owns account/authz columns only.
--
-- Before applying this migration to an existing owner schema, run:
--   scripts/dev/owner-user-profile-backfill.sh contract-preflight
-- with the required DEV-LOCAL confirmations and quiesced writers. That
-- preflight verifies the manifest-backed account/profile parity, including
-- soft-deleted source accounts. Fresh owner schemas have no legacy rows.
--
-- Do not edit the earlier expand migrations. App.user_profiles is the
-- canonical owner and sole writer for these profile fields.

-- The repository's shared-schema integration harness scans owner migrations too.
-- Owner preflight binds MIGRATION_DB_NAME to MIGRATION_SCHEMA, so only the
-- `auth` database may contract; the shared `ulticode` chain must not mutate its
-- legacy users table. If Auth is already narrowed, the contract is a no-op.
SET @auth_profile_drop_columns := (
    SELECT GROUP_CONCAT(
        CONCAT('DROP COLUMN `', COLUMN_NAME, '`')
        ORDER BY ORDINAL_POSITION SEPARATOR ', ')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME IN (
          'name', 'avatar', 'bio', 'company', 'github', 'location', 'twitter',
          'website', 'preferred_language'));

SET @auth_profile_contract_ddl := IF(
    DATABASE() <> 'auth' OR @auth_profile_drop_columns IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `users` ', @auth_profile_drop_columns));

PREPARE auth_profile_contract_stmt FROM @auth_profile_contract_ddl;
EXECUTE auth_profile_contract_stmt;
DEALLOCATE PREPARE auth_profile_contract_stmt;
