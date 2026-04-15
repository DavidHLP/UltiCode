SET FOREIGN_KEY_CHECKS=0;

-- Add retry_count column to submissions table for tracking admin rejudge attempts (D-23)
ALTER TABLE submissions ADD COLUMN retry_count INT DEFAULT 0 AFTER notes;

SET FOREIGN_KEY_CHECKS=1;
