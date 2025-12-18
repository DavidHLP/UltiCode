/*
  Warnings:

  - You are about to alter the column `author_id` on the `forum_comments` table. The data in that column could be lost. The data in that column will be cast from `VarChar(60)` to `VarChar(40)`.
  - You are about to alter the column `user_id` on the `forum_posts` table. The data in that column could be lost. The data in that column will be cast from `VarChar(60)` to `VarChar(40)`.
  - The primary key for the `forum_users` table will be changed. If it partially fails, the table could be left without primary key constraint.
  - A unique constraint covering the columns `[username]` on the table `forum_users` will be added. If there are existing duplicate values, this will fail.
  - Added the required column `id` to the `forum_users` table without a default value. This is not possible if the table is not empty.

*/
-- DropForeignKey
ALTER TABLE `forum_comments` DROP FOREIGN KEY `forum_comments_author_id_fkey`;

-- DropForeignKey
ALTER TABLE `forum_posts` DROP FOREIGN KEY `forum_posts_user_id_fkey`;

-- DropIndex
DROP INDEX `forum_comments_author_id_fkey` ON `forum_comments`;

-- DropIndex
DROP INDEX `forum_posts_user_id_fkey` ON `forum_posts`;

-- AlterTable
ALTER TABLE `forum_comments` MODIFY `author_id` VARCHAR(40) NOT NULL;

-- AlterTable
ALTER TABLE `forum_posts` MODIFY `user_id` VARCHAR(40) NOT NULL;

-- AlterTable
ALTER TABLE `forum_users` DROP PRIMARY KEY,
    ADD COLUMN `id` VARCHAR(40) NOT NULL,
    ADD PRIMARY KEY (`id`);

-- CreateIndex
CREATE UNIQUE INDEX `forum_users_username_key` ON `forum_users`(`username`);

-- AddForeignKey
ALTER TABLE `forum_posts` ADD CONSTRAINT `forum_posts_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `forum_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `forum_comments` ADD CONSTRAINT `forum_comments_author_id_fkey` FOREIGN KEY (`author_id`) REFERENCES `forum_users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
