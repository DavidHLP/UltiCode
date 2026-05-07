-- Add version column for optimistic locking on problem_lists table
SET FOREIGN_KEY_CHECKS=0;

ALTER TABLE `problem_lists` ADD COLUMN `version` INT NOT NULL DEFAULT 1 AFTER `banner_order`;

CREATE INDEX `idx_version` ON `problem_lists` (`version`);

SET FOREIGN_KEY_CHECKS=1;
