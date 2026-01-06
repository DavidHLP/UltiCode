-- AlterTable
ALTER TABLE `forum_comments` ADD COLUMN `deleted_at` DATETIME(3) NULL,
    ADD COLUMN `deleted_by` VARCHAR(40) NULL,
    ADD COLUMN `flagged_at` DATETIME(3) NULL,
    ADD COLUMN `flagged_reason` TEXT NULL,
    ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `is_flagged` BOOLEAN NOT NULL DEFAULT false;

-- AlterTable
ALTER TABLE `solution_comments` ADD COLUMN `deleted_at` DATETIME(3) NULL,
    ADD COLUMN `deleted_by` VARCHAR(40) NULL,
    ADD COLUMN `flagged_at` DATETIME(3) NULL,
    ADD COLUMN `flagged_reason` TEXT NULL,
    ADD COLUMN `is_deleted` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `is_flagged` BOOLEAN NOT NULL DEFAULT false;
