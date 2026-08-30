-- P1-AUDIT-001: App-local audit outbox. Admin consumes committed rows as
-- AuditRecorded events; App never writes the Admin schema.
CREATE TABLE IF NOT EXISTS `audit_outbox` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` varchar(64) NOT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) NOT NULL DEFAULT 'unknown',
  `user_agent` varchar(255) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT 0,
  `last_error` text DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_outbox_state_retry` (`state`, `next_retry_at`),
KEY `idx_audit_outbox_claim_owner` (`state`, `claim_owner`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
