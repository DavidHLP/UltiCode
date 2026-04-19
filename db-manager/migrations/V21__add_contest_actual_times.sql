SET FOREIGN_KEY_CHECKS=0;

-- Add actual_start_time and actual_end_time columns to contests table
-- These track when a contest actually started/ended vs. the scheduled times
ALTER TABLE `contests`
    ADD COLUMN `actual_start_time` datetime(3) DEFAULT NULL AFTER `end_time`,
    ADD COLUMN `actual_end_time` datetime(3) DEFAULT NULL AFTER `actual_start_time`;

SET FOREIGN_KEY_CHECKS=1;
