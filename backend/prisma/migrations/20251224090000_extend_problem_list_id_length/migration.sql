-- AlterTable: Extend problem_list_groups.id from VARCHAR(40) to VARCHAR(50)
ALTER TABLE `problem_list_groups` MODIFY COLUMN `id` VARCHAR(50) NOT NULL;

-- AlterTable: Extend problem_lists.id and group_id from VARCHAR(40) to VARCHAR(50)
ALTER TABLE `problem_lists` DROP FOREIGN KEY `problem_lists_group_id_fkey`;
ALTER TABLE `problem_lists` MODIFY COLUMN `id` VARCHAR(50) NOT NULL;
ALTER TABLE `problem_lists` MODIFY COLUMN `group_id` VARCHAR(50) NOT NULL;
ALTER TABLE `problem_lists` ADD CONSTRAINT `problem_lists_group_id_fkey` FOREIGN KEY (`group_id`) REFERENCES `problem_list_groups`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AlterTable: Extend problem_list_problem_relations.list_id from VARCHAR(40) to VARCHAR(50)
ALTER TABLE `problem_list_problem_relations` DROP FOREIGN KEY `problem_list_problem_relations_list_id_fkey`;
ALTER TABLE `problem_list_problem_relations` MODIFY COLUMN `list_id` VARCHAR(50) NOT NULL;
ALTER TABLE `problem_list_problem_relations` ADD CONSTRAINT `problem_list_problem_relations_list_id_fkey` FOREIGN KEY (`list_id`) REFERENCES `problem_lists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
