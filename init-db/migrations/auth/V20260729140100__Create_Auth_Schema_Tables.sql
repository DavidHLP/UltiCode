-- V20260729140100__Create_Auth_Schema_Tables.sql
-- Auth Owner Schema DDL (P5-SCHEMA-001)

CREATE TABLE IF NOT EXISTS `users` (
  `id` varchar(40) NOT NULL,
  `username` varchar(120) NOT NULL,
  `name` varchar(120) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL,
  `password` varchar(255) DEFAULT NULL,
  `bio` text,
  `company` varchar(255) DEFAULT NULL,
  `github` varchar(255) DEFAULT NULL,
  `joined_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `location` varchar(255) DEFAULT NULL,
  `twitter` varchar(255) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `preferred_language` varchar(50) DEFAULT NULL,
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL DEFAULT 'USER',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `is_banned` tinyint(1) NOT NULL DEFAULT '0',
  `banned_until` datetime(3) DEFAULT NULL,
  `banned_reason` text,
  `last_login_at` datetime(3) DEFAULT NULL,
  `created_by` varchar(40) DEFAULT NULL,
  `updated_by` varchar(40) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `password_reset_token_hash` varchar(255) DEFAULT NULL,
  `password_reset_expires_at` datetime(3) DEFAULT NULL,
  `authz_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `token` varchar(255) NOT NULL,
  `token_hash` varchar(255) DEFAULT NULL,
  `family_id` varchar(40) DEFAULT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `revoked_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_refresh_tokens_user_id` (`user_id`),
  KEY `idx_refresh_tokens_token_hash` (`token_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `password_resets` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `token` varchar(255) NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `used_at` datetime(3) DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `role_permissions` (
  `id` varchar(40) NOT NULL,
  `role` enum('USER','MODERATOR','ADMIN','SUPER_ADMIN') NOT NULL,
  `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') NOT NULL,
  `resource` enum('PROBLEM','CONTEST','SUBMISSION','SOLUTION','FORUM','USER','SYSTEM','AUDIT','COMMUNITY','ALL') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_permissions` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `action` enum('CREATE','READ','UPDATE','DELETE','MODERATE','PUBLISH','MANAGE_USERS','MANAGE_PERMISSIONS') NOT NULL,
  `resource` enum('PROBLEM','CONTEST','SUBMISSION','SOLUTION','FORUM','USER','SYSTEM','AUDIT','COMMUNITY','ALL') NOT NULL,
  `expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `oauth_provider_identities` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `provider` varchar(50) NOT NULL,
  `provider_user_id` varchar(255) NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_user` (`provider`, `provider_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
