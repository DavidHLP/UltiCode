-- CONTEST-003: make contest finalization recoverable.
-- RUNNING is first claimed as FINISHING; only the retry-safe finalizer may
-- publish FINISHED. The receipt prevents a crash after rating commit from
-- applying the same contest rating a second time.
ALTER TABLE contests
    MODIFY COLUMN status ENUM('DRAFT', 'UPCOMING', 'RUNNING', 'FINISHING', 'FINISHED', 'CANCELLED')
    NOT NULL DEFAULT 'DRAFT';

CREATE TABLE contest_rating_calculations (
    id VARCHAR(40) NOT NULL,
    contest_id VARCHAR(40) NOT NULL,
    calculated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_contest_rating_calculations_contest_id (contest_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
