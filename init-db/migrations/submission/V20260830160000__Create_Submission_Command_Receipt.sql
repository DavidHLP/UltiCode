-- P1-SUB-002: owner-local idempotency and audit receipt for Admin rejudge commands.
CREATE TABLE IF NOT EXISTS `submission_command_receipt` (
  `id`                  varchar(40)  NOT NULL,
  `command_id`          varchar(40)  NOT NULL,
  `service`             varchar(80)  NOT NULL,
  `operation`           varchar(80)  NOT NULL,
  `idempotency_key`     varchar(120) NOT NULL,
  `request_fingerprint` varchar(64)  DEFAULT NULL,
  `status`              varchar(20)  NOT NULL COMMENT 'PROCESSING or SUCCESS',
  `result_payload`      json         DEFAULT NULL,
  `actor_type`          varchar(30)  NOT NULL,
  `actor_id`            varchar(40)  NOT NULL,
  `trace_id`            varchar(80)  NOT NULL,
  `created_at`          datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_command_receipt`
    (`service`, `operation`, `idempotency_key`),
  KEY `idx_submission_command_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
