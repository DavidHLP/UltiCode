-- CONTEST-002: durable receipt for contest adjudication.
-- One committed receipt fences one submission generation from duplicate scoring.
CREATE TABLE IF NOT EXISTS `contest_adjudication_receipts` (
  `id`            varchar(40) NOT NULL COMMENT 'Receipt row ID (UUID)',
  `submission_id` varchar(40) NOT NULL,
  `generation`    bigint      NOT NULL COMMENT 'Monotonic judge generation',
  `verdict`       varchar(30) NOT NULL COMMENT 'Terminal submission verdict',
  `is_accepted`   tinyint(1)  NOT NULL,
  `created_at`    datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniq_contest_adjudication_receipt` (`submission_id`, `generation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
