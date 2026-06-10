-- V20260610140000__Add_User_Permission_Expires_At.sql
-- 为 fresh init-db 场景补齐 user_permissions.expires_at 列, 支持时效性直接授权
-- production / dev baseline (V20260602_120000) 已包含该列, 此迁移在 INFORMATION_SCHEMA 守护下幂等
--
-- 设计要点:
--   * MySQL 9.1 不支持 ADD COLUMN IF NOT EXISTS, 改用 INFORMATION_SCHEMA + PREPARE 动态 SQL
--   * 无需新建索引: user_permissions_user_id_idx 已支持读, UNIQUE (user_id, action, resource) 保证 grant 幂等

SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'user_permissions'
       AND COLUMN_NAME = 'expires_at'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `user_permissions` ADD COLUMN `expires_at` datetime(3) DEFAULT NULL AFTER `granted_at`',
    'SELECT 1');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
