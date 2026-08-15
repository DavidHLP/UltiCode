-- NOTIFY-006: target-state Notification owner tables.
--
-- These are final-shape tables for clean databases. Existing deployments use
-- scripts/dev/notification-schema-cutover.sh to preflight, reconcile, copy,
-- and later revoke the old App write grants before switching DB_NAME.

CREATE TABLE IF NOT EXISTS `notifications` (
  `id`              VARCHAR(40)  NOT NULL,
  `user_id`         VARCHAR(40)  NOT NULL,
  `type`            ENUM('COMMENT','REPLY','MENTION','UPVOTE','FOLLOW','SYSTEM',
                         'SUBMISSION','CONTEST','CONTEST_REMINDER','ACHIEVEMENT') NOT NULL,
  `category`        ENUM('COMMUNICATION','MARKETING','SECURITY','SYSTEM') NOT NULL,
  `title`           VARCHAR(255) NOT NULL,
  `body`            TEXT         NOT NULL,
  `link`            VARCHAR(255) DEFAULT NULL,
  `metadata`        JSON         DEFAULT NULL,
  `announcement_id` VARCHAR(64)  DEFAULT NULL,
  `is_read`         TINYINT(1)   NOT NULL DEFAULT 0,
  `read_at`         DATETIME(3)  DEFAULT NULL,
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `is_deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_read_created` (`user_id`, `is_read`, `created_at`),
  KEY `idx_notifications_user_type` (`user_id`, `type`),
  KEY `idx_notifications_user_category` (`user_id`, `category`),
  KEY `idx_notifications_announcement` (`announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `notification_preferences` (
  `id`             VARCHAR(40) NOT NULL,
  `user_id`        VARCHAR(40) NOT NULL,
  `communication`  TINYINT(1)  NOT NULL DEFAULT 1,
  `marketing`      TINYINT(1)  NOT NULL DEFAULT 0,
  `security`       TINYINT(1)  NOT NULL DEFAULT 1,
  `system_enabled` TINYINT(1)  NOT NULL DEFAULT 1,
  `created_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_preferences_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `notification_delivery_ledger` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `intent_id`       VARCHAR(255)    NOT NULL,
  `channel_id`      VARCHAR(32)     NOT NULL,
  `user_id`         VARCHAR(40)     NOT NULL,
  `intent_type`     VARCHAR(64)     NOT NULL,
  `delivery_state`  VARCHAR(16)     NOT NULL,
  `failure_reason`  VARCHAR(500)    DEFAULT NULL,
  `reclaim_attempts` INT             NOT NULL DEFAULT 0,
  `delivered_at`    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at`      DATETIME(3)     DEFAULT NULL,
  `claim_owner`     VARCHAR(80)     DEFAULT NULL,
  `updated_at`      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_ledger_intent_channel` (`intent_id`, `channel_id`),
  KEY `idx_notification_ledger_user_time` (`user_id`, `delivered_at`),
  KEY `idx_notification_ledger_state` (`delivery_state`, `delivered_at`),
  KEY `idx_notification_ledger_claim` (`delivery_state`, `claimed_at`, `claim_owner`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `email_templates` (
  `id`         VARCHAR(36)  NOT NULL,
  `name`       VARCHAR(100) NOT NULL,
  `subject`    VARCHAR(255) NOT NULL,
  `body`       TEXT         NOT NULL,
  `variables`  JSON         DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_template_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `email_logs` (
  `id`          VARCHAR(36)  NOT NULL,
  `template_id` VARCHAR(36)  DEFAULT NULL,
  `recipient`   VARCHAR(255) NOT NULL,
  `subject`     VARCHAR(255) NOT NULL,
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  `sent_at`     DATETIME     DEFAULT NULL,
  `error`       TEXT         DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_email_log_template` (`template_id`),
  KEY `idx_email_log_recipient` (`recipient`),
  KEY `idx_email_log_status` (`status`),
  KEY `idx_email_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `consumer_inbox` (
  `id`               VARCHAR(40)  NOT NULL,
  `consumer`         VARCHAR(40)  NOT NULL,
  `event_id`         VARCHAR(40)  NOT NULL,
  `event_type`       VARCHAR(120) NOT NULL,
  `payload`          JSON         NOT NULL,
  `state`            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  `attempts`         INT          NOT NULL DEFAULT 0,
  `last_error`       TEXT         DEFAULT NULL,
  `lease_owner`      VARCHAR(80)  DEFAULT NULL,
  `lease_expires_at` DATETIME(3)  DEFAULT NULL,
  `created_at`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at`     DATETIME(3)  DEFAULT NULL,
  `next_retry_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_inbox_consumer_event` (`consumer`, `event_id`),
  KEY `idx_notification_inbox_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `notification_command_receipt` (
  `id`                  VARCHAR(40)  NOT NULL,
  `command_id`          VARCHAR(40)  NOT NULL,
  `service`             VARCHAR(80)  NOT NULL,
  `operation`           VARCHAR(80)  NOT NULL,
  `idempotency_key`     VARCHAR(120) NOT NULL,
  `request_fingerprint` VARCHAR(64)  DEFAULT NULL,
  `status`              VARCHAR(20)  NOT NULL,
  `error_code`          VARCHAR(80)  DEFAULT NULL,
  `result_payload`      JSON         DEFAULT NULL,
  `actor_type`          VARCHAR(30)  DEFAULT NULL,
  `actor_id`            VARCHAR(40)  DEFAULT NULL,
  `trace_id`            VARCHAR(80)  DEFAULT NULL,
  `created_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_command_receipt` (`service`, `operation`, `idempotency_key`),
  KEY `idx_notification_command_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
