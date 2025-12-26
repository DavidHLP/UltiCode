/*
  Warnings:

  - You are about to drop the `user_problem_list_category_items` table. If the table is not empty, all the data it contains will be lost.
  - You are about to drop the `user_problem_list_categories` table. If the table is not empty, all the data it contains will be lost.
  - The values [CHARGE] on the enum `edge_operations_operation_type` will be removed. If these variants are still used in the database, this will fail.
*/

-- Drop collections tables if they exist (from previous failed migration)
DROP TABLE IF EXISTS `collection_items`;
DROP TABLE IF EXISTS `collections`;

-- Drop old category tables
DROP TABLE IF EXISTS `user_problem_list_category_items`;
DROP TABLE IF EXISTS `user_problem_list_categories`;

-- CreateTable for Collections
CREATE TABLE `collections` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `description` TEXT NULL,
    `icon` VARCHAR(50) NULL,
    `color` VARCHAR(20) NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `is_default` BOOLEAN NOT NULL DEFAULT false,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL,

    INDEX `collections_user_id_idx`(`user_id`),
    INDEX `collections_user_id_is_default_idx`(`user_id`, `is_default`),
    UNIQUE INDEX `collections_user_id_name_key`(`user_id`, `name`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable for CollectionItem
CREATE TABLE `collection_items` (
    `id` VARCHAR(40) NOT NULL,
    `collection_id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(40) NOT NULL,
    `target_type` ENUM('PROBLEM', 'SOLUTION', 'FORUM_POST', 'PROBLEM_LIST') NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `note` TEXT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    INDEX `collection_items_target_type_target_id_idx`(`target_type`, `target_id`),
    UNIQUE INDEX `collection_items_collection_id_target_type_target_id_key`(`collection_id`, `target_type`, `target_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `collections` ADD CONSTRAINT `collections_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `collection_items` ADD CONSTRAINT `collection_items_collection_id_fkey` FOREIGN KEY (`collection_id`) REFERENCES `collections`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AlterTable edge_operations - add created_at column
ALTER TABLE `edge_operations` ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

-- AlterTable edge_operations - update enum to remove CHARGE
ALTER TABLE `edge_operations` MODIFY `operation_type` ENUM('VOTE_UP', 'VOTE_DOWN', 'FAVORITE', 'ANALYZE') NOT NULL;
