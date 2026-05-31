-- =============================================================================
-- Fix contest table schema mismatches between entity and database
-- Issues fixed:
--   1. contest_type enum: weekly/biweekly/special -> ICPC/IOI/CUSTOM
--   2. status enum: missing DRAFT and CANCELLED states
--   3. scoring_mode enum: missing IOI mode
--   4. contest_problems missing unique index on (contest_id, problem_id)
-- Note: Columns (scoring_rule_id, is_rated, actual_start_time, etc.) already
--       exist in database from baseline, no need to add them.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Migrate existing contest_type values to new enum
-- Map old values to new values:
--   weekly       -> ICPC   (most weekly contests follow ICPC style)
--   biweekly     -> IOI    (biweekly tend to be IOI style)
--   special      -> CUSTOM (special contests are custom)
-- -----------------------------------------------------------------------------
UPDATE contests SET contest_type = 'CUSTOM' WHERE contest_type = 'special';
UPDATE contests SET contest_type = 'IOI' WHERE contest_type = 'biweekly';
UPDATE contests SET contest_type = 'ICPC' WHERE contest_type = 'weekly';

-- -----------------------------------------------------------------------------
-- 2. Modify contest_type enum to new values
-- -----------------------------------------------------------------------------
ALTER TABLE contests
  MODIFY COLUMN contest_type ENUM('ICPC', 'IOI', 'CUSTOM') NOT NULL
    COMMENT 'Contest type: ICPC (traditional), IOI (score-based), CUSTOM';

-- -----------------------------------------------------------------------------
-- 3. Modify status enum to add DRAFT and CANCELLED
-- -----------------------------------------------------------------------------
ALTER TABLE contests
  MODIFY COLUMN status ENUM('DRAFT', 'UPCOMING', 'RUNNING', 'FINISHED', 'CANCELLED') NOT NULL DEFAULT 'DRAFT'
    COMMENT 'Contest lifecycle status';

-- -----------------------------------------------------------------------------
-- 4. Modify scoring_mode enum to add IOI
-- -----------------------------------------------------------------------------
ALTER TABLE contests
  MODIFY COLUMN scoring_mode ENUM('SCORE', 'ICPC', 'IOI') NOT NULL DEFAULT 'SCORE'
    COMMENT 'Scoring mode determines ranking algorithm';

-- -----------------------------------------------------------------------------
-- 5. Add unique index on contest_problems to prevent duplicate problem entries
-- Check if index already exists before creating
-- -----------------------------------------------------------------------------
DELIMITER //
CREATE PROCEDURE add_contest_problem_unique_index()
BEGIN
  DECLARE idx_exists INT DEFAULT 0;
  SELECT COUNT(*) INTO idx_exists
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'contest_problems'
    AND INDEX_NAME = 'uk_contest_problem_id';
  IF idx_exists = 0 THEN
    CREATE UNIQUE INDEX uk_contest_problem_id
      ON contest_problems (contest_id, problem_id);
  END IF;
END //
DELIMITER ;
CALL add_contest_problem_unique_index();
DROP PROCEDURE add_contest_problem_unique_index;

-- -----------------------------------------------------------------------------
-- 6. Add indexes for common query patterns
-- -----------------------------------------------------------------------------
-- Index for finding contests by creator
CREATE INDEX idx_contests_created_by ON contests (created_by);

-- Index for virtual contest queries
CREATE INDEX idx_contests_is_virtual ON contests (is_virtual);

-- Composite index for admin contest list queries
CREATE INDEX idx_contests_status_type ON contests (status, contest_type);