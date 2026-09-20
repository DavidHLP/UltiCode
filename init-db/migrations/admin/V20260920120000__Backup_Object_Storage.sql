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
-- One-time owner cutover: preserve legacy metadata before Admin becomes the
-- sole owner of `backups`.  The legacy bytes remain under BACKUP_DIR; after
-- this copy, scripts/dev/migrate-object-storage.sh uploads them and updates
-- the corresponding Admin row's object_key/checksum.
--
-- Keep the source table and the optional object columns out of the parsed
-- statement when they are absent.  The target name stays unqualified so it
-- follows Flyway's current Admin schema; only the fixed `ulticode` source
-- schema is qualified, avoiding any caller-controlled identifier.
--
SET @legacy_backups_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.TABLES
     WHERE TABLE_SCHEMA = 'ulticode'
       AND TABLE_NAME = 'backups'
       AND TABLE_TYPE = 'BASE TABLE'
);
SET @legacy_backups_object_key_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = 'ulticode'
       AND TABLE_NAME = 'backups'
       AND COLUMN_NAME = 'object_key'
);
SET @legacy_backups_checksum_exists := (
    SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = 'ulticode'
       AND TABLE_NAME = 'backups'
       AND COLUMN_NAME = 'checksum'
);
SET @legacy_backups_copy_sql := IF(
    @legacy_backups_exists = 0,
    'SELECT 1',
    CONCAT(
        'INSERT INTO `backups` ',
        '(`id`, `filename`, `object_key`, `size`, `checksum`, `type`, ',
        '`status`, `created_by`, `created_at`, `completed_at`, `metadata`, `error`) ',
        'SELECT s.`id`, s.`filename`, ',
        IF(@legacy_backups_object_key_exists = 1, 's.`object_key`', 'NULL'),
        ', s.`size`, ',
        IF(@legacy_backups_checksum_exists = 1, 's.`checksum`', 'NULL'),
        ', s.`type`, s.`status`, s.`created_by`, s.`created_at`, ',
        's.`completed_at`, s.`metadata`, s.`error` ',
        'FROM `ulticode`.`backups` AS s ',
        'LEFT JOIN `backups` AS d ON d.`id` = s.`id` ',
        'WHERE d.`id` IS NULL'
    )
);
PREPARE legacy_backups_copy_stmt FROM @legacy_backups_copy_sql;
EXECUTE legacy_backups_copy_stmt;
DEALLOCATE PREPARE legacy_backups_copy_stmt;
