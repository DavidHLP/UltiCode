SET FOREIGN_KEY_CHECKS = 0;

-- Allow NULL values in memory column (measurement failure = null, not 0)
ALTER TABLE submissions MODIFY COLUMN memory DOUBLE NULL DEFAULT NULL;

-- Migrate dirty data: memory=0 where measurement failed
-- Case 1: test_details has memory=0 (judged before /usr/bin/time -v)
UPDATE submissions SET memory = NULL
WHERE memory = 0
  AND test_details LIKE '%\"memory\": 0%';

-- Case 2: test_details is NULL or empty (error/TLE submissions)
UPDATE submissions SET memory = NULL
WHERE memory = 0
  AND (test_details IS NULL OR test_details = '[]');

SET FOREIGN_KEY_CHECKS = 1;
