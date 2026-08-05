-- V20260806120000__Drop_Profile_Columns_From_Users.sql
-- P5-USERPROFILE-001 contract phase: drop profile columns from users table.
-- Profile data is now canonical in user_profiles (App-owned).
-- The dual-write window is closed; UserReadMapper reads profile columns
-- from user_profiles via LEFT JOIN.

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
