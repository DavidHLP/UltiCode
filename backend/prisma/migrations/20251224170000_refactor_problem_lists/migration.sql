-- Refactor Problem Lists: Remove global groups, add user-level saves and categories

-- Step 1: Create new tables first
CREATE TABLE `user_problem_list_categories` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `name` VARCHAR(120) NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `user_problem_list_categories_user_id_name_key`(`user_id`, `name`),
    INDEX `user_problem_list_categories_user_id_idx`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `user_problem_list_saves` (
    `id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `list_id` VARCHAR(50) NOT NULL,
    `category_id` VARCHAR(40) NULL,
    `saved_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    UNIQUE INDEX `user_problem_list_saves_user_id_list_id_key`(`user_id`, `list_id`),
    INDEX `user_problem_list_saves_user_id_idx`(`user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Step 2: Add is_featured column to problem_lists
ALTER TABLE `problem_lists` ADD COLUMN `is_featured` BOOLEAN NOT NULL DEFAULT false;

-- Step 3: Mark existing featured lists (from group-featured)
UPDATE `problem_lists` SET `is_featured` = true WHERE `group_id` = 'group-featured';

-- Step 4: Drop foreign key constraint from problem_lists to problem_list_groups
ALTER TABLE `problem_lists` DROP FOREIGN KEY `problem_lists_group_id_fkey`;

-- Step 5: Drop the group_id column from problem_lists
ALTER TABLE `problem_lists` DROP COLUMN `group_id`;

-- Step 6: Drop the problem_list_groups table
DROP TABLE `problem_list_groups`;

-- Step 7: Add foreign key constraints for new tables
ALTER TABLE `user_problem_list_saves` ADD CONSTRAINT `user_problem_list_saves_list_id_fkey` 
    FOREIGN KEY (`list_id`) REFERENCES `problem_lists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE `user_problem_list_saves` ADD CONSTRAINT `user_problem_list_saves_category_id_fkey` 
    FOREIGN KEY (`category_id`) REFERENCES `user_problem_list_categories`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;
