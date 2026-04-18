SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V10__daily_recommendations_feedback
-- Add feedback tracking fields to DailyRecommendation table
--
-- MySQL 9 does not allow NOW() or DATE_ADD(NOW(), ...) in DEFAULT or GENERATED
-- COLUMN expressions (Error 3763). Instead we use a default of 0 (Unix epoch)
-- and require the application layer to set expires_at = NOW() + 1 day at insert time.

ALTER TABLE `DailyRecommendation`
  ADD COLUMN `expires_at` DATETIME(3) DEFAULT 0 COMMENT '推荐过期时间，插入时由应用层设置为 NOW() + 1 day',
  ADD COLUMN `is_clicked` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户是否点击',
  ADD COLUMN `is_solved` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '用户是否完成';

-- Add index for efficient cleanup and querying
CREATE INDEX `idx_expires_at` ON `DailyRecommendation` (`expires_at`);
CREATE INDEX `idx_user_clicked` ON `DailyRecommendation` (`user_id`, `is_clicked`);

SET FOREIGN_KEY_CHECKS=1;
