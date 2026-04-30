SET FOREIGN_KEY_CHECKS = 0;

-- Add is_pinned column to solutions table for pinned solutions
ALTER TABLE solutions ADD COLUMN is_pinned tinyint(1) NOT NULL DEFAULT '0' COMMENT 'Whether the solution is pinned to the top';

SET FOREIGN_KEY_CHECKS = 1;