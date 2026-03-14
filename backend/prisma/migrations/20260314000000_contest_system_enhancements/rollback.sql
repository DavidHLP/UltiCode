-- ============================================
-- Phase 3: Remove foreign key constraints (reverse order)
-- ============================================

-- 3.4 Remove constraint for contest_analytics
ALTER TABLE `contest_analytics` DROP FOREIGN KEY `contest_analytics_contest_id_fkey`;

-- 3.3 Remove constraint for contest_announcements
ALTER TABLE `contest_announcements` DROP FOREIGN KEY `contest_announcements_contest_id_fkey`;

-- 3.2 Remove constraints for first_solve_records
ALTER TABLE `first_solve_records` DROP FOREIGN KEY `first_solve_records_user_id_fkey`;
ALTER TABLE `first_solve_records` DROP FOREIGN KEY `first_solve_records_contest_id_fkey`;

-- 3.1 Remove constraint for contests
ALTER TABLE `contests` DROP FOREIGN KEY `contests_scoring_rule_id_fkey`;

-- ============================================
-- Phase 2: Drop new columns from extended tables
-- ============================================

-- 2.8 Drop new column from contest_rankings
ALTER TABLE `contest_rankings` DROP COLUMN `is_frozen`;

-- 2.7 Drop new columns from contest_problem_results
ALTER TABLE `contest_problem_results` DROP COLUMN `is_first_solve`, DROP COLUMN `time_bonus`, DROP COLUMN `time_spent`;

-- 2.6 Revert label update (drop the column, will be recreated in reverse migration if needed)
ALTER TABLE `contest_problems` DROP COLUMN `label`;

-- 2.5 Drop new columns from contest_problems
ALTER TABLE `contest_problems` DROP COLUMN `time_bonus`, DROP COLUMN `base_score`;

-- 2.4 Drop new columns from contest_participants
ALTER TABLE `contest_participants` DROP COLUMN `attempt_count`, DROP COLUMN `total_time`, DROP COLUMN `total_score`, DROP COLUMN `is_virtual`, DROP COLUMN `checked_in_at`;

-- 2.3 Reset scoring_rule_id for existing contests
UPDATE `contests` SET `scoring_rule_id` = NULL WHERE `scoring_rule_id` = 'default-weekly';

-- 2.2 Remove end_time calculation (drop the column)
ALTER TABLE `contests` DROP COLUMN `end_time`;

-- 2.1 Drop new columns from contests
ALTER TABLE `contests` DROP COLUMN `submission_count`, DROP COLUMN `scoring_rule_id`, DROP COLUMN `max_participants`, DROP COLUMN `is_virtual`, DROP COLUMN `freeze_time`, DROP COLUMN `registration_end`, DROP COLUMN `registration_start`;

-- ============================================
-- Phase 1: Drop new tables
-- ============================================

-- 1.5 Drop contest_analytics table
DROP TABLE IF EXISTS `contest_analytics`;

-- 1.4 Drop contest_announcements table
DROP TABLE IF EXISTS `contest_announcements`;

-- 1.3 Drop first_solve_records table
DROP TABLE IF EXISTS `first_solve_records`;

-- 1.2 Remove default scoring rules
DELETE FROM `contest_scoring_rules` WHERE `id` IN ('default-weekly', 'default-icpc');

-- 1.1 Drop contest_scoring_rules table
DROP TABLE IF EXISTS `contest_scoring_rules`;