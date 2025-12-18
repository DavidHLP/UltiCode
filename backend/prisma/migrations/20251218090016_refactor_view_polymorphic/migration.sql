/*
  Warnings:

  - You are about to drop the `forum_post_views` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `solution_views` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropForeignKey
ALTER TABLE `forum_post_views` DROP FOREIGN KEY `forum_post_views_post_id_fkey`;

-- DropForeignKey
ALTER TABLE `forum_post_views` DROP FOREIGN KEY `forum_post_views_user_id_fkey`;

-- DropForeignKey
ALTER TABLE `solution_views` DROP FOREIGN KEY `solution_views_solution_id_fkey`;

-- DropForeignKey
ALTER TABLE `solution_views` DROP FOREIGN KEY `solution_views_user_id_fkey`;

-- DropTable
DROP TABLE `forum_post_views`;

-- DropTable
DROP TABLE `solution_views`;

-- CreateTable
CREATE TABLE `views` (
    `id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(40) NOT NULL,
    `target_type` ENUM('SOLUTION', 'FORUM_POST') NOT NULL,
    `user_id` VARCHAR(40) NULL,
    `ip` VARCHAR(45) NULL,
    `viewed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `views_target_type_target_id_user_id_ip_idx`(`target_type`, `target_id`, `user_id`, `ip`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `views` ADD CONSTRAINT `views_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
