-- V20260823150200__Add_System_Settings_Updated_At.sql
-- The per-owner Admin bootstrap (V20260729140200) authored system_settings
-- from a pre-refactor snapshot without the `updated_at` audit column that the
-- legacy canonical DDL (V20260602_120000) and SystemSetting entity both carry.
-- Backward compatible: additive column only; existing rows keep defaults.

ALTER TABLE `system_settings`
  ADD COLUMN `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    ON UPDATE CURRENT_TIMESTAMP(3) AFTER `description`;
