-- ============================================================================
-- Migration: 20260320000000_add_moderation_tables
-- Description: Add moderation system tables (reports, queue, actions, warnings, bans, appeals)
--
-- This is an IDEMPOTENT migration using stored procedures.
-- It can be executed multiple times safely without causing errors or data loss.
--
-- Features:
-- - Checks if objects exist before creating
-- - Uses information_schema for column/index detection
-- - Records version in schema_migrations table
-- - Cleans up procedure after execution
-- ============================================================================

SET NAMES utf8mb4;
SET @migration_version = '20260320000000';
SET @migration_name = 'add_moderation_tables';

-- ============================================================================
-- Clean up any existing procedure with the same name
-- ============================================================================
DROP PROCEDURE IF EXISTS apply_migration_20260320000000;

DELIMITER //

-- ============================================================================
-- Create the migration procedure
-- ============================================================================
CREATE PROCEDURE apply_migration_20260320000000()
BEGIN
    -- Variables for checks
    DECLARE v_table_exists INT DEFAULT 0;
    DECLARE v_column_exists INT DEFAULT 0;
    DECLARE v_index_exists INT DEFAULT 0;
    DECLARE v_migration_applied INT DEFAULT 0;

    -- Check if this migration was already applied
    SELECT COUNT(*) INTO v_migration_applied
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'schema_migrations';

    IF v_migration_applied > 0 THEN
        SELECT COUNT(*) INTO v_migration_applied
        FROM schema_migrations
        WHERE version = @migration_version;

        IF v_migration_applied > 0 THEN
            SELECT CONCAT('Migration ', @migration_version, ' already applied, skipping.') AS message;
            -- Exit the procedure without doing anything
            LEAVE main_block;
        END IF;
    END IF;

    -- Main block label for early exit
    main_block: BEGIN
        -- ====================================================================
        -- 1. Reports table
        -- ====================================================================
        SELECT COUNT(*) INTO v_table_exists
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports';

        IF v_table_exists = 0 THEN
            CREATE TABLE `reports` (
                `id` VARCHAR(40) NOT NULL,
                `reporter_id` VARCHAR(40) NOT NULL,
                `entity_type` VARCHAR(50) NOT NULL,
                `entity_id` VARCHAR(50) NOT NULL,
                `category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE',
                               'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER',
                               'COPYRIGHT', 'OTHER') NOT NULL,
                `reason` TEXT,
                `evidence` TEXT,
                `status` ENUM('PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED')
                         NOT NULL DEFAULT 'PENDING',
                `queue_id` VARCHAR(40),
                `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                `updated_at` DATETIME(3) NOT NULL,

                PRIMARY KEY (`id`),
                INDEX `reports_entity_type_entity_id_idx` (`entity_type`, `entity_id`),
                INDEX `reports_reporter_id_idx` (`reporter_id`),
                INDEX `reports_status_idx` (`status`),
                INDEX `reports_category_idx` (`category`),
                CONSTRAINT `reports_reporter_id_fkey`
                    FOREIGN KEY (`reporter_id`)
                    REFERENCES `users`(`id`) ON DELETE CASCADE
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

            SELECT '✓ Created table: reports' AS status;
        ELSE
            SELECT '→ Table already exists: reports' AS status;
        END IF;

        -- ====================================================================
        -- 2. Moderation queue table
        -- ====================================================================
        SELECT COUNT(*) INTO v_table_exists
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'moderation_queue';

        IF v_table_exists = 0 THEN
            CREATE TABLE `moderation_queue` (
                `id` VARCHAR(40) NOT NULL,
                `entity_type` VARCHAR(50) NOT NULL,
                `entity_id` VARCHAR(50) NOT NULL,
                `author_id` VARCHAR(40) NOT NULL,
                `priority` INT NOT NULL DEFAULT 0,
                `status` ENUM('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED', 'APPEAL_PENDING')
                         NOT NULL DEFAULT 'PENDING',
                `report_count` INT NOT NULL DEFAULT 0,
                `primary_category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE',
                                        'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER',
                                        'COPYRIGHT', 'OTHER'),
                `assigned_to_id` VARCHAR(40),
                `assigned_at` DATETIME(3),
                `reviewed_by_id` VARCHAR(40),
                `reviewed_at` DATETIME(3),
                `resolution` ENUM('DELETED', 'HIDDEN', 'RESTORED', 'WARNED',
                                 'TEMP_BANNED', 'PERM_BANNED', 'DISMISSED', 'RESOLVED',
                                 'APPEAL_PENDING', 'APPEAL_APPROVED'),
                `resolution_note` TEXT,
                `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                `updated_at` DATETIME(3) NOT NULL,
                `resolved_at` DATETIME(3),

                PRIMARY KEY (`id`),
                UNIQUE INDEX `moderation_queue_entity_type_entity_id_key`
                    (`entity_type`, `entity_id`),
                INDEX `moderation_queue_status_idx` (`status`),
                INDEX `moderation_queue_assigned_to_id_idx` (`assigned_to_id`),
                INDEX `moderation_queue_priority_idx` (`priority`),
                INDEX `moderation_queue_author_id_idx` (`author_id`),
                CONSTRAINT `moderation_queue_author_id_fkey`
                    FOREIGN KEY (`author_id`)
                    REFERENCES `users`(`id`) ON DELETE CASCADE,
                CONSTRAINT `moderation_queue_assigned_to_id_fkey`
                    FOREIGN KEY (`assigned_to_id`)
                    REFERENCES `users`(`id`) ON DELETE SET NULL,
                CONSTRAINT `moderation_queue_reviewed_by_id_fkey`
                    FOREIGN KEY (`reviewed_by_id`)
                    REFERENCES `users`(`id`) ON DELETE SET NULL
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

            SELECT '✓ Created table: moderation_queue' AS status;
        ELSE
            SELECT '→ Table already exists: moderation_queue' AS status;
        END IF;

        -- ====================================================================
        -- 3. Moderation actions table
        -- ====================================================================
        SELECT COUNT(*) INTO v_table_exists
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'moderation_actions';

        IF v_table_exists = 0 THEN
            CREATE TABLE `moderation_actions` (
                `id` VARCHAR(40) NOT NULL,
                `queue_id` VARCHAR(40) NOT NULL,
                `action` ENUM('DELETED', 'HIDDEN', 'RESTORED', 'WARNED',
                             'TEMP_BANNED', 'PERM_BANNED', 'DISMISSED', 'RESOLVED',
                             'APPEAL_PENDING', 'APPEAL_APPROVED') NOT NULL,
                `performed_by_id` VARCHAR(40) NOT NULL,
                `note` TEXT,
                `duration_days` INT,
                `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

                PRIMARY KEY (`id`),
                INDEX `moderation_actions_queue_id_idx` (`queue_id`),
                INDEX `moderation_actions_performed_by_id_idx` (`performed_by_id`),
                INDEX `moderation_actions_action_idx` (`action`),
                CONSTRAINT `moderation_actions_queue_id_fkey`
                    FOREIGN KEY (`queue_id`)
                    REFERENCES `moderation_queue`(`id`) ON DELETE CASCADE,
                CONSTRAINT `moderation_actions_performed_by_id_fkey`
                    FOREIGN KEY (`performed_by_id`)
                    REFERENCES `users`(`id`) ON DELETE RESTRICT
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

            SELECT '✓ Created table: moderation_actions' AS status;
        ELSE
            SELECT '→ Table already exists: moderation_actions' AS status;
        END IF;

        -- ====================================================================
        -- 4. User warnings table
        -- ====================================================================
        SELECT COUNT(*) INTO v_table_exists
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_warnings';

        IF v_table_exists = 0 THEN
            CREATE TABLE `user_warnings` (
                `id` VARCHAR(40) NOT NULL,
                `user_id` VARCHAR(40) NOT NULL,
                `queue_id` VARCHAR(40),
                `action_id` VARCHAR(40),
                `reason` TEXT NOT NULL,
                `category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE',
                               'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER',
                               'COPYRIGHT', 'OTHER') NOT NULL,
                `acknowledged_at` DATETIME(3),
                `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                `expires_at` DATETIME(3),

                PRIMARY KEY (`id`),
                INDEX `user_warnings_user_id_idx` (`user_id`),
                INDEX `user_warnings_created_at_idx` (`created_at`),
                CONSTRAINT `user_warnings_user_id_fkey`
                    FOREIGN KEY (`user_id`)
                    REFERENCES `users`(`id`) ON DELETE CASCADE
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

            SELECT '✓ Created table: user_warnings' AS status;
        ELSE
            SELECT '→ Table already exists: user_warnings' AS status;
        END IF;

        -- ====================================================================
        -- 5. User bans table
        -- ====================================================================
        SELECT COUNT(*) INTO v_table_exists
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_bans';

        IF v_table_exists = 0 THEN
            CREATE TABLE `user_bans` (
                `id` VARCHAR(40) NOT NULL,
                `user_id` VARCHAR(40) NOT NULL,
                `is_permanent` BOOLEAN NOT NULL DEFAULT FALSE,
                `reason` TEXT NOT NULL,
                `category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE',
                               'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER',
                               'COPYRIGHT', 'OTHER'),
                `queue_id` VARCHAR(40),
                `action_id` VARCHAR(40),
                `banned_by_id` VARCHAR(40) NOT NULL,
                `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                `ends_at` DATETIME(3),
                `unbanned_at` DATETIME(3),
                `unbanned_by_id` VARCHAR(40),
                `unban_reason` TEXT,
                `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                `updated_at` DATETIME(3) NOT NULL,

                PRIMARY KEY (`id`),
                INDEX `user_bans_user_id_idx` (`user_id`),
                INDEX `user_bans_ends_at_idx` (`ends_at`),
                CONSTRAINT `user_bans_user_id_fkey`
                    FOREIGN KEY (`user_id`)
                    REFERENCES `users`(`id`) ON DELETE CASCADE,
                CONSTRAINT `user_bans_banned_by_id_fkey`
                    FOREIGN KEY (`banned_by_id`)
                    REFERENCES `users`(`id`) ON DELETE RESTRICT,
                CONSTRAINT `user_bans_unbanned_by_id_fkey`
                    FOREIGN KEY (`unbanned_by_id`)
                    REFERENCES `users`(`id`) ON DELETE SET NULL
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

            SELECT '✓ Created table: user_bans' AS status;
        ELSE
            SELECT '→ Table already exists: user_bans' AS status;
        END IF;

        -- ====================================================================
        -- 6. Appeals table
        -- ====================================================================
        SELECT COUNT(*) INTO v_table_exists
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'appeals';

        IF v_table_exists = 0 THEN
            CREATE TABLE `appeals` (
                `id` VARCHAR(40) NOT NULL,
                `queue_id` VARCHAR(40) NOT NULL,
                `appellant_id` VARCHAR(40) NOT NULL,
                `reason` TEXT NOT NULL,
                `evidence` TEXT,
                `status` ENUM('PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'ESCALATED')
                         NOT NULL DEFAULT 'PENDING',
                `reviewed_by_id` VARCHAR(40),
                `reviewed_at` DATETIME(3),
                `response` TEXT,
                `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                `updated_at` DATETIME(3) NOT NULL,

                PRIMARY KEY (`id`),
                INDEX `appeals_queue_id_idx` (`queue_id`),
                INDEX `appeals_appellant_id_idx` (`appellant_id`),
                INDEX `appeals_status_idx` (`status`),
                CONSTRAINT `appeals_queue_id_fkey`
                    FOREIGN KEY (`queue_id`)
                    REFERENCES `moderation_queue`(`id`) ON DELETE CASCADE,
                CONSTRAINT `appeals_appellant_id_fkey`
                    FOREIGN KEY (`appellant_id`)
                    REFERENCES `users`(`id`) ON DELETE CASCADE,
                CONSTRAINT `appeals_reviewed_by_id_fkey`
                    FOREIGN KEY (`reviewed_by_id`)
                    REFERENCES `users`(`id`) ON DELETE SET NULL
            ) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

            SELECT '✓ Created table: appeals' AS status;
        ELSE
            SELECT '→ Table already exists: appeals' AS status;
        END IF;

        -- ====================================================================
        -- 7. Add foreign key from reports to moderation_queue
        -- ====================================================================
        SELECT COUNT(*) INTO v_index_exists
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'reports'
          AND CONSTRAINT_NAME = 'reports_queue_id_fkey';

        IF v_index_exists = 0 THEN
            ALTER TABLE `reports`
            ADD CONSTRAINT `reports_queue_id_fkey`
            FOREIGN KEY (`queue_id`)
            REFERENCES `moderation_queue`(`id`) ON DELETE SET NULL;

            SELECT '✓ Added foreign key: reports.queue_id -> moderation_queue.id' AS status;
        ELSE
            SELECT '→ Foreign key already exists: reports_queue_id_fkey' AS status;
        END IF;

        -- ====================================================================
        -- Record migration in schema_migrations table
        -- ====================================================================
        -- Ensure schema_migrations table exists
        CREATE TABLE IF NOT EXISTS `schema_migrations` (
            `version` VARCHAR(255) NOT NULL,
            `name` VARCHAR(255) NOT NULL,
            `checksum` VARCHAR(64) NULL,
            `execution_time_ms` INT NULL,
            `applied_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            `applied_by` VARCHAR(100) NULL,
            PRIMARY KEY (`version`),
            INDEX `schema_migrations_applied_at_idx` (`applied_at`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

        INSERT INTO schema_migrations (version, name, applied_by)
        VALUES (@migration_version, @migration_name, CURRENT_USER())
        ON DUPLICATE KEY UPDATE applied_at = CURRENT_TIMESTAMP;

        SELECT CONCAT('✓ Migration ', @migration_version, ' completed successfully') AS status;

    END main_block;

END //

DELIMITER ;

-- ============================================================================
-- Execute the migration
-- ============================================================================
CALL apply_migration_20260320000000();

-- ============================================================================
-- Clean up the procedure (optional - comment out to keep for debugging)
-- ============================================================================
DROP PROCEDURE IF EXISTS apply_migration_20260320000000;

-- ============================================================================
-- Summary
-- ============================================================================
SELECT 'Migration 20260320000000_add_moderation_tables completed' AS summary;
