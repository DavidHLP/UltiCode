/*
  Warnings:

  - You are about to drop the column `dislikes` on the `forum_comments` table. All the data in the column will be lost.
  - You are about to drop the column `likes` on the `forum_comments` table. All the data in the column will be lost.
  - You are about to drop the column `dislikes` on the `forum_posts` table. All the data in the column will be lost.
  - You are about to drop the column `likes` on the `forum_posts` table. All the data in the column will be lost.
  - You are about to drop the column `dislikes` on the `solution_comments` table. All the data in the column will be lost.
  - You are about to drop the column `likes` on the `solution_comments` table. All the data in the column will be lost.
  - You are about to drop the column `dislikes` on the `solutions` table. All the data in the column will be lost.
  - You are about to drop the column `likes` on the `solutions` table. All the data in the column will be lost.

*/
-- AlterTable
ALTER TABLE `forum_comments` DROP COLUMN `dislikes`,
    DROP COLUMN `likes`;

-- AlterTable
ALTER TABLE `forum_posts` DROP COLUMN `dislikes`,
    DROP COLUMN `likes`,
    ADD COLUMN `views` INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE `solution_comments` DROP COLUMN `dislikes`,
    DROP COLUMN `likes`;

-- AlterTable
ALTER TABLE `solutions` DROP COLUMN `dislikes`,
    DROP COLUMN `likes`;

-- CreateTable
CREATE TABLE `solution_views` (
    `id` VARCHAR(40) NOT NULL,
    `solution_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NULL,
    `ip` VARCHAR(45) NULL,
    `viewed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `solution_views_solution_id_user_id_ip_idx`(`solution_id`, `user_id`, `ip`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `forum_post_views` (
    `id` VARCHAR(40) NOT NULL,
    `post_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NULL,
    `ip` VARCHAR(45) NULL,
    `viewed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `forum_post_views_post_id_user_id_ip_idx`(`post_id`, `user_id`, `ip`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `solution_views` ADD CONSTRAINT `solution_views_solution_id_fkey` FOREIGN KEY (`solution_id`) REFERENCES `solutions`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `solution_views` ADD CONSTRAINT `solution_views_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_post_views` ADD CONSTRAINT `forum_post_views_post_id_fkey` FOREIGN KEY (`post_id`) REFERENCES `forum_posts`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_post_views` ADD CONSTRAINT `forum_post_views_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
