-- AlterTable
ALTER TABLE `role_permissions` MODIFY `resource` ENUM('USER', 'PROBLEM', 'CONTEST', 'SOLUTION', 'FORUM_POST', 'FORUM_COMMENT', 'SYSTEM', 'PROBLEM_LIST', 'TAG') NOT NULL;

-- AlterTable
ALTER TABLE `user_permissions` MODIFY `resource` ENUM('USER', 'PROBLEM', 'CONTEST', 'SOLUTION', 'FORUM_POST', 'FORUM_COMMENT', 'SYSTEM', 'PROBLEM_LIST', 'TAG') NOT NULL;

-- CreateTable
CREATE TABLE `system_announcements` (
    `id` VARCHAR(40) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `type` ENUM('COMMENT', 'REPLY', 'MENTION', 'UPVOTE', 'FOLLOW', 'SYSTEM', 'SUBMISSION', 'CONTEST') NOT NULL,
    `created_by` VARCHAR(40) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `system_announcement_reads` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `announcement_id` VARCHAR(40) NOT NULL,
    `is_read` BOOLEAN NOT NULL DEFAULT true,
    `read_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `system_announcement_reads_user_id_announcement_id_key`(`user_id`, `announcement_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `system_announcements` ADD CONSTRAINT `system_announcements_created_by_fkey` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`) ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `system_announcement_reads` ADD CONSTRAINT `system_announcement_reads_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `system_announcement_reads` ADD CONSTRAINT `system_announcement_reads_announcement_id_fkey` FOREIGN KEY (`announcement_id`) REFERENCES `system_announcements`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
