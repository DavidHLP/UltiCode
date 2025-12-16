/*
  Warnings:

  - You are about to drop the `solution_comment_votes` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `solution_votes` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropForeignKey
ALTER TABLE `solution_comment_votes` DROP FOREIGN KEY `solution_comment_votes_comment_id_fkey`;

-- DropForeignKey
ALTER TABLE `solution_comment_votes` DROP FOREIGN KEY `solution_comment_votes_user_id_fkey`;

-- DropForeignKey
ALTER TABLE `solution_votes` DROP FOREIGN KEY `solution_votes_solution_id_fkey`;

-- DropForeignKey
ALTER TABLE `solution_votes` DROP FOREIGN KEY `solution_votes_user_id_fkey`;

-- DropTable
DROP TABLE `solution_comment_votes`;

-- DropTable
DROP TABLE `solution_votes`;

-- CreateTable
CREATE TABLE `votes` (
    `id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(40) NOT NULL,
    `target_type` ENUM('SOLUTION', 'SOLUTION_COMMENT', 'FORUM_POST', 'FORUM_COMMENT') NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `vote_type` INTEGER NOT NULL,

    INDEX `votes_target_type_target_id_idx`(`target_type`, `target_id`),
    UNIQUE INDEX `votes_user_id_target_type_target_id_key`(`user_id`, `target_type`, `target_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `votes` ADD CONSTRAINT `votes_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
