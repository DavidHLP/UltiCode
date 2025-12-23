/*
  Warnings:

  - You are about to drop the `votes` table. If the table is not empty, all the data it contains will be lost.

*/
-- DropTable
DROP TABLE `votes`;

-- CreateTable
CREATE TABLE `edge_operations` (
    `id` VARCHAR(40) NOT NULL,
    `target_id` VARCHAR(40) NOT NULL,
    `target_type` ENUM('SOLUTION', 'SOLUTION_COMMENT', 'FORUM_POST', 'FORUM_COMMENT', 'PROBLEM') NOT NULL,
    `operator_id` VARCHAR(40) NOT NULL,
    `operation_type` ENUM('VOTE_UP', 'VOTE_DOWN', 'FAVORITE', 'CHARGE', 'ANALYZE') NOT NULL,

    INDEX `edge_ops_target`(`target_type`, `target_id`),
    INDEX `edge_ops_operation_target`(`operation_type`, `target_type`, `target_id`),
    UNIQUE INDEX `edge_ops_unique`(`operator_id`, `operation_type`, `target_type`, `target_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- AddForeignKey
ALTER TABLE `edge_operations` ADD CONSTRAINT `edge_operations_operator_id_fkey` FOREIGN KEY (`operator_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;
