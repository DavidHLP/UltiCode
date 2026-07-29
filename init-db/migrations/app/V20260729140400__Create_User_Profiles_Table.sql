-- V20260729140400__Create_User_Profiles_Table.sql
-- App Owner Schema DDL (P5-USERPROFILE-001)
-- Target-state user_profiles table in app schema for independent service deployment

CREATE TABLE IF NOT EXISTS `user_profiles` (
  `account_id` varchar(40) NOT NULL COMMENT 'FK to auth.users.id',
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
