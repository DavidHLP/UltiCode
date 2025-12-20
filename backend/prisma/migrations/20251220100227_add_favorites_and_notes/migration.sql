-- AlterTable
ALTER TABLE `votes` MODIFY `target_type` ENUM('SOLUTION', 'SOLUTION_COMMENT', 'FORUM_POST', 'FORUM_COMMENT', 'PROBLEM') NOT NULL;

-- CreateTable
CREATE TABLE `favorites` (
    `id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(40) NOT NULL,
    `target_type` ENUM('SOLUTION', 'FORUM_POST', 'PROBLEM') NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `favorites_target_type_target_id_idx`(`target_type`, `target_id`),
    UNIQUE INDEX `favorites_user_id_target_type_target_id_key`(`user_id`, `target_type`, `target_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `problem_notes` (
    `id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `content` TEXT NOT NULL,
    `updated_at` DATETIME(3) NOT NULL,

    UNIQUE INDEX `problem_notes_user_id_problem_id_key`(`user_id`, `problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `favorites` ADD CONSTRAINT `favorites_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_notes` ADD CONSTRAINT `problem_notes_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_notes` ADD CONSTRAINT `problem_notes_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
