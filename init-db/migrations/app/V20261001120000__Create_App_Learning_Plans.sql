-- V20261001120000__Create_App_Learning_Plans.sql
-- U03: App-owned write-side table for confirmed learning plans.
--
-- One row per (user_id, idempotency_key). The row is written once and never
-- updated; a replay of the same key with the same payload returns the stored
-- row, and the same key with a different payload is rejected with 40900 by the
-- application using request_fingerprint.
--
-- user_id and source_submission_id are contract references to Auth/Submission
-- owners: there is no cross-owner foreign key, no seed data, no default account
-- and no privilege change here.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `learning_plans` (
  `id`                  varchar(40)                           NOT NULL COMMENT 'Learning plan row ID (canonical UUID)',
  `user_id`             varchar(40)                           NOT NULL COMMENT 'Owner account id (Auth contract reference)',
  `idempotency_key`     char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Canonical lowercase UUID from the Idempotency-Key header',
  `request_fingerprint` char(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SHA-256 of the canonical payload JSON array',
  `source_submission_id` varchar(40)                          NOT NULL COMMENT 'Submission contract reference, ownership verified before write',
  `draft_version`       int                                   NOT NULL COMMENT 'Caller-confirmed positive draft version',
  `title`               varchar(200)                          NOT NULL,
  `content`             text                                  NOT NULL,
  `created_at`          datetime(3)                           NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_learning_plans_user_key` (`user_id`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
