-- Revoke dead GRANTs on non-existent tables left by V20260728213000
-- V20260728213000 grants DML on bookmarks, problem_list_items,
-- submission_test_details, and votes to app_rw — tables that no migration
-- ever creates. On a fresh database these GRANT statements fail with
-- Error 1146 and block the entire Flyway chain.
--
-- This migration conditionally revokes those grants (only if the table
-- exists) so existing databases converge without editing the applied
-- V20260728213000. On fresh databases where the tables never existed,
-- the REVOKE is a no-op.

-- Conditionally revoke each dead grant using a prepared statement gated on
-- table existence, so non-existent tables don't raise Error 1146.
DROP PROCEDURE IF EXISTS `_revoke_dead_grants`;
DELIMITER //
CREATE PROCEDURE `_revoke_dead_grants`()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tbl_name VARCHAR(64);
    DECLARE cur CURSOR FOR
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND table_name IN ('bookmarks', 'problem_list_items', 'submission_test_details', 'votes');
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION BEGIN END;

    OPEN cur;
    read_loop: LOOP
        FETCH cur INTO tbl_name;
        IF done = 1 THEN
            LEAVE read_loop;
        END IF;
        SET @sql = CONCAT('REVOKE SELECT, INSERT, UPDATE, DELETE ON `', DATABASE(), '`.`', tbl_name, '` FROM ''app_rw''@''%''');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;
CALL `_revoke_dead_grants`();
DROP PROCEDURE IF EXISTS `_revoke_dead_grants`;
