-- Storage cleanup outbox: durable post-commit deletion of replaced avatar objects (App owner schema).
CREATE TABLE IF NOT EXISTS `storage_cleanup_outbox` (
  `id`            VARCHAR(40)  NOT NULL,
  `object_key`    VARCHAR(512) NOT NULL,
  `state`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
  `attempts`      INT          NOT NULL DEFAULT 0,
  `last_error`    VARCHAR(500) DEFAULT NULL,
  `next_retry_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at`    DATETIME(3)  DEFAULT NULL,
  `claim_owner`   VARCHAR(80)  DEFAULT NULL,
  `created_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `delivered_at`  DATETIME(3)  DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_storage_cleanup_object_key` (`object_key`),
  KEY `idx_storage_cleanup_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
