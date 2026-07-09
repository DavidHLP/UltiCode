-- ADR-003 M3a: Judge Outbox table (shadow-mode dispatch truth).
-- Outbox + generation fence: at-least-once enqueue + exactly-once judging.
-- Unique (submission_id, generation) makes duplicate dispatch physically impossible.
-- is_shadow = 1 during M3a/M3b (shadow mode, not the active producer); flipped to 0 at
-- M3c cutover (watermark: created_at > cutover_at), per F13.
-- created_at / next_retry_at use DB CURRENT_TIMESTAMP(3), NOT Java clock (ADR F3).
CREATE TABLE `judge_outbox` (
  `id`            varchar(40)  NOT NULL,
  `submission_id` varchar(40)  NOT NULL,
  `generation`    bigint       NOT NULL,
  `payload`       json         NOT NULL,
  `state`         varchar(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING / SENT / DEAD / ARCHIVED
  `is_shadow`     tinyint(1)   NOT NULL DEFAULT 1,          -- F13: M3a shadow = 1, M3c real = 0
  `attempts`      int          NOT NULL DEFAULT 0,
  `last_error`    text         DEFAULT NULL,
  `created_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `sent_at`       datetime(3)  DEFAULT NULL,
  `next_retry_at` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_dispatch` (`submission_id`, `generation`),  -- ★ same generation enqueued at most once
  KEY `idx_state_retry` (`state`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
