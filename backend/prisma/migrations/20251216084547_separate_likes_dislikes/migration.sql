/*
  Warnings:

  - You are about to drop the column `upvotes` on the `forum_comments` table. All the data in the column will be lost.

*/
-- AlterTable
ALTER TABLE `forum_comments` DROP COLUMN `upvotes`,
    ADD COLUMN `dislikes` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `likes` INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE `forum_posts` ADD COLUMN `dislikes` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `likes` INTEGER NOT NULL DEFAULT 0;
