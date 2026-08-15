-- NOTIFY-002: compatibility-mode Notification-owned command receipt.
--
-- During the same-database transition backend-notification still points at
-- DB_NAME. Keep its receipt in a Notification-named table rather than using
-- app_command_receipt, which belongs exclusively to backend-app.

CREATE TABLE IF NOT EXISTS `notification_command_receipt` (
  `id`                  VARCHAR(40)  NOT NULL COMMENT 'Receipt row ID (UUID)',
  `command_id`          VARCHAR(40)  NOT NULL COMMENT 'RPC command ID',
  `service`             VARCHAR(80)  NOT NULL COMMENT 'Service interface FQCN or simple name',
  `operation`           VARCHAR(80)  NOT NULL COMMENT 'RPC operation method name',
  `idempotency_key`     VARCHAR(120) NOT NULL COMMENT 'Client/caller idempotency key',
  `request_fingerprint` VARCHAR(64)  DEFAULT NULL COMMENT 'SHA-256 digest of request business payload',
  `status`              VARCHAR(20)  NOT NULL COMMENT 'SUCCESS or PROCESSING',
  `error_code`          VARCHAR(80)  DEFAULT NULL COMMENT 'Namespaced error code if operation failed',
  `result_payload`      JSON         DEFAULT NULL COMMENT 'Serialized result for replay',
  `actor_type`          VARCHAR(30)  DEFAULT NULL COMMENT 'USER/ADMIN/SERVICE/SYSTEM',
  `actor_id`            VARCHAR(40)  DEFAULT NULL COMMENT 'Actor identifier',
  `trace_id`            VARCHAR(80)  DEFAULT NULL COMMENT 'Distributed trace ID',
  `created_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_command_receipt` (`service`, `operation`, `idempotency_key`),
  KEY `idx_notification_command_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
