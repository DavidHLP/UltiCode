-- P1-DATA-001: destructive legacy-table contraction.
-- This file is not in the normal Flyway locations. It is invoked only through
-- the explicit owner-schema-contraction runbook after parity, writer, and
-- compatibility proof has been recorded in owner_contraction_proof.

DROP PROCEDURE IF EXISTS `_retire_legacy_owner_tables`;
DELIMITER //
CREATE PROCEDURE `_retire_legacy_owner_tables`()
BEGIN
    DECLARE proof_count INT DEFAULT 0;
    DECLARE app_dml_grants INT DEFAULT 0;

    IF '${contraction_confirmed}' <> 'true' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'owner contraction requires an explicit confirmation token';
    END IF;

    SELECT COUNT(*) INTO proof_count
    FROM `owner_contraction_proof`
    WHERE `owner` IN ('Submission', 'Notification')
      AND `source_schema` = 'ulticode'
      AND `source_rows` = `target_rows`
      AND `snapshot_hash` <> ''
      AND `app_dml_grants` = 0;

    IF proof_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'owner contraction requires verified Submission and Notification proofs';
    END IF;

    IF '${backup_confirmed}' <> 'true' OR '${quiesce_confirmed}' <> 'true' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'owner contraction requires verified backup and writer-quiescence confirmations';
    END IF;

    SELECT COUNT(*) INTO proof_count
    FROM `owner_contraction_proof`
    WHERE `owner` IN ('Submission', 'Notification')
      AND `backup_reference` <> ''
      AND `backup_verified_at` IS NOT NULL
      AND `writers_quiesced_at` IS NOT NULL;

    IF proof_count <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'owner contraction requires durable backup and writer-quiescence proof';
    END IF;

    SELECT (
        SELECT COUNT(*) FROM information_schema.table_privileges
        WHERE GRANTEE = CONCAT(CHAR(39), '${app_db_user}', CHAR(39), '@', CHAR(39), '${app_db_host}', CHAR(39))
          AND TABLE_SCHEMA = 'ulticode'
          AND TABLE_NAME IN ('submissions','judge_outbox','submission_result_outbox',
                             'notifications','notification_preferences',
                             'notification_delivery_ledger','email_templates','email_logs',
                             'notification_command_receipt')
          AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','GRANT OPTION')
    ) + (
        SELECT COUNT(*) FROM information_schema.column_privileges
        WHERE GRANTEE = CONCAT(CHAR(39), '${app_db_user}', CHAR(39), '@', CHAR(39), '${app_db_host}', CHAR(39))
          AND TABLE_SCHEMA = 'ulticode'
    ) + (
        SELECT COUNT(*) FROM information_schema.schema_privileges
        WHERE GRANTEE = CONCAT(CHAR(39), '${app_db_user}', CHAR(39), '@', CHAR(39), '${app_db_host}', CHAR(39))
          AND TABLE_SCHEMA = 'ulticode'
          AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','GRANT OPTION')
    ) + (
        SELECT COUNT(*) FROM information_schema.user_privileges
        WHERE GRANTEE = CONCAT(CHAR(39), '${app_db_user}', CHAR(39), '@', CHAR(39), '${app_db_host}', CHAR(39))
          AND PRIVILEGE_TYPE IN ('SELECT','INSERT','UPDATE','DELETE','ALL PRIVILEGES','GRANT OPTION')
    ) INTO app_dml_grants;

    IF app_dml_grants <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'owner contraction refuses to drop legacy tables while App privileges remain';
    END IF;

    DROP TABLE IF EXISTS
        `ulticode`.`submission_result_outbox`,
        `ulticode`.`judge_outbox`,
        `ulticode`.`submissions`,
        `ulticode`.`notification_delivery_ledger`,
        `ulticode`.`notification_preferences`,
        `ulticode`.`notifications`,
        `ulticode`.`email_logs`,
        `ulticode`.`email_templates`,
        `ulticode`.`notification_command_receipt`;
END//
DELIMITER ;
CALL `_retire_legacy_owner_tables`();
DROP PROCEDURE IF EXISTS `_retire_legacy_owner_tables`;
