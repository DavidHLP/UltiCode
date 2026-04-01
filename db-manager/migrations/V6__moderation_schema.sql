SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V6__moderation_schema
-- Generated from ulticode.sql
-- Tables: 3

CREATE TABLE `appeals` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `queue_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `appellant_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `evidence` text COLLATE utf8mb4_unicode_ci,
  `status` enum('PENDING','UNDER_REVIEW','APPROVED','REJECTED','ESCALATED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `reviewed_by_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `response` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `appeals_queue_id_idx` (`queue_id`),
  KEY `appeals_appellant_id_idx` (`appellant_id`),
  KEY `appeals_status_idx` (`status`),
  KEY `appeals_reviewed_by_id_fkey` (`reviewed_by_id`),
  CONSTRAINT `appeals_appellant_id_fkey` FOREIGN KEY (`appellant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `appeals_queue_id_fkey` FOREIGN KEY (`queue_id`) REFERENCES `moderation_queue` (`id`) ON DELETE CASCADE,
  CONSTRAINT `appeals_reviewed_by_id_fkey` FOREIGN KEY (`reviewed_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
);

CREATE TABLE `moderation_actions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `queue_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` enum('DELETED','HIDDEN','RESTORED','WARNED','TEMP_BANNED','PERM_BANNED','DISMISSED','RESOLVED','APPEAL_PENDING','APPEAL_APPROVED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `performed_by_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` text COLLATE utf8mb4_unicode_ci,
  `duration_days` int DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `moderation_actions_queue_id_idx` (`queue_id`),
  KEY `moderation_actions_performed_by_id_idx` (`performed_by_id`),
  KEY `moderation_actions_action_idx` (`action`),
  CONSTRAINT `moderation_actions_performed_by_id_fkey` FOREIGN KEY (`performed_by_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `moderation_actions_queue_id_fkey` FOREIGN KEY (`queue_id`) REFERENCES `moderation_queue` (`id`) ON DELETE CASCADE
);

CREATE TABLE `moderation_queue` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `author_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `status` enum('PENDING','UNDER_REVIEW','RESOLVED','DISMISSED','APPEAL_PENDING') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `report_count` int NOT NULL DEFAULT '0',
  `primary_category` enum('SPAM','HARASSMENT','HATE_SPEECH','VIOLENCE','SEXUAL_CONTENT','MISINFORMATION','WRONG_ANSWER','COPYRIGHT','OTHER') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_to_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assigned_at` datetime(3) DEFAULT NULL,
  `reviewed_by_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reviewed_at` datetime(3) DEFAULT NULL,
  `resolution` enum('DELETED','HIDDEN','RESTORED','WARNED','TEMP_BANNED','PERM_BANNED','DISMISSED','RESOLVED','APPEAL_PENDING','APPEAL_APPROVED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolution_note` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  `resolved_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `moderation_queue_entity_type_entity_id_key` (`entity_type`,`entity_id`),
  KEY `moderation_queue_status_idx` (`status`),
  KEY `moderation_queue_assigned_to_id_idx` (`assigned_to_id`),
  KEY `moderation_queue_priority_idx` (`priority`),
  KEY `moderation_queue_author_id_idx` (`author_id`),
  KEY `moderation_queue_reviewed_by_id_fkey` (`reviewed_by_id`),
  CONSTRAINT `moderation_queue_assigned_to_id_fkey` FOREIGN KEY (`assigned_to_id`) REFERENCES `users` (`id`) ON DELETE SET NULL,
  CONSTRAINT `moderation_queue_author_id_fkey` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `moderation_queue_reviewed_by_id_fkey` FOREIGN KEY (`reviewed_by_id`) REFERENCES `users` (`id`) ON DELETE SET NULL
);

SET FOREIGN_KEY_CHECKS=1;
