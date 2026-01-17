-- AlterTable
ALTER TABLE `problems` ADD COLUMN `flag_notes` TEXT NULL,
    ADD COLUMN `flag_reason` TEXT NULL,
    ADD COLUMN `flag_reported_at` DATETIME(3) NULL,
    ADD COLUMN `flag_reported_by` VARCHAR(40) NULL,
    ADD COLUMN `flag_reviewed_at` DATETIME(3) NULL,
    ADD COLUMN `flag_reviewed_by` VARCHAR(40) NULL,
    ADD COLUMN `flag_status` ENUM('PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED') NULL,
    ADD COLUMN `is_flagged` BOOLEAN NOT NULL DEFAULT false;
