-- SPLIT-003: target-state Submission owner tables.
--
-- These are final-shape tables for clean databases. Existing deployments use
-- scripts/dev/submission-schema-cutover.sh to preflight, reconcile, copy,
-- and later revoke the old App write grants before switching to the
-- backend-submission runtime as the sole writer.
--
-- The root Flyway location also discovers this directory (like
-- migrations/notification). All statements are therefore idempotent:
-- CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS so the
-- compatibility pass over the shared `ulticode` schema is a no-op, while the
-- dedicated `MIGRATION_SCHEMA=submission` pass creates the owner schema
-- tables in their final shape.

-- Submissions aggregate (final shape: baseline V20260602_120000 +
-- V20260613110000 generation/lease columns + stats columns).
CREATE TABLE IF NOT EXISTS `submissions` (
  `id` varchar(40) NOT NULL,
  `problem_id` bigint NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `language` varchar(50) NOT NULL,
  `code` text NOT NULL,
  `status` varchar(40) NOT NULL,
  `runtime` int NOT NULL,
  `memory` double DEFAULT NULL,
  `notes` text,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `runtime_percentile` double DEFAULT NULL,
  `memory_percentile` double DEFAULT NULL,
  `test_details` json DEFAULT NULL,
  `memoryDistBinsMb` json DEFAULT NULL,
  `runtimeDistBinsMs` json DEFAULT NULL,
  `retry_count` int NOT NULL DEFAULT '0',
  `generation` bigint NOT NULL DEFAULT '1',
  `current_attempt_id` varchar(40) DEFAULT NULL,
  `judging_lease_expires_at` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `submissions_problem_id_user_id_idx` (`problem_id`,`user_id`),
  KEY `submissions_user_id_fkey` (`user_id`),
  KEY `submissions_created_at_idx` (`created_at`),
  KEY `submissions_user_id_status_created_at_idx` (`user_id`,`status`,`created_at`),
  KEY `submissions_problem_id_user_id_status_runtime_memory_idx` (`problem_id`,`user_id`,`status`,`runtime`,`memory`),
  KEY `idx_lease_expiry` (`status`,`judging_lease_expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Judge dispatch outbox (ADR-003 M3a). Unique (submission_id, generation)
-- makes double dispatch physically impossible.
CREATE TABLE IF NOT EXISTS `judge_outbox` (
  `id`            varchar(40)  NOT NULL,
  `submission_id` varchar(40)  NOT NULL,
  `generation`    bigint       NOT NULL,
  `payload`       json         NOT NULL,
  `state`         varchar(16)  NOT NULL DEFAULT 'PENDING',
  `is_shadow`     tinyint(1)   NOT NULL DEFAULT 1,
  `attempts`      int          NOT NULL DEFAULT 0,
  `last_error`    text         DEFAULT NULL,
  `created_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at`       datetime(3)  DEFAULT NULL,
  `next_retry_at` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_dispatch` (`submission_id`, `generation`),
  KEY `idx_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Result outbox (P6-RESULT-001): verdict -> Contest/Notification/Achievement.
-- Final shape includes claimed_at / claim_owner dispatcher fencing.
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
  `claimed_at`      datetime(3)  DEFAULT NULL,
  `claim_owner`     varchar(80)  DEFAULT NULL COMMENT 'Dispatcher lease owner',
  `delivered_at`    datetime(3)  DEFAULT NULL,
  `next_retry_at`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_result_sub_gen` (`submission_id`, `generation`),
  KEY `idx_result_state_retry` (`state`, `next_retry_at`),
  KEY `idx_submission_result_outbox_claim_owner` (`state`,`claim_owner`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
