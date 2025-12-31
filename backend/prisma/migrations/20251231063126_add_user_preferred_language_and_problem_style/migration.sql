-- AlterTable
ALTER TABLE `problem_languages` ADD COLUMN `style` VARCHAR(20) NULL;

-- AlterTable
ALTER TABLE `submissions` MODIFY `status` VARCHAR(40) NOT NULL;

-- AlterTable
ALTER TABLE `users` ADD COLUMN `preferred_language` VARCHAR(50) NULL;

-- CreateTable
CREATE TABLE `submission_statuses` (
    `key` VARCHAR(40) NOT NULL,
    `code` VARCHAR(10) NOT NULL,
    `label` VARCHAR(60) NOT NULL,
    `description` TEXT NULL,
    `suggestion` TEXT NULL,
    `category` VARCHAR(20) NOT NULL,
    `severity` VARCHAR(20) NOT NULL,
    `is_terminal` BOOLEAN NOT NULL DEFAULT true,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `submission_statuses_category_severity_idx`(`category`, `severity`),
    PRIMARY KEY (`key`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
