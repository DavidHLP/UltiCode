-- V20260816220000__Add_Search_Version_Columns.sql
-- SEARCH-003 slice-2 (DEC-016): monotonic per-document version columns for the
-- search backfill watermark. The Search worker version ledger compares event
-- versions (epoch millis); backfill snapshots derive their version from these
-- row timestamps so a snapshot can never overwrite a newer live write.
--
-- All three changes are additive / backward compatible:
--   * forum_posts  — no updated column existed (posts are editable); add one.
--   * solutions    — updated_at exists but is not auto-maintained; add ON UPDATE.
--   * users        — no updated column existed (identity + ban writes); add one.
--
-- user_profiles.updated_at already exists with ON UPDATE (App-owned profile
-- writes); the user document version is GREATEST(users.updated_at,
-- user_profiles.updated_at, users.deleted_at, users.joined_at).

ALTER TABLE `forum_posts`
  ADD COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE `solutions`
  MODIFY COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3);

ALTER TABLE `users`
  ADD COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3);
