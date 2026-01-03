-- AlterTable
ALTER TABLE `contest_participants` ADD COLUMN `last_solve_time` INTEGER NULL,
    ADD COLUMN `total_attempts` INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE `contest_problems` ADD COLUMN `penalty_per_wrong` INTEGER NULL;

-- AlterTable
ALTER TABLE `contest_rankings` ADD COLUMN `finish_time` INTEGER NULL,
    ADD COLUMN `total_attempts` INTEGER NOT NULL DEFAULT 0;

-- AlterTable
ALTER TABLE `contests` ADD COLUMN `penalty_per_wrong` INTEGER NOT NULL DEFAULT 300,
    ADD COLUMN `scoring_mode` ENUM('SCORE', 'ICPC') NOT NULL DEFAULT 'SCORE',
    ADD COLUMN `tie_breaker` ENUM('LAST_SOLVE_TIME', 'TOTAL_ATTEMPTS', 'NONE') NOT NULL DEFAULT 'LAST_SOLVE_TIME';
