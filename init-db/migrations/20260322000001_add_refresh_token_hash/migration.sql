-- migration.sql: 添加 token_hash 列到 refresh_tokens 表
-- 注意: MySQL 不支持 IF NOT EXISTS，使用存储过程实现幂等

DELIMITER //

-- 添加 token_hash 列
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND COLUMN_NAME = 'token_hash');
SET @sql := IF(@exist = 0,
    'ALTER TABLE refresh_tokens ADD COLUMN token_hash VARCHAR(255) NULL COMMENT ''Token哈希值'' AFTER token',
    'SELECT ''Column token_hash already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refresh_tokens' AND INDEX_NAME = 'idx_refresh_tokens_token_hash');
SET @sql := IF(@exist = 0,
    'CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash)',
    'SELECT ''Index idx_refresh_tokens_token_hash already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

//
DELIMITER ;
