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

ALTER TABLE `users`
  DROP COLUMN `name`,
  DROP COLUMN `avatar`,
  DROP COLUMN `bio`,
  DROP COLUMN `company`,
  DROP COLUMN `github`,
  DROP COLUMN `location`,
  DROP COLUMN `twitter`,
  DROP COLUMN `website`,
  DROP COLUMN `preferred_language`;
