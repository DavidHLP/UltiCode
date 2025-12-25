/*
  Warnings:

  - You are about to drop the `favorites` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `user_problem_list_saves` table. If the table is not empty, all the data it contains will be lost.
*/

-- DropTable
DROP TABLE `favorites`;

-- DropTable
DROP TABLE `user_problem_list_saves`;

-- CreateTable
CREATE TABLE `user_problem_list_category_items` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `list_id` VARCHAR(50) NOT NULL,
    `category_id` VARCHAR(40) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE INDEX `user_problem_list_category_items_user_id_list_id_key`(`user_id`, `list_id`),
    INDEX `user_problem_list_category_items_user_id_category_id_idx`(`user_id`, `category_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AlterTable
ALTER TABLE `edge_operations` MODIFY `target_type` ENUM('SOLUTION', 'SOLUTION_COMMENT', 'FORUM_POST', 'FORUM_COMMENT', 'PROBLEM', 'PROBLEM_LIST') NOT NULL;

-- AddForeignKey
ALTER TABLE `user_problem_list_category_items` ADD CONSTRAINT `user_problem_list_category_items_list_id_fkey`
    FOREIGN KEY (`list_id`) REFERENCES `problem_lists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `user_problem_list_category_items` ADD CONSTRAINT `user_problem_list_category_items_category_id_fkey`
    FOREIGN KEY (`category_id`) REFERENCES `user_problem_list_categories`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
