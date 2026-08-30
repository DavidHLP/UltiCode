-- P1-AUDIT-001: App-local audit outbox. Admin consumes committed rows as
-- AuditRecorded events; App never writes the Admin schema.
CREATE TABLE IF NOT EXISTS `audit_outbox` (
  `id` varchar(40) NOT NULL,
  `performer_id` varchar(40) NOT NULL,
  `user_id` varchar(40) DEFAULT NULL,
  `action` varchar(64) NOT NULL,
  `entity_type` varchar(64) NOT NULL,
  `entity_id` varchar(64) NOT NULL,
  `old_values` json DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `ip_address` varchar(45) NOT NULL DEFAULT 'unknown',
  `user_agent` varchar(255) DEFAULT NULL,
  `state` varchar(16) NOT NULL DEFAULT 'PENDING',
  `attempts` int NOT NULL DEFAULT 0,
  `last_error` text DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `claimed_at` datetime(3) DEFAULT NULL,
  `claim_owner` varchar(80) DEFAULT NULL,
  `delivered_at` datetime(3) DEFAULT NULL,
  `next_retry_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_audit_outbox_state_retry` (`state`, `next_retry_at`),
  KEY `idx_audit_outbox_claim_owner` (`state`, `claim_owner`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Remove the former cross-owner INSERT grant only after the local outbox
-- exists. The exact-table probe keeps fresh/upgrade migration idempotent.
DROP PROCEDURE IF EXISTS `_revoke_app_cross_owner_audit_grant`;
DELIMITER //
CREATE PROCEDURE `_revoke_app_cross_owner_audit_grant`()
BEGIN
    DECLARE grant_count INT DEFAULT 0;
    SELECT COUNT(*) INTO grant_count
    FROM information_schema.table_privileges
    WHERE GRANTEE = '''app_rw''@''%'''
      AND TABLE_SCHEMA = 'admin'
      AND TABLE_NAME = 'audit_outbox'
      AND PRIVILEGE_TYPE = 'INSERT';
    IF grant_count > 0 THEN
        SET @revoke_sql = 'REVOKE INSERT ON `admin`.`audit_outbox` FROM ''app_rw''@''%''';
        PREPARE revoke_stmt FROM @revoke_sql;
        EXECUTE revoke_stmt;
        DEALLOCATE PREPARE revoke_stmt;
    END IF;
    SELECT COUNT(*) INTO grant_count
    FROM information_schema.table_privileges
    WHERE GRANTEE = '''app_rw''@''%'''
      AND TABLE_SCHEMA = 'admin'
      AND TABLE_NAME = 'audit_outbox'
      AND PRIVILEGE_TYPE = 'INSERT';
    IF grant_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'app_rw still has a cross-owner audit_outbox INSERT grant';
    END IF;
END//
DELIMITER ;
CALL `_revoke_app_cross_owner_audit_grant`();
DROP PROCEDURE IF EXISTS `_revoke_app_cross_owner_audit_grant`;
