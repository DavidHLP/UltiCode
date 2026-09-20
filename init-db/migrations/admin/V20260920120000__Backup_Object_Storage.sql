-- Store durable admin backup objects in the private object store.
--
-- The Admin owner owns this table.  Keep the CREATE TABLE branch compatible
-- with a fresh Admin schema and repair only the two additive columns when an
-- older Admin backups table already exists.  MySQL 9.1 does not support
-- ADD COLUMN IF NOT EXISTS, so the upgrade branch uses INFORMATION_SCHEMA and
-- prepared DDL instead.

CREATE TABLE IF NOT EXISTS `backups` (
  `id` varchar(40) NOT NULL,
  `filename` varchar(255) NOT NULL,
  `object_key` varchar(512) NULL,
  `size` bigint NOT NULL DEFAULT '0',
  `checksum` char(64) NULL,
  `type` enum('FULL','INCREMENTAL') NOT NULL,
  `status` enum('PENDING','IN_PROGRESS','COMPLETED','FAILED') NOT NULL,
  `created_by` varchar(40) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `completed_at` datetime(3) DEFAULT NULL,
  `metadata` JSON DEFAULT NULL,
  `error` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_created_at` (`status`, `created_at`),
  KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET @admin_backups_object_key_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'backups'
       AND COLUMN_NAME = 'object_key'
);
SET @admin_backups_object_key_ddl := IF(
    @admin_backups_object_key_exists = 0,
    'ALTER TABLE `backups` ADD COLUMN `object_key` varchar(512) NULL AFTER `filename`',
    'SELECT 1'
);
PREPARE admin_backups_object_key_stmt FROM @admin_backups_object_key_ddl;
EXECUTE admin_backups_object_key_stmt;
DEALLOCATE PREPARE admin_backups_object_key_stmt;

SET @admin_backups_checksum_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'backups'
       AND COLUMN_NAME = 'checksum'
);
SET @admin_backups_checksum_ddl := IF(
    @admin_backups_checksum_exists = 0,
    'ALTER TABLE `backups` ADD COLUMN `checksum` char(64) NULL AFTER `size`',
    'SELECT 1'
);
PREPARE admin_backups_checksum_stmt FROM @admin_backups_checksum_ddl;
EXECUTE admin_backups_checksum_stmt;
DEALLOCATE PREPARE admin_backups_checksum_stmt;
