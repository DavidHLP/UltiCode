-- AlterTable
ALTER TABLE `solution_comments` ADD COLUMN `dislikes` INTEGER NOT NULL DEFAULT 0;

-- CreateTable
CREATE TABLE `solution_comment_votes` (
    `comment_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `vote_type` INTEGER NOT NULL,

    PRIMARY KEY (`comment_id`, `user_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `solution_comment_votes` ADD CONSTRAINT `solution_comment_votes_comment_id_fkey` FOREIGN KEY (`comment_id`) REFERENCES `solution_comments`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_comment_votes` ADD CONSTRAINT `solution_comment_votes_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
