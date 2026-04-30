SET FOREIGN_KEY_CHECKS=0;

-- V13: Add vote counts and comment count to solutions table
-- This denormalizes counts for better query performance

-- Add missing vote counts to solutions table (only if not exists)
SET @dbname = DATABASE();
SET @tablename = 'solutions';

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @dbname
    AND table_name = @tablename
    AND column_name = 'likes'
);

SET @sql = IF(@column_exists = 0,
    'ALTER TABLE `solutions` ADD COLUMN `likes` int NOT NULL DEFAULT \'0\' AFTER `views`, ADD COLUMN `dislikes` int NOT NULL DEFAULT \'0\' AFTER `likes`, ADD COLUMN `comment_count` int NOT NULL DEFAULT \'0\' AFTER `dislikes`',
    'SELECT \'Columns already exist\' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Add index for vote sorting (descending for top solutions) (only if not exists)
SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = @dbname
    AND table_name = @tablename
    AND index_name = 'solutions_likes_idx'
);

SET @sql = IF(@index_exists = 0,
    'ALTER TABLE `solutions` ADD INDEX `solutions_likes_idx` (`likes` DESC)',
    'SELECT \'Index already exists\' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Initialize counts from existing data
-- Calculate likes/dislikes from edge_operations (only if tables exist)
SET @table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = @dbname
    AND table_name = 'edge_operations'
);

SET @sql = IF(@table_exists > 0,
    'UPDATE `solutions` s SET `likes` = (SELECT COUNT(*) FROM `edge_operations` e WHERE e.`target_id` = s.`id` AND e.`target_type` = \'SOLUTION\' AND e.`operation_type` = \'VOTE_UP\'), `dislikes` = (SELECT COUNT(*) FROM `edge_operations` e WHERE e.`target_id` = s.`id` AND e.`target_type` = \'SOLUTION\' AND e.`operation_type` = \'VOTE_DOWN\'), `comment_count` = (SELECT COUNT(*) FROM `solution_comments` c WHERE c.`solution_id` = s.`id` AND c.`is_deleted` = 0)',
    'SELECT \'edge_operations table does not exist yet, skipping count initialization\' AS message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS=1;
