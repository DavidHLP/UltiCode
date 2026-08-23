-- V20260823170000__Align_Forum_Posts_With_Runtime_Contracts.sql
-- Additive App Owner repair for the forum_posts table created by
-- V20260729140300__Create_App_Schema_Tables.sql.
--
-- The original table is a six-column legacy shape. The current ForumPost
-- entity, mapper and read projection require the full baseline contract:
-- soft-delete, sort/statistics, JSON payloads, excerpt and update fields.
-- Preserve the legacy `content` column for compatibility; new reads use
-- `excerpt` and new writes are allowed to omit `content`.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `forum_posts` (
  `id` varchar(40) NOT NULL,
  `community_id` varchar(40) NOT NULL,
  `user_id` varchar(40) NOT NULL,
  `permalink` varchar(255) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `flair_type` enum('announcement','discussion','showcase','question','hiring') DEFAULT NULL,
  `flair_label` varchar(60) DEFAULT NULL,
  `tags` json NOT NULL,
  `excerpt` text,
  `media` json DEFAULT NULL,
  `recommendation` json DEFAULT NULL,
  `vote_state` enum('upvoted','downvoted','neutral') NOT NULL DEFAULT 'neutral',
  `is_saved` tinyint(1) NOT NULL DEFAULT '0',
  `impressions` int NOT NULL DEFAULT '0',
  `is_pinned` tinyint(1) NOT NULL DEFAULT '0',
  `is_locked` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `stats` json DEFAULT NULL,
  `views` int NOT NULL DEFAULT '0',
  `is_flagged` tinyint(1) NOT NULL DEFAULT '0',
  `flagged_reason` text,
  `flagged_at` datetime(3) DEFAULT NULL,
  `is_deleted` tinyint(1) NOT NULL DEFAULT '0',
  `deleted_at` datetime(3) DEFAULT NULL,
  `deleted_by` varchar(40) DEFAULT NULL,
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `forum_posts_community_id_fkey` (`community_id`),
  KEY `forum_posts_user_id_fkey` (`user_id`),
  KEY `forum_posts_is_deleted_created_at_idx` (`is_deleted`,`created_at`),
  KEY `forum_posts_community_id_created_at_idx` (`community_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

DROP PROCEDURE IF EXISTS `_align_forum_posts_contract`;
DELIMITER $$
CREATE PROCEDURE `_align_forum_posts_contract`()
BEGIN
  SET @forum_posts_ddl := 'ALTER TABLE `forum_posts`';
  SET @forum_posts_has_clause := 0;

  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'permalink') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      ' ADD COLUMN `permalink` varchar(255) DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'flair_type') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`flair_type` enum(''announcement'',''discussion'',''showcase'',''question'',''hiring'') DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'flair_label') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`flair_label` varchar(60) DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'tags') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`tags` json NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'excerpt') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`excerpt` text NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'media') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`media` json DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'recommendation') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`recommendation` json DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'vote_state') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`vote_state` enum(''upvoted'',''downvoted'',''neutral'') NOT NULL DEFAULT ''neutral''');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'is_saved') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`is_saved` tinyint(1) NOT NULL DEFAULT 0');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'impressions') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`impressions` int NOT NULL DEFAULT 0');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'is_pinned') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`is_pinned` tinyint(1) NOT NULL DEFAULT 0');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'is_locked') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`is_locked` tinyint(1) NOT NULL DEFAULT 0');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'stats') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`stats` json DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'views') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`views` int NOT NULL DEFAULT 0');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'is_flagged') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`is_flagged` tinyint(1) NOT NULL DEFAULT 0');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'flagged_reason') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`flagged_reason` text DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'flagged_at') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`flagged_at` datetime(3) DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'is_deleted') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`is_deleted` tinyint(1) NOT NULL DEFAULT 0');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'deleted_at') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`deleted_at` datetime(3) DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'deleted_by') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`deleted_by` varchar(40) DEFAULT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'updated_at') = 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ADD COLUMN ', ' ADD COLUMN '),
      '`updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)');
    SET @forum_posts_has_clause = 1;
  END IF;

  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'title') > 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ', ' '),
      'MODIFY COLUMN `title` varchar(255) NOT NULL');
    SET @forum_posts_has_clause = 1;
  END IF;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'content') > 0 THEN
    SET @forum_posts_ddl = CONCAT(@forum_posts_ddl,
      IF(@forum_posts_has_clause = 1, ', ', ' '),
      'MODIFY COLUMN `content` text NULL');
    SET @forum_posts_has_clause = 1;
  END IF;

  IF @forum_posts_has_clause = 1 THEN
    PREPARE forum_posts_stmt FROM @forum_posts_ddl;
    EXECUTE forum_posts_stmt;
    DEALLOCATE PREPARE forum_posts_stmt;
  END IF;

  UPDATE `forum_posts` SET `tags` = JSON_ARRAY() WHERE `tags` IS NULL;
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND COLUMN_NAME = 'content') > 0 THEN
    UPDATE `forum_posts` SET `excerpt` = `content`
      WHERE `excerpt` IS NULL AND `content` IS NOT NULL;
  END IF;
  ALTER TABLE `forum_posts` MODIFY COLUMN `tags` json NOT NULL;

  SET @forum_posts_index_ddl := IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND INDEX_NAME = 'forum_posts_is_deleted_created_at_idx') = 0,
    'ALTER TABLE `forum_posts` ADD KEY `forum_posts_is_deleted_created_at_idx` (`is_deleted`,`created_at`)',
    'SELECT 1');
  PREPARE forum_posts_index_stmt FROM @forum_posts_index_ddl;
  EXECUTE forum_posts_index_stmt;
  DEALLOCATE PREPARE forum_posts_index_stmt;

  SET @forum_posts_index_ddl := IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'forum_posts'
        AND INDEX_NAME = 'forum_posts_community_id_created_at_idx') = 0,
    'ALTER TABLE `forum_posts` ADD KEY `forum_posts_community_id_created_at_idx` (`community_id`,`created_at`)',
    'SELECT 1');
  PREPARE forum_posts_index_stmt FROM @forum_posts_index_ddl;
  EXECUTE forum_posts_index_stmt;
  DEALLOCATE PREPARE forum_posts_index_stmt;
END$$
DELIMITER ;

CALL `_align_forum_posts_contract`();
DROP PROCEDURE IF EXISTS `_align_forum_posts_contract`;
