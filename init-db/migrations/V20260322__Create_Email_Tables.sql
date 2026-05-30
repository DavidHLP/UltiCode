-- Migration:
--   V20260322__Create_Email_Tables.sql
--
-- Purpose:
--   Create email_templates and email_logs tables for email module.
--
-- Risk:
--   Low. Creating new tables only. No data loss risk.
--
-- Compatibility:
--   Compatible with old application versions. No dependencies on existing schema.
--
-- Rollback:
--   DROP TABLE IF EXISTS `email_logs`;
--   DROP TABLE IF EXISTS `email_templates`;
--
-- Verify:
--   DESC email_templates;
--   DESC email_logs;
--   SHOW CREATE TABLE email_templates;
--   SHOW CREATE TABLE email_logs;

-- Email module tables
-- Creates email_templates and email_logs tables

CREATE TABLE IF NOT EXISTS `email_templates` (
    `id` VARCHAR(36) NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `body` TEXT NOT NULL,
    `variables` JSON,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_email_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `email_logs` (
    `id` VARCHAR(36) NOT NULL,
    `template_id` VARCHAR(36),
    `recipient` VARCHAR(255) NOT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `sent_at` DATETIME,
    `error` TEXT,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_email_log_template_id` (`template_id`),
    KEY `idx_email_log_recipient` (`recipient`),
    KEY `idx_email_log_status` (`status`),
    KEY `idx_email_log_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;