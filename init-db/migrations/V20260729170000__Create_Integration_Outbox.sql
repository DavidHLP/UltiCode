-- V20260729170000__Create_Integration_Outbox.sql
-- P6-OUTBOX-001: Integration outbox for durable cross-service events
-- Follows the existing judge_outbox/audit_outbox pattern with richer event envelope

CREATE TABLE IF NOT EXISTS `integration_outbox` (
  `event_id`           varchar(40)  NOT NULL COMMENT 'Unique event identifier (UUID)',
  `owner`              varchar(20)  NOT NULL COMMENT 'Publishing Owner: Auth/Admin/App',
  `aggregate_id`       varchar(120) NOT NULL COMMENT 'Root aggregate identifier',
  `aggregate_version`  bigint       NOT NULL DEFAULT 0 COMMENT 'Aggregate version for ordering',
  `causation_id`       varchar(40)  DEFAULT NULL COMMENT 'Causation event ID (saga chaining)',
  `trace_id`           varchar(40)  DEFAULT NULL COMMENT 'OpenTelemetry trace ID',
  `event_type`         varchar(120) NOT NULL COMMENT 'Domain event type (e.g., UserRegistered)',
  `schema_version`     int          NOT NULL DEFAULT 1 COMMENT 'Payload schema version',
  `payload`            json         NOT NULL COMMENT 'Event payload as JSON',
  `state`              varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLAIMED/DELIVERED/DEAD',
  `attempts`           int          NOT NULL DEFAULT 0,
  `last_error`         text         DEFAULT NULL,
  `stream_id`          varchar(80)  DEFAULT NULL COMMENT 'Redis Streams XADD return ID',
  `created_at`         datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at`         datetime(3)  DEFAULT NULL,
  `delivered_at`       datetime(3)  DEFAULT NULL,
  `next_retry_at`      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`event_id`),
  KEY `idx_outbox_state_retry` (`state`, `next_retry_at`),
  KEY `idx_outbox_aggregate` (`aggregate_id`, `aggregate_version`),
  KEY `idx_outbox_owner_type` (`owner`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
