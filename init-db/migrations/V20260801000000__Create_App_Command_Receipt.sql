-- V20260801000000__Create_App_Command_Receipt.sql
-- P7-APP-IDEMPOTENCY-001: App-owned durable command receipt table for RPC idempotency
-- App-owner schema copy for app schema.
-- Implements §6.2 WriteCommand provider-side replay-dedup mandate.

CREATE TABLE IF NOT EXISTS `app_command_receipt` (
  `id`                  VARCHAR(40)  NOT NULL COMMENT 'Receipt row ID (UUID)',
  `command_id`          VARCHAR(40)  NOT NULL COMMENT 'RPC command ID',
  `service`             VARCHAR(80)  NOT NULL COMMENT 'Service interface FQCN or simple name',
  `operation`           VARCHAR(80)  NOT NULL COMMENT 'RPC operation method name',
  `idempotency_key`     VARCHAR(120) NOT NULL COMMENT 'Client/caller idempotency key',
  `request_fingerprint` VARCHAR(64)  DEFAULT NULL COMMENT 'SHA-256 digest of request business payload',
  `status`              VARCHAR(20)  NOT NULL COMMENT 'SUCCESS or FAILED',
  `error_code`          VARCHAR(80)  DEFAULT NULL COMMENT 'Namespaced error code if operation failed',
  `result_payload`      JSON         DEFAULT NULL COMMENT 'Serialized result for replay',
  `actor_type`          VARCHAR(30)  DEFAULT NULL COMMENT 'USER/ADMIN/SERVICE/SYSTEM',
  `actor_id`            VARCHAR(40)  DEFAULT NULL COMMENT 'Actor identifier',
  `trace_id`            VARCHAR(80)  DEFAULT NULL COMMENT 'Distributed trace ID',
  `created_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_app_command_receipt` (`service`, `operation`, `idempotency_key`),
  KEY `idx_app_cmd_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
