SET FOREIGN_KEY_CHECKS=0;
-- UltiCode Migration: V5__subscription_schema
-- Generated from ulticode.sql
-- Tables: 1

CREATE TABLE `subscriptions` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `plan` enum('FREE','PREMIUM_MONTHLY','PREMIUM_YEARLY') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'FREE',
  `status` enum('ACTIVE','CANCELLED','EXPIRED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `started_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` datetime(3) DEFAULT NULL,
  `cancelled_at` datetime(3) DEFAULT NULL,
  `transaction_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Payment transaction ID',
  `auto_renew` tinyint(1) NOT NULL DEFAULT '1' COMMENT 'Auto-renewal flag',
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Soft delete flag',
  `deleted_at` datetime(3) DEFAULT NULL COMMENT 'Soft delete timestamp',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `subscriptions_user_id_idx` (`user_id`),
  KEY `subscriptions_status_idx` (`status`),
  KEY `subscriptions_is_deleted_idx` (`is_deleted`),
  CONSTRAINT `subscriptions_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
);

SET FOREIGN_KEY_CHECKS=1;
