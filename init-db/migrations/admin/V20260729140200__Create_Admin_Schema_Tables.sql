-- V20260729140200__Create_Admin_Schema_Tables.sql
-- Admin Owner Schema DDL (P5-SCHEMA-001)

CREATE TABLE IF NOT EXISTS `audit_logs` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(60) NOT NULL,
  `resource_type` varchar(60) NOT NULL,
  `resource_id` varchar(60) DEFAULT NULL,
  `details` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `audit_outbox` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(60) NOT NULL,
  `resource_type` varchar(60) NOT NULL,
  `resource_id` varchar(60) DEFAULT NULL,
  `details` text,
  `status` enum('PENDING','PROCESSED','FAILED') NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `system_settings` (
  `key` varchar(50) NOT NULL,
  `value` text NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `moderation_queue` (
  `id` varchar(40) NOT NULL,
  `entity_type` varchar(50) NOT NULL,
  `entity_id` varchar(50) NOT NULL,
  `status` enum('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `moderation_actions` (
  `id` varchar(40) NOT NULL,
  `queue_id` varchar(40) NOT NULL,
  `action` enum('DELETED','HIDDEN','RESTORED','WARNED','TEMP_BANNED','PERM_BANNED','DISMISSED','RESOLVED','APPEAL_PENDING','APPEAL_APPROVED','APPEAL_REJECTED') NOT NULL,
  `moderator_id` varchar(40) NOT NULL,
  `reason` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_warnings` (
  `id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `queue_id` varchar(40) DEFAULT NULL,
  `reason` text NOT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
