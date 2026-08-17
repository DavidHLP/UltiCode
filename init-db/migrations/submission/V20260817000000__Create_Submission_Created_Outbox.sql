-- SPLIT-003-slice-7: durable contest association handoff.
-- The table is Submission-owned; App-Contest consumes the event and remains
-- the sole writer of contest_submissions.
CREATE TABLE IF NOT EXISTS `submission_created_outbox` (
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
