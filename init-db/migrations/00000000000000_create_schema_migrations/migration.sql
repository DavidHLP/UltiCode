-- Schema Migrations Version Control Table
-- This table tracks manually executed migrations for idempotent execution
-- It complements Prisma's _prisma_migrations table

CREATE TABLE IF NOT EXISTS `schema_migrations` (
    `version` VARCHAR(255) NOT NULL COMMENT 'Migration version identifier (e.g., 20260320000000)',
    `name` VARCHAR(255) NOT NULL COMMENT 'Migration name (e.g., add_moderation_tables)',
    `checksum` VARCHAR(64) NULL COMMENT 'SHA-256 hash of migration SQL for integrity check',
    `execution_time_ms` INT NULL COMMENT 'Time taken to execute the migration',
    `applied_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When the migration was applied',
    `applied_by` VARCHAR(100) NULL COMMENT 'User or system that applied the migration',

    PRIMARY KEY (`version`),
    INDEX `schema_migrations_applied_at_idx` (`applied_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tracks manually executed SQL migrations for idempotent execution';

-- Rollback: DROP TABLE IF EXISTS `schema_migrations`;
