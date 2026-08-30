-- P2-MIG-001: cross-owner grant cleanup runs only after all owner-local
-- outboxes exist and only through the privileged shared migration identity.
REVOKE INSERT ON `admin`.`audit_outbox` FROM 'auth_rw'@'%';
REVOKE INSERT ON `admin`.`audit_outbox` FROM 'app_rw'@'%';

DROP PROCEDURE IF EXISTS `_assert_owner_audit_boundaries`;
DELIMITER //
CREATE PROCEDURE `_assert_owner_audit_boundaries`()
BEGIN
    DECLARE owner_outbox_count INT DEFAULT 0;
    DECLARE cross_grant_count INT DEFAULT 0;

    SELECT COUNT(*) INTO owner_outbox_count
    FROM information_schema.tables
    WHERE (table_schema = 'auth' AND table_name = 'audit_outbox')
       OR (table_schema = 'app' AND table_name = 'audit_outbox');
    IF owner_outbox_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'owner-local audit outboxes must exist before cross-owner grant cleanup';
    END IF;

    SELECT COUNT(*) INTO cross_grant_count
    FROM information_schema.table_privileges
    WHERE GRANTEE IN ('''auth_rw''@''%''', '''app_rw''@''%''')
      AND TABLE_SCHEMA = 'admin'
      AND TABLE_NAME = 'audit_outbox'
      AND PRIVILEGE_TYPE = 'INSERT';
    IF cross_grant_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'owner runtime accounts still have cross-owner audit_outbox INSERT grants';
    END IF;
END//
DELIMITER ;
CALL `_assert_owner_audit_boundaries`();
DROP PROCEDURE IF EXISTS `_assert_owner_audit_boundaries`;
