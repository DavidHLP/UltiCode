-- V20260729150000__Create_User_Profiles_Table.sql
-- P5-USERPROFILE-001: Vertical split of users into account + profile
-- Expand phase: create user_profiles table and backfill from users

CREATE TABLE IF NOT EXISTS `user_profiles` (
  `account_id` varchar(40) NOT NULL COMMENT 'FK to users.id',
  `name` varchar(120) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `bio` text,
  `company` varchar(255) DEFAULT NULL,
  `github` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `twitter` varchar(255) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `preferred_language` varchar(50) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Backfill: copy profile columns from users into user_profiles
-- ON DUPLICATE KEY UPDATE makes this idempotent (safe to re-run)
INSERT INTO `user_profiles` (`account_id`, `name`, `avatar`, `bio`, `company`, `github`, `location`, `twitter`, `website`, `preferred_language`)
SELECT `id`, `name`, `avatar`, `bio`, `company`, `github`, `location`, `twitter`, `website`, `preferred_language`
FROM `users`
WHERE `is_deleted` = 0
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `avatar` = VALUES(`avatar`),
  `bio` = VALUES(`bio`),
  `company` = VALUES(`company`),
  `github` = VALUES(`github`),
  `location` = VALUES(`location`),
  `twitter` = VALUES(`twitter`),
  `website` = VALUES(`website`),
  `preferred_language` = VALUES(`preferred_language`);
