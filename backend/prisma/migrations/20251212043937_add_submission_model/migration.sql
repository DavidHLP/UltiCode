-- CreateTable
CREATE TABLE `submissions` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `language` VARCHAR(50) NOT NULL,
    `code` TEXT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `runtime` INTEGER NOT NULL,
    `memory` DOUBLE NOT NULL,
    `notes` TEXT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `runtime_percentile` DOUBLE NULL,
    `memory_percentile` DOUBLE NULL,
    `test_details` JSON NULL,

    INDEX `submissions_problem_id_user_id_idx`(`problem_id`, `user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `submissions` ADD CONSTRAINT `submissions_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `submissions` ADD CONSTRAINT `submissions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
