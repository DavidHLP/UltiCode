-- V20260816230000__Precreate_Submission_Created_Outbox_For_Shared_Chain.sql
-- SPLIT-003-slice-7: shared-chain compatibility for the submission-chain
-- migration V20260817000000.
--
-- The shared Flyway location (`filesystem:migrations`) discovers owner-chain
-- directories recursively. Most owner-dir migrations are tolerated by the
-- shared chain (CREATE TABLE IF NOT EXISTS against already-existing shared
-- tables), but V20260817000000 creates `submission_created_outbox`, which
-- has no legacy shared-chain ancestor — an unqualified CREATE would
-- materialize a phantom table in the App `ulticode` schema on every default
-- migrate.sh run.
--
-- This migration pre-creates the submission schema and outbox table
-- (idempotent, schema-qualified) so the shared chain applies V20260817000000
-- as a no-op. The submission chain itself (flyway-submission.conf,
-- defaultSchema=submission) remains the owner of the table shape; its
-- V20260817000000 now uses the qualified name and no-ops against the
-- existing table. Never edit V20260817000000 (applied in real environments
-- after this commit).

CREATE SCHEMA IF NOT EXISTS `submission`;

CREATE TABLE IF NOT EXISTS `submission`.`submission_created_outbox` (
  `id`                  varchar(40)  NOT NULL,
  `submission_id`       varchar(40)  NOT NULL,
  `generation`          bigint       NOT NULL DEFAULT 1,
  `user_id`             varchar(40)  NOT NULL,
  `problem_id`          varchar(120) NOT NULL,
  `contest_id`          varchar(40)  NOT NULL,
  `virtual_session_id`  varchar(40)  DEFAULT NULL,
  `language`            varchar(50)  NOT NULL,
  `occurred_at`         datetime(3)  NOT NULL,
  `state`               varchar(16)  NOT NULL DEFAULT 'PENDING',
  `attempts`            int          NOT NULL DEFAULT 0,
  `last_error`          text         DEFAULT NULL,
  `created_at`          datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at`          datetime(3)  DEFAULT NULL,
  `claim_owner`         varchar(80)  DEFAULT NULL,
  `delivered_at`        datetime(3)  DEFAULT NULL,
  `next_retry_at`       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_created_sub_gen` (`submission_id`, `generation`),
  KEY `idx_created_state_retry` (`state`, `next_retry_at`),
  KEY `idx_created_claim_owner` (`state`, `claim_owner`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
