-- V20260730120000__Create_Auth_Command_Receipt.sql
-- P7-AUTH-IDEMPOTENCY-SCHEMA-001: Auth-owned durable command receipt table for RPC idempotency
-- Per-owner schema copy for auth schema.

CREATE TABLE IF NOT EXISTS `auth_command_receipt` (
  `id`                  VARCHAR(40)  NOT NULL COMMENT 'Receipt row ID (UUID)',
  `command_id`          VARCHAR(40)  NOT NULL COMMENT 'RPC command ID',
  `service`             VARCHAR(80)  NOT NULL COMMENT 'Service interface FQCN or simple name',
  `operation`           VARCHAR(80)  NOT NULL COMMENT 'RPC operation method name',
  `idempotency_key`     VARCHAR(120) NOT NULL COMMENT 'Client/caller idempotency key',
  `request_fingerprint` VARCHAR(64)  DEFAULT NULL COMMENT 'SHA-256 digest of request payload',
  `status`              VARCHAR(20)  NOT NULL COMMENT 'SUCCESS or FAILED',
  `error_code`          VARCHAR(80)  DEFAULT NULL COMMENT 'Namespaced error code if operation failed',
  `result_payload`      JSON         DEFAULT NULL COMMENT 'Serialized result or error payload for replay',
  `actor_type`          VARCHAR(30)  DEFAULT NULL COMMENT 'USER/ADMIN/SERVICE/SYSTEM',
  `actor_id`            VARCHAR(40)  DEFAULT NULL COMMENT 'Actor identifier',
  `trace_id`            VARCHAR(80)  DEFAULT NULL COMMENT 'Distributed trace ID',
  `created_at`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_auth_command_receipt` (`service`, `operation`, `idempotency_key`),
  KEY `idx_auth_cmd_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
