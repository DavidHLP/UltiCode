-- Audit Outbox table for intra-JVM transaction-bound audit log dispatch (P3-AUDIT-001)
CREATE TABLE IF NOT EXISTS `audit_outbox` (
  `id`           VARCHAR(40)  NOT NULL,
  `performer_id` VARCHAR(40)  DEFAULT NULL,
  `user_id`      VARCHAR(40)  DEFAULT NULL,
  `action`       VARCHAR(64)  NOT NULL,
  `entity_type`  VARCHAR(64)  NOT NULL,
  `entity_id`    VARCHAR(64)  DEFAULT NULL,
  `old_values`   JSON         DEFAULT NULL,
  `new_values`   JSON         DEFAULT NULL,
  `ip_address`   VARCHAR(45)  DEFAULT 'unknown',
  `user_agent`   VARCHAR(255) DEFAULT NULL,
  `state`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `processed_at` DATETIME(3)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_state_created` (`state`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
