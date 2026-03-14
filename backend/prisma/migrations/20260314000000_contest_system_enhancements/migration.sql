-- ============================================
-- Phase 1: Create new tables
-- ============================================

-- 1.1 Create contest_scoring_rules table
CREATE TABLE IF NOT EXISTS `contest_scoring_rules` (
  `id` VARCHAR(40) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` TEXT NULL,
  `base_score_per_problem` INT NOT NULL DEFAULT 100,
  `time_bonus_per_minute` INT NOT NULL DEFAULT 1,
  `wrong_answer_penalty` INT NOT NULL DEFAULT 5,
  `time_limit_penalty` INT NOT NULL DEFAULT 0,
  `first_solve_bonus` INT NOT NULL DEFAULT 10,
  `full_score_bonus` INT NOT NULL DEFAULT 0,
  `is_default` BOOLEAN NOT NULL DEFAULT FALSE,
  `is_active` BOOLEAN NOT NULL DEFAULT TRUE,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1.2 Insert default scoring rules
INSERT INTO `contest_scoring_rules` (`id`, `name`, `description`, `base_score_per_problem`, `time_bonus_per_minute`, `wrong_answer_penalty`, `first_solve_bonus`, `is_default`) VALUES
('default-weekly', '标准周赛规则', 'LeetCode 风格的简单积分规则', 100, 1, 5, 10, TRUE),
('default-icpc', 'ICPC 规则', 'ACM/ICPC 风格的罚时规则', 100, 0, 20, 0, FALSE);

-- 1.3 Create first_solve_records table
CREATE TABLE IF NOT EXISTS `first_solve_records` (
  `id` VARCHAR(40) NOT NULL,
  `contest_id` VARCHAR(40) NOT NULL,
  `problem_id` BIGINT NOT NULL,
  `user_id` VARCHAR(40) NOT NULL,
  `solved_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `time_spent` INT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `first_solve_records_contest_id_problem_id_key` (`contest_id`, `problem_id`),
  KEY `first_solve_records_contest_id_idx` (`contest_id`),
  KEY `first_solve_records_user_id_idx` (`user_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1.4 Create contest_announcements table
CREATE TABLE IF NOT EXISTS `contest_announcements` (
  `id` VARCHAR(40) NOT NULL,
  `contest_id` VARCHAR(40) NOT NULL,
  `title` VARCHAR(200) NOT NULL,
  `content` TEXT NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `is_pinned` BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (`id`),
  KEY `contest_announcements_contest_id_created_at_idx` (`contest_id`, `created_at`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 1.5 Create contest_analytics table
CREATE TABLE IF NOT EXISTS `contest_analytics` (
  `id` VARCHAR(40) NOT NULL,
  `contest_id` VARCHAR(40) NOT NULL,
  `total_registered` INT NOT NULL DEFAULT 0,
  `total_participated` INT NOT NULL DEFAULT 0,
  `completion_rate` DOUBLE NOT NULL DEFAULT 0,
  `problem_stats` JSON NULL,
  `score_distribution` JSON NULL,
  `time_distribution` JSON NULL,
  `top_users` JSON NULL,
  `generated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `contest_analytics_contest_id_key` (`contest_id`)
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================
-- Phase 2: Extend existing tables
-- ============================================

-- 2.1 Extend contests table
ALTER TABLE `contests`
  ADD COLUMN `end_time` DATETIME(3) NULL,
  ADD COLUMN `registration_start` DATETIME(3) NULL,
  ADD COLUMN `registration_end` DATETIME(3) NULL,
  ADD COLUMN `freeze_time` DATETIME(3) NULL,
  ADD COLUMN `is_virtual` BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN `max_participants` INT NULL,
  ADD COLUMN `scoring_rule_id` VARCHAR(40) NULL,
  ADD COLUMN `submission_count` INT NULL DEFAULT 0;

-- 2.2 Calculate and populate end_time for existing contests
UPDATE `contests`
SET `end_time` = DATE_ADD(`start_time`, INTERVAL `duration_minutes` MINUTE)
WHERE `end_time` IS NULL AND `start_time` IS NOT NULL AND `duration_minutes` IS NOT NULL;

-- 2.3 Link existing contests to default scoring rule
UPDATE `contests`
SET `scoring_rule_id` = 'default-weekly'
WHERE `scoring_rule_id` IS NULL;

-- 2.4 Extend contest_participants table
ALTER TABLE `contest_participants`
  ADD COLUMN `checked_in_at` DATETIME(3) NULL,
  ADD COLUMN `is_virtual` BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN `total_score` INT NOT NULL DEFAULT 0,
  ADD COLUMN `total_time` INT NOT NULL DEFAULT 0,
  ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 0;

-- 2.5 Extend contest_problems table
ALTER TABLE `contest_problems`
  ADD COLUMN `label` VARCHAR(10) NULL,
  ADD COLUMN `base_score` INT NULL,
  ADD COLUMN `time_bonus` INT NULL;

-- 2.6 Populate label from problem_index
UPDATE `contest_problems`
SET `label` = `problem_index`
WHERE `label` IS NULL;

-- 2.7 Extend contest_problem_results table
ALTER TABLE `contest_problem_results`
  ADD COLUMN `time_spent` INT NOT NULL DEFAULT 0,
  ADD COLUMN `time_bonus` INT NOT NULL DEFAULT 0,
  ADD COLUMN `is_first_solve` BOOLEAN NOT NULL DEFAULT FALSE;

-- 2.8 Extend contest_rankings table
ALTER TABLE `contest_rankings`
  ADD COLUMN `is_frozen` BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================
-- Phase 3: Add foreign key constraints
-- ============================================

-- 3.1 Add foreign key for scoring_rule_id
ALTER TABLE `contests`
  ADD CONSTRAINT `contests_scoring_rule_id_fkey`
  FOREIGN KEY (`scoring_rule_id`) REFERENCES `contest_scoring_rules`(`id`)
  ON DELETE SET NULL ON UPDATE CASCADE;

-- 3.2 Add foreign keys for first_solve_records
ALTER TABLE `first_solve_records`
  ADD CONSTRAINT `first_solve_records_contest_id_fkey`
  FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `first_solve_records_user_id_fkey`
  FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  ADD CONSTRAINT `first_solve_records_problem_id_fkey`
  FOREIGN KEY (`problem_id`) REFERENCES `problems`(`id`) ON DELETE CASCADE;

-- 3.3 Add foreign keys for contest_announcements
ALTER TABLE `contest_announcements`
  ADD CONSTRAINT `contest_announcements_contest_id_fkey`
  FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;

-- 3.4 Add foreign key for contest_analytics
ALTER TABLE `contest_analytics`
  ADD CONSTRAINT `contest_analytics_contest_id_fkey`
  FOREIGN KEY (`contest_id`) REFERENCES `contests`(`id`) ON DELETE CASCADE ON UPDATE CASCADE;