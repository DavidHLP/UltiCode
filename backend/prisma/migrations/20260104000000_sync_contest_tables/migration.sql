/*
  Warnings:

  - You are about to drop the column `finish_time_seconds` on the `contest_participants` table. All the data in the column will be lost.
  - You are about to drop the column `rank` on the `contest_participants` table. All the data in the column will be lost.
  - You are about to drop the column `score` on the `contest_participants` table. All the data in the column will be lost.
  - You are about to drop the column `username` on the `contest_participants` table. All the data in the column will be lost.
  - You are about to alter the column `status` on the `contest_participants` table. The data in that column could be lost. The data in that column will be cast from `VarChar(20)` to `Enum(EnumId(6))`.
  - You are about to drop the column `country` on the `contest_rankings` table. All the data in the column will be lost.
  - You are about to drop the column `finish_time_seconds` on the `contest_rankings` table. All the data in the column will be lost.
  - You are about to drop the column `q1_time_seconds` on the `contest_rankings` table. All the data in the column will be lost.
  - You are about to drop the column `q2_time_seconds` on the `contest_rankings` table. All the data in the column will be lost.
  - You are about to drop the column `q3_time_seconds` on the `contest_rankings` table. All the data in the column will be lost.
  - You are about to drop the column `q4_time_seconds` on the `contest_rankings` table. All the data in the column will be lost.
  - You are about to drop the column `score` on the `contest_rankings` table. All the data in the column will be lost.
  - You are about to drop the column `username` on the `contest_rankings` table. All the data in the column will be lost.
  - The values [FAVORITE] on the enum `edge_operations_operation_type` will be removed. If these variants are still used in the database, this will fail.
  - A unique constraint covering the columns `[contest_id,user_id,virtual_session_id]` on the table `contest_participants` will be added. If there are existing duplicate values, this will fail.
  - A unique constraint covering the columns `[contest_id,user_id]` on the table `contest_rankings` will be added. If there are existing duplicate values, this will fail.
  - Added the required column `updated_at` to the `contests` table without a default value. This is not possible if the table is not empty.
  - Added the required column `updated_at` to the `global_rankings` table without a default value. This is not possible if the table is not empty.

*/
-- AlterTable
ALTER TABLE `collection_items` MODIFY `target_id` VARCHAR(50) NOT NULL,
    MODIFY `target_type` ENUM('PROBLEM', 'SOLUTION', 'FORUM_POST', 'PROBLEM_LIST', 'SOLUTION_COMMENT', 'FORUM_COMMENT') NOT NULL;

-- AlterTable
ALTER TABLE `contest_participants` DROP COLUMN `finish_time_seconds`,
    DROP COLUMN `rank`,
    DROP COLUMN `score`,
    DROP COLUMN `username`,
    ADD COLUMN `final_rank` INTEGER NULL,
    ADD COLUMN `total_penalty` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `total_score` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `virtual_session_id` VARCHAR(40) NULL,
    MODIFY `status` ENUM('REGISTERED', 'STARTED', 'FINISHED', 'DISQUALIFIED') NOT NULL DEFAULT 'REGISTERED',
    MODIFY `registered_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

-- AlterTable
ALTER TABLE `contest_rankings` DROP COLUMN `country`,
    DROP COLUMN `finish_time_seconds`,
    DROP COLUMN `q1_time_seconds`,
    DROP COLUMN `q2_time_seconds`,
    DROP COLUMN `q3_time_seconds`,
    DROP COLUMN `q4_time_seconds`,
    DROP COLUMN `score`,
    DROP COLUMN `username`,
    ADD COLUMN `is_virtual` BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN `solved_count` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `total_penalty` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `total_score` INTEGER NOT NULL DEFAULT 0,
    MODIFY `rating_before` INTEGER NOT NULL DEFAULT 1500,
    MODIFY `rating_after` INTEGER NOT NULL DEFAULT 1500,
    MODIFY `rating_change` INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE `contests` ADD COLUMN `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    ADD COLUMN `created_by` VARCHAR(40) NULL,
    ADD COLUMN `is_visible` BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN `rules` TEXT NULL,
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL;

-- AlterTable
ALTER TABLE `edge_operations` MODIFY `operation_type` ENUM('VOTE_UP', 'VOTE_DOWN', 'ANALYZE') NOT NULL;

-- AlterTable
ALTER TABLE `global_rankings` ADD COLUMN `contests_rated` INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN `last_contest_id` VARCHAR(40) NULL,
    ADD COLUMN `max_rating_title` ENUM('NEWBIE', 'PUPIL', 'SPECIALIST', 'EXPERT', 'CANDIDATE_MASTER', 'MASTER', 'INTERNATIONAL_MASTER', 'GRANDMASTER', 'INTERNATIONAL_GRANDMASTER', 'LEGENDARY_GRANDMASTER') NOT NULL DEFAULT 'NEWBIE',
    ADD COLUMN `rating_title` ENUM('NEWBIE', 'PUPIL', 'SPECIALIST', 'EXPERT', 'CANDIDATE_MASTER', 'MASTER', 'INTERNATIONAL_MASTER', 'GRANDMASTER', 'INTERNATIONAL_GRANDMASTER', 'LEGENDARY_GRANDMASTER') NOT NULL DEFAULT 'NEWBIE',
    ADD COLUMN `updated_at` DATETIME(3) NOT NULL,
    MODIFY `country` VARCHAR(10) NULL;

-- CreateTable
CREATE TABLE `contest_problem_results` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `contest_problem_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `participant_id` VARCHAR(40) NOT NULL,
    `ranking_id` VARCHAR(40) NULL,
    `is_solved` BOOLEAN NOT NULL DEFAULT false,
    `score` INTEGER NOT NULL DEFAULT 0,
    `attempts` INTEGER NOT NULL DEFAULT 0,
    `first_solve_time` INTEGER NULL,
    `penalty_time` INTEGER NOT NULL DEFAULT 0,
    `best_submission_id` VARCHAR(40) NULL,

    INDEX `contest_problem_results_contest_id_user_id_idx`(`contest_id`, `user_id`),
    INDEX `contest_problem_results_contest_problem_id_idx`(`contest_problem_id`),
    UNIQUE INDEX `contest_problem_results_participant_id_contest_problem_id_key`(`participant_id`, `contest_problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `virtual_contest_sessions` (
    `id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `user_id` VARCHAR(40) NOT NULL,
    `status` ENUM('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED') NOT NULL DEFAULT 'NOT_STARTED',
    `started_at` DATETIME(3) NULL,
    `ends_at` DATETIME(3) NULL,
    `finished_at` DATETIME(3) NULL,
    `total_score` INTEGER NOT NULL DEFAULT 0,
    `total_penalty` INTEGER NOT NULL DEFAULT 0,

    INDEX `virtual_contest_sessions_user_id_status_idx`(`user_id`, `status`),
    INDEX `virtual_contest_sessions_contest_id_user_id_idx`(`contest_id`, `user_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateTable
CREATE TABLE `contest_submissions` (
    `id` VARCHAR(40) NOT NULL,
    `submission_id` VARCHAR(40) NOT NULL,
    `contest_id` VARCHAR(40) NOT NULL,
    `contest_problem_id` VARCHAR(40) NOT NULL,
    `participant_id` VARCHAR(40) NOT NULL,
    `virtual_session_id` VARCHAR(40) NULL,
    `submitted_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `time_from_start` INTEGER NOT NULL,
    `is_accepted` BOOLEAN NOT NULL DEFAULT false,

    INDEX `contest_submissions_contest_id_participant_id_idx`(`contest_id`, `participant_id`),
    INDEX `contest_submissions_contest_problem_id_idx`(`contest_problem_id`),
    PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- CreateIndex
CREATE INDEX `contest_participants_contest_id_final_rank_idx` ON `contest_participants`(`contest_id`, `final_rank`);

-- CreateIndex
CREATE UNIQUE INDEX `contest_participants_contest_id_user_id_virtual_session_id_key` ON `contest_participants`(`contest_id`, `user_id`, `virtual_session_id`);

-- CreateIndex
CREATE INDEX `contest_rankings_contest_id_rank_idx` ON `contest_rankings`(`contest_id`, `rank`);

-- CreateIndex
CREATE UNIQUE INDEX `contest_rankings_contest_id_user_id_key` ON `contest_rankings`(`contest_id`, `user_id`);

-- CreateIndex
CREATE INDEX `contests_status_start_time_idx` ON `contests`(`status`, `start_time`);

-- CreateIndex
CREATE INDEX `contests_contest_type_idx` ON `contests`(`contest_type`);

-- CreateIndex
CREATE INDEX `contests_slug_idx` ON `contests`(`slug`);

-- CreateIndex
CREATE INDEX `forum_communities_slug_idx` ON `forum_communities`(`slug`);

-- CreateIndex
CREATE INDEX `global_rankings_global_rank_idx` ON `global_rankings`(`global_rank`);

-- CreateIndex
CREATE INDEX `global_rankings_rating_idx` ON `global_rankings`(`rating`);

-- AddForeignKey
ALTER TABLE `global_rankings` ADD CONSTRAINT `global_rankings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_participants` ADD CONSTRAINT `contest_participants_virtual_session_id_fkey` FOREIGN KEY (`virtual_session_id`) REFERENCES `virtual_contest_sessions`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_contest_problem_id_fkey` FOREIGN KEY (`contest_problem_id`) REFERENCES `contest_problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_participant_id_fkey` FOREIGN KEY (`participant_id`) REFERENCES `contest_participants`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_problem_results` ADD CONSTRAINT `contest_problem_results_ranking_id_fkey` FOREIGN KEY (`ranking_id`) REFERENCES `contest_rankings`(`id`) ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `virtual_contest_sessions` ADD CONSTRAINT `virtual_contest_sessions_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `virtual_contest_sessions` ADD CONSTRAINT `virtual_contest_sessions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_submission_id_fkey` FOREIGN KEY (`submission_id`) REFERENCES `submissions`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_contest_id_fkey` FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_contest_problem_id_fkey` FOREIGN KEY (`contest_problem_id`) REFERENCES `contest_problems`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE `contest_submissions` ADD CONSTRAINT `contest_submissions_participant_id_fkey` FOREIGN KEY (`participant_id`) REFERENCES `contest_participants`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- RenameIndex
ALTER TABLE `contest_participants` RENAME INDEX `contest_participants_user_id_fkey` TO `contest_participants_user_id_idx`;

-- RenameIndex
ALTER TABLE `contest_problems` RENAME INDEX `contest_problems_contest_id_fkey` TO `contest_problems_contest_id_idx`;

-- RenameIndex
ALTER TABLE `contest_rankings` RENAME INDEX `contest_rankings_user_id_fkey` TO `contest_rankings_user_id_idx`;
