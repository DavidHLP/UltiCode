-- V20260729180000__Create_Consumer_Inbox.sql
-- P6-INBOX-001: Consumer inbox for cross-service event dedup
-- Matches the integration_outbox pattern from P6-OUTBOX-001

CREATE TABLE IF NOT EXISTS `consumer_inbox` (
  `id`              varchar(40)  NOT NULL COMMENT 'Inbox row ID (UUID)',
  `consumer`        varchar(40)  NOT NULL COMMENT 'Consumer name (e.g., App, Admin, Auth)',
  `event_id`        varchar(40)  NOT NULL COMMENT 'Event ID from integration_outbox',
  `event_type`      varchar(120) NOT NULL,
  `payload`         json         NOT NULL,
  `state`           varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/PROCESSED/DEAD',
  `attempts`        int          NOT NULL DEFAULT 0,
  `last_error`      text         DEFAULT NULL,
  `lease_owner`     varchar(80)  DEFAULT NULL COMMENT 'PID/hostname that holds the lease',
  `lease_expires_at` datetime(3) DEFAULT NULL,
  `created_at`      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at`    datetime(3)  DEFAULT NULL,
  `next_retry_at`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_consumer_event` (`consumer`, `event_id`),
  KEY `idx_inbox_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
