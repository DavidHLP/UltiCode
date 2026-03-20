-- MySQL does not support CREATE TYPE, use ENUM directly in column definition

-- CreateTable
CREATE TABLE IF NOT EXISTS `DailyRecommendation` (
    `id` VARCHAR(191) NOT NULL,
    `user_id` VARCHAR(191) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `problem_slug` VARCHAR(191) NOT NULL,
    `problem_title` VARCHAR(191) NOT NULL,
    `difficulty` VARCHAR(191) NOT NULL,
    `score` DECIMAL(65,30) NOT NULL,
    `tags` JSON NOT NULL DEFAULT ('[]'),
    `reason` VARCHAR(191) NOT NULL,
    `scenario` ENUM('DAILY', 'SIMILAR', 'WEAK_POINT', 'CHALLENGE') NOT NULL DEFAULT 'DAILY',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`id`),
    UNIQUE KEY `DailyRecommendation_user_id_problem_id_scenario_key` (`user_id`, `problem_id`, `scenario`),
    INDEX `DailyRecommendation_user_id_idx` (`user_id`),
    INDEX `DailyRecommendation_scenario_idx` (`scenario`),
    INDEX `DailyRecommendation_created_at_idx` (`created_at`),
    INDEX `DailyRecommendation_user_id_fkey` (`user_id`),
    CONSTRAINT `DailyRecommendation_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
