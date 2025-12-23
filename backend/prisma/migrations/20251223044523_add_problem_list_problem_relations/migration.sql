-- CreateTable
CREATE TABLE `problem_list_problem_relations` (
    `list_id` VARCHAR(40) NOT NULL,
    `problem_id` BIGINT NOT NULL,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `added_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (`list_id`, `problem_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `problem_list_problem_relations` ADD CONSTRAINT `problem_list_problem_relations_list_id_fkey` FOREIGN KEY (`list_id`) REFERENCES `problem_lists`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `problem_list_problem_relations` ADD CONSTRAINT `problem_list_problem_relations_problem_id_fkey` FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
