-- migration.sql: 添加用户表逻辑删除字段
-- 注意: MySQL 不支持 IF NOT EXISTS，使用存储过程实现幂等

DELIMITER //

-- 添加 is_deleted 列
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'is_deleted');
SET @sql := IF(@exist = 0,
    'ALTER TABLE users ADD COLUMN is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除标记''',
    'SELECT ''Column is_deleted already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 deleted_at 列
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'deleted_at');
SET @sql := IF(@exist = 0,
    'ALTER TABLE users ADD COLUMN deleted_at DATETIME NULL COMMENT ''删除时间''',
    'SELECT ''Column deleted_at already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 deleted_by 列
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'deleted_by');
SET @sql := IF(@exist = 0,
    'ALTER TABLE users ADD COLUMN deleted_by VARCHAR(40) NULL COMMENT ''删除人ID''',
    'SELECT ''Column deleted_by already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加索引
SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND INDEX_NAME = 'idx_users_is_deleted');
SET @sql := IF(@exist = 0,
    'CREATE INDEX idx_users_is_deleted ON users(is_deleted)',
    'SELECT ''Index idx_users_is_deleted already exists''');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

//
DELIMITER ;
