-- Copy legacy backup metadata after every owner migration has completed.
--
-- The Admin owner migration runs with an account scoped to admin.* and cannot
-- reliably see ulticode.backups through INFORMATION_SCHEMA.  This chain runs
-- with the shared privileged migration identity, so the cutover is visible and
-- remains idempotent when a deployment is upgraded more than once.

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
        'INSERT INTO `admin`.`backups` ',
        '(`id`, `filename`, `object_key`, `size`, `checksum`, `type`, ',
        '`status`, `created_by`, `created_at`, `completed_at`, `metadata`, `error`) ',
        'SELECT s.`id`, s.`filename`, ',
        IF(@legacy_backups_object_key_exists = 1, 's.`object_key`', 'NULL'),
        ', s.`size`, ',
        IF(@legacy_backups_checksum_exists = 1, 's.`checksum`', 'NULL'),
        ', s.`type`, s.`status`, s.`created_by`, s.`created_at`, ',
        's.`completed_at`, s.`metadata`, s.`error` ',
        'FROM `ulticode`.`backups` AS s ',
        'LEFT JOIN `admin`.`backups` AS d ON d.`id` = s.`id` ',
        'WHERE d.`id` IS NULL'
    )
);
PREPARE legacy_backups_copy_stmt FROM @legacy_backups_copy_sql;
EXECUTE legacy_backups_copy_stmt;
DEALLOCATE PREPARE legacy_backups_copy_stmt;
