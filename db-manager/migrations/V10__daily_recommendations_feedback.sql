SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V10__daily_recommendations_feedback
-- Add feedback tracking fields to DailyRecommendation table
--
-- MySQL 8+ syntax: DATETIME DEFAULT (expr) requires GENERATED ALWAYS AS for
-- expression defaults. VIRTUAL generated columns cannot be indexed, so we use
-- STORED. The NOT NULL constraint is removed since generated columns are
-- implicitly nullable (but the COMMENT documents the business intent).

ALTER TABLE `DailyRecommendation`
  ADD COLUMN `expires_at` DATETIME(3) GENERATED ALWAYS AS (DATE_ADD(NOW(), INTERVAL 1 DAY)) STORED COMMENT '推荐过期时间',
  ADD COLUMN `is_clicked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户是否点击',
  ADD COLUMN `is_solved` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户是否完成';

-- Add index for efficient cleanup and querying
CREATE INDEX `idx_expires_at` ON `DailyRecommendation` (`expires_at`);
CREATE INDEX `idx_user_clicked` ON `DailyRecommendation` (`user_id`, `is_clicked`);

SET FOREIGN_KEY_CHECKS=1;
