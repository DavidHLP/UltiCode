-- Moderation System Tables
-- This migration adds the moderation queue, reports, warnings, bans, and appeals tables

-- Reports table - individual reports from users
CREATE TABLE `reports` (
  `id` VARCHAR(40) NOT NULL,
  `reporter_id` VARCHAR(40) NOT NULL,
  `entity_type` VARCHAR(50) NOT NULL,
  `entity_id` VARCHAR(50) NOT NULL,
  `category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE', 'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER', 'COPYRIGHT', 'OTHER') NOT NULL,
  `reason` TEXT,
  `evidence` TEXT,
  `status` ENUM('PENDING', 'REVIEWED', 'RESOLVED', 'DISMISSED') NOT NULL DEFAULT 'PENDING',
  `queue_id` VARCHAR(40),
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL,

  PRIMARY KEY (`id`),
  INDEX `reports_entity_type_entity_id_idx` (`entity_type`, `entity_id`),
  INDEX `reports_reporter_id_idx` (`reporter_id`),
  INDEX `reports_status_idx` (`status`),
  INDEX `reports_category_idx` (`category`),
  CONSTRAINT `reports_reporter_id_fkey` FOREIGN KEY (`reporter_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Moderation queue table - aggregated items for moderators to review
CREATE TABLE `moderation_queue` (
  `id` VARCHAR(40) NOT NULL,
  `entity_type` VARCHAR(50) NOT NULL,
  `entity_id` VARCHAR(50) NOT NULL,
  `author_id` VARCHAR(40) NOT NULL,
  `priority` INT NOT NULL DEFAULT 0,
  `status` ENUM('PENDING', 'UNDER_REVIEW', 'RESOLVED', 'DISMISSED', 'APPEAL_PENDING') NOT NULL DEFAULT 'PENDING',
  `report_count` INT NOT NULL DEFAULT 0,
  `primary_category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE', 'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER', 'COPYRIGHT', 'OTHER'),
  `assigned_to_id` VARCHAR(40),
  `assigned_at` DATETIME(3),
  `reviewed_by_id` VARCHAR(40),
  `reviewed_at` DATETIME(3),
  `resolution` ENUM('DELETED', 'HIDDEN', 'RESTORED', 'WARNED', 'TEMP_BANNED', 'PERM_BANNED', 'DISMISSED', 'RESOLVED', 'APPEAL_PENDING', 'APPEAL_APPROVED'),
  `resolution_note` TEXT,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL,
  `resolved_at` DATETIME(3),

  PRIMARY KEY (`id`),
  UNIQUE INDEX `moderation_queue_entity_type_entity_id_key` (`entity_type`, `entity_id`),
  INDEX `moderation_queue_status_idx` (`status`),
  INDEX `moderation_queue_assigned_to_id_idx` (`assigned_to_id`),
  INDEX `moderation_queue_priority_idx` (`priority`),
  INDEX `moderation_queue_author_id_idx` (`author_id`),
  CONSTRAINT `moderation_queue_author_id_fkey` FOREIGN KEY (`author_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `moderation_queue_assigned_to_id_fkey` FOREIGN KEY (`assigned_to_id`) REFERENCES `users`(`id`) ON DELETE SET NULL,
  CONSTRAINT `moderation_queue_reviewed_by_id_fkey` FOREIGN KEY (`reviewed_by_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Moderation actions table - log of all moderation actions taken
CREATE TABLE `moderation_actions` (
  `id` VARCHAR(40) NOT NULL,
  `queue_id` VARCHAR(40) NOT NULL,
  `action` ENUM('DELETED', 'HIDDEN', 'RESTORED', 'WARNED', 'TEMP_BANNED', 'PERM_BANNED', 'DISMISSED', 'RESOLVED', 'APPEAL_PENDING', 'APPEAL_APPROVED') NOT NULL,
  `performed_by_id` VARCHAR(40) NOT NULL,
  `note` TEXT,
  `duration_days` INT,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  PRIMARY KEY (`id`),
  INDEX `moderation_actions_queue_id_idx` (`queue_id`),
  INDEX `moderation_actions_performed_by_id_idx` (`performed_by_id`),
  INDEX `moderation_actions_action_idx` (`action`),
  CONSTRAINT `moderation_actions_queue_id_fkey` FOREIGN KEY (`queue_id`) REFERENCES `moderation_queue`(`id`) ON DELETE CASCADE,
  CONSTRAINT `moderation_actions_performed_by_id_fkey` FOREIGN KEY (`performed_by_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- User warnings table
CREATE TABLE `user_warnings` (
  `id` VARCHAR(40) NOT NULL,
  `user_id` VARCHAR(40) NOT NULL,
  `queue_id` VARCHAR(40),
  `action_id` VARCHAR(40),
  `reason` TEXT NOT NULL,
  `category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE', 'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER', 'COPYRIGHT', 'OTHER') NOT NULL,
  `acknowledged_at` DATETIME(3),
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `expires_at` DATETIME(3),

  PRIMARY KEY (`id`),
  INDEX `user_warnings_user_id_idx` (`user_id`),
  INDEX `user_warnings_created_at_idx` (`created_at`),
  CONSTRAINT `user_warnings_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- User bans table
CREATE TABLE `user_bans` (
  `id` VARCHAR(40) NOT NULL,
  `user_id` VARCHAR(40) NOT NULL,
  `is_permanent` BOOLEAN NOT NULL DEFAULT FALSE,
  `reason` TEXT NOT NULL,
  `category` ENUM('SPAM', 'HARASSMENT', 'HATE_SPEECH', 'VIOLENCE', 'SEXUAL_CONTENT', 'MISINFORMATION', 'WRONG_ANSWER', 'COPYRIGHT', 'OTHER'),
  `queue_id` VARCHAR(40),
  `action_id` VARCHAR(40),
  `banned_by_id` VARCHAR(40) NOT NULL,
  `started_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `ends_at` DATETIME(3),
  `unbanned_at` DATETIME(3),
  `unbanned_by_id` VARCHAR(40),
  `unban_reason` TEXT,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL,

  PRIMARY KEY (`id`),
  INDEX `user_bans_user_id_idx` (`user_id`),
  INDEX `user_bans_ends_at_idx` (`ends_at`),
  CONSTRAINT `user_bans_user_id_fkey` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `user_bans_banned_by_id_fkey` FOREIGN KEY (`banned_by_id`) REFERENCES `users`(`id`) ON DELETE RESTRICT,
  CONSTRAINT `user_bans_unbanned_by_id_fkey` FOREIGN KEY (`unbanned_by_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Appeals table
CREATE TABLE `appeals` (
  `id` VARCHAR(40) NOT NULL,
  `queue_id` VARCHAR(40) NOT NULL,
  `appellant_id` VARCHAR(40) NOT NULL,
  `reason` TEXT NOT NULL,
  `evidence` TEXT,
  `status` ENUM('PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'ESCALATED') NOT NULL DEFAULT 'PENDING',
  `reviewed_by_id` VARCHAR(40),
  `reviewed_at` DATETIME(3),
  `response` TEXT,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL,

  PRIMARY KEY (`id`),
  INDEX `appeals_queue_id_idx` (`queue_id`),
  INDEX `appeals_appellant_id_idx` (`appellant_id`),
  INDEX `appeals_status_idx` (`status`),
  CONSTRAINT `appeals_queue_id_fkey` FOREIGN KEY (`queue_id`) REFERENCES `moderation_queue`(`id`) ON DELETE CASCADE,
  CONSTRAINT `appeals_appellant_id_fkey` FOREIGN KEY (`appellant_id`) REFERENCES `users`(`id`) ON DELETE CASCADE,
  CONSTRAINT `appeals_reviewed_by_id_fkey` FOREIGN KEY (`reviewed_by_id`) REFERENCES `users`(`id`) ON DELETE SET NULL
) DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Add foreign key from reports to moderation_queue
ALTER TABLE `reports` ADD CONSTRAINT `reports_queue_id_fkey` FOREIGN KEY (`queue_id`) REFERENCES `moderation_queue`(`id`) ON DELETE SET NULL;