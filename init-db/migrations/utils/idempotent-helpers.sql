-- ============================================================================
-- Idempotent Migration Utilities
--
-- This file provides reusable SQL helper functions for writing idempotent
-- migrations. Include this file at the beginning of your migration scripts.
--
-- Usage:
--   SOURCE migrations/utils/idempotent-helpers.sql;
--   -- Then use the helper procedures in your migration
-- ============================================================================

SET NAMES utf8mb4;

-- ============================================================================
-- Ensure schema_migrations table exists
-- ============================================================================
CREATE TABLE IF NOT EXISTS `schema_migrations` (
    `version` VARCHAR(255) NOT NULL COMMENT 'Migration version identifier',
    `name` VARCHAR(255) NOT NULL COMMENT 'Migration name',
    `checksum` VARCHAR(64) NULL COMMENT 'SHA-256 hash of migration SQL',
    `execution_time_ms` INT NULL COMMENT 'Execution time in milliseconds',
    `applied_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `applied_by` VARCHAR(100) NULL COMMENT 'User who applied the migration',
    PRIMARY KEY (`version`),
    INDEX `schema_migrations_applied_at_idx` (`applied_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- Helper: Check if migration is already applied
-- Returns: 1 if applied, 0 if not
-- ============================================================================
DROP FUNCTION IF EXISTS is_migration_applied;

DELIMITER //

CREATE FUNCTION is_migration_applied(p_version VARCHAR(255))
RETURNS BOOLEAN
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM schema_migrations
    WHERE version = p_version;

    RETURN v_count > 0;
END //

DELIMITER ;

-- ============================================================================
-- Helper: Check if table exists
-- Returns: 1 if exists, 0 if not
-- ============================================================================
DROP FUNCTION IF EXISTS table_exists;

DELIMITER //

CREATE FUNCTION table_exists(p_table_name VARCHAR(255))
RETURNS BOOLEAN
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name;

    RETURN v_count > 0;
END //

DELIMITER ;

-- ============================================================================
-- Helper: Check if column exists in table
-- Returns: 1 if exists, 0 if not
-- ============================================================================
DROP FUNCTION IF EXISTS column_exists;

DELIMITER //

CREATE FUNCTION column_exists(
    p_table_name VARCHAR(255),
    p_column_name VARCHAR(255)
)
RETURNS BOOLEAN
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND COLUMN_NAME = p_column_name;

    RETURN v_count > 0;
END //

DELIMITER ;

-- ============================================================================
-- Helper: Check if index exists
-- Returns: 1 if exists, 0 if not
-- ============================================================================
DROP FUNCTION IF EXISTS index_exists;

DELIMITER //

CREATE FUNCTION index_exists(
    p_table_name VARCHAR(255),
    p_index_name VARCHAR(255)
)
RETURNS BOOLEAN
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND INDEX_NAME = p_index_name;

    RETURN v_count > 0;
END //

DELIMITER ;

-- ============================================================================
-- Helper: Check if foreign key exists
-- Returns: 1 if exists, 0 if not
-- ============================================================================
DROP FUNCTION IF EXISTS foreign_key_exists;

DELIMITER //

CREATE FUNCTION foreign_key_exists(
    p_table_name VARCHAR(255),
    p_constraint_name VARCHAR(255)
)
RETURNS BOOLEAN
DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_count INT DEFAULT 0;

    SELECT COUNT(*) INTO v_count
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table_name
      AND CONSTRAINT_NAME = p_constraint_name
      AND CONSTRAINT_TYPE = 'FOREIGN KEY';

    RETURN v_count > 0;
END //

DELIMITER ;

-- ============================================================================
-- Procedure: Create table if not exists with standard options
-- ============================================================================
DROP PROCEDURE IF EXISTS create_table_if_not_exists;

DELIMITER //

CREATE PROCEDURE create_table_if_not_exists(
    IN p_table_name VARCHAR(255),
    IN p_create_sql TEXT
)
BEGIN
    IF NOT table_exists(p_table_name) THEN
        SET @sql = p_create_sql;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('Created table: ', p_table_name) AS status;
    ELSE
        SELECT CONCAT('Table already exists: ', p_table_name) AS status;
    END IF;
END //

DELIMITER ;

-- ============================================================================
-- Procedure: Add column if not exists
-- ============================================================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //

CREATE PROCEDURE add_column_if_not_exists(
    IN p_table_name VARCHAR(255),
    IN p_column_name VARCHAR(255),
    IN p_column_definition VARCHAR(500)
)
BEGIN
    IF NOT column_exists(p_table_name, p_column_name) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `',
                          p_column_name, '` ', p_column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('Added column: ', p_table_name, '.', p_column_name) AS status;
    ELSE
        SELECT CONCAT('Column already exists: ', p_table_name, '.', p_column_name) AS status;
    END IF;
END //

DELIMITER ;

-- ============================================================================
-- Procedure: Add index if not exists
-- ============================================================================
DROP PROCEDURE IF EXISTS add_index_if_not_exists;

DELIMITER //

CREATE PROCEDURE add_index_if_not_exists(
    IN p_table_name VARCHAR(255),
    IN p_index_name VARCHAR(255),
    IN p_columns VARCHAR(500),
    IN p_is_unique BOOLEAN
)
BEGIN
    IF NOT index_exists(p_table_name, p_index_name) THEN
        IF p_is_unique THEN
            SET @sql = CONCAT('CREATE UNIQUE INDEX `', p_index_name, '` ON `',
                              p_table_name, '` (', p_columns, ')');
        ELSE
            SET @sql = CONCAT('CREATE INDEX `', p_index_name, '` ON `',
                              p_table_name, '` (', p_columns, ')');
        END IF;
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('Added index: ', p_index_name) AS status;
    ELSE
        SELECT CONCAT('Index already exists: ', p_index_name) AS status;
    END IF;
END //

DELIMITER ;

-- ============================================================================
-- Procedure: Add foreign key if not exists
-- ============================================================================
DROP PROCEDURE IF EXISTS add_foreign_key_if_not_exists;

DELIMITER //

CREATE PROCEDURE add_foreign_key_if_not_exists(
    IN p_table_name VARCHAR(255),
    IN p_constraint_name VARCHAR(255),
    IN p_columns VARCHAR(255),
    IN p_ref_table VARCHAR(255),
    IN p_ref_columns VARCHAR(255),
    IN p_on_delete VARCHAR(50),
    IN p_on_update VARCHAR(50)
)
BEGIN
    IF NOT foreign_key_exists(p_table_name, p_constraint_name) THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name,
                          '` ADD CONSTRAINT `', p_constraint_name,
                          '` FOREIGN KEY (', p_columns, ') REFERENCES `',
                          p_ref_table, '`(', p_ref_columns, ')');

        IF p_on_delete IS NOT NULL AND p_on_delete != '' THEN
            SET @sql = CONCAT(@sql, ' ON DELETE ', p_on_delete);
        END IF;

        IF p_on_update IS NOT NULL AND p_on_update != '' THEN
            SET @sql = CONCAT(@sql, ' ON UPDATE ', p_on_update);
        END IF;

        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('Added foreign key: ', p_constraint_name) AS status;
    ELSE
        SELECT CONCAT('Foreign key already exists: ', p_constraint_name) AS status;
    END IF;
END //

DELIMITER ;

-- ============================================================================
-- Procedure: Record migration as applied
-- ============================================================================
DROP PROCEDURE IF EXISTS record_migration;

DELIMITER //

CREATE PROCEDURE record_migration(
    IN p_version VARCHAR(255),
    IN p_name VARCHAR(255),
    IN p_checksum VARCHAR(64),
    IN p_execution_time_ms INT
)
BEGIN
    INSERT INTO schema_migrations (version, name, checksum, execution_time_ms, applied_by)
    VALUES (p_version, p_name, p_checksum, p_execution_time_ms, CURRENT_USER())
    ON DUPLICATE KEY UPDATE
        checksum = p_checksum,
        execution_time_ms = p_execution_time_ms,
        applied_at = CURRENT_TIMESTAMP,
        applied_by = CURRENT_USER();
END //

DELIMITER ;

-- ============================================================================
-- Example usage in a migration:
-- ============================================================================
/*

SET @migration_version = '20260321000000';
SET @migration_name = 'example_migration';

-- Skip if already applied
SELECT is_migration_applied(@migration_version) INTO @applied;

IF @applied = 0 THEN
    -- Create table
    CALL create_table_if_not_exists('my_table', '
        CREATE TABLE `my_table` (
            `id` VARCHAR(40) NOT NULL,
            `name` VARCHAR(255) NOT NULL,
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`)
        ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    ');

    -- Add column
    CALL add_column_if_not_exists('my_table', 'description', 'TEXT NULL');

    -- Add index
    CALL add_index_if_not_exists('my_table', 'my_table_name_idx', '`name`', FALSE);

    -- Add foreign key
    CALL add_foreign_key_if_not_exists(
        'my_table', 'my_table_user_id_fkey',
        '`user_id`', 'users', '`id`',
        'CASCADE', NULL
    );

    -- Record migration
    CALL record_migration(@migration_version, @migration_name, NULL, NULL);

    SELECT 'Migration completed successfully' AS status;
ELSE
    SELECT 'Migration already applied, skipping' AS status;
END IF;

*/
