-- NOTIFY-006: align the physical owner tables with the legacy source shape.
--
-- The root Flyway location also discovers this directory. Skip the ALTERs
-- during the compatibility-schema pass when the target tables do not exist;
-- the dedicated Notification Flyway pass applies them after V20260815100000.

SET @notification_notifications_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = 'notification' AND table_name = 'notifications'
);
SET @notification_notifications_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = 'notification'
    AND table_name = 'notifications'
    AND index_name = 'idx_notifications_user_deleted'
);
SET @notification_sql = IF(
  @notification_notifications_exists = 0,
  'SELECT 1',
  IF(
    @notification_notifications_index_exists > 0,
    'ALTER TABLE `notification`.`notifications` MODIFY `updated_at` DATETIME(3) NOT NULL, MODIFY `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0',
    'ALTER TABLE `notification`.`notifications` MODIFY `updated_at` DATETIME(3) NOT NULL, MODIFY `is_deleted` TINYINT UNSIGNED NOT NULL DEFAULT 0, ADD KEY `idx_notifications_user_deleted` (`user_id`, `is_deleted`, `created_at`)'
  )
);
PREPARE notification_stmt FROM @notification_sql;
EXECUTE notification_stmt;
DEALLOCATE PREPARE notification_stmt;

SET @notification_preferences_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = 'notification' AND table_name = 'notification_preferences'
);
SET @notification_sql = IF(
  @notification_preferences_exists = 1,
  'ALTER TABLE `notification`.`notification_preferences` MODIFY `updated_at` DATETIME(3) NOT NULL',
  'SELECT 1'
);
PREPARE notification_stmt FROM @notification_sql;
EXECUTE notification_stmt;
DEALLOCATE PREPARE notification_stmt;

SET @notification_ledger_exists = (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = 'notification' AND table_name = 'notification_delivery_ledger'
);
SET @notification_sql = IF(
  @notification_ledger_exists = 1,
  'ALTER TABLE `notification`.`notification_delivery_ledger` MODIFY `intent_id` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, MODIFY `channel_id` VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, MODIFY `user_id` VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, MODIFY `intent_type` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, MODIFY `delivery_state` VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL, MODIFY `failure_reason` VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL, MODIFY `claim_owner` VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL',
  'SELECT 1'
);
PREPARE notification_stmt FROM @notification_sql;
EXECUTE notification_stmt;
DEALLOCATE PREPARE notification_stmt;
