-- V20260729190000__Create_Submission_Result_Outbox.sql
-- P6-RESULT-001: Result outbox for verdict → Contest/Notification/Achievement
-- Ensures downstream side-effects survive JVM crash after verdict commit
--
-- Idempotency key: (submission_id, generation) — each rejudge generation
-- gets its own immutable row, preserving event history and preventing
-- a stale dispatcher from erasing a newer PENDING signal.

CREATE TABLE IF NOT EXISTS `submission_result_outbox` (
  `id`              varchar(40)  NOT NULL COMMENT 'Outbox row ID (UUID)',
  `submission_id`   varchar(40)  NOT NULL,
  `generation`      bigint       NOT NULL DEFAULT 0 COMMENT 'Fence generation (monotonic rejudge key); legacy path uses 0',
  `user_id`         varchar(40)  NOT NULL,
  `problem_id`      varchar(120) NOT NULL,
  `verdict`         varchar(30)  NOT NULL COMMENT 'Wire-format verdict (ACCEPTED, WRONG_ANSWER, ...)',
  `runtime_ms`      int          NOT NULL DEFAULT 0,
  `memory_mb`       double       NOT NULL DEFAULT 0,
  `contest_id`      varchar(40)  DEFAULT NULL,
  `state`           varchar(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLAIMED/DELIVERED/DEAD',
  `attempts`        int          NOT NULL DEFAULT 0,
  `last_error`      text         DEFAULT NULL,
  `created_at`      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `delivered_at`    datetime(3)  DEFAULT NULL,
  `next_retry_at`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_result_sub_gen` (`submission_id`, `generation`) COMMENT 'One result event per (submission, generation)',
  KEY `idx_result_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
