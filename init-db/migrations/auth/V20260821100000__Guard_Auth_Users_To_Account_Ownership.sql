-- V20260821100000__Guard_Auth_Users_To_Account_Ownership.sql
-- Compatibility follow-up for schemas that already applied the original
-- Auth-owner contract migration. The earlier version remains unchanged so
-- existing Flyway checksums continue to validate.

SET @auth_profile_drop_columns := (
    SELECT GROUP_CONCAT(
        CONCAT('DROP COLUMN `', COLUMN_NAME, '`')
        ORDER BY ORDINAL_POSITION SEPARATOR ', ')
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'users'
      AND COLUMN_NAME IN (
          'name', 'avatar', 'bio', 'company', 'github', 'location', 'twitter',
          'website', 'preferred_language'));

SET @auth_profile_contract_ddl := IF(
    DATABASE() <> 'auth' OR @auth_profile_drop_columns IS NULL,
    'SELECT 1',
    CONCAT('ALTER TABLE `users` ', @auth_profile_drop_columns));

PREPARE auth_profile_contract_stmt FROM @auth_profile_contract_ddl;
EXECUTE auth_profile_contract_stmt;
DEALLOCATE PREPARE auth_profile_contract_stmt;
