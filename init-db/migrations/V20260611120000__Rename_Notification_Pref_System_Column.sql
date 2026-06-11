-- V20260611120000__Rename_Notification_Pref_System_Column.sql
-- Rename `system` column to `system_enabled` to avoid MySQL 9.x reserved keyword
-- (caused BadSqlGrammarException on INSERT/UPDATE in NotificationPreferenceMapper).
-- See docs/notification-api-test-questions.md Q23 / P0-1.

ALTER TABLE notification_preferences
    CHANGE COLUMN `system` system_enabled TINYINT(1) NOT NULL DEFAULT 1;
