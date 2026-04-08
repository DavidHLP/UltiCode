SET FOREIGN_KEY_CHECKS=0;

-- V13: Add vote counts and comment count to solutions table
-- This denormalizes counts for better query performance

-- Add missing vote counts to solutions table
ALTER TABLE `solutions`
  ADD COLUMN `likes` int NOT NULL DEFAULT '0' AFTER `views`,
  ADD COLUMN `dislikes` int NOT NULL DEFAULT '0' AFTER `likes`,
  ADD COLUMN `comment_count` int NOT NULL DEFAULT '0' AFTER `dislikes`;

-- Add index for vote sorting (descending for top solutions)
ALTER TABLE `solutions`
  ADD INDEX `solutions_likes_idx` (`likes` DESC);

-- Initialize counts from existing data
-- Calculate likes/dislikes from edge_operations
UPDATE `solutions` s
SET
  `likes` = (
    SELECT COUNT(*) FROM `edge_operations` e
    WHERE e.`target_id` = s.`id`
    AND e.`target_type` = 'SOLUTION'
    AND e.`operation_type` = 'VOTE_UP'
  ),
  `dislikes` = (
    SELECT COUNT(*) FROM `edge_operations` e
    WHERE e.`target_id` = s.`id`
    AND e.`target_type` = 'SOLUTION'
    AND e.`operation_type` = 'VOTE_DOWN'
  ),
  `comment_count` = (
    SELECT COUNT(*) FROM `solution_comments` c
    WHERE c.`solution_id` = s.`id`
    AND c.`is_deleted` = 0
  );

SET FOREIGN_KEY_CHECKS=1;
