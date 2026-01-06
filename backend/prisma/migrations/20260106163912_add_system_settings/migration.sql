-- CreateTable
CREATE TABLE `system_settings` (
    `key` VARCHAR(50) NOT NULL,
    `value` TEXT NOT NULL,
    `description` VARCHAR(255) NULL,
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`key`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
